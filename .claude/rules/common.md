# Rule chung (mọi ngôn ngữ)

Áp dụng cho mọi code trong repo. Rule theo ngôn ngữ nằm ở các file cùng thư mục và được ưu tiên khi hai bên khác nhau.
Thứ tự ưu tiên khi có mâu thuẫn: yêu cầu của người dùng > formatter/linter đã cấu hình > rule trong thư mục này >
code xung quanh > style guide bên ngoài.

Nguồn tham khảo (đã chọn lọc cho khớp code hiện có): Google Engineering Practices (code review), *Clean Code*,
*A Philosophy of Software Design*, *The Pragmatic Programmer*, Twelve-Factor App, OWASP ASVS / Cheat Sheet Series,
Conventional Commits.

---

## 1. Cách thay đổi code

1. **Sửa ít nhất có thể** để đạt yêu cầu. Diff nhỏ, người review đọc một lượt là hiểu.
2. Không đổi tên, định dạng lại, sắp xếp lại import hay refactor đoạn code không liên quan tới yêu cầu. Thấy chỗ đáng
   sửa thì ghi lại và báo người dùng, không sửa lẫn vào.
3. Trước khi viết kiểu mới, tìm cách làm sẵn có: service mẫu cùng ngôn ngữ (inventory/tax cho Go, checkout/ledger cho
   Java, catalog cho NestJS, fraud cho Python), file bên cạnh, thư viện trong `libs/*`.
4. Không thêm dependency mới khi `libs/*` hoặc thư viện đang có đã làm được. Thêm dependency thì phải nói lý do.
5. Không để lại code chết: không comment-out code, không để hàm/biến/import không dùng, không `TODO` không có ngữ cảnh.
   `TODO` nếu có phải ghi lý do và điều kiện để làm: `// TODO(ADR 0030): bỏ khi storefront-bff chuyển sang search-service`.
6. Một thay đổi một mục đích. Đổi hành vi và refactor tách thành hai bước (hai commit) nếu cả hai đều cần.
7. Không sửa file sinh tự động (danh sách ở `CLAUDE.md`). Sửa nguồn rồi chạy lệnh sinh.

## 2. Đặt tên

1. Tên nói *cái gì* và *vì sao*, không nói *cách làm*. Dùng thuật ngữ nghiệp vụ đang có trong code và ADR: session,
   quote, claim, reservation, posting, journal entry, store credit, dispute. Không tự đặt từ đồng nghĩa mới cho khái
   niệm đã có tên.
2. Độ dài tên tỷ lệ với phạm vi: biến trong vòng lặp vài dòng được ngắn (`i`, `s`, `p`, `c`); field, tham số public,
   hằng số phải đầy đủ nghĩa.
3. Tên viết tắt cục bộ được chấp nhận vì đã dùng khắp repo: `ctx`, `err`, `tx`, `cmd`, `req`, `res`, `cfg`, `svc`,
   `repo`, `uow`, `id`, `ids`. Không tự chế thêm viết tắt khác (`mgr`, `prc`, `amt`, `qty` → viết đầy đủ).
4. Viết tắt trong định danh viết như một từ theo quy ước ngôn ngữ: Java/TS `OrderId`, `httpClient`, `JsonMapper`;
   Go giữ nguyên chữ hoa cho từ viết tắt (`OrderID`, `HTTPClient`, `URL`).
5. Collection đặt tên số nhiều theo phần tử: `sessions`, `postings`, `lines`, `rates`, `items`. Map đặt theo quan hệ
   `xByY` (`priceByProductId`, `ratesByCountry`). Không thêm hậu tố `List`/`Map`/`Set` trừ khi cần phân biệt hai biến
   cùng nội dung khác kiểu.
6. Boolean đọc như câu hỏi: `active`, `isExpired`, `hasChildren`, `canCancel`, `includeInactive`. Không đặt tên phủ
   định (`notActive`, `disableX = false`).
7. Hàm: động từ cho hành động (`place`, `reserve`, `requote`, `release`), danh từ/tính từ cho truy vấn không side effect
   (`total()`, `balance()`, `isOpen()`). Hàm ném lỗi khi điều kiện sai đặt tiền tố `require`/`ensure`
   (`requireOpen`, `requireCurrency`, `ensureSlugFree`).
8. Hằng số và giá trị enum: `UPPER_SNAKE_CASE`. Mã lỗi máy đọc: `UPPER_SNAKE_CASE` (`PRICE_CHANGED`).
9. Không dùng số/chuỗi "ma thuật": giá trị có ý nghĩa nghiệp vụ đặt thành hằng số có tên, kèm comment lý do
   (`STREAM_MAX_AGE = timedelta(hours=1)`). Giá trị có thể đổi theo môi trường đưa vào cấu hình.

## 3. Hàm và luồng điều khiển

1. **Thoát sớm**: kiểm tra điều kiện tiên quyết ở đầu hàm, sai thì `return`/`throw`/`continue` ngay. Luồng chính
   không bị thụt lề sâu.
2. Không viết `else` sau một nhánh đã `return`/`throw`/`continue`.
3. Mỗi điều kiện validation là một khối `if` riêng với message riêng, để biết chính xác điều kiện nào sai.
4. Không lồng `if` quá 3 tầng, không lồng ternary. Điều kiện dài tách thành biến boolean có tên.
5. Hàm làm một việc ở một mức trừu tượng. Hàm dài hơn khoảng 60 dòng hoặc có nhiều đoạn được comment "bước 1, bước 2"
   thì cân nhắc tách hàm private có tên.
6. Hạn chế tham số: quá 4–5 tham số cùng loại thì gom thành object/record/struct (`Command`, `Draft`, `Params`).
7. Không dùng tham số boolean làm cờ đổi hành vi ở hàm public (`save(x, true)`); tách hai hàm hoặc dùng enum.
8. Tránh side effect ẩn: hàm tên truy vấn không được ghi dữ liệu; hàm sửa trạng thái phải thấy rõ qua tên.
9. Ưu tiên dữ liệu bất biến: value object, DTO, event payload không sửa sau khi tạo. Biến cục bộ không gán lại khi
   không cần.
10. Tra cứu trong vòng lặp thì dựng map trước vòng lặp (O(1)); không lặp lồng nhau trên hai danh sách lớn.

## 4. Lỗi

1. Phân biệt **lỗi nghiệp vụ** (input sai, không tìm thấy, xung đột, bị cấm, phụ thuộc không sẵn sàng) và **lỗi hạ
   tầng** (DB, mạng, bug). Lỗi nghiệp vụ dùng kiểu lỗi có `Kind` của từng ngôn ngữ (Java `DomainException`, Go
   `apperr`, TS `DomainError`, Python exception riêng của service); adapter đổi `Kind` sang HTTP/gRPC status.
2. Không nuốt lỗi. Chỉ bắt lỗi khi xử lý được thật: đổi kiểu, thêm ngữ cảnh, retry, hoặc bỏ qua có chủ đích kèm log và
   comment vì sao bỏ qua là an toàn.
3. Không trả chi tiết lỗi nội bộ (stack trace, SQL, tên bảng) ra API. Lỗi không phải lỗi nghiệp vụ trả 500 với
   message chung và log đầy đủ phía server.
4. Fail-closed: thiếu cấu hình bắt buộc, thiếu currency, không xác thực được token thì từ chối, không đoán giá trị mặc
   định.
5. Message lỗi tiếng Anh, chữ thường đầu câu, không dấu chấm cuối, nêu giá trị gây lỗi khi an toàn:
   `"shipping option UPS/GROUND is not available for this address"`.

## 5. Comment và tài liệu

1. Comment, doc comment, docstring viết **tiếng Việt**, ngắn gọn. Message log, lỗi API, mã lỗi viết **tiếng Anh**.
2. Comment giải thích *vì sao* và quy tắc nghiệp vụ (ràng buộc, ADR, edge case), không lặp lại điều code đã nói.
   ✅ `// Đơn gia hạn dùng cùng phương thức đã lưu nên không chấm gian lận lại.`
   ❌ `// tăng i lên 1`
3. Mọi type/hàm public của port, use case, domain có doc comment một câu nói nó làm gì và điều kiện đặc biệt (trả rỗng
   khi nào, ném lỗi khi nào, có idempotent không).
4. Tham chiếu ADR khi code hiện thực một quyết định kiến trúc: `(ADR 0029)`.
5. Comment sai còn tệ hơn không có: đổi code thì đổi comment đi kèm.

## 6. Tiền, id, thời gian, số

1. **Tiền không bao giờ dùng float/double.** Java `BigDecimal` trong `Money`; Go/Python số nguyên đơn vị nhỏ nhất
   (`int64`/`int`, tên có hậu tố `Minor`/`_minor`); DB `NUMERIC(19,4)` hoặc `BIGINT` minor; proto
   `ecommerce.common.v1.Money`.
2. Mọi số tiền đi kèm `currency` ISO 4217 chữ hoa. Thiếu currency thì báo lỗi, không mặc định USD (ADR 0029). Không
   cộng/so sánh hai số tiền khác currency.
3. Làm tròn tiền có chế độ rõ ràng (Java `RoundingMode.HALF_UP`/`HALF_EVEN` theo nghiệp vụ, Go `money.ToMinor`), số chữ
   số lẻ theo currency (JPY, KRW, VND = 0).
4. Id thực thể là Snowflake BIGINT từ generator trong `libs/*` (port `Ids`/`IdGenerator`). Không tự sinh UUID cho id
   thực thể; UUID chỉ dùng cho khoá idempotency hoặc id suy ra từ khoá (`nameUUIDFromBytes`) khi đã có tiền lệ.
5. Thời gian lấy qua `Clock` được inject, không gọi giờ hệ thống trực tiếp trong domain/use case. Lưu và truyền UTC
   (`TIMESTAMPTZ`, `Instant`, `time.Time` UTC, `datetime` có tz). Chỉ đổi sang múi giờ khi hiển thị hoặc khi nghiệp vụ
   yêu cầu (ngày hạch toán theo `America/New_York`, ADR 0015).
6. Thời lượng dùng kiểu thời lượng (`Duration`, `time.Duration`, `timedelta`), không dùng số giây trần trong API nội bộ.

## 7. Bảo mật

1. Không log, không trả về, không commit: mật khẩu, token, secret, số thẻ, CVV, khoá API. Dữ liệu cá nhân (email, số
   điện thoại, địa chỉ) chỉ log khi thật cần và nên che bớt.
2. Secret chỉ đọc từ biến môi trường/secret manager. `.env` không commit; thêm biến mới vào `.env.example` với giá trị
   giả.
3. Mọi input từ ngoài (HTTP, Kafka, webhook, file) đều không tin cậy: validate kiểu, độ dài, miền giá trị ở adapter vào,
   validate quy tắc nghiệp vụ ở domain.
4. SQL luôn dùng tham số bind, không nối chuỗi input vào SQL. Tên cột/bảng động phải lấy từ danh sách cho phép.
5. Gọi URL do người dùng cung cấp (webhook, ảnh) phải chặn SSRF như `webhook-service` đang làm.
6. Kiểm quyền ở service, không chỉ dựa vào gateway: token được kiểm lại, quyền theo quan hệ đi qua OpenFGA (ADR 0010).
   Endpoint ghi dữ liệu của admin phải yêu cầu role rõ ràng.
7. Webhook đến phải kiểm chữ ký (HMAC/Stripe signature) trước khi xử lý.

## 8. Đồng thời, idempotency, độ tin cậy

1. Mọi handler của event Kafka phải idempotent (bảng `processed_events`), vì event có thể đến nhiều lần.
2. API tạo tài nguyên có tác động tiền/hàng nhận `Idempotency-Key`; gọi lại cùng key trả kết quả cũ.
3. Cập nhật có thể tranh chấp dùng khoá lạc quan (`version`) hoặc ràng buộc DB, không đọc-rồi-ghi không bảo vệ.
4. Gọi service khác luôn có timeout; retry chỉ cho thao tác idempotent, có backoff và giới hạn số lần.
5. Không giữ transaction DB trong lúc gọi mạng ra ngoài.
6. Activity của Temporal phải idempotent; đổi logic workflow phải giữ tương thích bằng versioning.

## 9. Log, trace, metric

1. Log có cấu trúc (key-value), dùng logger chuẩn của từng ngôn ngữ (SLF4J, `slog`, NestJS `Logger`, `logging`).
   Không `System.out`, `fmt.Println`, `console.log`, `print`.
2. Mức log: `error` cho lỗi cần người xử lý; `warn` cho tình huống bất thường đã tự xử lý (bỏ qua, fallback có chủ
   đích); `info` cho sự kiện vòng đời (khởi động, bật worker, job dọn dẹp); `debug` cho chi tiết khi điều tra.
3. Log kèm id nghiệp vụ để tra được (`orderId`, `sessionId`, `eventId`), không log cả object lớn.
4. Không log trong vòng lặp nóng mỗi phần tử; gom số lượng rồi log một lần.
5. Trace đã có OpenTelemetry tự động; không tự tạo span trừ khi đoạn code đáng đo riêng.

## 10. Cấu hình

1. Mọi cấu hình đọc từ biến môi trường (Twelve-Factor): Go `internal/config`, Java `application.yaml` với
   `${ENV:default}`, TS `config/env.ts`, Python `config.py`.
2. Biến mới phải có ở `.env.example` của service, `deploy/local` nếu cần chạy local, và Helm values.
3. Giá trị mặc định chỉ dùng cho thứ an toàn khi mặc định (cổng, timeout). Secret, URL dịch vụ ngoài, currency không có
   mặc định.

## 11. Test

1. Test theo kim tự tháp: nhiều test domain (không cần Docker), ít hơn test use case với port giả, ít test tích hợp với
   Postgres/Redis thật.
2. Port giả viết tay (map trong bộ nhớ, ghi lại lời gọi), không dùng thư viện mock cho code mới.
3. Một test kiểm một hành vi; tên test mô tả hành vi và kết quả mong đợi.
4. Cấu trúc Arrange / Act / Assert rõ ràng, cách nhau bằng dòng trống khi test dài.
5. Không phụ thuộc thời gian thật, thứ tự chạy, mạng ngoài. Dùng `Clock` giả, dữ liệu tự tạo trong test.
6. Sửa bug phải kèm test tái hiện bug (fail trước khi sửa, pass sau khi sửa) nếu test được ở tầng domain/use case.
7. Không xoá hay nới assertion để test qua. Không tắt test kiến trúc.

## 12. Git và commit (chỉ khi người dùng yêu cầu commit)

1. Commit message theo Conventional Commits: `feat(checkout): ...`, `fix(ledger): ...`, `refactor`, `test`, `docs`,
   `chore`, `build`, `ci`. Scope là tên service hoặc thư viện.
2. Dòng đầu dưới 72 ký tự, mệnh lệnh, không dấu chấm cuối. Thân giải thích vì sao.
3. Không commit `.env`, file build (`build/`, `dist/`, `node_modules/`), file IDE.
