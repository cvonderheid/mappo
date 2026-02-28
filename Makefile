SHELL := /bin/bash
.DEFAULT_GOAL := help
RETENTION_DAYS ?= 90
COMPOSE_FILE := infra/docker-compose.yml
IAC_DIR := infra/pulumi
PULUMI_STACK ?= dev
IAC_TARGET_EXPORT ?= .data/mappo-target-inventory.json
PULUMI_CONFIG_PASSPHRASE ?= mappo-local-dev
export PULUMI_CONFIG_PASSPHRASE

.PHONY: help install install-deps install-backend install-frontend \
	dev dev-up dev-down dev-logs dev-backend dev-frontend build build-backend build-frontend \
	lint lint-backend lint-frontend typecheck typecheck-backend typecheck-frontend \
	test test-backend test-frontend test-frontend-e2e import-targets retention-prune \
	azure-auth-bootstrap azure-tenant-map azure-onboard-multitenant-runtime dev-backend-azure azure-preflight bootstrap-releases \
	partner-center-token partner-center-api \
	cleanup-legacy-managed-app-demo \
	db-migrate db-validate db-info db-clean db-reset models-gen openapi client-gen \
	iac-install iac-stack-init iac-preview iac-up iac-destroy iac-export-targets iac-prepare-dual-stack \
	workflow-discipline-check docs-consistency-check golden-principles-check check-no-demo-leak \
	phase1-gate-fast phase1-gate-full

help: ## Show available targets
	@awk 'BEGIN {FS = ":.*##"; printf "\nTargets:\n"} /^[a-zA-Z0-9_.-]+:.*##/ {printf "  %-28s %s\n", $$1, $$2} END {printf "\n"}' $(MAKEFILE_LIST)

install: install-deps db-migrate lint typecheck test phase1-gate-full build ## Full bootstrap: deps + DB + verification + build

install-deps: install-backend install-frontend ## Install all dependencies only

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

dev-backend-azure: ## Run backend with local Azure env file loaded
	./scripts/with_mappo_azure_env.sh uv --directory backend run --package mappo-backend -- uvicorn app.main:app --host 0.0.0.0 --port 8010 --reload

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

import-targets: ## Import fleet targets from .data/mappo-target-inventory.json
	uv --directory backend run --package mappo-backend -- python scripts/import_targets.py --file $(abspath .data/mappo-target-inventory.json) --clear-runs

bootstrap-releases: ## Bootstrap default release records (set FORCE=1 to replace existing)
	uv --directory backend run --package mappo-backend -- python scripts/bootstrap_releases.py $(if $(FORCE),--force,)

azure-auth-bootstrap: ## Create Azure SP credentials and write .data/mappo-azure.env
	./scripts/azure_auth_bootstrap.sh

azure-tenant-map: ## Build MAPPO_AZURE_TENANT_BY_SUBSCRIPTION from Azure account context
	@if [ -z "$(SUBSCRIPTION_IDS)" ]; then \
		echo "usage: make azure-tenant-map SUBSCRIPTION_IDS=<sub1,sub2,...> [ENV_FILE=.data/mappo-azure.env]"; \
		exit 2; \
	fi
	./scripts/azure_tenant_map.sh \
		--subscriptions "$(SUBSCRIPTION_IDS)" \
		$(if $(ENV_FILE),--env-file "$(ENV_FILE)",)

azure-onboard-multitenant-runtime: ## Onboard runtime app across target subscriptions/tenants (multi-tenant + SP + RBAC)
	@if [ -z "$(CLIENT_ID)" ] || [ -z "$(SUBSCRIPTION_IDS)" ]; then \
		echo "usage: make azure-onboard-multitenant-runtime CLIENT_ID=<app-id> SUBSCRIPTION_IDS=<sub1,sub2,...> [HOME_SUBSCRIPTION_ID=<sub-id>] [INVENTORY_FILE=.data/mappo-target-inventory.json]"; \
		exit 2; \
	fi
	./scripts/azure_onboard_multitenant_runtime.sh \
		--client-id "$(CLIENT_ID)" \
		--target-subscriptions "$(SUBSCRIPTION_IDS)" \
		$(if $(HOME_SUBSCRIPTION_ID),--home-subscription-id "$(HOME_SUBSCRIPTION_ID)",) \
		$(if $(INVENTORY_FILE),--inventory-file "$(INVENTORY_FILE)",)

partner-center-token: ## Acquire Partner Center access token into .data/mappo-partnercenter.env
	./scripts/partner_center_get_token.sh --env-file .data/mappo-partnercenter.env

partner-center-api: ## Call Partner Center API (requires URL=<https://...>)
	@if [ -z "$(URL)" ]; then \
		echo "usage: make partner-center-api URL=<https://...> [METHOD=GET] [BODY_FILE=payload.json]"; \
		exit 2; \
	fi
	./scripts/partner_center_api.sh \
		--method "$(or $(METHOD),GET)" \
		--url "$(URL)" \
		$(if $(BODY_FILE),--body-file "$(BODY_FILE)",)

cleanup-legacy-managed-app-demo: ## Delete legacy pre-Pulumi managed-app demo resource groups
	@if [ -z "$(PROVIDER_SUBSCRIPTION_ID)" ] || [ -z "$(CUSTOMER_SUBSCRIPTION_ID)" ]; then \
		echo "usage: make cleanup-legacy-managed-app-demo PROVIDER_SUBSCRIPTION_ID=<id> CUSTOMER_SUBSCRIPTION_ID=<id>"; \
		exit 2; \
	fi
	./scripts/cleanup_legacy_managed_app_demo.sh \
		--provider-subscription-id "$(PROVIDER_SUBSCRIPTION_ID)" \
		--customer-subscription-id "$(CUSTOMER_SUBSCRIPTION_ID)"

azure-preflight: ## Validate Azure environment readiness for production-like multi-tenant demo
	./scripts/azure_preflight.sh

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

iac-install: ## Install Pulumi IaC dependencies
	cd $(IAC_DIR) && npm install

iac-stack-init: ## Select or initialize Pulumi stack (default: dev)
	cd $(IAC_DIR) && pulumi login --local
	cd $(IAC_DIR) && (pulumi stack select $(PULUMI_STACK) || pulumi stack init $(PULUMI_STACK))

iac-preview: iac-install iac-stack-init ## Preview Pulumi managed-app IaC changes
	cd $(IAC_DIR) && npm run build && pulumi preview --stack $(PULUMI_STACK) --non-interactive

iac-up: iac-install iac-stack-init ## Deploy Pulumi managed-app IaC changes
	cd $(IAC_DIR) && npm run build && pulumi up --stack $(PULUMI_STACK) --yes

iac-destroy: iac-install iac-stack-init ## Destroy Pulumi managed-app IaC stack resources
	cd $(IAC_DIR) && npm run build && pulumi destroy --stack $(PULUMI_STACK) --yes

iac-export-targets: iac-stack-init ## Export MAPPO target inventory from Pulumi stack output
	@mkdir -p $(dir $(IAC_TARGET_EXPORT))
	cd $(IAC_DIR) && pulumi stack output mappoTargetInventory --stack $(PULUMI_STACK) --json > $(abspath $(IAC_TARGET_EXPORT))
	@echo "wrote $(abspath $(IAC_TARGET_EXPORT))"

iac-prepare-dual-stack: ## Generate dual-subscription Pulumi stack config for managed-app demo targets
	@if [ -z "$(CUSTOMER_SUBSCRIPTION_ID)" ]; then \
		echo "usage: make iac-prepare-dual-stack CUSTOMER_SUBSCRIPTION_ID=<id> [PULUMI_STACK=<name>] [PROVIDER_SUBSCRIPTION_ID=<id>] [PROVIDER_PRINCIPAL_OBJECT_ID=<object-id>] [CUSTOMER_PRINCIPAL_OBJECT_ID=<object-id>] [LOCATION=<region>] [TARGET_COUNT=<n>]"; \
		exit 2; \
	fi
	./scripts/iac_prepare_dual_stack.sh \
		--stack "$(PULUMI_STACK)" \
		$(if $(PROVIDER_SUBSCRIPTION_ID),--provider-subscription-id "$(PROVIDER_SUBSCRIPTION_ID)",) \
		--customer-subscription-id "$(CUSTOMER_SUBSCRIPTION_ID)" \
		$(if $(PROVIDER_PRINCIPAL_OBJECT_ID),--provider-principal-object-id "$(PROVIDER_PRINCIPAL_OBJECT_ID)",) \
		$(if $(PUBLISHER_PRINCIPAL_OBJECT_ID),--publisher-principal-object-id "$(PUBLISHER_PRINCIPAL_OBJECT_ID)",) \
		$(if $(CUSTOMER_PRINCIPAL_OBJECT_ID),--customer-principal-object-id "$(CUSTOMER_PRINCIPAL_OBJECT_ID)",) \
		$(if $(LOCATION),--location "$(LOCATION)",) \
		$(if $(TARGET_COUNT),--target-count "$(TARGET_COUNT)",)

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
