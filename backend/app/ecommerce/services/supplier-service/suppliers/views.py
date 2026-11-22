"""Adapter vào (REST): parse request, kiểm vai trò, gọi SupplierService; SupplierError đổi sang HTTP ở envelope.py."""

from __future__ import annotations

from drf_spectacular.types import OpenApiTypes
from drf_spectacular.utils import OpenApiParameter, extend_schema, inline_serializer
from rest_framework import exceptions, serializers
from rest_framework.decorators import api_view
from rest_framework.request import Request

from .container import supplier_service
from .domain import Actor, SupplierStatus
from .envelope import ok
from .serializers import PublicSupplierSerializer, StatusSerializer, SupplierSerializer, UpdateSerializer

MAX_SIZE = 100
TAGS = ["supplier"]


def _envelope(name: str, data: serializers.Field) -> serializers.Serializer:
    """Schema phong bì {message, data, status, errorCode} cho tài liệu OpenAPI."""
    return inline_serializer(
        name=name,
        fields={
            "message": serializers.CharField(),
            "data": data,
            "status": serializers.ChoiceField(choices=["Success", "Failed"]),
            "errorCode": serializers.IntegerField(),
        },
    )


SUPPLIER = _envelope("SupplierEnvelope", SupplierSerializer())
ERROR = _envelope("ErrorEnvelope", serializers.JSONField(allow_null=True))
PAGE = _envelope(
    "SupplierPageEnvelope",
    inline_serializer(
        name="SupplierPage",
        fields={
            "items": SupplierSerializer(many=True),
            "totalItems": serializers.IntegerField(),
            "page": serializers.IntegerField(),
            "size": serializers.IntegerField(),
        },
    ),
)
KEY = OpenApiParameter("key", OpenApiTypes.STR, OpenApiParameter.PATH, description="Id (Snowflake) hoặc slug")


def _actor(request: Request) -> Actor | None:
    user = request.user
    if not getattr(user, "is_authenticated", False):
        return None
    return Actor(id=user.id, is_admin=user.has_role("ADMIN"))


def _require_actor(request: Request) -> Actor:
    actor = _actor(request)
    if actor is None:
        raise exceptions.NotAuthenticated("Authentication required")
    return actor


def _int(v: str | None, default: int, lo: int, hi: int) -> int:
    try:
        return max(lo, min(hi, int(v))) if v else default
    except ValueError as e:
        raise exceptions.ValidationError({"page": "must be a number"}) from e


@extend_schema(
    methods=["GET"],
    operation_id="listSuppliers",
    tags=TAGS,
    summary="Danh sách gian hàng (khách chỉ thấy ACTIVE và trường công khai)",
    parameters=[
        OpenApiParameter("q", OpenApiTypes.STR, description="Tìm trong tên/slug"),
        OpenApiParameter("status", OpenApiTypes.STR, enum=[s.value for s in SupplierStatus], description="Chỉ ADMIN"),
        OpenApiParameter("page", OpenApiTypes.INT, default=1),
        OpenApiParameter("size", OpenApiTypes.INT, default=20),
    ],
    responses={200: PAGE},
)
@extend_schema(
    methods=["POST"],
    operation_id="registerSupplier",
    tags=TAGS,
    summary="Đăng ký gian hàng (SELLER, ADMIN); mỗi tài khoản một gian hàng",
    request=SupplierSerializer,
    responses={201: SUPPLIER, 400: ERROR, 401: ERROR, 403: ERROR, 409: ERROR},
)
@api_view(["GET", "POST"])
def collection(request: Request):
    svc = supplier_service()
    if request.method == "POST":
        actor = _require_actor(request)
        if not request.user.has_role("SELLER", "ADMIN"):
            raise exceptions.PermissionDenied("requires one of roles: SELLER, ADMIN")
        ser = SupplierSerializer(data=request.data)
        ser.is_valid(raise_exception=True)
        supplier = svc.register(actor, dict(ser.validated_data))
        return ok(SupplierSerializer(supplier).data, "Create supplier successfully", 201)

    actor = _actor(request)
    is_admin = actor is not None and actor.is_admin
    # Khách chỉ thấy gian hàng đang hoạt động; admin lọc theo trạng thái bất kỳ.
    status = request.query_params.get("status") if is_admin else SupplierStatus.ACTIVE
    page = _int(request.query_params.get("page"), 1, 1, 10_000)
    size = _int(request.query_params.get("size"), 20, 1, MAX_SIZE)
    items, total = svc.search(request.query_params.get("q"), status, page, size)
    ser = SupplierSerializer if is_admin else PublicSupplierSerializer
    return ok(
        {"items": ser(items, many=True).data, "totalItems": total, "page": page, "size": size},
        "Get suppliers successfully",
    )


@extend_schema(
    operation_id="getMySupplier",
    tags=TAGS,
    summary="Gian hàng của tôi",
    responses={200: SUPPLIER, 401: ERROR, 404: ERROR},
)
@api_view(["GET"])
def mine(request: Request):
    supplier = supplier_service().mine(_require_actor(request).id)
    return ok(SupplierSerializer(supplier).data, "Get supplier successfully")


@extend_schema(
    methods=["GET"],
    operation_id="getSupplier",
    tags=TAGS,
    summary="Chi tiết gian hàng (chưa ACTIVE thì chỉ chủ và ADMIN thấy)",
    parameters=[KEY],
    responses={200: SUPPLIER, 404: ERROR},
)
@extend_schema(
    methods=["PATCH"],
    operation_id="editSupplier",
    tags=TAGS,
    summary="Sửa gian hàng (chủ hoặc ADMIN); gửi version để khoá lạc quan",
    parameters=[KEY],
    request=UpdateSerializer,
    responses={200: SUPPLIER, 400: ERROR, 401: ERROR, 403: ERROR, 404: ERROR, 409: ERROR},
)
@api_view(["GET", "PATCH"])
def detail(request: Request, key: str):
    svc = supplier_service()
    if request.method == "PATCH":
        actor = _require_actor(request)
        ser = UpdateSerializer(data=request.data, partial=True)
        ser.is_valid(raise_exception=True)
        supplier = svc.edit(actor, key, dict(ser.validated_data))
        return ok(SupplierSerializer(supplier).data, "Update supplier successfully")
    actor = _actor(request)
    supplier = svc.get_visible(key, actor)
    ser = SupplierSerializer if supplier.can_be_managed_by(actor) else PublicSupplierSerializer
    return ok(ser(supplier).data, "Get supplier successfully")


@extend_schema(
    operation_id="changeSupplierStatus",
    tags=TAGS,
    summary="Duyệt, khoá hoặc đóng gian hàng (ADMIN)",
    parameters=[KEY],
    request=StatusSerializer,
    responses={200: SUPPLIER, 401: ERROR, 403: ERROR, 404: ERROR, 409: ERROR},
)
@api_view(["PUT"])
def status(request: Request, key: str):
    if not _require_actor(request).is_admin:
        raise exceptions.PermissionDenied("requires one of roles: ADMIN")
    ser = StatusSerializer(data=request.data)
    ser.is_valid(raise_exception=True)
    supplier = supplier_service().change_status(key, SupplierStatus(ser.validated_data["status"]))
    return ok(SupplierSerializer(supplier).data, "Update supplier status successfully")
