# Continuous profiling (Pyroscope)

- Go: service bật profiling qua SDK `grafana/pyroscope-go` khi có `PYROSCOPE_SERVER_ADDRESS` (đợt sau thêm vào libs/go/platform).
- Java: Pyroscope Java agent đi kèm OTel Java agent (`tools/agents`), bật bằng `PYROSCOPE_SERVER_ADDRESS`.
- Trên K8s: Grafana Alloy (eBPF) thu profile mọi pod, không cần sửa code — xem `k8s/alloy-values.yaml`.
