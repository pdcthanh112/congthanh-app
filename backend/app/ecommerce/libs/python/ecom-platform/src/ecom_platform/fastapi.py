"""Tích hợp FastAPI: dependency xác thực, envelope phản hồi và lỗi giống service Go/Java
({message, data, status, errorCode}), health check /health/live và /health/ready."""

from __future__ import annotations

import logging
from collections.abc import Awaitable, Callable
from typing import Annotated, Any

from fastapi import APIRouter, Depends, FastAPI, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from .auth import AuthError, Principal, Verifier, bearer

log = logging.getLogger(__name__)


def ok(data: Any, message: str = "Success", code: int = 200) -> JSONResponse:
    return JSONResponse({"message": message, "data": data, "status": "Success", "errorCode": 0}, status_code=code)


def _fail(code: int, message: str) -> JSONResponse:
    return JSONResponse({"message": message, "data": None, "status": "Failed", "errorCode": code}, status_code=code)


def install_error_handlers(app: FastAPI) -> None:
    @app.exception_handler(HTTPException)
    async def http_exc(_: Request, exc: HTTPException):
        return _fail(exc.status_code, str(exc.detail))

    @app.exception_handler(RequestValidationError)
    async def validation_exc(_: Request, exc: RequestValidationError):
        first = exc.errors()[0] if exc.errors() else {}
        where = ".".join(str(p) for p in first.get("loc", [])[1:])
        return _fail(400, f"Invalid request: {where} {first.get('msg', '')}".strip())

    @app.exception_handler(Exception)
    async def unhandled(request: Request, exc: Exception):
        log.exception("unhandled error", extra={"x_path": request.url.path})
        return _fail(500, "Internal server error")


class Auth:
    """Dependency: Auth(verifier).optional / .required / .roles("ADMIN")."""

    def __init__(self, verifier: Verifier | None):
        self.verifier = verifier

    def _principal(self, request: Request) -> Principal | None:
        token = bearer(request.headers.get("authorization"))
        if self.verifier is None:
            # Tắt xác thực (dev): dùng header X-User-Id để thử.
            sub = request.headers.get("x-user-id")
            return Principal(subject=sub, roles=frozenset({"ADMIN"})) if sub else None
        if token is None:
            return None
        try:
            return self.verifier.verify(token)
        except AuthError as e:
            raise HTTPException(401, "Invalid or expired token") from e

    def optional(self, request: Request) -> Principal | None:
        return self._principal(request)

    def required(self, request: Request) -> Principal:
        p = self._principal(request)
        if p is None:
            raise HTTPException(401, "Authentication required")
        return p

    def roles(self, *roles: str) -> Callable[..., Principal]:
        def dep(p: Annotated[Principal, Depends(self.required)]) -> Principal:
            if not p.has_role(*roles):
                raise HTTPException(403, "requires one of roles: " + ", ".join(roles))
            return p

        return dep


def health_router(ready: Callable[[], Awaitable[None]] | None = None) -> APIRouter:
    r = APIRouter()

    @r.get("/health/live", include_in_schema=False)
    async def live():
        return {"status": "UP"}

    @r.get("/health/ready", include_in_schema=False)
    async def readiness():
        if ready is not None:
            try:
                await ready()
            except Exception as e:
                log.warning("not ready: %s", e)
                return JSONResponse({"status": "DOWN"}, status_code=503)
        return {"status": "UP"}

    return r
