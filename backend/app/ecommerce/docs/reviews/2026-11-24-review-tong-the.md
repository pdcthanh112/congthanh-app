# Review tổng thể backend/app/ecommerce (2026-11-24)

## Tóm tắt

Nhìn chung nền tảng giữ kỷ luật tốt. Luật lục giác được tuân thủ: ArchUnit Java pass, domain Go và Python không import
hạ tầng. Không có SQL nối chuỗi từ input, không có secret cứng nguy hiểm. Mọi topic Kafka đang dùng đều được khai trong
`topics.yaml` kèm DLQ. Consumer đều idempotent, qua `processed_events` hoặc version ngoài của Elasticsearch (search-service).
Các file sinh (gateway, AsyncAPI, Kargo, supergraph) khớp với nguồn.

Có 3 vấn đề nên xử lý trước: 1 lỗ hổng phân quyền, 1 lỗi làm khách mất tiền trong saga, và 1 lỗi lệch giờ có thể khiến
webhook không được gửi. Ngoài ra có một loạt vấn đề mức trung bình và việc vệ sinh code. Review chỉ đọc code, không sửa gì.

## Kết quả build, test, lint

| Phần | Kết quả |
|---|---|
| Java (`./gradlew test`, gồm ArchUnit) | Pass toàn bộ |
| Go `go vet` + `go test` (9 service) | Unit test pass. 2 integration test fail: `TestPricingFlow` (pricing), `TestWebhookDelivery` (webhook) |
| Go `golangci-lint` | Không chạy được vì máy chưa cài |
| TS typecheck | Pass |
| TS Vitest (13 file, 76 test) | Pass |
| TS lint (Biome) | Fail: 138 lỗi (124 format, 12 organizeImports, 2 `noExplicitAny` ở `libs/ts/platform/src/db`) |
| TS dependency-cruiser | Fail: 4 vi phạm `inbound-not-outbound` |
| Python ruff + pytest (7 service) | Pass, trừ translate-service (1 lỗi ruff do khoảng trắng thừa, 1 file chưa format). supplier-service có 7 test API lỗi vì không kết nối được Postgres (lỗi môi trường, không phải lỗi code) |
| Gen check (routes, AsyncAPI, Kargo, supergraph) | Pass. `buf lint` không chạy được vì máy chưa cài `buf` |

Lưu ý: khi có Docker, `go test ./...` tự bật Testcontainers. Vì vậy 2 integration test Go ở trên đã chạy trên Docker
(container tự dọn sau khi chạy). Theo CLAUDE.md, việc này lẽ ra chỉ làm khi được bảo. Lần sau tôi sẽ loại
`internal/integration` ra khỏi lệnh test.

## Ưu tiên cao

### 1. product-service: SELLER nào cũng sửa hoặc xoá được sản phẩm của người khác

`config/SecurityConfig.java` cho mọi `SELLER` ghi vào `/api/ecommerce/**`. Tuy nhiên bảng `products` không có cột chủ sở
hữu (supplier hay seller), và không controller nào đọc `jwt.getSubject()`. Do đó một seller có thể gọi `PUT /products`,
`PATCH /products/{id}/status` hay `DELETE /products/delete/{id}` lên sản phẩm của bất kỳ ai. Điều này vi phạm rule bảo
mật 7.6: phải kiểm quyền ở service, và quyền theo quan hệ đi qua OpenFGA (ADR 0010).

Đề xuất: thêm `supplier_id` vào sản phẩm (NOT NULL ở DB), rồi kiểm quyền sở hữu ở use case hoặc qua OpenFGA. Trong lúc
chờ, có thể chỉ cho `ADMIN` ghi.

### 2. Checkout saga: xác nhận đơn thất bại thì khách mất store credit và điểm thưởng

Trong `CheckoutWorkflowImpl`, sau pivot, workflow gọi `storedValueMustComplete.capture` và `loyaltyMustComplete.commit`
rồi mới gọi `confirmOrder`. Nếu `confirmOrder` bị từ chối, nhánh catch chỉ `refund` qua cổng thanh toán và
`cancelOrder`. Store credit đã trừ hẳn và điểm đã commit thì không được hoàn. Khách trả bằng store credit hoặc điểm sẽ
mất số đó.

Đề xuất: trong nhánh lỗi, hoàn store credit và hoàn điểm bằng activity idempotent theo `orderId`. Cách khác là dời bước
capture/commit tender ra sau `confirmOrder`. Thay đổi phải bọc trong `Workflow.getVersion`.

### 3. webhook-service: lượt giao có thể không bao giờ được nhận vì giờ app lệch với giờ DB

`FanOut` đặt `next_attempt_at` theo giờ của app (`s.now()`), còn `ClaimDue` so `next_attempt_at <= now()` theo giờ
Postgres. Khi giờ DB chậm hơn giờ app (thường gặp với container Docker trên macOS), lượt giao bị coi là chưa đến hạn và
worker bỏ qua. Integration test `TestWebhookDelivery` fail đúng triệu chứng này (`n=0 calls=0`). Đây là suy luận từ
code; tôi chưa chạy lại test để xác nhận.

Đề xuất: chỉ dùng một nguồn giờ. Ví dụ, để DB đặt `next_attempt_at = now()` khi enqueue, hoặc truyền giờ của app vào
`ClaimDue` thay cho `now()`.

## Ưu tiên trung bình

4. **tax-service tính tiền bằng float.** `POST /tax/calculate` nhận `taxableAmount float64` rồi tính
   `roundHalfUp(amount * 10^n)`. Với những số như 1.005, phép nhân 100 cho ra 100.4999…, nên kết quả bị làm tròn lệch 1
   đơn vị nhỏ nhất. Điều này vi phạm rule 6.1. Nên nhận chuỗi thập phân hoặc số nguyên đơn vị nhỏ nhất (minor).
5. **Test pricing chưa cập nhật theo ADR 0029.** `TestPricingFlow` gọi `Prices` với `Context{Groups: ...}` mà không có
   currency. Code mới báo lỗi là đúng, nhưng test bỏ qua lỗi (`ws, _ :=`) nên thông báo fail khó hiểu. Cần thêm
   `Currency` vào test và kiểm `err`.
6. **Làm tròn cố định 2 chữ số bất kể currency.** `ReturnRequest` (returns), `Account` (stored-value) và
   `ProductReadModelConsumer.amount` (product) đều dùng `setScale(2)`. Với JPY, KRW, VND (không có chữ số lẻ), số tiền
   hoàn sẽ mang phần lẻ không hợp lệ. Điều này vi phạm rule 6.3.
7. **payment-service trả 400 cho event Stripe không xử lý.** `StripeProvider.verifyCallback` ném lỗi `invalid` với mọi
   loại event ngoài 4 loại đã biết. Stripe coi 4xx là giao thất bại và gửi lại trong nhiều ngày. Nên trả 200 cho event
   được bỏ qua, chỉ trả 400 khi sai chữ ký.
8. **product-service để GET công khai cả endpoint back-office.** Quy tắc `GET /api/ecommerce/**` là `permitAll`, nên
   bao gồm cả `/products/{id}`, `/products/{id}/detail`, `/products/back-office/slug/{slug}` và
   `/backoffice/product-variations/{parentId}`. Nếu các endpoint này trả cả sản phẩm DRAFT hoặc INACTIVE thì sản phẩm
   chưa công bố sẽ bị lộ. Cần xác nhận lại truy vấn của các endpoint này.
9. **Một số read model có giá mà thiếu currency.** `ProductDetail.Price float64` trong cart-service và `Price float64`
   trong search-service không đi kèm currency (ADR 0029), lại còn dùng float.
10. **Luật lục giác TS bị vi phạm ở 4 service.** File `adapters/in/kafka/privacy-erasure.ts` của customer, message,
    notification và review import thẳng `adapters/out/persistence/schema.ts`. Nên đi qua use case hoặc port.
11. **Secret có giá trị mặc định.** `DJANGO_SECRET_KEY` mặc định là `dev-only-not-secret` (supplier).
    `PAYMENT_SANDBOX_SECRET` mặc định là `local-sandbox-secret` (payment; sandbox mặc định tắt). Rule 10.3 yêu cầu
    secret không có giá trị mặc định, nên service phải dừng khởi động khi thiếu biến này ở production.

## Vệ sinh code và công cụ

- Chạy `npm run format` (Biome) để sửa 136 lỗi format và import. Sửa tay 2 chỗ `any` trong `libs/ts/platform/src/db`.
- translate-service: chạy `uv run ruff check --fix . && uv run ruff format .`.
- Cài `golangci-lint` và `buf` lên máy để `make lint` và `make check` chạy đủ như CI.
- cart-service ép kết quả `strconv.Atoi` sang `int32` mà không kiểm tràn số: một số rất lớn sẽ quay vòng thành số nhỏ
  trông hợp lệ. golangci-lint (gosec G115) sẽ bắt được lỗi này.
- fraud-service ghi cứng bảng ngưỡng theo currency trong code (`app/domain/rules.py`). Nên đưa vào cấu hình.
- Có cảnh báo deprecation: `starlette.testclient` (fraud, recommendation), và API deprecated trong `ProductGrpcService`
  (các trường giá kiểu `double`).

## Những điểm đã kiểm và ổn

- Phân quyền route Go: mọi route ghi đều bọc `RequireRole`. Cart kiểm chủ sở hữu ở mọi thao tác và lọc item theo cart.
- Java: mọi service đều có `anyRequest().denyAll()`. Webhook Stripe (payment, dispute) và sandbox đều kiểm chữ ký,
  HMAC được so sánh theo thời gian hằng định.
- Saga checkout: bù trừ được đăng ký trước khi gọi activity, và các thay đổi đã bọc `Workflow.getVersion`.
- Không có UUID làm id thực thể. Domain không gọi giờ hệ thống trực tiếp (Python dùng clock được inject). Use case
  không publish Kafka trực tiếp; ngoại lệ là flash sale của inventory, đã có ADR 0007 và 0018.
- SQL đều dùng tham số bind; những chỗ nối chuỗi chỉ ghép hằng tên cột.

## Phạm vi chưa phủ

Review đã quét toàn repo bằng công cụ, và đọc kỹ các luồng chính liên quan tới tiền và bảo mật: checkout, payment,
returns, cart, product, webhook, tax, pricing. Chưa đọc kỹ từng dòng ở các service còn lại (ledger, loyalty,
subscription, dispute, reconciliation, các service AI). Chưa chạy `:integrationTest` của Java, `make fga-test`,
`buf breaking`, và chưa chạy stack local.
