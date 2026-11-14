from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    http_port: int = 5039
    database_url: str = "postgresql://postgres:postgres@localhost:5432/ecommerce"
    db_schema: str = "moderation"
    policy_version: str = "2026-11"
    # Tầng LLM: bật khi có ANTHROPIC_API_KEY (SDK tự đọc). Tắt thì chỉ chạy luật, phần không chắc vào hàng chờ.
    llm_enabled: bool = True
    anthropic_model: str = "claude-haiku-5-5"
    llm_timeout_seconds: float = 4.0
    # Từ cấm bổ sung (phân tách bằng dấu phẩy), quản lý bởi team Trust & Safety ngoài code.
    banned_terms: str = ""
    strike_threshold: int = 3
    strike_window_days: int = 30
    kafka_brokers: str = ""  # chỉ để nhận yêu cầu xoá dữ liệu từ privacy-service
    kafka_group: str = "moderation-service"

    @property
    def brokers(self) -> list[str]:
        return [b.strip() for b in self.kafka_brokers.split(",") if b.strip()]


settings = Settings()
