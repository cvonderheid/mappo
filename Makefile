SHELL := /bin/bash
.DEFAULT_GOAL := help

.PHONY: help install install-backend install-frontend \
	dev-backend dev-frontend build build-backend build-frontend \
	lint lint-backend lint-frontend typecheck typecheck-backend typecheck-frontend \
	test test-backend test-frontend \
	workflow-discipline-check docs-consistency-check golden-principles-check check-no-demo-leak \
	phase1-gate-fast phase1-gate-full

help: ## Show available targets
	@awk 'BEGIN {FS = ":.*##"; printf "\nTargets:\n"} /^[a-zA-Z0-9_.-]+:.*##/ {printf "  %-28s %s\n", $$1, $$2} END {printf "\n"}' $(MAKEFILE_LIST)

install: install-backend install-frontend ## Install all dependencies

install-backend: ## Install backend dependencies via uv
	uv sync --all-packages --all-groups

install-frontend: ## Install frontend dependencies via npm
	cd frontend && npm install

dev-backend: ## Run FastAPI backend server
	uv --directory backend run --package mappo-backend -- uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload

dev-frontend: ## Run React frontend
	cd frontend && npm run dev -- --host 0.0.0.0 --port 5173

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

test: test-backend test-frontend ## Run backend + frontend tests

test-backend: ## Run backend tests
	uv --directory backend run --package mappo-backend -- pytest -q

test-frontend: ## Run frontend tests
	cd frontend && npm run test

workflow-discipline-check: ## Validate required planning artifacts and structure
	python3 scripts/workflow_discipline_check.py

docs-consistency-check: ## Validate required docs/plan consistency markers
	python3 scripts/docs_consistency_check.py

golden-principles-check: ## Validate golden principles contract markers
	python3 scripts/golden_principles_check.py

check-no-demo-leak: ## Scan production paths for demo-only runtime leakage markers
	python3 scripts/check_no_demo_leak.py

phase1-gate-fast: workflow-discipline-check docs-consistency-check golden-principles-check ## Fast quality gate for Phase 1
	@echo "phase1-gate-fast: PASS"

phase1-gate-full: phase1-gate-fast check-no-demo-leak ## Full quality gate for Phase 1
	@echo "phase1-gate-full: PASS"
