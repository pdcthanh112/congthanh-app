from django.http import Http404
from rest_framework import exceptions
from rest_framework.decorators import api_view
from rest_framework.request import Request

from . import service
from .envelope import ok
from .models import Supplier
from .serializers import PublicSupplierSerializer, StatusSerializer, SupplierSerializer, UpdateSerializer

MAX_SIZE = 100


def _user(request: Request):
    if not getattr(request.user, "is_authenticated", False):
        raise exceptions.NotAuthenticated("Authentication required")
    return request.user


def _can_manage(request: Request, s: Supplier) -> bool:
    u = request.user
    return bool(getattr(u, "is_authenticated", False) and (u.id == s.owner_id or u.has_role("ADMIN")))


def _get(key: str) -> Supplier:
    s = service.find(key)
    if s is None:
        raise Http404
    return s


def _int(v: str | None, default: int, lo: int, hi: int) -> int:
    try:
        return max(lo, min(hi, int(v))) if v else default
    except ValueError as e:
        raise exceptions.ValidationError({"page": "must be a number"}) from e


@api_view(["GET", "POST"])
def collection(request: Request):
    if request.method == "POST":
        u = _user(request)
        if not u.has_role("SELLER", "ADMIN"):
            raise exceptions.PermissionDenied("requires one of roles: SELLER, ADMIN")
        ser = SupplierSerializer(data=request.data)
        ser.is_valid(raise_exception=True)
        try:
            s = service.create(u.id, dict(ser.validated_data))
        except service.Conflict as e:
            raise _conflict(str(e)) from e
        return ok(SupplierSerializer(s).data, "Create supplier successfully", 201)

    is_admin = getattr(request.user, "is_authenticated", False) and request.user.has_role("ADMIN")
    # Khách chỉ thấy gian hàng đang hoạt động; admin lọc theo trạng thái bất kỳ.
    status = request.query_params.get("status") if is_admin else Supplier.Status.ACTIVE
    page = _int(request.query_params.get("page"), 1, 1, 10_000)
    size = _int(request.query_params.get("size"), 20, 1, MAX_SIZE)
    qs = service.search(request.query_params.get("q"), status)
    total = qs.count()
    items = qs[(page - 1) * size : page * size]
    ser = SupplierSerializer if is_admin else PublicSupplierSerializer
    return ok(
        {"items": ser(items, many=True).data, "totalItems": total, "page": page, "size": size},
        "Get suppliers successfully",
    )


@api_view(["GET"])
def mine(request: Request):
    s = Supplier.objects.filter(owner_id=_user(request).id).first()
    if s is None:
        raise Http404
    return ok(SupplierSerializer(s).data, "Get supplier successfully")


@api_view(["GET", "PATCH"])
def detail(request: Request, key: str):
    s = _get(key)
    manage = _can_manage(request, s)
    if request.method == "PATCH":
        if not manage:
            _user(request)
            raise exceptions.PermissionDenied("only the owner or an admin can edit this supplier")
        ser = UpdateSerializer(s, data=request.data, partial=True)
        ser.is_valid(raise_exception=True)
        try:
            s = service.update(s, dict(ser.validated_data))
        except service.Conflict as e:
            raise _conflict(str(e)) from e
        return ok(SupplierSerializer(s).data, "Update supplier successfully")
    if s.status != Supplier.Status.ACTIVE and not manage:
        raise Http404  # gian hàng chưa duyệt/bị khoá: không lộ là có tồn tại
    return ok((SupplierSerializer if manage else PublicSupplierSerializer)(s).data, "Get supplier successfully")


@api_view(["PUT"])
def status(request: Request, key: str):
    if not _user(request).has_role("ADMIN"):
        raise exceptions.PermissionDenied("requires one of roles: ADMIN")
    s = _get(key)
    ser = StatusSerializer(data=request.data)
    ser.is_valid(raise_exception=True)
    s = service.set_status(s, ser.validated_data["status"])
    return ok(SupplierSerializer(s).data, "Update supplier status successfully")


class _Conflict(exceptions.APIException):
    status_code = 409
    default_detail = "Conflict"


def _conflict(msg: str) -> exceptions.APIException:
    return _Conflict(msg)
