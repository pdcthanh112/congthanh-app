#!/usr/bin/env bash
# Sinh Python protobuf (chỉ message, không gRPC stub) cho event và kiểu dùng chung từ api/proto.
# Chạy: make proto-python   (cần uv). Code sinh ra được commit giống libs/go/proto.
set -euo pipefail
cd "$(dirname "$0")"
ROOT=../../..
OUT=src
rm -rf "$OUT/ecommerce"
mkdir -p "$OUT"
mapfile -t FILES < <(cd "$ROOT/api/proto" && find ecommerce -name '*_events.proto' -o -name 'money.proto' -o -name 'event_envelope.proto' | sort)
uvx --exclude-newer 2026-10-27T00:00:00Z --from grpcio-tools==1.85.0 python -m grpc_tools.protoc \
  -I"$ROOT/api/proto" --python_out="$OUT" --pyi_out="$OUT" "${FILES[@]}"
# Gói namespace: thêm __init__.py để hatchling đóng gói đủ thư mục.
find "$OUT/ecommerce" -type d -exec touch {}/__init__.py \;
