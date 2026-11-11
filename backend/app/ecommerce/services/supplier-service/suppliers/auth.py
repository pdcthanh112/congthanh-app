"""Xác thực DRF bằng JWT Keycloak qua ecommerce_platform (cùng quy ước vai trò với service Go/Node)."""

from __future__ import annotations

from dataclasses import dataclass

from ecommerce_platform.auth import AuthError, Principal, bearer, verifier_from_env
from rest_framework import authentication, exceptions

_verifier = verifier_from_env()


@dataclass(frozen=True)
class User:
    principal: Principal
    is_authenticated: bool = True

    @property
    def id(self) -> str:
        return self.principal.subject

    def has_role(self, *roles: str) -> bool:
        return self.principal.has_role(*roles)


class JWTAuthentication(authentication.BaseAuthentication):
    def authenticate(self, request):
        if _verifier is None:
            # Tắt xác thực (dev): X-User-Id + X-User-Roles để thử.
            sub = request.headers.get("X-User-Id")
            if not sub:
                return None
            roles = frozenset(r.strip().upper() for r in request.headers.get("X-User-Roles", "").split(",") if r)
            return User(Principal(subject=sub, roles=roles)), None
        token = bearer(request.headers.get("Authorization"))
        if token is None:
            return None
        try:
            return User(_verifier.verify(token)), token
        except AuthError as e:
            raise exceptions.AuthenticationFailed("Invalid or expired token") from e

    def authenticate_header(self, request):
        return 'Bearer realm="ecommerce"'
