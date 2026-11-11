from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    http_port: int = 5017
    # ANTHROPIC_API_KEY do SDK tự đọc (Vault → ExternalSecret ở cluster).
    anthropic_model: str = "claude-opus-5-5"
    # Dịch là việc đơn giản: effort thấp giữ độ trễ và chi phí nhỏ; tăng nếu chất lượng chưa đạt.
    anthropic_effort: str = "low"
    redis_url: str = "redis://localhost:6379/5"
    cache_ttl_seconds: int = 30 * 24 * 3600
    max_texts: int = 50
    max_chars: int = 20_000  # tổng ký tự mỗi request


settings = Settings()
