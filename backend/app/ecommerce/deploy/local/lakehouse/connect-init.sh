#!/bin/sh
# 1) Đăng ký JSON Schema clickstream vào Apicurio (group "clickstream", artifact "clickstream.events-value").
# 2) Đăng ký connector Debezium ods-cdc. Bảng nguồn do service Java tạo (Liquibase), nên khi chưa chạy
#    profile apps-java connector sẽ FAILED; script thử lại một lúc rồi dừng, chạy lại bằng `make lakehouse-cdc`.
set -u
REGISTRY=${REGISTRY_URL:-http://schema-registry:8080/apis/registry/v3}
CONNECT=${CONNECT_URL:-http://connect:8083}

for i in $(seq 1 30); do
  curl -sf "$REGISTRY/system/info" >/dev/null && break
  sleep 2
done
schema=$(sed 's/"/\\"/g' /schemas/clickstream-event.json | tr -d '\n')
code=$(curl -s -o /tmp/resp -w '%{http_code}' -X POST "$REGISTRY/groups/clickstream/artifacts?ifExists=FIND_OR_CREATE_VERSION" \
  -H 'Content-Type: application/json' \
  -d "{\"artifactId\":\"clickstream.events-value\",\"artifactType\":\"JSON\",
       \"firstVersion\":{\"content\":{\"content\":\"$schema\",\"contentType\":\"application/json\"}}}")
echo "schema clickstream.events-value: HTTP $code"

curl -sf -X PUT -H 'Content-Type: application/json' --data @/connectors/ods-cdc.json \
  "$CONNECT/connectors/ods-cdc/config" >/dev/null && echo "registered ods-cdc"

for i in $(seq 1 20); do
  sleep 15
  status=$(curl -s "$CONNECT/connectors/ods-cdc/status")
  case "$status" in
    *'"tasks":[{"id":0,"state":"RUNNING"'*) echo "ods-cdc RUNNING"; exit 0 ;;
    *FAILED*)
      echo "ods-cdc chưa chạy được (thiếu bảng nguồn? cần profile apps-java), thử lại..."
      curl -s -X POST "$CONNECT/connectors/ods-cdc/restart?includeTasks=true&onlyFailed=true" >/dev/null ;;
  esac
done
echo "ods-cdc vẫn chưa RUNNING; bật apps-java rồi chạy: make lakehouse-cdc"
exit 0
