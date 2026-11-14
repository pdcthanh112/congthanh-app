from ecommerce.common.v1 import money_pb2 as _money_pb2
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from collections.abc import Mapping as _Mapping
from typing import ClassVar as _ClassVar, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class PaymentAuthorized(_message.Message):
    __slots__ = ("payment_id", "order_id", "amount", "provider", "method")
    PAYMENT_ID_FIELD_NUMBER: _ClassVar[int]
    ORDER_ID_FIELD_NUMBER: _ClassVar[int]
    AMOUNT_FIELD_NUMBER: _ClassVar[int]
    PROVIDER_FIELD_NUMBER: _ClassVar[int]
    METHOD_FIELD_NUMBER: _ClassVar[int]
    payment_id: str
    order_id: str
    amount: _money_pb2.Money
    provider: str
    method: str
    def __init__(self, payment_id: _Optional[str] = ..., order_id: _Optional[str] = ..., amount: _Optional[_Union[_money_pb2.Money, _Mapping]] = ..., provider: _Optional[str] = ..., method: _Optional[str] = ...) -> None: ...

class PaymentFailed(_message.Message):
    __slots__ = ("order_id", "reason")
    ORDER_ID_FIELD_NUMBER: _ClassVar[int]
    REASON_FIELD_NUMBER: _ClassVar[int]
    order_id: str
    reason: str
    def __init__(self, order_id: _Optional[str] = ..., reason: _Optional[str] = ...) -> None: ...

class PaymentCaptured(_message.Message):
    __slots__ = ("payment_id", "order_id", "amount", "provider", "method", "customer_id", "provider_ref")
    PAYMENT_ID_FIELD_NUMBER: _ClassVar[int]
    ORDER_ID_FIELD_NUMBER: _ClassVar[int]
    AMOUNT_FIELD_NUMBER: _ClassVar[int]
    PROVIDER_FIELD_NUMBER: _ClassVar[int]
    METHOD_FIELD_NUMBER: _ClassVar[int]
    CUSTOMER_ID_FIELD_NUMBER: _ClassVar[int]
    PROVIDER_REF_FIELD_NUMBER: _ClassVar[int]
    payment_id: str
    order_id: str
    amount: _money_pb2.Money
    provider: str
    method: str
    customer_id: str
    provider_ref: str
    def __init__(self, payment_id: _Optional[str] = ..., order_id: _Optional[str] = ..., amount: _Optional[_Union[_money_pb2.Money, _Mapping]] = ..., provider: _Optional[str] = ..., method: _Optional[str] = ..., customer_id: _Optional[str] = ..., provider_ref: _Optional[str] = ...) -> None: ...

class PaymentRefunded(_message.Message):
    __slots__ = ("payment_id", "order_id", "amount", "refund_id", "reason", "provider", "fully_refunded", "provider_ref")
    PAYMENT_ID_FIELD_NUMBER: _ClassVar[int]
    ORDER_ID_FIELD_NUMBER: _ClassVar[int]
    AMOUNT_FIELD_NUMBER: _ClassVar[int]
    REFUND_ID_FIELD_NUMBER: _ClassVar[int]
    REASON_FIELD_NUMBER: _ClassVar[int]
    PROVIDER_FIELD_NUMBER: _ClassVar[int]
    FULLY_REFUNDED_FIELD_NUMBER: _ClassVar[int]
    PROVIDER_REF_FIELD_NUMBER: _ClassVar[int]
    payment_id: str
    order_id: str
    amount: _money_pb2.Money
    refund_id: str
    reason: str
    provider: str
    fully_refunded: bool
    provider_ref: str
    def __init__(self, payment_id: _Optional[str] = ..., order_id: _Optional[str] = ..., amount: _Optional[_Union[_money_pb2.Money, _Mapping]] = ..., refund_id: _Optional[str] = ..., reason: _Optional[str] = ..., provider: _Optional[str] = ..., fully_refunded: _Optional[bool] = ..., provider_ref: _Optional[str] = ...) -> None: ...
