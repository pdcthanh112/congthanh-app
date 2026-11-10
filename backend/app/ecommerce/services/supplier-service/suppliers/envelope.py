"""Envelope phản hồi giống service Go/Java: {message, data, status, errorCode}."""

from django.http import Http404
from rest_framework import exceptions
from rest_framework.renderers import JSONRenderer
from rest_framework.response import Response
from rest_framework.views import exception_handler as drf_handler


class EnvelopeRenderer(JSONRenderer):
    def render(self, data, accepted_media_type=None, renderer_context=None):
        response = renderer_context and renderer_context.get("response")
        if response is not None and not (isinstance(data, dict) and "errorCode" in data):
            code = response.status_code
            message = getattr(response, "message", None) or "Success"
            data = {"message": message, "data": data, "status": "Success", "errorCode": 0}
            if code >= 400:
                data.update(status="Failed", errorCode=code, data=None)
        return super().render(data, accepted_media_type, renderer_context)


def _message(detail) -> str:
    if isinstance(detail, dict):
        field, errs = next(iter(detail.items()))
        return f"Invalid request: {field} {_message(errs)}"
    if isinstance(detail, list):
        return _message(detail[0]) if detail else ""
    return str(detail)


def exception_handler(exc, context):
    response = drf_handler(exc, context)
    if response is None:
        return None  # lỗi không lường trước: Django trả 500 và ghi log
    if isinstance(exc, Http404):
        msg = "Supplier not found"
    elif isinstance(exc, exceptions.APIException):
        msg = _message(exc.detail)
    else:
        msg = "Request failed"
    response.data = {"message": msg, "data": None, "status": "Failed", "errorCode": response.status_code}
    return response


def ok(data, message: str = "Success", status: int = 200) -> Response:
    r = Response(data, status=status)
    r.message = message
    return r
