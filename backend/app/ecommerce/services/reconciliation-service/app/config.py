from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    http_port: int = 5036
    database_url: str = "postgresql://postgres:postgres@localhost:5432/ecommerce"
    db_schema: str = "reconciliation"
    kafka_brokers: str = ""  # trống = không dựng sổ nội bộ từ event (mọi giao dịch cổng sẽ thành MISSING_INTERNAL)
    kafka_group: str = "reconciliation-service"
    stripe_api_key: str = ""  # trống = chỉ đối soát bằng file CSV tải lên
    stripe_api_base: str = "https://api.stripe.com"
    # Chênh lệch tối đa (đơn vị nhỏ nhất) vẫn coi là khớp, ví dụ làm tròn khác nhau; mặc định phải khớp tuyệt đối.
    amount_tolerance_minor: int = 0
    # Giờ UTC chạy đối soát tự động cho ngày hôm trước (Stripe báo cáo trễ ~1 ngày); -1 = tắt.
    daily_run_hour_utc: int = 6

    @property
    def brokers(self) -> list[str]:
        return [b.strip() for b in self.kafka_brokers.split(",") if b.strip()]


settings = Settings()
