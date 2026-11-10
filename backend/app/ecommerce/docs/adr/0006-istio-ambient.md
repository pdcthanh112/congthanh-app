# 0006. Istio Ambient mesh

- Trạng thái: Accepted

mTLS tự động giữa mọi pod (danh tính SPIFFE theo ServiceAccount), AuthorizationPolicy từ chối mặc định và chỉ mở các đường gọi đồng bộ trong kiến trúc. Ambient (ztunnel + waypoint) không cần sidecar nên ít tốn tài nguyên và không phải restart pod khi nâng cấp mesh. Retry/timeout/outlier detection cho gRPC đặt ở waypoint thay vì trong code.
