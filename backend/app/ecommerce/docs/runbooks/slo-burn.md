# Runbook: cảnh báo SLO burn rate

1. Mở Grafana → dashboard của service trong nhãn `slo` của cảnh báo; xem lỗi theo `http.route`/`rpc.method` (spanmetrics).
2. Có deploy trong 30 phút gần nhất? → `kubectl argo rollouts abort <service> -n ecommerce` rồi `undo`.
3. Lỗi phụ thuộc (DB, Kafka, service khác)? Xem service graph trong Tempo; kiểm tra `kubectl get kafka,cluster -A`.
4. Flash sale: tắt nhanh bằng flag `inventory.flash-sale.claim` trên Unleash.
5. Ghi lại timeline, mở postmortem nếu đốt > 10% error budget tháng.
