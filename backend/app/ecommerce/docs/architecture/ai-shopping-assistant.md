# Đề xuất: Chatbox AI trợ lý mua sắm (agent có tool)

Trạng thái: đề xuất, chưa có code. Ngày 2026-11-26. Khi chốt hướng thì viết ADR 0033.

## 1. Hiện trạng: đã có gì

| Thành phần | Đang làm gì | Thiếu gì để thành agent |
|---|---|---|
| `services/chatbot-service` (Python, FastAPI) | RAG một bước: gọi `search-service` lấy 5 sản phẩm, nhét vào system prompt, stream câu trả lời qua SSE. Lịch sử 10 lượt ở Redis (TTL 24h), khoá theo `subject` + `sessionId`. Provider `anthropic` (mặc định `claude-opus-5-5`, effort `low`, đã bật server-side fallback), `ollama`, `vllm`. | Không có tool calling; không chuyển tiếp token của khách nên không xem/sửa giỏ, đơn được; lịch sử chỉ lưu text (không lưu được `tool_use`/`tool_result`); system prompt nói thẳng "không xem được đơn". |
| `ai/commerce-mcp-service` (MCP Streamable HTTP) | Tool cho agent **bên ngoài**: `search_products`, `get_product`, `view_cart`, `add_to_cart`, `update_cart_item`, `list_orders`, `get_order`, `cancel_order(confirm=true)`. Ba lớp quyền: scope OAuth (`commerce:read/cart/orders.cancel`) → OpenFGA (contextual tuple) → service đích tự kiểm token. Không có tool thanh toán. | Token phải có audience `commerce-mcp` (client `commerce-mcp-agent`), token của `storefront-web` không dùng được. Chưa có gợi ý, tìm có facet, checkout. |
| Storefront GraphQL (Cosmo Router, `/graphql`) | Đủ thao tác của web: `searchCatalog` (facet, ADR 0030), `searchProducts`, `product`, `recommendedForYou`, `trendingProducts`, `myCart`, `addToCart`, `updateCartItem`, `removeCartItem`, `quotePromotion`, `checkout`, `checkoutSession`, `checkoutProgress`, `myOrders`, `order`, `cancelOrder`, địa chỉ giao hàng. Router + subgraph tự kiểm JWT. | — |
| `recommendation-service` | Bought-together, similar (pgvector HNSW, mặc định embedding hashing, tuỳ chọn Ollama `bge-m3`), trending, for-you. | Chưa có tìm kiếm ngữ nghĩa theo câu hỏi tự do. |
| `search-service` (Go, Elasticsearch) | BM25 + lọc thuộc tính/option/brand/giá + facet. | Không có kNN/hybrid. |
| `checkout-service` | Phiên checkout: mở, báo giá, sửa, `place` (bắt buộc `Idempotency-Key`, `expectedTotal`, ConfirmationToken Stripe), huỷ. | — |
| Gateway APISIX | Route `/api/ecommerce/chatbot` giới hạn 20 req/60s theo IP; MCP 120/60s. | Khách đăng nhập nên giới hạn theo người dùng, không theo IP. |
| Frontend | Next.js 15 + next-auth (Keycloak). Mobile Flutter. | Chưa có widget chat nào. |

Kết luận: **không cần service mới**. Nâng `chatbot-service` từ "RAG một bước" thành agent có vòng lặp tool, gọi API sẵn có bằng token của chính khách.

## 2. Kiến trúc đề xuất

```
Web (Next.js widget) / Mobile (Flutter)
   │  POST /api/ecommerce/chatbot/chat   (Authorization: Bearer <token storefront-web>, SSE)
   │  POST /api/ecommerce/chatbot/actions/{id}/confirm   (khách bấm nút xác nhận)
   ▼
APISIX ── rate limit theo consumer/IP ──► chatbot-service (Python, hexagonal sẵn có)
                                             │ domain: Conversation, ToolPolicy, PendingAction
                                             │ app: AgentLoop (Messages API + tool use, streaming)
                                             │ ports: LLM, Tools, History, PendingActions, Policies
                                             ├─► Claude API (claude-opus-5-5)  | vLLM khi dữ liệu không được rời VPC
                                             ├─► Cosmo Router /graphql (persisted operations, token khách)
                                             ├─► recommendation-service, search-service (đọc công khai)
                                             ├─► Redis (lịch sử nóng, pending action, budget)
                                             └─► Postgres schema `chatbot` (hội thoại để audit/eval, outbox)
```

### 2.1 Vì sao tool gọi GraphQL storefront, không gọi thẳng service hay qua MCP

- GraphQL là **cùng cửa web đang dùng**: cùng kiểm JWT, cùng OpenFGA, cùng validate. Agent không có đường tắt dữ liệu, đúng tinh thần ADR 0014 cho MCP.
- Đã có sẵn những thứ MCP chưa có (gợi ý, facet, mã giảm giá, checkout session).
- Dùng **persisted operations** (danh sách query cố định đã đăng ký với Cosmo Router): agent không gửi được GraphQL tuỳ ý, mỗi tool ứng với một operation.
- `commerce-mcp-service` giữ nguyên vai trò cửa cho agent bên ngoài (Claude Desktop, ChatGPT…). Về sau có thể cho MCP service cũng gọi GraphQL để hai bên chung một tầng.
- Phương án thay thế: chatbot-service làm MCP client tới commerce-mcp-service nội bộ, đổi token bằng Keycloak Token Exchange (RFC 8693) sang audience `commerce-mcp`. Gọn hơn về tái dùng, nhưng phải thêm token exchange và bổ sung tool cho MCP. Không chọn cho giai đoạn đầu.

### 2.2 Vòng lặp agent

- Dùng **Messages API + vòng lặp tool tự viết** (hoặc Tool Runner của SDK `anthropic`) trong `chatbot-service`, không dùng Managed Agents: tool cần token của khách và mạng nội bộ cluster, cần độ trễ thấp mỗi lượt chat, và service đã có harness, SSE, Redis.
- Mỗi lượt: tối đa ~6 vòng tool, có `task_budget`/đếm token để dừng êm. Chạy song song các tool đọc trong cùng một lượt; trả mọi `tool_result` trong một message.
- Tool định nghĩa `strict: true`, `eager_input_streaming: true` (đang stream) và validate input lại trong code trước khi chạy.
- Opus 5.5 không cho `tool_choice` ép buộc (`any`/`tool` trả 400): dùng `auto` và hướng dẫn trong prompt.
- Giữ `fallbacks: "default"` như adapter hiện tại; xử lý `stop_reason == "refusal"`.

### 2.3 Bộ tool

Tool được bật theo trạng thái đăng nhập. Khách vãng lai chỉ có nhóm đọc công khai (cart-service hiện không có giỏ khách, nên muốn thêm giỏ thì widget mời đăng nhập).

| Tool | Gọi tới | Rủi ro | Cách chạy |
|---|---|---|---|
| `search_catalog(query, filters)` | GraphQL `searchCatalog` (facet, giá, thuộc tính) | đọc | chạy ngay |
| `get_product(id\|slug)` | GraphQL `product` (variant, tồn kho hiển thị) | đọc | chạy ngay |
| `recommend(kind, productId?)` | `recommendedForYou`, `trendingProducts`, `/similar`, `/bought-together` | đọc | chạy ngay |
| `get_policy(topic)` | FAQ vận chuyển/đổi trả/thanh toán | đọc | xem 2.5 |
| `view_cart`, `quote_promotion(code)` | `myCart`, `quotePromotion` | đọc dữ liệu riêng | cần đăng nhập |
| `add_to_cart`, `update_cart_item`, `remove_cart_item` | mutation tương ứng | ghi, hoàn tác được | chạy ngay, UI hiện "Đã thêm · Hoàn tác" |
| `list_orders`, `get_order`, `track_order` | `myOrders`, `order`, `checkoutProgress` | đọc dữ liệu riêng | cần đăng nhập |
| `list_addresses` | địa chỉ đã lưu | đọc PII | chỉ trả tóm tắt (thành phố, 4 ký tự cuối) cho model |
| `propose_checkout` | mở/báo giá checkout session | nhạy cảm | **pending action** (2.4) |
| `propose_cancel_order(code, reason)` | `cancelOrder` | không hoàn tác | **pending action** |
| `handoff_to_human` | ticket/support | — | chạy ngay |

Kết quả tool trả cho model là JSON gọn (id, tên, slug, giá **dạng chuỗi minor + currency**), đồng thời phát sự kiện SSE có cấu trúc để UI tự vẽ thẻ sản phẩm. Giá hiển thị cho khách lấy từ dữ liệu cấu trúc, không lấy từ chữ model viết.

### 2.4 Xác nhận hành động nhạy cảm: pending action, không để model tự bấm

1. Model gọi `propose_checkout` / `propose_cancel_order`. Service kiểm quyền và báo giá trước, rồi lưu **PendingAction** vào Redis: `id`, `subject`, loại, tham số, hash tham số, tổng tiền + currency, TTL 10 phút, dùng một lần.
2. SSE phát `action_proposed` → UI vẽ thẻ xác nhận (danh sách hàng, phí ship, thuế, giảm giá, tổng, nút "Xác nhận" / "Huỷ"). Model chỉ nhận lại "đang chờ khách xác nhận".
3. Chỉ một **HTTP request riêng do chính khách bấm** (`POST /actions/{id}/confirm`, cùng `subject` trong token) mới thực thi. Model không có tool nào để xác nhận, nên prompt injection không vượt qua được bước này.
4. Thanh toán: chatbot **không bao giờ cầm thông tin thẻ hay ConfirmationToken**. Thẻ checkout trong widget nhúng Stripe Payment Element; client tự gọi mutation `checkout` với `idempotencyKey` sinh một lần và `expectedTotal` = tổng đã hiển thị (giá đổi thì `PRICE_CHANGED`, thẻ yêu cầu xem lại). PayPal/BNPL thì chuyển sang `checkoutUrl`. Tức là "checkout giúp khách" nghĩa là gom giỏ, chọn địa chỉ/ship/mã giảm giá, báo giá, còn bước trả tiền vẫn do khách bấm trên UI chuẩn.
5. Kết quả (đã đặt, chờ duyệt, bị từ chối) được chèn lại vào hội thoại dưới dạng mid-conversation system message để model báo cho khách và theo dõi `checkoutProgress`.

### 2.5 RAG và tìm kiếm ngữ nghĩa

- **Sản phẩm**: không dựng RAG riêng cho chatbot. Model tự gọi `search_catalog` với từ khoá + bộ lọc nó suy ra ("áo khoác nam chống nước dưới 100$" → filter giá, thuộc tính). Elasticsearch là nguồn tìm kiếm duy nhất (ADR 0030), nên khi cần ngữ nghĩa thì thêm **hybrid BM25 + kNN `dense_vector`** vào `search-service` (embedding đa ngôn ngữ như `bge-m3`, nhúng lúc index từ event product). Không đọc pgvector của recommendation-service từ chatbot (vi phạm database per service); pgvector ở đó tiếp tục phục vụ "tương tự".
- **Chính sách/FAQ** (ship, đổi trả, thanh toán, BNPL, thuế): khối lượng nhỏ, đặt thẳng vào system prompt có `cache_control` (vài nghìn token, đọc cache rẻ). Chỉ chuyển sang pgvector trong schema `chatbot` khi tài liệu lớn hơn vài chục nghìn token.

### 2.6 Hội thoại và bộ nhớ

- **Ngắn hạn**: Redis như hiện tại nhưng lưu **nguyên content block** (text, `tool_use`, `tool_result`, thinking block) vì API yêu cầu gửi lại đúng như cũ. Lịch sử chỉ append, không sửa giữa chừng (preserved thinking của Opus 5.5 kiểm điều này); dài thì cắt từ đầu theo ranh giới lượt hoặc bật compaction.
- **Lưu bền**: bảng `chatbot.conversations`/`messages` (Snowflake id, `subject`, thời điểm UTC) để audit, eval và đo chuyển đổi. Có thời hạn giữ (ví dụ 90 ngày), partition theo tháng như ADR 0032.
- **Dài hạn (sau)**: sở thích khách (size, thương hiệu, ngân sách) dạng key-value có cấu trúc, khách xem và xoá được. Không lưu PII thô.
- **Quyền riêng tư**: nghe `ErasureRequested` (privacy-service, ADR 0024) để xoá lịch sử và bộ nhớ; consumer idempotent qua `processed_events`.
- Khách vãng lai đăng nhập giữa chừng: chuyển phiên `anon:<sessionId>` sang `subject` (chỉ khi cùng trình duyệt giữ sessionId).

### 2.7 Xác thực và phân quyền

- Widget gửi access token `storefront-web` (next-auth đã có). chatbot-service kiểm token (lib `ecommerce_platform.auth` sẵn có) rồi **chuyển tiếp nguyên token** khi gọi GraphQL. Không dùng service account, nên agent không làm được gì vượt quyền khách.
- Token ngắn hạn: một lượt chat dài có thể vượt hạn, nên client gửi token mới mỗi request và service không giữ token quá một lượt (không lưu Redis).
- Hành động nhạy cảm cần thêm điều kiện: token có `auth_time` gần đây (ví dụ dưới 30 phút) cho checkout/huỷ đơn, không thì widget yêu cầu đăng nhập lại.
- Đặt quan hệ `can_cancel` vẫn do order-service/OpenFGA quyết, chatbot không tự suy.

### 2.8 Guardrails và chống prompt injection

- **Nguồn không tin cậy**: mô tả sản phẩm, review, tên shop của seller đều có thể chứa chỉ thị. Bọc trong tool_result, nhắc trong system prompt rằng đó là dữ liệu; chỉ dẫn vận hành giữa hội thoại gửi bằng mid-conversation system message.
- **Quyền tối thiểu theo ngữ cảnh**: chỉ khai báo tool cần thiết theo trạng thái (vãng lai, đăng nhập). Ghi luôn có trần: số lượng ≤ 99/dòng, ≤ N mutation mỗi lượt.
- **Hậu kiểm đầu ra**: link `/products/<slug>` và giá trong câu trả lời phải khớp kết quả tool trong lượt; không khớp thì bỏ link/giá. Không cho model hứa giảm giá, hoàn tiền, chính sách ngoài FAQ.
- **Đầu vào**: giới hạn độ dài (đang 2000 ký tự), lọc nội dung qua moderation-service nếu cần, chặn hỏi lạc đề bằng prompt.
- **Lạm dụng và chi phí**: rate limit theo `subject` (đăng nhập) và IP (vãng lai) ở APISIX; trần token mỗi phiên/ngày ở Redis; `max_tokens` hợp lý.
- **Log**: không log token, địa chỉ đầy đủ, nội dung thanh toán. Log id nghiệp vụ (`conversationId`, `orderCode`).
- **Red team**: bộ test injection cố định (review chứa "ignore previous…", sản phẩm tên giả lệnh) chạy trong eval.

### 2.9 Streaming và UI

Mở rộng sự kiện SSE hiện có (`products`, `delta`, `error`, `done`):

| Sự kiện | Dữ liệu | UI |
|---|---|---|
| `delta` | đoạn text | gõ dần |
| `tool_started` / `tool_finished` | tên tool, trạng thái | "Đang tìm sản phẩm…" |
| `products` | danh sách thẻ (id, ảnh, giá minor + currency, slug) | carousel, nút "Thêm vào giỏ" bấm thẳng (không qua model) |
| `cart_updated` | giỏ mới | cập nhật badge giỏ, "Hoàn tác" |
| `action_proposed` | PendingAction | thẻ xác nhận / Payment Element |
| `handoff` | ticket | chuyển người thật |
| `done` / `error` | — | — |

- Web: component client trong `frontend/ecommerce/src/features/chat`, đọc SSE bằng `fetch` + `ReadableStream` (EventSource không gửi được header Authorization). Thẻ sản phẩm dùng lại component sẵn có; nút trên thẻ gọi thẳng GraphQL như trang sản phẩm.
- Mobile Flutter: đọc stream HTTP, cùng hợp đồng sự kiện. Hợp đồng SSE đặt trong `api/openapi` để hai client sinh chung kiểu.
- Huỷ giữa chừng: client đóng kết nối, service huỷ vòng lặp và không chạy tool ghi còn lại.

### 2.10 Observability và đánh giá

- OTel: span cho mỗi lượt, mỗi lần gọi model (token vào/ra, `cache_read_input_tokens`, model thực phục vụ, fallback), mỗi tool (độ trễ, lỗi). Metric Prometheus: chi phí ước tính/lượt, tỉ lệ cache hit, tỉ lệ refusal, tool error, thời gian tới token đầu.
- Kinh doanh: phễu chat → xem sản phẩm → thêm giỏ → đặt hàng. Gắn `conversationId` vào event `trackProductView`/thêm giỏ để đo chuyển đổi trong lakehouse (ADR 0013).
- SLO Pyrra: tỉ lệ lượt lỗi, p95 tới token đầu.
- Eval offline: bộ hội thoại mẫu (tìm đúng sản phẩm, lọc đúng giá, không bịa, hỏi lại khi thiếu size, xin xác nhận trước checkout, chống injection), chấm bằng kiểm tra tất định (tool nào được gọi, tham số) cộng LLM-judge. Chạy khi đổi prompt, tool hoặc model; thêm vào CI dạng job tuỳ chọn vì tốn tiền.

## 3. Chi phí (ước tính, giá Anthropic API tháng 11/2026)

Giả định mỗi lượt: system + tool + FAQ ~6k token (đọc cache), lịch sử + kết quả tool ~5k token không cache, ~400 token ra, trung bình 2 lần gọi model mỗi lượt.

| Model | Giá vào/ra mỗi 1M token (đọc cache) | Ước tính mỗi lượt | 100k lượt/tháng |
|---|---|---|---|
| `claude-opus-5-5` (đang cấu hình, effort `low`) | $4 / $20 ($0,20) | ~$0,06 | ~$6.000 |
| `claude-sonnet-5-5` | $2 / $10 ($0,10) | ~$0,03 | ~$3.000 |
| `claude-haiku-5-5` | $0,10 / $0,50 | ~$0,002 | ~$200 |

Đòn bẩy giảm chi phí theo thứ tự: prompt caching ổn định (tool + system không đổi, không chèn giờ vào prompt), cắt kết quả tool gọn, giới hạn số vòng tool, rồi mới tới đổi model hoặc chia model theo loại câu (câu hỏi FAQ đơn giản sang model rẻ). Đổi model nên quyết sau khi có số đo từ eval. vLLM tự host chỉ đáng khi bắt buộc dữ liệu không rời VPC; GPU tốn cố định.

## 4. Lộ trình

| Giai đoạn | Nội dung | Kết quả |
|---|---|---|
| **0. Dọn nền** (vài ngày) | Sửa `Product.price` đang là `float` sang minor + currency (vi phạm rule tiền); lưu lịch sử dạng content block; chuyển tiếp token; rate limit theo người dùng; viết ADR 0033. | Chatbot hiện tại chạy đúng luật repo. |
| **1. Trợ lý chỉ đọc** | Vòng lặp tool + `search_catalog`, `get_product`, `recommend`, FAQ cache, `view_cart`, `list_orders`, `get_order`. Widget web với thẻ sản phẩm. Bộ eval đầu tiên. | Tư vấn, so sánh, tra đơn. |
| **2. Thao tác giỏ** | `add/update/remove_cart_item`, nút hoàn tác, mời đăng nhập cho khách vãng lai, guardrail hậu kiểm link/giá. | Khách mua qua chat tới bước giỏ. |
| **3. Checkout và huỷ đơn** | PendingAction, thẻ checkout + Stripe Payment Element, theo dõi `checkoutProgress`, `propose_cancel_order`, `auth_time` gần đây, handoff người thật. | Hoàn tất mua hàng trong khung chat, khách luôn tự bấm trả tiền. |
| **4. Thông minh hơn** | Hybrid search trong search-service, bộ nhớ sở thích + xoá theo privacy, mobile Flutter, dashboard chi phí/chuyển đổi, A/B prompt và model, eval trong CI. | Gợi ý tốt hơn, đo được ROI. |

## 5. Những điểm cần người dùng quyết

1. Tool đi qua GraphQL storefront (đề xuất) hay qua commerce-mcp-service bằng token exchange.
2. Ngôn ngữ trợ lý: prompt hiện chốt thị trường Mỹ, tiếng Anh là chính (ADR 0015). Có cần tiếng Việt mặc định không.
3. Model mặc định sau giai đoạn 1: giữ Opus 5.5 hay thử Sonnet 5.5/Haiku 5.5 dựa trên eval.
4. Thời hạn giữ lịch sử chat và có dùng hội thoại cho phân tích hay không.
