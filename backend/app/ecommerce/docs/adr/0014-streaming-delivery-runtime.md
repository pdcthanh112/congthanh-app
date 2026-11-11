# 0014. Stream processing, giao hàng theo SLO, supply chain SLSA, eBPF, agentic commerce và độ bền vận hành

- Trạng thái: Proposed

## Bối cảnh
Sau ADR 0012, fraud/recommendation/webhook chỉ thấy dữ liệu theo từng event rời rạc hoặc batch hằng giờ
(lakehouse, ADR 0013): không có feature theo cửa sổ thời gian thực (số lần thanh toán lỗi 24h, đơn dồn dập,
xu hướng 1 giờ, sắp hết hàng). Canary của Argo Rollouts chia traffic bằng số replica và chỉ xét tỉ lệ lỗi lúc
promote; promote giữa môi trường là sửa tay values. Ảnh image đã ký Cosign + SBOM nhưng chưa có provenance
(không chứng minh được image build từ workflow nào). Không có runtime security, network policy L7 hay đo RED
cho service chưa gắn OTel. AI agent của khách (Claude, IDE, app bên thứ ba) không có cách thao tác an toàn
thay khách. Chưa có backup cluster, chaos chỉ chạy tay, SLO viết tay (SLO checkout còn đo nhầm order-service).

## Quyết định
1. **Flink (stream-processor, Java 21 vì image Flink 2.2)** chạy bằng Flink Kubernetes Operator, state RocksDB +
   checkpoint S3 (Pod Identity). Ba pipeline đọc topic outbox và ghi topic compacted `ecommerce.stream.*`
   (proto `ecommerce.stream.v1`): `CustomerRiskFeatures` (cửa sổ 24h theo khách) → fraud-service lưu Redis,
   thêm luật `STREAM_*`; `ProductTrending` (cửa sổ trượt 1h/5 phút) → recommendation-service ưu tiên dữ liệu live
   15 phút, hết hạn thì quay về batch; `StockCover` (số giờ còn bán được) → webhook `inventory.stockout_risk`
   chỉ khi trạng thái đổi. Envelope sinh id tất định (UUIDv3) nên ghi at-least-once vẫn khử trùng được.
2. **Giao hàng tiến dần**: Argo Rollouts chia traffic thật bằng Gateway API (GAMMA HTTPRoute/GRPCRoute, plugin
   `argoproj-labs/gatewayAPI`) thay vì đếm replica, phân tích nền `slo-burn-rate` suốt canary (đốt > 14.4×
   ngân sách là huỷ). **Kargo** promote Freight (bộ image theo SHA) dev → staging tự động, prod qua PR, có bước
   verify. Edge north-south vẫn là ApisixRoute (APISIX chưa thay bằng Gateway API vì chart không tải được trong
   lần này; đánh giá lại sau).
3. **Supply chain SLSA**: mọi image có provenance (`actions/attest-build-provenance`, đẩy vào registry); Kyverno
   kiểm cả chữ ký, SBOM và provenance (builder GitHub-hosted, workflow trên `main`) cho namespace ecommerce và
   streaming. Renovate bổ sung Dependabot cho manifest Argo CD/Helm/Kustomize, chờ 14 ngày sau phát hành.
   Tetragon (audit) theo dõi shell trong container và đọc file nhạy cảm.
4. **eBPF**: Cilium chaining với aws-cni (giữ IP VPC), bật Hubble; CiliumNetworkPolicy ở chế độ audit trước khi
   enforce. Alloy `beyla.ebpf` đo RED + service graph cho pod chưa có OTel SDK, `pyroscope.ebpf` profiling.
5. **Agentic commerce**: commerce-mcp-service (MCP Streamable HTTP, stateless, SDK `mcp` 2.x) cho agent tìm sản
   phẩm, quản lý giỏ, xem và huỷ đơn thay khách. Ba lớp quyền: scope OAuth do khách chọn trên màn consent
   Keycloak (`commerce:read`, `commerce:cart`, `commerce:orders.cancel`, audience `commerce-mcp`), OpenFGA check
   (quan hệ sở hữu lấy từ service nguồn gửi dạng contextual tuple, fail-closed), và service đích tự kiểm token
   chuyển tiếp. Huỷ đơn bắt buộc `confirm=true`; không có tool thanh toán. Chatbot có thêm provider `vllm`:
   model mở (Qwen3-8B) trên KServe chế độ Standard, node GPU do Karpenter cấp, cho trường hợp dữ liệu không được
   rời VPC.
6. **Java**: giữ JVM + virtual threads (đã bật) làm mặc định; thêm GraalVM native image opt-in qua Spring AOT
   (`congthanh.spring-native`), CI kiểm `processAot` mỗi PR và build image `<sha>-native` trên main
   (Paketo). Chart nhận `runtime: java-native`.
7. **Độ bền**: Velero backup tài nguyên + snapshot EBS mỗi cluster (bucket riêng, Pod Identity); Chaos Mesh
   `Schedule` định kỳ trên staging với kỳ vọng ghi ngay trong manifest; SLO chuyển sang **Pyrra** (CRD
   `ServiceLevelObjective`, nạp thẳng vào Mimir ruler), thêm SLO cho pricing, fraud, recommendation, MCP và sửa
   SLO checkout đo đúng checkout-service.

## Hệ quả
- Thêm một runtime (Flink) phải vận hành: savepoint khi nâng cấp, theo dõi checkpoint. Feature streaming là
  phụ trợ: fraud/recommendation vẫn chạy đúng khi stream dừng (bỏ qua dữ liệu cũ hơn 1 giờ / 15 phút).
- Canary cần Gateway API CRD và mesh hỗ trợ GAMMA; HTTPRoute weight do Rollouts sửa nên Argo CD bỏ qua diff đó.
- Kyverno chặn image thiếu provenance: image build ngoài CI (thủ công) không deploy được vào ecommerce/streaming.
- Cilium chaining và Tetragon chạy privileged trên node; bắt đầu ở audit, enforce sau khi xem Hubble/log.
- MCP mở một cửa mới cho bên ngoài: giới hạn tần suất ở APISIX, quyền theo scope; mọi thao tác ghi vẫn đi qua
  API hiện có nên không có đường tắt dữ liệu.
- Node GPU tốn tiền: chỉ staging/prod, Karpenter thu hồi khi rỗng; dev dùng Claude API hoặc Ollama.
- Native image build lâu và cần reachability metadata; giữ JVM làm mặc định, bật native theo từng service sau
  khi đo thực tế.
- Đổi tên IAM role node Karpenter thành tên cố định: nếu cluster đã apply, Terraform sẽ tạo role mới.
