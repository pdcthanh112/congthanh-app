"""commerce-mcp-service: MCP server (Streamable HTTP, stateless) tại /api/ecommerce/mcp."""

from __future__ import annotations

import contextlib

import httpx
from ecommerce_platform import logging as eplog
from ecommerce_platform.auth import verifier_from_env
from mcp.server.auth.settings import AuthSettings
from starlette.applications import Starlette
from starlette.requests import Request
from starlette.responses import JSONResponse
from starlette.routing import Mount, Route

from .adapters.http import CartClient, CatalogClient, OrderClient
from .adapters.openfga import OpenFgaAuthorizer
from .config import settings
from .server import JwtTokenVerifier, build_server
from .service import CommerceTools

eplog.setup("commerce-mcp-service")

verifier = verifier_from_env()
if verifier is None:
    # MCP server thao tác thay người dùng: không có chế độ chạy không xác thực, kể cả khi dev.
    raise SystemExit("JWT_JWKS_URL is required for commerce-mcp-service")

timeout = httpx.Timeout(5.0, connect=2.0)
clients = {
    "search": httpx.AsyncClient(base_url=settings.search_url, timeout=timeout),
    "product": httpx.AsyncClient(base_url=settings.product_url, timeout=timeout),
    "cart": httpx.AsyncClient(base_url=settings.cart_url, timeout=timeout),
    "order": httpx.AsyncClient(base_url=settings.order_url, timeout=httpx.Timeout(8.0, connect=2.0)),
    "fga": httpx.AsyncClient(base_url=settings.openfga_url, timeout=httpx.Timeout(2.0)),
}
tools = CommerceTools(
    CatalogClient(clients["search"], clients["product"]),
    CartClient(clients["cart"]),
    OrderClient(clients["order"]),
    OpenFgaAuthorizer(clients["fga"], settings.openfga_store_id, settings.openfga_model_id),
    platform=settings.openfga_platform,
    max_results=settings.max_results,
    history_window=settings.order_history_window,
)
mcp = build_server(
    tools,
    token_verifier=JwtTokenVerifier(verifier),
    auth=AuthSettings(
        issuer_url=settings.issuer_url,
        resource_server_url=settings.public_url,
        required_scopes=None,  # scope kiểm theo từng tool để agent chỉ-đọc vẫn dùng được phần đọc
        validate_token_resource=False,  # Verifier đã kiểm audience (JWT_AUDIENCE)
    ),
)
mcp_app = mcp.streamable_http_app(streamable_http_path="/api/ecommerce/mcp", stateless_http=True, host="0.0.0.0")


async def live(_: Request) -> JSONResponse:
    return JSONResponse({"status": "UP"})


async def ready(_: Request) -> JSONResponse:
    try:
        r = await clients["fga"].get("/healthz")
        r.raise_for_status()
    except httpx.HTTPError:
        return JSONResponse({"status": "DOWN"}, status_code=503)
    return JSONResponse({"status": "UP"})


@contextlib.asynccontextmanager
async def lifespan(_: Starlette):
    async with mcp.session_manager.run():
        yield
    for c in clients.values():
        await c.aclose()


app = Starlette(
    routes=[Route("/health/live", live), Route("/health/ready", ready), Mount("/", app=mcp_app)],
    lifespan=lifespan,
)
