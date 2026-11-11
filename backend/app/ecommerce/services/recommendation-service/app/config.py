from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    http_port: int = 5028
    database_url: str = "postgresql://postgres:123456@localhost:5432/CongThanhApp-Ecommerce"
    db_schema: str = "recommendation"
    kafka_brokers: str = ""
    kafka_group: str = "recommendation-service"
    # pgvector cho "sản phẩm tương tự" và vector sở thích; false = chỉ dùng mua kèm + xu hướng.
    vector_enabled: bool = True
    # hashing: không cần model (từ khoá). ollama: embedding ngữ nghĩa đa ngôn ngữ.
    embeddings_provider: str = "hashing"
    embedding_dim: int = 256
    ollama_url: str = "http://localhost:11434"
    embedding_model: str = "bge-m3"
    # Số đơn tối thiểu có cùng cặp sản phẩm để tính "mua kèm" (tăng khi dữ liệu nhiều để bớt nhiễu).
    min_support: int = 1
    rebuild_interval_seconds: int = 6 * 3600

    @property
    def brokers(self) -> list[str]:
        return [b.strip() for b in self.kafka_brokers.split(",") if b.strip()]


settings = Settings()
