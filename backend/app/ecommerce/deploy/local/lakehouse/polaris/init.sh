#!/bin/sh
# Khởi tạo Polaris cho lakehouse (idempotent, chạy lại an toàn):
#   1. catalog "lakehouse" trỏ vào s3://lakehouse trên MinIO, namespace bronze/ods/silver/gold
#   2. phân quyền theo nguyên tắc tối thiểu:
#        etl     (Spark, dbt)        → catalog role "writer": CATALOG_MANAGE_CONTENT trên toàn catalog
#        analyst (Superset, Feast)   → catalog role "reader": chỉ đọc namespace silver và gold
#      root chỉ dùng để quản trị (script này). Mật khẩu cố định để chạy local, KHÔNG dùng ngoài máy dev.
set -eu
POLARIS=${POLARIS_URL:-http://polaris:8181}
S3_ENDPOINT=${S3_ENDPOINT:-http://minio:9000}
MGMT="$POLARIS/api/management/v1"

TOKEN=$(curl -sf "$POLARIS/api/catalog/v1/oauth/tokens" \
  -d grant_type=client_credentials -d client_id=root -d client_secret=s3cr3t -d scope=PRINCIPAL_ROLE:ALL \
  | sed -E 's/.*"access_token":"([^"]+)".*/\1/')

# call METHOD PATH [JSON]: 2xx hoặc 409 (đã tồn tại) coi như thành công, lỗi khác thì dừng.
call() {
  code=$(curl -s -o /tmp/resp -w '%{http_code}' -X "$1" "$MGMT$2" \
    -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' ${3:+-d "$3"})
  case "$code" in
    2??|409) ;;
    *) echo "$1 $2 lỗi HTTP $code"; cat /tmp/resp; echo; exit 1 ;;
  esac
}

call POST /catalogs "{
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
}"
echo "catalog lakehouse: OK"

for ns in bronze ods silver gold; do
  curl -s -o /dev/null -X POST "$POLARIS/api/catalog/v1/lakehouse/namespaces" \
    -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "{\"namespace\": [\"$ns\"]}"
done
echo "namespace bronze ods silver gold: OK"

# principal TÊN SECRET PRINCIPAL_ROLE CATALOG_ROLE
principal() {
  call POST /principals "{\"principal\": {\"name\": \"$1\"}, \"credentialRotationRequired\": false}"
  call POST "/principals/$1/reset" "{\"clientId\": \"$1\", \"clientSecret\": \"$2\"}"
  call POST /principal-roles "{\"principalRole\": {\"name\": \"$3\"}}"
  call PUT "/principals/$1/principal-roles" "{\"principalRole\": {\"name\": \"$3\"}}"
  call POST /catalogs/lakehouse/catalog-roles "{\"catalogRole\": {\"name\": \"$4\"}}"
  call PUT "/principal-roles/$3/catalog-roles/lakehouse" "{\"catalogRole\": {\"name\": \"$4\"}}"
}

principal etl etl-s3cr3t etl_role writer
call PUT /catalogs/lakehouse/catalog-roles/writer/grants \
  '{"grant": {"type": "catalog", "privilege": "CATALOG_MANAGE_CONTENT"}}'
echo "principal etl → writer (CATALOG_MANAGE_CONTENT): OK"

principal analyst analyst-s3cr3t analyst_role reader
# Liệt kê tên namespace ở cấp catalog (Superset/Trino cần SHOW SCHEMAS); không đọc được bảng ngoài silver/gold.
call PUT /catalogs/lakehouse/catalog-roles/reader/grants \
  '{"grant": {"type": "catalog", "privilege": "NAMESPACE_LIST"}}'
for ns in silver gold; do
  for priv in NAMESPACE_READ_PROPERTIES TABLE_LIST TABLE_READ_PROPERTIES TABLE_READ_DATA; do
    call PUT /catalogs/lakehouse/catalog-roles/reader/grants \
      "{\"grant\": {\"type\": \"namespace\", \"namespace\": [\"$ns\"], \"privilege\": \"$priv\"}}"
  done
done
echo "principal analyst → reader (chỉ đọc silver, gold): OK"
