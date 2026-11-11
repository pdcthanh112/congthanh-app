from ecommerce.common.v1 import money_pb2 as _money_pb2
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from collections.abc import Mapping as _Mapping
from typing import ClassVar as _ClassVar, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class PaymentAuthorized(_message.Message):
    __slots__ = ("payment_id", "order_id", "amount", "provider")
    PAYMENT_ID_FIELD_NUMBER: _ClassVar[int]
    ORDER_ID_FIELD_NUMBER: _ClassVar[int]
    AMOUNT_FIELD_NUMBER: _ClassVar[int]
    PROVIDER_FIELD_NUMBER: _ClassVar[int]
    payment_id: str
    order_id: str
    amount: _money_pb2.Money
    provider: str
    def __init__(self, payment_id: _Optional[str] = ..., order_id: _Optional[str] = ..., amount: _Optional[_Union[_money_pb2.Money, _Mapping]] = ..., provider: _Optional[str] = ...) -> None: ...

class PaymentFailed(_message.Message):
    __slots__ = ("order_id", "reason")
    ORDER_ID_FIELD_NUMBER: _ClassVar[int]
    REASON_FIELD_NUMBER: _ClassVar[int]
    order_id: str
    reason: str
    def __init__(self, order_id: _Optional[str] = ..., reason: _Optional[str] = ...) -> None: ...

class PaymentRefunded(_message.Message):
    __slots__ = ("payment_id", "order_id", "amount")
    PAYMENT_ID_FIELD_NUMBER: _ClassVar[int]
    ORDER_ID_FIELD_NUMBER: _ClassVar[int]
    AMOUNT_FIELD_NUMBER: _ClassVar[int]
    payment_id: str
    order_id: str
    amount: _money_pb2.Money
    def __init__(self, payment_id: _Optional[str] = ..., order_id: _Optional[str] = ..., amount: _Optional[_Union[_money_pb2.Money, _Mapping]] = ...) -> None: ...
