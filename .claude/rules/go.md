---
paths:
  - "backend/app/ecommerce/**/*.go"
  - "backend/app/ecommerce/**/go.mod"
---

# Rule Go

Khuôn mẫu: `services/tax-service` (nhỏ, dùng `libs/go/platform` đầy đủ), `services/inventory-service` (đầy đủ:
outbox, Kafka, Redis Lua, integration test). Lint `golangci-lint` theo `.golangci.yml` (standard + bodyclose, errorlint,
gosec, misspell, nilerr, noctx, rowserrcheck, sqlclosecheck, unconvert, revive); format `gofmt` + `goimports`.

Nguồn tham khảo: Effective Go, Go Code Review Comments (wiki), Google Go Style Guide (Decisions, Best Practices),
Uber Go Style Guide, *100 Go Mistakes*.

---

## 1. Định dạng và file

1. Code phải qua `gofmt`/`goimports`; không tranh luận định dạng.
2. Import ba nhóm cách dòng trống: thư viện chuẩn; thư viện ngoài; module trong repo
   (`github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/...`). Alias chỉ khi trùng tên hoặc package proto
   (`commonv1`, `inventoryv1`).
3. Mỗi package có **doc comment tiếng Việt** ở một file (thường file chính):
   `// Package app là use case của tax-service; chỉ biết domain và port Repository.`
4. Thứ tự trong file: const → var → type → constructor `New…` → method → hàm helper không export.
5. Dòng dài thì xuống dòng theo tham số; không có giới hạn cứng nhưng giữ khoảng 120–150 ký tự.
6. Tên file chữ thường, gạch dưới nếu cần (`flash_sale.go` hoặc `flashsale.go` theo package); test `_test.go`.

## 2. Tổ chức package

1. Khung service: `cmd/server/main.go` (composition root), `internal/config`, `internal/domain`, `internal/app`,
   `internal/adapter/{http,grpc,messaging,repository/postgres,repository/redis}`, `internal/integration`,
   `migrations/` (goose, nhúng bằng `embed.go`).
2. `domain` không import gì ngoài thư viện chuẩn và `libs/go/platform/apperr`/`money`. Không import `pgx`, `chi`,
   Kafka, proto.
3. **Port khai báo ở package dùng nó** (`internal/app`), không ở adapter. Interface nhỏ, chỉ gồm method use case cần.
4. `main.go` là nơi duy nhất nối adapter vào use case; không có biến toàn cục giữ dependency, không dùng `init()` để
   khởi tạo trạng thái.
5. Tên package ngắn, chữ thường, một từ, không gạch dưới, không số nhiều chung chung (`util`, `common`, `helpers` cấm).
   Tên package không lặp trong tên định danh (`app.Service`, không `app.AppService`).

## 3. Đặt tên

1. `MixedCaps`/`mixedCaps`, không gạch dưới. Từ viết tắt giữ nguyên hoa/thường cả từ: `OrderID`, `HTTPHandler`,
   `parseURL`, `userID`.
2. Receiver một hoặc hai chữ, thống nhất cho mọi method của type: `s` (Service), `h` (Handler), `r` (Repository),
   `c` (Client). Không dùng `this`/`self`.
3. Constructor `New` + type, trả con trỏ: `func NewService(r Repository) *Service`. Package chỉ có một type chính thì
   `New` (`http.New`).
4. Getter không có tiền tố `Get`: `s.Name()`, không `s.GetName()` (trừ code sinh từ proto).
5. Interface một method đặt tên động từ + `er` khi tự nhiên (`Publisher`, `Verifier`); còn lại theo vai trò
   (`Repository`, `UnitOfWork`, `FlashSaleStore`).
6. Lỗi sentinel `ErrXxx` (`var ErrClaimNotPublished = errors.New("flash sale claim not published")`); type lỗi
   `XxxError`.
7. Tên biến ngắn trong phạm vi ngắn (`i`, `c`, `r`, `ok`), dài hơn khi sống lâu hơn.

## 4. Lỗi

1. Hàm có thể lỗi trả `error` ở vị trí cuối. Kiểm ngay sau lời gọi, thoát sớm; luồng chính không thụt lề:
   ```go
   if err := domain.ValidateClassName(name); err != nil {
       return domain.TaxClass{}, err
   }
   ```
2. Lỗi nghiệp vụ dùng `apperr.Invalid/NotFound/Conflict/Forbidden/Unavailable(format, args...)`; adapter đổi sang
   HTTP/gRPC bằng `apperr.ToHTTP`/`apperr.ToGRPC`. (cart, inventory giữ lỗi riêng trong domain, giữ nguyên.)
3. Bọc lỗi hạ tầng bằng `%w` với ngữ cảnh ngắn, chữ thường, không chữ "failed"/"error" thừa:
   `fmt.Errorf("write outbox: %w", err)`. Không dùng `%v` khi cần caller unwrap.
4. So lỗi bằng `errors.Is`/`errors.As`, không so chuỗi, không so `==` với lỗi đã bọc.
5. Xử lý lỗi một lần: hoặc log, hoặc trả lên; không vừa log vừa trả lên.
6. Không bỏ qua lỗi bằng `_` trừ khi chắc chắn vô hại, kèm comment (`_ = rows.Close() // đã đọc xong`).
7. Không `panic` ngoài `main`/khởi tạo không thể tiếp tục. Không `recover` để che bug.
8. Trả zero value cùng lỗi (`return domain.TaxClass{}, err`), không trả giá trị dở dang.

## 5. Context, goroutine, đồng thời

1. `ctx context.Context` là tham số đầu tiên của mọi hàm có I/O hoặc có thể chậm. Không lưu ctx trong struct, không
   truyền `nil` (dùng `context.Background()` ở main/test).
2. Mọi goroutine phải có cách dừng (ctx hủy hoặc channel đóng) và người chờ (`errgroup`, `sync.WaitGroup`). Không
   goroutine "bắn rồi quên".
3. Ưu tiên `errgroup.WithContext` khi chạy song song nhiều việc có thể lỗi.
4. Channel: người gửi đóng channel, người nhận không đóng. Kích thước buffer có lý do (0 hoặc 1 là mặc định).
5. Mutex đặt cạnh dữ liệu nó bảo vệ, không nhúng `sync.Mutex` vào struct export. Không copy struct chứa mutex.
6. Map dùng đồng thời phải có khoá. Test chạy với `-race`.
7. Timer/ticker phải `Stop()`; dùng `time.NewTimer` thay `time.After` trong vòng lặp.

## 6. Kiểu, struct, interface

1. Chấp nhận interface, trả struct cụ thể.
2. Không tạo interface trước khi có ít nhất hai hiện thực hoặc cần thay khi test.
3. Kiểm tra struct hiện thực interface lúc biên dịch khi quan trọng: `var _ app.Repository = (*Repository)(nil)`.
4. Dùng `any`, không `interface{}`; chỉ khi thật sự không biết kiểu.
5. Khởi tạo struct dùng tên field (`TaxRate{ID: 1, CountryCode: "US"}`); bỏ tên field chỉ trong bảng test ngắn.
6. Zero value nên dùng được; không cần constructor chỉ để gán zero.
7. Enum dùng type riêng + `iota` bắt đầu từ 1 để zero value là "chưa đặt" (`KindInvalid Kind = iota + 1`).
8. Slice trả về có thể là `nil` khi rỗng, nhưng JSON phải ra `[]`: khởi tạo `make([]T, 0)` cho dữ liệu trả qua API.
9. Copy slice/map ở biên khi nhận vào để giữ lại hoặc khi trả ra trạng thái nội bộ.
10. Receiver con trỏ khi method sửa trạng thái hoặc struct lớn; thống nhất một kiểu receiver cho một type.

## 7. HTTP, gRPC, Kafka

1. Router `chi`. Handler có dạng `func (h *Handler) x(w http.ResponseWriter, r *http.Request) error`, đăng ký qua
   `apperr.Handler` (`H(h.listClasses)`); trả lỗi để handler chung đổi sang status.
2. Response thành công qua `httpx.OK(w, status, message, data)` (phong bì `{status, message, data}`); message tiếng
   Anh dạng `"Get tax classes successfully"`.
3. Route khai trong `Mount(r chi.Router)` dưới `/api/ecommerce/<miền>`; route ghi bọc `auth.RequireRole(..., "ADMIN")`.
4. Parse path/query ở adapter (`pathID(r)`), lỗi parse trả `apperr.Invalid`.
5. Đóng `resp.Body` của mọi HTTP client call; request ra ngoài luôn có ctx (`http.NewRequestWithContext`) và timeout.
6. gRPC server ở `adapter/grpc`, map proto ↔ domain tại đó; lỗi qua `apperr.ToGRPC`.
7. Consumer Kafka dùng `libs/go/platform/kafka` (retry + DLQ sẵn); handler idempotent qua `processed_events`.

## 8. Dữ liệu

1. Postgres qua `pgx/v5` (pool từ `libs/go/platform/db`); SQL viết tay với tham số `$1, $2`. Không ORM.
2. `rows.Close()` sau `Query`, kiểm `rows.Err()` sau vòng lặp.
3. Ghi dữ liệu và outbox trong cùng transaction (`libs/go/platform/outbox`, `UnitOfWork`).
4. Tiền là `int64` đơn vị nhỏ nhất, field có hậu tố `Minor` (`TaxableMinor`); đổi với proto bằng
   `libs/go/platform/money`. Không `float64` cho tiền.
5. Id sinh bằng `libs/go/platform/idgen` (Snowflake).
6. Migration goose: `migrations/NNNNN_mo_ta.sql`, luôn có `-- +goose Up` và `-- +goose Down`; nhúng qua `embed.go`.

## 9. Log và cấu hình

1. Log bằng `log/slog`. Có ctx thì dùng biến thể `...Context`: `slog.WarnContext(ctx, "rejected processor reply",
   "request", id, "err", err)`. Key chữ thường; lỗi luôn key `"err"`.
2. Không `fmt.Println`, `log.Printf`, `log.Fatal` trong service (trừ `main` khi chưa có logger).
3. Cấu hình đọc trong `internal/config` từ biến môi trường, kiểm hợp lệ khi khởi động; thiếu biến bắt buộc thì thoát
   với lỗi rõ (fail-closed, như JWT).

## 10. Test

1. Chỉ dùng package `testing` chuẩn; không testify, không gomock. Port giả là struct tự viết trong file test.
2. **Table-driven**: slice `cases` struct, lặp `for _, c := range cases`; cần tên thì dùng `t.Run(c.name, ...)`.
3. Báo lỗi dạng `got …, want …`: `t.Errorf("%+v: got id=%d, want id=%d", c.a, got.ID, c.want)`. `t.Fatalf` khi không
   thể tiếp tục.
4. Helper gọi `t.Helper()`. Dọn dẹp bằng `t.Cleanup`.
5. Test domain đặt cạnh code (`internal/domain/*_test.go`, cùng package). Test cần Postgres/Redis ở
   `internal/integration`, dùng `libs/go/platform/testkit`; bỏ qua khi thiếu `TEST_DATABASE_URL`/Docker.
6. Không `time.Sleep` để chờ; dùng clock giả hoặc chờ có điều kiện với timeout.
7. Chạy `go vet ./... && go test -race ./...` trong module service.
