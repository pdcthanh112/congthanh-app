# Lệnh thường dùng cho platform e-commerce. Chạy từ backend/app/ecommerce.
SHELL := /bin/bash
COMPOSE := docker compose -f deploy/local/docker-compose.yaml --env-file .env
PROFILES ?=
GO_MODULES := libs/go/platform services/cart-service data/ingestion/clickstream-collector services/inventory-service services/pricing-service services/webhook-service
TEST_DATABASE_URL ?= postgres://postgres:postgres@localhost:5432/ecommerce?sslmode=disable
TEST_REDIS_URL ?= redis://localhost:6379/15

.PHONY: help up down logs ps proto proto-python gen check test test-go test-java lint build-java token

help: ## Liệt kê lệnh
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  %-14s %s\n", $$1, $$2}'

.env:
	cp .env.example .env

up: .env ## Chạy stack local (PROFILES="observability workflow analytics streaming apps-java apps-node apps-python ollama")
	$(COMPOSE) $(foreach p,$(PROFILES),--profile $(p)) up -d --build

down: ## Dừng stack local
	$(COMPOSE) --profile observability --profile workflow --profile analytics --profile apps-java --profile apps-node --profile apps-python --profile ollama down

logs: ## Xem log (S=tên service)
	$(COMPOSE) logs -f $(S)

ps:
	$(COMPOSE) ps

proto: ## Lint + sinh code từ api/proto (Go + Python)
	buf lint && buf generate
	libs/python/ecommerce-proto/generate.sh

proto-python: ## Chỉ sinh lại libs/python/ecommerce-proto
	libs/python/ecommerce-proto/generate.sh

gen: ## Sinh cấu hình APISIX, AsyncAPI, pipeline Kargo và supergraph GraphQL
	python3 -I tools/scripts/gen-apisix-routes.py
	python3 -I tools/scripts/gen-asyncapi.py
	python3 -I tools/scripts/gen-kargo.py
	npm run build && npm run schema:emit --workspaces --if-present && npm run supergraph

check: ## Kiểm tra code/cấu hình sinh ra còn khớp (như CI)
	buf lint
	python3 -I tools/scripts/gen-apisix-routes.py --check
	python3 -I tools/scripts/gen-asyncapi.py --check
	python3 -I tools/scripts/gen-kargo.py --check
	npm run supergraph:check

test: test-go test-java test-node ## Chạy toàn bộ test

test-go: ## Test Go (cần Postgres/Redis: make up)
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

fga-init: ## Tạo store OpenFGA local và nạp model (in store id + model id để đặt vào .env)
	docker run --rm --network congthanhapp_default -v $(CURDIR)/platform/authz/openfga:/m:ro openfga/cli:v0.7.20 \
	  store create --name ecommerce --model /m/model.fga --api-url http://openfga:8080

fga-test: ## Chạy test model OpenFGA (platform/authz/openfga/tests.fga.yaml)
	docker run --rm -v $(CURDIR)/platform/authz/openfga:/m:ro openfga/cli:v0.7.20 model test --tests /m/tests.fga.yaml

token: ## Lấy access token Keycloak local (USER=, PASS=)
	@curl -s -d client_id=dev-cli -d grant_type=password -d username=$(USER) -d password=$(PASS) \
	  http://localhost:8180/realms/ecommerce/protocol/openid-connect/token | python3 -c 'import sys,json;print(json.load(sys.stdin)["access_token"])'

# ---------------- Data lakehouse (deploy/local/docker-compose.lakehouse.yaml, INSTRUCTION.md mục 12) ----------------
LAKEHOUSE_COMPOSE := docker compose -f deploy/local/docker-compose.yaml -f deploy/local/docker-compose.lakehouse.yaml --env-file .env
LAKEHOUSE_PROFILES ?= lakehouse

.PHONY: lakehouse-up lakehouse-stop lakehouse-batch lakehouse-cdc lakehouse-sql lakehouse-simulate

lakehouse-up: .env ## Lakehouse: MinIO, Polaris, Spark, Trino, collector (LAKEHOUSE_PROFILES="lakehouse airflow", PROFILES=...)
	$(LAKEHOUSE_COMPOSE) $(foreach p,$(PROFILES) $(LAKEHOUSE_PROFILES),--profile $(p)) up -d --build

lakehouse-stop: ## Dừng riêng các container lakehouse (giữ stack lõi)
	$(LAKEHOUSE_COMPOSE) --profile lakehouse --profile airflow stop spark-connect lake-stream trino polaris clickstream-collector airflow

lakehouse-batch: ## Chạy silver → kiểm tra chất lượng → gold một lần (không cần Airflow)
	$(LAKEHOUSE_COMPOSE) exec spark-connect bash -c 'for j in silver checks gold; do /opt/spark/bin/spark-submit --driver-memory 1g /opt/lakehouse/jobs/$$j.py || exit 1; done'

lakehouse-cdc: ## Đăng ký lại connector ods-cdc (sau khi bật apps-java)
	$(LAKEHOUSE_COMPOSE) --profile lakehouse up --no-deps --force-recreate connect-init-lakehouse

lakehouse-sql: ## Mở Trino CLI trên catalog lakehouse
	$(LAKEHOUSE_COMPOSE) exec trino trino --catalog lakehouse

lakehouse-simulate: ## Gửi clickstream giả qua collector (SESSIONS=200)
	python3 -I data/lakehouse/tools/simulate_clickstream.py --sessions $(or $(SESSIONS),200)
