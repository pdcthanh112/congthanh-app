# Istio Ambient mesh

Ambient (ztunnel + waypoint) cho mTLS tự động, L4 authz, retry/timeout/circuit-breaking L7 mà không cần sidecar.

```bash
istioctl install -f platform/mesh/istio/istio-ambient.yaml -y
kubectl apply -k platform/mesh/istio
```

- `namespaces.yaml`: gắn nhãn `istio.io/dataplane-mode=ambient` cho mọi namespace ứng dụng.
- `peer-authentication.yaml`: mTLS STRICT toàn mesh.
- `authorization-policies.yaml`: mặc định từ chối, chỉ mở các đường gọi có trong kiến trúc (zero trust, danh tính SPIFFE theo ServiceAccount).
- `traffic.yaml`: waypoint + retry/timeout/outlier detection cho gRPC nội bộ.
