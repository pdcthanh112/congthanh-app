"""Kiểu dữ liệu và lỗi nghiệp vụ của MCP server (không phụ thuộc framework)."""

from __future__ import annotations

from dataclasses import dataclass

# Scope OAuth mà agent phải được người dùng cấp (Keycloak client scope, hiện trên màn hình consent).
SCOPE_READ = "commerce:read"  # xem sản phẩm, giỏ, đơn của chính mình
SCOPE_CART = "commerce:cart"  # thêm/sửa/xoá hàng trong giỏ
SCOPE_ORDERS_CANCEL = "commerce:orders.cancel"  # huỷ đơn (thao tác không hoàn tác được)


class CommerceError(Exception):
    """Lỗi trả về cho agent dưới dạng tool error (thông điệp an toàn để hiển thị)."""


class Unauthenticated(CommerceError):
    pass


class Forbidden(CommerceError):
    pass


class NotFound(CommerceError):
    pass


class InvalidInput(CommerceError):
    pass


class Unavailable(CommerceError):
    pass


@dataclass(frozen=True)
class Tuple:
    """Một quan hệ OpenFGA: user có relation với object."""

    user: str
    relation: str
    object: str
