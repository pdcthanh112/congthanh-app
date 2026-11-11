from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    http_port: int = 5026
    database_url: str = "postgresql://postgres:123456@localhost:5432/CongThanhApp-Ecommerce"
    db_schema: str = "fraud"
    redis_url: str = "redis://localhost:6379/6"
    kafka_brokers: str = ""  # trống = không dựng lịch sử từ event (luật lịch sử luôn = 0)
    kafka_group: str = "fraud-service"
    review_at: int = 50
    reject_at: int = 80

    @property
    def brokers(self) -> list[str]:
        return [b.strip() for b in self.kafka_brokers.split(",") if b.strip()]


settings = Settings()
