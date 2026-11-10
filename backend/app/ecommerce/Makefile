# Lệnh thường dùng cho platform e-commerce. Chạy từ backend/app/ecommerce.
SHELL := /bin/bash
COMPOSE := docker compose -f deploy/local/docker-compose.yaml --env-file .env
PROFILES ?=
GO_MODULES := libs/go/platform services/cart-service services/inventory-service
TEST_DATABASE_URL ?= postgres://postgres:postgres@localhost:5432/ecommerce?sslmode=disable
TEST_REDIS_URL ?= redis://localhost:6379/15

.PHONY: help up down logs ps proto gen check test test-go test-java lint build-java token

help: ## Liệt kê lệnh
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  %-14s %s\n", $$1, $$2}'

.env:
	cp .env.example .env

up: .env ## Chạy stack local (PROFILES="observability workflow analytics apps-java apps-node apps-python ollama")
	$(COMPOSE) $(foreach p,$(PROFILES),--profile $(p)) up -d --build

down: ## Dừng stack local
	$(COMPOSE) --profile observability --profile workflow --profile analytics --profile apps-java --profile apps-node --profile apps-python --profile ollama down

logs: ## Xem log (S=tên service)
	$(COMPOSE) logs -f $(S)

ps:
	$(COMPOSE) ps

proto: ## Lint + sinh code từ api/proto
	buf lint && buf generate

gen: ## Sinh cấu hình APISIX, AsyncAPI và supergraph GraphQL
	python3 -I tools/scripts/gen-apisix-routes.py
	python3 -I tools/scripts/gen-asyncapi.py
	npm run build && npm run schema:emit --workspaces --if-present && npm run supergraph

check: ## Kiểm tra code/cấu hình sinh ra còn khớp (như CI)
	buf lint
	python3 -I tools/scripts/gen-apisix-routes.py --check
	python3 -I tools/scripts/gen-asyncapi.py --check
	npm run supergraph:check

test: test-go test-java test-node ## Chạy toàn bộ test

test-go: ## Test Go (cần Postgres/Valkey: make up)
	@for m in $(GO_MODULES); do echo "== $$m"; (cd $$m && go vet ./... && TEST_DATABASE_URL=$(TEST_DATABASE_URL) TEST_REDIS_URL=$(TEST_REDIS_URL) go test -race ./...) || exit 1; done

test-java: ## Test Java
	./gradlew test

test-node: ## Test NestJS/BFF (Vitest; test Postgres chạy khi có TEST_DATABASE_URL)
	TEST_DATABASE_URL=$(TEST_DATABASE_URL) npm test

lint: ## golangci-lint cho Go; Biome + dependency-cruiser cho TS
	@for m in $(GO_MODULES); do (cd $$m && golangci-lint run ./...) || exit 1; done
	npm run lint

build-java: ## Build jar các service Java
	./gradlew build -x test

token: ## Lấy access token Keycloak local (USER=, PASS=)
	@curl -s -d client_id=dev-cli -d grant_type=password -d username=$(USER) -d password=$(PASS) \
	  http://localhost:8180/realms/ecommerce/protocol/openid-connect/token | python3 -c 'import sys,json;print(json.load(sys.stdin)["access_token"])'
