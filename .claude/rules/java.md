---
paths:
  - "backend/app/ecommerce/services/**/*.java"
  - "backend/app/ecommerce/libs/java/**/*.java"
  - "backend/app/ecommerce/data/streaming/**/*.java"
  - "backend/app/ecommerce/**/*.gradle.kts"
---

# Rule Java / Spring Boot

Khuôn mẫu: `services/checkout-service`, `services/ledger-service`, `services/order-service`.
`product-service` còn nhiều code đời cũ (Lombok `@Data`/`@Builder`/`@Getter`/`@Setter`, `ResponseEntity`,
`ResponseObject`, JPA entity): sửa trong đó thì giữ nhất quán với file xung quanh, code mới ở chỗ khác không bắt chước.

Nguồn tham khảo: *Effective Java* (Bloch, 3rd ed.), Google Java Style Guide, Alibaba Java Coding Guidelines,
Spring Boot reference, ArchUnit. Chỗ nào repo khác guide (thụt 4 space, dòng 150) thì theo repo.

---

## 1. Định dạng

1. Thụt **4 space**, không tab. Dòng tối đa **150** ký tự (`.editorconfig`). Dòng nối tiếp thụt thêm 8 space hoặc căn
   theo tham số như code hiện có.
2. Một file một top-level class. Thứ tự trong class: hằng `static final` → field `static` → field instance → constructor
   → method public → method package/private → type lồng (record, enum, interface).
3. **Luôn có `{}`** cho `if`, `else`, `for`, `while`, `do`, kể cả thân một dòng. Ngoại lệ duy nhất: factory một dòng
   kiểu `public static X invalid(String m) { return new X(Kind.INVALID, m); }`.
4. Mở ngoặc `{` cùng dòng (K&R). `else`/`catch`/`finally` cùng dòng với `}`.
5. Không import wildcard (`import java.util.*;`). Không import không dùng. Nhóm import: `com.congthanh.*` và thư viện
   ngoài (theo alphabet), dòng trống, `java.*`/`javax.*`, dòng trống, `import static`.
6. Không viết tên đầy đủ (`java.util.UUID`, `org.springframework...`) giữa code; import ở đầu file. Chỉ dùng tên đầy đủ
   khi trùng tên hai class.
7. Mỗi dòng một khai báo biến. Annotation của class/method/field mỗi cái một dòng; annotation tham số cùng dòng.
8. Literal `long` viết hoa `L` (`100L`). Số lớn dùng `_` (`1_000_000`).

## 2. Đặt tên

1. Package chữ thường liền: `com.congthanh.<tên>service.<tầng>` (`com.congthanh.ledgerservice.adapter.in.web`).
2. Class/record/enum/interface: `PascalCase`, danh từ. Port không có tiền tố `I` hay hậu tố `Port`: `Pricing`, `Catalog`,
   `SessionRepository`, `CheckoutWorkflows`, `Ids`.
3. Hậu tố theo vai trò, thống nhất trong repo:
   | Vai trò | Tên |
   |---|---|
   | Use case (port in) | `XxxUseCase`, hoặc tên nghiệp vụ (`LedgerQueries`, `ManualEntries`) |
   | Hiện thực use case | `XxxService` trong `application/service` |
   | Controller REST | `XxxController` |
   | gRPC server | `XxxGrpcService`; gRPC client adapter `XxxGrpcAdapter` |
   | HTTP client adapter | `XxxHttpAdapter` |
   | Kafka consumer | `XxxEventsConsumer` / `XxxConsumer` |
   | Repository JDBC | `JdbcXxxRepository`; JPA `XxxJpaRepository` |
   | Cấu hình bean | `XxxConfig`; properties `XxxProperties` |
   | DTO web | `XxxDto` (ra), `XxxRequest` (vào) |
   | Lỗi | `XxxException` |
   | Test | `XxxTest`, tích hợp `XxxIntegrationTest` |
4. Method/field/biến: `camelCase`, không `snake_case`, không tiền tố `m`/`_`.
5. Accessor của domain và record không có tiền tố `get`: `status()`, `orderId()`, `totals()`. Chỉ dùng `getX()` ở chỗ
   framework bắt buộc (JPA entity, Jackson bean cũ).
6. Hằng số `static final` bất biến: `UPPER_SNAKE_CASE`. Logger là ngoại lệ, tên `log`.

## 3. Kiểu dữ liệu và thiết kế class

1. **`record`** cho DTO, command, query, kết quả, value object, event payload, cấu hình nhỏ (`Settings`). Validation
   bất biến đặt trong compact constructor.
2. **Domain model** (aggregate) là class có hành vi, trạng thái chỉ đổi qua method nghiệp vụ có tên
   (`changeAddresses`, `selectShipping`, `requireOpen`, `expireIfDue`). Không có setter công khai, không để field
   `public`.
3. Tạo đối tượng phức tạp qua **static factory** có tên (`CheckoutSession.open(...)`, `Money.of(...)`,
   `Posting.debit(...)`), không phơi constructor nhiều tham số.
4. Giảm phạm vi truy cập tối đa: class adapter/controller để package-private khi không cần public; field `private final`.
5. Không dùng `Object`, `Map<String, Object>`, `Object[]` cho dữ liệu nghiệp vụ; khai báo record cụ thể. `Map` chỉ cho
   dữ liệu thật sự là map (tra cứu theo khoá).
6. Enum cho tập giá trị đóng (`SessionStatus`, `Kind`); xử lý bằng switch expression để compiler báo thiếu nhánh. Map
   khoá enum dùng `EnumMap`.
7. So sánh: enum dùng `==`; object dùng `equals`/`Objects.equals`; hằng chuỗi đặt trước để tránh NPE
   (`"DEBIT".equalsIgnoreCase(side)`). `BigDecimal` so bằng `compareTo`, không `equals` (khác scale).
8. `equals`/`hashCode` phải đi cùng nhau; record đã tự có.
9. Kế thừa chỉ khi thật sự là quan hệ "là một"; còn lại dùng composition. Class không thiết kế để kế thừa thì `final`.
10. `var` chỉ dùng khi kiểu đã hiển nhiên ở vế phải (`var tb = queries.trialBalance(asOf);`,
    `var list = new ArrayList<Line>();`). Không dùng `var` khi vế phải là lời gọi mà kiểu không rõ.

## 4. Null, Optional, collection

1. Không trả `null` cho collection hay mảng; trả `List.of()`, `Map.of()`.
2. Truy vấn có thể không có kết quả trả `Optional<T>`. Không dùng `Optional` làm field, tham số, phần tử collection.
   Không gọi `Optional.get()` khi chưa kiểm; dùng `orElseThrow(() -> XxxException.notFound(...))`, `orElse`, `map`.
3. Kiểm chuỗi rỗng: `x == null || x.isBlank()`. Kiểm collection rỗng: `list == null || list.isEmpty()`. Repo không dùng
   `StringUtils`/`CollectionUtils` cho việc này; không thêm.
4. Tham số bắt buộc ở biên domain kiểm bằng `Objects.requireNonNull(x, "x")` hoặc validation nghiệp vụ với message rõ.
5. Không gộp kiểm tra null với so sánh khác thành một biểu thức dài; tách thành các `if` hoặc biến trung gian.
6. Collection trả ra ngoài là bất biến (`List.copyOf`, `.toList()`); collection nhận vào thì copy trước khi giữ lại.
7. Khởi tạo collection có dung lượng biết trước: `new ArrayList<>(n)`; cần giữ thứ tự chèn thì `LinkedHashMap`.

## 5. Stream và vòng lặp

1. Stream dùng cho biến đổi ngắn, không side effect: `map`/`filter`/`toList()`, `min`/`max` với `Comparator`,
   `collect(groupingBy)` đơn giản. Kết thúc bằng `.toList()` (Java 16+), không `collect(Collectors.toList())`.
2. Viết vòng `for (Item item : items)` khi: có `throw`, có `continue`/`break`, ghi ra biến bên ngoài, gọi I/O, hoặc logic
   nhiều hơn 3 bước. Không dùng `forEach` với lambda có side effect.
3. Mỗi thao tác stream một dòng khi chuỗi dài hơn 2 bước.
4. Không lồng stream trong stream; dựng `Map` trước để tra cứu.
5. Không dùng `parallelStream()` trong service.

## 6. Ngoại lệ

1. Lỗi nghiệp vụ kế thừa `DomainException` (`libs/java/common-core`) theo mẫu `LedgerException`: enum `Kind`, factory
   tĩnh `invalid`, `notFound`, `conflict`, `forbidden`, `unavailable`; `CommonProblemHandler` đổi sang `ProblemDetail`
   (RFC 9457) với HTTP status tương ứng.
2. Không ném `RuntimeException`, `IllegalArgumentException`, `IllegalStateException` trần từ use case/domain cho lỗi mà
   client cần biết. `IllegalStateException` chỉ cho lỗi lập trình (trạng thái không thể xảy ra).
3. Không `catch (Exception e)` rộng trừ ở biên (consumer, scheduler) để log và đẩy DLQ/retry; khi đó phải log đủ ngữ
   cảnh.
4. Bọc checked exception của hạ tầng thành unchecked kèm cause; không mất cause.
5. Không dùng exception cho luồng điều khiển bình thường.
6. `try-with-resources` cho mọi thứ `AutoCloseable`.
7. Không `return` trong `finally`.

## 7. Spring

1. **Inject qua constructor**: `@RequiredArgsConstructor` + field `private final`, hoặc constructor viết tay. Không
   `@Autowired` trên field. Port tuỳ chọn mới dùng setter `@Autowired(required = false)` có comment lý do.
2. Bean của use case khai báo bằng `@Service` + `@RequiredArgsConstructor`, hoặc bằng `@Bean` trong `config/XxxConfig`
   khi cần dựng tay (như `CheckoutService`). Domain không có annotation Spring.
3. Controller: class package-private, `@RestController`, `@RequestMapping("/api/ecommerce/<miền>")`, method
   package-private trả DTO trực tiếp; tạo mới thêm `@ResponseStatus(HttpStatus.CREATED)`. Chỉ dùng `ResponseEntity`
   khi cần header hoặc status động.
4. Validate request bằng `@Valid` + `jakarta.validation` trên record `XxxRequest` (`@NotBlank`, `@NotNull`,
   `@Positive`, `@Size`). Quy tắc nghiệp vụ vẫn kiểm trong domain.
5. Người gọi lấy từ `@AuthenticationPrincipal Jwt jwt` (`jwt.getSubject()`); không đọc header token thủ công.
6. Cấu hình đọc từ `application.yaml` (`${ENV:default}`). Nhóm nhiều giá trị liên quan dùng `@ConfigurationProperties`
   record (`XxxProperties`); giá trị lẻ dùng `@Value` trong `config/` hoặc adapter. Use case và domain không đọc
   `@Value`, chỉ nhận giá trị qua constructor (như `CheckoutService.Settings`). Mặc định production chung nằm ở
   `libs/java/platform/.../ecommerce-platform-defaults.yaml`.
7. `@Transactional` đặt ở application service (method public), không ở controller hay repository. Đọc dùng
   `@Transactional(readOnly = true)`. Adapter bắt buộc chạy trong transaction có sẵn dùng
   `Propagation.MANDATORY`.
8. Ghi event bằng `OutboxWriter` trong cùng transaction với dữ liệu. Không gọi `KafkaTemplate` từ use case.
9. Persistence mới dùng **`JdbcClient`** với SQL viết tay trong `adapter/out/persistence/JdbcXxxRepository`; bind tham số
   có tên (`:id`). JPA chỉ còn ở product, order, payment, promotion; không thêm entity JPA mới ở service khác.
10. Không gọi mạng ra ngoài (gRPC, HTTP) trong khi đang mở transaction.
11. Scheduler (`@Scheduled`) phải an toàn khi nhiều pod cùng chạy (khoá DB, `SKIP LOCKED`, hoặc idempotent).

## 8. Lục giác (ArchUnit kiểm)

1. `domain` không import Spring, JPA, Jackson, gRPC, Kafka, `adapter`, `application`.
2. `application` chỉ import `domain` và port của chính nó; không import `adapter`.
3. `adapter.in` gọi port in (use case); `adapter.out` hiện thực port out. Adapter vào không gọi adapter ra.
4. Map giữa DTO/proto/row và domain nằm ở adapter (`XxxDto.of(domain)`, mapper riêng), không ở domain.
5. Không import package của service khác; dùng chung qua `libs/java/*` hoặc proto.

## 9. Log

1. `private static final Logger log = LoggerFactory.getLogger(X.class);` (SLF4J). Không `System.out`/`printStackTrace`.
2. Dùng placeholder `{}`: `log.warn("subscription {} promotion {} not applied: {}", id, code, error);`. Không nối
   chuỗi, không `String.format` trong log.
3. Exception truyền làm tham số cuối để có stack trace: `log.error("unhandled exception", e);`.
4. Không log token, số thẻ, payload thanh toán, dữ liệu cá nhân đầy đủ.

## 10. Đồng thời

1. Không dùng `synchronized` cho dữ liệu nhiều pod cùng sửa; dùng khoá lạc quan (`version`) hoặc ràng buộc DB.
2. Không tự tạo `Thread`/`Executors` trần; dùng executor do Spring quản lý.
3. Field của bean singleton phải bất biến hoặc thread-safe.

## 11. Test

1. JUnit 5 + AssertJ (`assertThat`, `assertThatThrownBy`). Không dùng assert của JUnit/Hamcrest cho code mới.
2. Class test package-private, method `void` package-private, tên camelCase mô tả hành vi:
   `placeRequotesAndRejectsChangedTotal`, `sessionsExpireAndArePrivate`.
3. Port giả viết tay trong class test (map trong bộ nhớ, list ghi lại lời gọi như `placed`, `cancelled`). Không Mockito
   cho code mới.
4. `Clock` giả (`Clock.fixed` hoặc clock tự tăng) để test hết hạn, không `Thread.sleep`.
5. Mỗi service có `ArchitectureTest` dùng `HexagonalArchitecture.strict(BASE)`; không nới luật để qua test.
6. Test cần Docker (Testcontainers) gắn `@Tag("integration")`, tên `*IntegrationTest`, chạy bằng
   `./gradlew :services:<svc>:integrationTest`.
7. Saga Temporal test bằng `TestWorkflowEnvironment` (time-skipping), như `CheckoutWorkflowTest`.

## 12. Gradle

1. Service dùng plugin convention `id("congthanh.spring-service")` (build-logic); không lặp cấu hình chung trong
   `build.gradle.kts` của service.
2. Phiên bản dependency khai trong version catalog (`gradle/libs.versions.toml`), không ghi cứng trong service.
