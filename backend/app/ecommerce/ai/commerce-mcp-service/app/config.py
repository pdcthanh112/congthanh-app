from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    http_port: int = 5030
    # URL công khai của MCP endpoint (resource indicator, RFC 9728). Client MCP lấy metadata ở
    # /.well-known/oauth-protected-resource rồi xin token từ Keycloak với audience này.
    public_url: str = "https://api.congthanh.vn/api/ecommerce/mcp"
    issuer_url: str = "http://localhost:8180/realms/ecommerce"
    # Audience bắt buộc trong token (client scope "commerce-mcp" ở Keycloak gắn audience mapper).
    jwt_audience: str = "commerce-mcp"
    jwt_jwks_url: str = "http://localhost:8180/realms/ecommerce/protocol/openid-connect/certs"

    search_url: str = "http://localhost:5013"
    product_url: str = "http://localhost:5008"
    cart_url: str = "http://localhost:5002"
    order_url: str = "http://localhost:5006"

    openfga_url: str = "http://localhost:8089"
    openfga_store_id: str = ""
    openfga_model_id: str = ""
    # Đối tượng platform trong model OpenFGA (quan hệ admin/support).
    openfga_platform: str = "platform:congthanh"

    max_results: int = 20
    order_history_window: int = 100  # số đơn gần nhất dùng để xác minh quan hệ buyer


settings = Settings()
