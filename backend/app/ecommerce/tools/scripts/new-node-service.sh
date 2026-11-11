#!/usr/bin/env bash
# Tạo khung service NestJS mới từ catalog-service: ./tools/scripts/new-node-service.sh <tên> "<mô tả>"
set -euo pipefail
name=$1; desc=${2:-""}; schema=${name//-/_}
root=$(cd "$(dirname "$0")/../.." && pwd); src=$root/services/catalog-service; dst=$root/services/$name
mkdir -p "$dst/src/config" "$dst/src/domain" "$dst/src/application" "$dst/src/adapters/in" "$dst/src/adapters/out/persistence" "$dst/test"
for f in tsconfig.json tsconfig.build.json drizzle.config.ts src/main.ts src/migrate.ts src/emit-schema.ts; do
  sed "s/catalog_service/$schema/g; s/catalog-service/$name/g" "$src/$f" > "$dst/$f"
done
node -e '
const fs=require("fs");const p=JSON.parse(fs.readFileSync(process.argv[1]+"/package.json"));
p.name="@ecommerce/"+process.argv[2];p.description=process.argv[3];
fs.writeFileSync(process.argv[4]+"/package.json",JSON.stringify(p,null,2)+"\n");' "$src" "$name" "$desc" "$dst"
echo "created services/$name"
