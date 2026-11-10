from typing import Literal

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    http_port: int = 5024
    # anthropic: Claude API (mặc định). ollama: model tự host (dev/offline, thay Spring AI + Ollama cũ).
    llm_provider: Literal["anthropic", "ollama"] = "anthropic"
    # ANTHROPIC_API_KEY do SDK tự đọc (Vault → ExternalSecret ở cluster).
    anthropic_model: str = "claude-opus-5-5"
    anthropic_effort: Literal["low", "medium", "high"] = "low"
    ollama_url: str = "http://localhost:11434"
    ollama_model: str = "mistral"
    search_url: str = "http://localhost:5013"
    product_context_size: int = 5
    valkey_url: str = "redis://localhost:6379/6"
    history_turns: int = 10  # số lượt (hỏi + đáp) gửi kèm làm ngữ cảnh
    history_ttl_seconds: int = 24 * 3600
    max_message_chars: int = 2000
    max_output_tokens: int = 4000
    store_name: str = "CongThanh Shop"


settings = Settings()
