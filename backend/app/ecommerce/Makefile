SHELL := /bin/bash
COMPOSE := docker compose -f deploy/local/docker-compose.yaml --env-file .env
PROFILES ?=
GO_MODULES := libs/go/platform services/cart-service data/ingestion/clickstream-collector services/inventory-service services/media-service services/pricing-service services/search-service services/shipping-service services/tax-service services/webhook-service
TEST_DATABASE_URL ?=
TEST_REDIS_URL ?=

.PHONY: help up down logs ps proto proto-python gen check test test-go test-java lint build-java token

help:
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
	python3 -I services/api-gateway/scripts/gen-routes.py
	python3 -I tools/scripts/gen-asyncapi.py
	python3 -I tools/scripts/gen-kargo.py
	npm run build && npm run schema:emit --workspaces --if-present && npm run supergraph

check: ## Kiểm tra code/cấu hình sinh ra còn khớp (như CI)
	buf lint
	python3 -I services/api-gateway/scripts/gen-routes.py --check
	python3 -I tools/scripts/gen-asyncapi.py --check
	python3 -I tools/scripts/gen-kargo.py --check
	npm run supergraph:check

test: test-go test-java test-node ## Chạy toàn bộ test

test-go: ## Test Go (cần Docker cho Testcontainers, hoặc đặt TEST_DATABASE_URL/TEST_REDIS_URL)
	@for m in $(GO_MODULES); do echo "== $$m"; (cd $$m && go vet ./... && TEST_DATABASE_URL=$(TEST_DATABASE_URL) TEST_REDIS_URL=$(TEST_REDIS_URL) go test -race ./...) || exit 1; done

test-java: ## Test Java
	./gradlew test

test-node: ## Test NestJS/BFF (Vitest; TEST_DATABASE_URL trống thì tự bật Postgres bằng Testcontainers)
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
# Profile tuỳ chọn: airflow (điều phối), bi (Superset), lineage (Marquez), ml (Feast), catalog (OpenMetadata).
# Có "lineage" thì bật luôn gửi lineage từ Spark, dbt, Airflow.
ifneq ($(filter lineage,$(LAKEHOUSE_PROFILES)),)
export OPENLINEAGE_DISABLED := false
export LAKE_SPARK_LISTENERS := io.openlineage.spark.agent.OpenLineageSparkListener
endif
LAKEHOUSE_ALL_PROFILES := --profile lakehouse --profile airflow --profile bi --profile lineage --profile ml --profile catalog --profile dbt

.PHONY: lakehouse-up lakehouse-stop lakehouse-batch lakehouse-dbt lakehouse-cdc lakehouse-sql lakehouse-simulate lakehouse-catalog lakehouse-dashboards

lakehouse-up: .env ## Lakehouse: MinIO, Polaris, Spark, Trino, collector (LAKEHOUSE_PROFILES="lakehouse airflow bi lineage ml catalog")
	$(LAKEHOUSE_COMPOSE) $(foreach p,$(PROFILES) $(LAKEHOUSE_PROFILES),--profile $(p)) up -d --build

lakehouse-stop: ## Dừng riêng các container lakehouse (giữ stack lõi)
	$(LAKEHOUSE_COMPOSE) $(LAKEHOUSE_ALL_PROFILES) stop spark-connect lake-stream trino polaris clickstream-collector \
	  airflow superset marquez marquez-web feast openmetadata openmetadata-search

lakehouse-batch: ## Giải mã protobuf rồi dbt build silver/gold + test một lần (không cần Airflow)
	$(LAKEHOUSE_COMPOSE) exec spark-connect /opt/spark/bin/spark-submit --driver-memory 1g /opt/lakehouse/jobs/decode_events.py
	$(LAKEHOUSE_COMPOSE) --profile dbt run --rm dbt build

lakehouse-dbt: ## Chạy lệnh dbt tuỳ ý: make lakehouse-dbt ARGS="test -s gold" | ARGS="source freshness" | ARGS="docs generate"
	$(LAKEHOUSE_COMPOSE) --profile dbt run --rm dbt $(or $(ARGS),build)

lakehouse-cdc: ## Đăng ký lại connector ods-cdc (sau khi bật apps-java)
	$(LAKEHOUSE_COMPOSE) --profile lakehouse up --no-deps --force-recreate connect-init-lakehouse

lakehouse-sql: ## Mở Trino CLI trên catalog lakehouse
	$(LAKEHOUSE_COMPOSE) exec trino trino --catalog lakehouse

lakehouse-simulate: ## Gửi clickstream giả qua collector (SESSIONS=200)
	python3 -I data/lakehouse/tools/simulate_clickstream.py --sessions $(or $(SESSIONS),200)

lakehouse-dashboards: ## Nạp lại dashboard Superset (sau khi sửa bootstrap_dashboards.py hoặc dbt vừa tạo bảng mới)
	$(LAKEHOUSE_COMPOSE) --profile bi up --no-deps --force-recreate superset-init

lakehouse-catalog: ## Nạp lại metadata Trino + dbt vào OpenMetadata (cần LAKEHOUSE_PROFILES có catalog)
	$(LAKEHOUSE_COMPOSE) --profile dbt run --rm dbt docs generate
	$(LAKEHOUSE_COMPOSE) --profile catalog up --no-deps --force-recreate openmetadata-ingest
