#!/bin/sh
# Tạo catalog Iceberg "lakehouse" trong Polaris, dữ liệu nằm ở s3://lakehouse trên MinIO.
# Idempotent: catalog đã có thì Polaris trả 409 và script bỏ qua.
set -eu
POLARIS=${POLARIS_URL:-http://polaris:8181}
S3_ENDPOINT=${S3_ENDPOINT:-http://minio:9000}

TOKEN=$(curl -sf "$POLARIS/api/catalog/v1/oauth/tokens" \
  -d grant_type=client_credentials -d client_id=root -d client_secret=s3cr3t -d scope=PRINCIPAL_ROLE:ALL \
  | sed -E 's/.*"access_token":"([^"]+)".*/\1/')

code=$(curl -s -o /tmp/resp -w '%{http_code}' -X POST "$POLARIS/api/management/v1/catalogs" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{
    \"catalog\": {
      \"name\": \"lakehouse\",
      \"type\": \"INTERNAL\",
      \"properties\": {\"default-base-location\": \"s3://lakehouse\"},
      \"storageConfigInfo\": {
        \"storageType\": \"S3\",
        \"allowedLocations\": [\"s3://lakehouse/\"],
        \"endpoint\": \"$S3_ENDPOINT\",
        \"endpointInternal\": \"$S3_ENDPOINT\",
        \"pathStyleAccess\": true,
        \"stsUnavailable\": true
      }
    }
  }")
case "$code" in
  201) echo "đã tạo catalog lakehouse" ;;
  409) echo "catalog lakehouse đã có" ;;
  *) echo "tạo catalog lỗi HTTP $code"; cat /tmp/resp; exit 1 ;;
esac

# Namespace theo tầng dữ liệu (job Spark cũng tự tạo nếu thiếu).
for ns in bronze ods silver gold; do
  curl -s -o /dev/null -X POST "$POLARIS/api/catalog/v1/lakehouse/namespaces" \
    -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
    -d "{\"namespace\": [\"$ns\"]}"
done
echo "namespace: bronze ods silver gold"
