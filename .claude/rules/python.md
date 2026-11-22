---
paths:
  - "backend/app/ecommerce/services/**/*.py"
  - "backend/app/ecommerce/ai/**/*.py"
  - "backend/app/ecommerce/libs/python/**/*.py"
  - "backend/app/ecommerce/data/**/*.py"
---

# Rule Python (FastAPI, Django `supplier-service`, data/lakehouse)

Khuôn mẫu: `services/fraud-service`. Công cụ: `uv` (lock `uv.lock`, chỉ nhận bản phát hành ≥ 2 tuần qua
`exclude-newer`), Ruff (line 120, `py313`, luật E, F, W, I, B, UP, SIM, RUF), pytest. Image chung
`services/python.Dockerfile`.

Nguồn tham khảo: PEP 8, PEP 257, PEP 484/604 (type hints), Google Python Style Guide, FastAPI docs (dependencies,
lifespan, async), Ruff rules (flake8-bugbear, pyupgrade, flake8-simplify), *Fluent Python*.

---

## 1. Định dạng và module

1. Code phải qua `ruff check` và `ruff format`; không định dạng tay khác đi.
2. Mỗi module mở đầu bằng **docstring tiếng Việt** một dòng (PEP 257), rồi `from __future__ import annotations`:
   ```python
   """Use case của fraud-service: chấm điểm giao dịch, hàng chờ duyệt, danh sách chặn."""

   from __future__ import annotations
   ```
3. Import ba nhóm (chuẩn, ngoài, nội bộ) do Ruff `I` sắp. Trong cùng service dùng import tương đối
   (`from .ports import Store`). Không `import *`.
4. Thứ tự trong module: docstring → import → `log = logging.getLogger(__name__)` → hằng số → exception → hàm/class
   public → helper `_private`.

## 2. Đặt tên (PEP 8)

1. Module, hàm, biến, method: `snake_case`. Class, exception: `PascalCase`. Hằng số module-level: `UPPER_SNAKE_CASE`
   (`BLOCK_KINDS`, `STREAM_MAX_AGE`).
2. Thành viên nội bộ một dấu `_` (`_load`, `_row_to_item`). Không dùng `__` name mangling.
3. Exception nghiệp vụ đặt tên theo loại lỗi (`NotFound`, `Invalid`, `Conflict`) như fraud-service, hoặc `XxxError`.
4. Không đặt tên che built-in (`id`, `type`, `list`, `input`) cho biến sống lâu; trong phạm vi ngắn chấp nhận `id` khi
   khớp tên cột.

## 3. Type hint

1. Mọi hàm/method public có type hint đầy đủ cho tham số và giá trị trả về.
2. Dùng cú pháp mới: `X | None`, `list[str]`, `dict[str, int]`, `tuple[int, ...]`. Không `Optional`, `Union`, `List`,
   `Dict` (Ruff `UP` sẽ báo).
3. Port dùng `typing.Protocol`; value object dùng `@dataclass(frozen=True)` (thêm `slots=True` khi mới viết); tập giá
   trị đóng dùng `Enum`/`StrEnum`.
4. Không dùng `Any` khi biết kiểu; dữ liệu JSON chưa kiểm thì parse qua Pydantic.
5. Callable inject (clock) khai kiểu `Callable[[], datetime]`.

## 4. Cấu trúc lục giác

1. `app/domain/` thuần: chỉ thư viện chuẩn, không FastAPI, asyncpg, Redis, Kafka, Pydantic.
2. `app/ports.py`: `Protocol` cho repo/cache/stream.
3. `app/service.py`: class use case nhận port qua `__init__` (dependency injection bằng tay); `clock` có mặc định
   `lambda: datetime.now(UTC)`.
4. `app/adapters/`: `postgres.py`, `redis_store.py`, `kafka.py`, client HTTP ngoài.
5. `app/main.py`: tạo FastAPI app, route, `lifespan` mở/đóng kết nối và chạy migration; `app/config.py`:
   `pydantic-settings`.
6. Route chỉ parse request (Pydantic `BaseModel`), gọi use case, đổi exception nghiệp vụ sang `HTTPException`.
   Domain/service không ném `HTTPException`.

## 5. Hàm và luồng

1. Thoát sớm; mỗi điều kiện validation một `if` với message riêng.
2. Không dùng giá trị mặc định có thể thay đổi (`def f(x=[])`) — dùng `None` rồi tạo trong hàm.
3. List/dict/set comprehension cho biến đổi một bước; logic nhiều bước dùng vòng `for`. Không comprehension lồng hai
   tầng có điều kiện.
4. Dùng `with`/`async with` cho mọi tài nguyên (kết nối, transaction, file).
5. Không bắt `except Exception` trừ ở biên (consumer, background task) và phải log `exc_info=True`. Không `except:` trần.
6. `raise ... from e` khi đổi kiểu lỗi để giữ cause.
7. So sánh `None` bằng `is`/`is not`. Kiểm rỗng bằng truthiness (`if not items:`) khi rõ nghĩa.
8. Không dùng `assert` cho validation chạy thật (bị tắt với `-O`).

## 6. Async

1. I/O là `async def` (asyncpg, `redis.asyncio`, aiokafka, httpx async). Không gọi thư viện chặn (requests, time.sleep,
   psycopg đồng bộ) trong hàm async; việc CPU nặng chạy bằng `asyncio.to_thread`.
2. Task nền phải được giữ tham chiếu và hủy khi shutdown trong `lifespan`.
3. Gọi HTTP ra ngoài luôn có timeout.

## 7. Dữ liệu

1. Tiền là `int` đơn vị nhỏ nhất, tên hậu tố `_minor` (`amount_minor`), luôn đi kèm `currency`. Không `float` cho tiền;
   cần số thập phân thì `Decimal`.
2. Id thực thể sinh bằng `ecommerce_platform.ids.snowflake`.
3. SQL viết tay với tham số `$1, $2` (asyncpg); bảng trong schema `DB_SCHEMA`. Migration là file SQL đánh số trong
   `app/migrations/NNN_mo_ta.sql`, chạy khi khởi động, phải chạy lại được (idempotent).
4. Thời gian luôn có tz (`datetime.now(UTC)`), không `datetime.utcnow()`.

## 8. Log

1. `log = logging.getLogger(__name__)`. Không `print`.
2. Lỗi đã xử lý: `log.warning("product search failed", exc_info=True)`. Ngữ cảnh thêm qua `extra={"x_status": ...}`.
3. Message dùng tham số lazy (`log.info("processed %d events", n)`), không f-string trong log.

## 9. Test

1. pytest; file `tests/test_*.py`; hàm `test_<hanh_vi>` (async: `async def test_…`).
2. Tham số hoá bằng `@pytest.mark.parametrize(("text", "sid"), [...])`.
3. Test domain không cần DB; test service dùng port giả tự viết (class có cùng method với `Protocol`).
4. Test cần DB/Redis: `pytestmark = pytest.mark.skipif(not DB, reason="TEST_DATABASE_URL not set")`.
5. Không gọi API ngoài thật (Stripe, Anthropic, Ollama) trong test; giả lập ở adapter.
6. Chạy: `uv run ruff check . && uv run ruff format --check . && uv run pytest -q` trong thư mục service.

## 10. Django (`supplier-service`)

1. Theo cấu trúc sẵn có: `suppliers/{models,views,serializers,service}.py`; logic nghiệp vụ ở `service.py`, không ở
   view hay serializer.
2. Migration sinh bằng `manage.py makemigrations`, không sửa migration đã có.
3. Test với `pytestmark = pytest.mark.django_db`.
