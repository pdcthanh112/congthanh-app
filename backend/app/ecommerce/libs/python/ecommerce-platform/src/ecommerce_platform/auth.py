"""Xác thực JWT do Keycloak cấp (RS256 qua JWKS), cùng quy ước vai trò với service Go/Node.

Vai trò lấy từ realm_access.roles, resource_access.*.roles và claim "roles", viết hoa, bỏ tiền tố ROLE_.
"""

from __future__ import annotations

import os
from dataclasses import dataclass, field

import jwt


class AuthError(Exception):
    """Token thiếu, sai chữ ký, hết hạn hoặc sai issuer."""


@dataclass(frozen=True)
class Principal:
    subject: str
    roles: frozenset[str] = field(default_factory=frozenset)
    token: str = ""
    # OAuth scope trong token ("scope": "a b c"); dùng khi client là agent/MCP được uỷ quyền một phần.
    scopes: frozenset[str] = field(default_factory=frozenset)

    def has_scope(self, scope: str) -> bool:
        return scope in self.scopes

    def has_role(self, *roles: str) -> bool:
        return any(r.upper() in self.roles for r in roles)


def _norm(role: str) -> str:
    role = role.upper()
    return role.removeprefix("ROLE_")


def extract_roles(claims: dict) -> frozenset[str]:
    roles: set[str] = set()
    roles.update((claims.get("realm_access") or {}).get("roles", []))
    for client in (claims.get("resource_access") or {}).values():
        roles.update((client or {}).get("roles", []))
    extra = claims.get("roles")
    if isinstance(extra, list):
        roles.update(extra)
    return frozenset(_norm(r) for r in roles if isinstance(r, str))


class Verifier:
    """Kiểm token bằng JWKS (khoá được cache và tự làm mới khi gặp kid lạ)."""

    def __init__(self, jwks_url: str, issuer: str | None = None, audience: str | None = None, leeway: int = 30):
        self._jwks = jwt.PyJWKClient(jwks_url, cache_keys=True, lifespan=600, timeout=5)
        self._issuer = issuer or None
        self._audience = audience or None
        self._leeway = leeway

    def verify(self, token: str) -> Principal:
        try:
            key = self._jwks.get_signing_key_from_jwt(token)
            claims = jwt.decode(
                token,
                key.key,
                algorithms=["RS256", "ES256"],
                issuer=self._issuer,
                audience=self._audience,
                leeway=self._leeway,
                options={"require": ["exp", "sub"], "verify_aud": self._audience is not None},
            )
        except (jwt.PyJWTError, jwt.PyJWKClientError) as e:
            raise AuthError(str(e)) from e
        scope = claims.get("scope")
        scopes = frozenset(scope.split()) if isinstance(scope, str) else frozenset()
        return Principal(subject=claims["sub"], roles=extract_roles(claims), token=token, scopes=scopes)


def bearer(header: str | None) -> str | None:
    if not header:
        return None
    scheme, _, value = header.partition(" ")
    return value.strip() if scheme.lower() == "bearer" and value.strip() else None


def verifier_from_env() -> Verifier | None:
    """JWT_JWKS_URL trống = tắt xác thực (chỉ dùng khi dev)."""
    url = os.getenv("JWT_JWKS_URL", "")
    if not url:
        return None
    return Verifier(url, issuer=os.getenv("JWT_ISSUER") or None, audience=os.getenv("JWT_AUDIENCE") or None)
