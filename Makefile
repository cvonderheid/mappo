SHELL := /bin/bash
.DEFAULT_GOAL := help
RETENTION_DAYS ?= 90
COMPOSE_FILE := infra/docker-compose.yml

.PHONY: help install install-backend install-frontend \
	dev dev-up dev-down dev-logs dev-backend dev-frontend build build-backend build-frontend \
	lint lint-backend lint-frontend typecheck typecheck-backend typecheck-frontend \
	test test-backend test-frontend test-frontend-e2e demo-reset retention-prune \
	db-migrate db-validate db-info db-clean db-reset models-gen openapi client-gen \
	workflow-discipline-check docs-consistency-check golden-principles-check check-no-demo-leak \
	phase1-gate-fast phase1-gate-full

help: ## Show available targets
	@awk 'BEGIN {FS = ":.*##"; printf "\nTargets:\n"} /^[a-zA-Z0-9_.-]+:.*##/ {printf "  %-28s %s\n", $$1, $$2} END {printf "\n"}' $(MAKEFILE_LIST)

install: install-backend install-frontend ## Install all dependencies

install-backend: ## Install backend dependencies via uv
	uv sync --all-packages --all-groups

install-frontend: ## Install frontend dependencies via npm
	cd frontend && npm install

dev: ## Run full local stack (Postgres, migrate, backend, frontend)
	docker compose -f $(COMPOSE_FILE) up

dev-up: ## Start full local stack in background
	docker compose -f $(COMPOSE_FILE) up -d

dev-down: ## Stop full local stack
	docker compose -f $(COMPOSE_FILE) down --remove-orphans

dev-logs: ## Tail local stack logs
	docker compose -f $(COMPOSE_FILE) logs -f

dev-backend: ## Run FastAPI backend server
	uv --directory backend run --package mappo-backend -- uvicorn app.main:app --host 0.0.0.0 --port 8010 --reload

dev-frontend: ## Run React frontend
	cd frontend && npm run dev -- --host 0.0.0.0 --port 5174

build: build-backend build-frontend ## Build backend and frontend

build-backend: ## Build backend package
	uv --directory backend build --package mappo-backend

build-frontend: ## Build frontend app
	cd frontend && npm run build

lint: lint-backend lint-frontend ## Run backend + frontend linters

lint-backend: ## Run backend lint checks
	uv --directory backend run --package mappo-backend -- ruff check .

lint-frontend: ## Run frontend lint checks
	cd frontend && npm run lint

typecheck: typecheck-backend typecheck-frontend ## Run backend + frontend type checks

typecheck-backend: ## Run backend mypy checks
	uv --directory backend run --package mappo-backend -- mypy app tests

typecheck-frontend: ## Run frontend TypeScript checks
	cd frontend && npm run typecheck

test: test-backend test-frontend ## Run backend + frontend unit tests

test-backend: ## Run backend tests
	uv --directory backend run --package mappo-backend -- pytest -q

test-frontend: ## Run frontend tests
	cd frontend && npm run test

test-frontend-e2e: ## Run frontend Playwright click-through tests
	cd frontend && npm run test:e2e:ci

demo-reset: ## Reset and reseed deterministic 10-target demo data
	uv --directory backend run --package mappo-backend -- python scripts/demo_reset.py

retention-prune: ## Prune run history older than RETENTION_DAYS
	uv --directory backend run --package mappo-backend -- python scripts/prune_retention.py --days $(RETENTION_DAYS)

db-migrate: ## Run Flyway migrations against local Postgres
	./backend/scripts/ensure_db.sh
	./backend/scripts/flyway.sh migrate

db-validate: ## Validate Flyway migrations against local Postgres
	./backend/scripts/flyway.sh validate

db-info: ## Show Flyway migration status
	./backend/scripts/flyway.sh info

db-clean: ## Drop all objects in local database (development only)
	./backend/scripts/flyway.sh clean

db-reset: db-clean db-migrate ## Recreate schema from scratch

models-gen: ## Regenerate SQLAlchemy models from database schema
	cd backend && ./scripts/gen_models.sh

openapi: ## Generate backend OpenAPI schema
	uv --directory backend run --package mappo-backend -- python scripts/generate_openapi.py

client-gen: openapi ## Generate frontend API types from OpenAPI
	cd frontend && npm run client-gen

workflow-discipline-check: ## Validate required planning artifacts and structure
	python3 scripts/workflow_discipline_check.py

docs-consistency-check: ## Validate required docs/plan consistency markers
	python3 scripts/docs_consistency_check.py

golden-principles-check: ## Validate golden principles contract markers
	python3 scripts/golden_principles_check.py

check-no-demo-leak: ## Scan production paths for demo-only runtime leakage markers
	python3 scripts/check_no_demo_leak.py

phase1-gate-fast: workflow-discipline-check docs-consistency-check golden-principles-check openapi client-gen ## Fast quality gate for Phase 1
	@echo "phase1-gate-fast: PASS"

phase1-gate-full: phase1-gate-fast check-no-demo-leak test-frontend-e2e ## Full quality gate for Phase 1
	@echo "phase1-gate-full: PASS"
