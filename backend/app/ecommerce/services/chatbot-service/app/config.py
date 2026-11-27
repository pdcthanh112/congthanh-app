from typing import Literal

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    http_port: int = 5024
    # anthropic: Claude API (mặc định). ollama: model tự host khi dev/offline.
    # vllm: model mở tự host trên cluster (KServe InferenceService, backend vLLM), dùng khi cần dữ liệu không rời VPC.
    llm_provider: Literal["anthropic", "ollama", "vllm"] = "anthropic"
    # ANTHROPIC_API_KEY do SDK tự đọc (Vault → ExternalSecret ở cluster).
    anthropic_model: str = "claude-opus-5-5"
    anthropic_effort: Literal["low", "medium", "high"] = "low"
    ollama_url: str = "http://localhost:11434"
    ollama_model: str = "mistral"
    vllm_url: str = "http://shop-assistant-llm-predictor.inference.svc.cluster.local"
    vllm_path: str = "/openai/v1/chat/completions"
    vllm_model: str = "shop-assistant"
    # Router GraphQL storefront (Cosmo). Tool của trợ lý đi qua đây bằng token của khách, như web.
    graphql_url: str = "http://localhost:4000/graphql"
    graphql_timeout_seconds: float = 5.0
    redis_url: str = "redis://localhost:6379/6"
    history_turns: int = 10  # số lượt (câu hỏi + mọi bước tool + câu trả lời) gửi kèm làm ngữ cảnh
    history_ttl_seconds: int = 24 * 3600
    max_message_chars: int = 2000
    # Gồm cả phần suy luận của model (Opus 5.5 luôn bật adaptive thinking), không chỉ câu trả lời.
    max_output_tokens: int = 8000
    store_name: str = "CongThanh Shop"


settings = Settings()
