from ecommerce.common.v1 import money_pb2 as _money_pb2
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from collections.abc import Mapping as _Mapping
from typing import ClassVar as _ClassVar, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class StoredValueCredited(_message.Message):
    __slots__ = ("account_id", "customer_id", "kind", "amount", "reason", "reference", "balance_after")
    ACCOUNT_ID_FIELD_NUMBER: _ClassVar[int]
    CUSTOMER_ID_FIELD_NUMBER: _ClassVar[int]
    KIND_FIELD_NUMBER: _ClassVar[int]
    AMOUNT_FIELD_NUMBER: _ClassVar[int]
    REASON_FIELD_NUMBER: _ClassVar[int]
    REFERENCE_FIELD_NUMBER: _ClassVar[int]
    BALANCE_AFTER_FIELD_NUMBER: _ClassVar[int]
    account_id: str
    customer_id: str
    kind: str
    amount: _money_pb2.Money
    reason: str
    reference: str
    balance_after: _money_pb2.Money
    def __init__(self, account_id: _Optional[str] = ..., customer_id: _Optional[str] = ..., kind: _Optional[str] = ..., amount: _Optional[_Union[_money_pb2.Money, _Mapping]] = ..., reason: _Optional[str] = ..., reference: _Optional[str] = ..., balance_after: _Optional[_Union[_money_pb2.Money, _Mapping]] = ...) -> None: ...

class StoredValueHeld(_message.Message):
    __slots__ = ("account_id", "hold_id", "order_id", "amount")
    ACCOUNT_ID_FIELD_NUMBER: _ClassVar[int]
    HOLD_ID_FIELD_NUMBER: _ClassVar[int]
    ORDER_ID_FIELD_NUMBER: _ClassVar[int]
    AMOUNT_FIELD_NUMBER: _ClassVar[int]
    account_id: str
    hold_id: str
    order_id: str
    amount: _money_pb2.Money
    def __init__(self, account_id: _Optional[str] = ..., hold_id: _Optional[str] = ..., order_id: _Optional[str] = ..., amount: _Optional[_Union[_money_pb2.Money, _Mapping]] = ...) -> None: ...

class StoredValueDebited(_message.Message):
    __slots__ = ("account_id", "customer_id", "kind", "hold_id", "order_id", "amount", "balance_after")
    ACCOUNT_ID_FIELD_NUMBER: _ClassVar[int]
    CUSTOMER_ID_FIELD_NUMBER: _ClassVar[int]
    KIND_FIELD_NUMBER: _ClassVar[int]
    HOLD_ID_FIELD_NUMBER: _ClassVar[int]
    ORDER_ID_FIELD_NUMBER: _ClassVar[int]
    AMOUNT_FIELD_NUMBER: _ClassVar[int]
    BALANCE_AFTER_FIELD_NUMBER: _ClassVar[int]
    account_id: str
    customer_id: str
    kind: str
    hold_id: str
    order_id: str
    amount: _money_pb2.Money
    balance_after: _money_pb2.Money
    def __init__(self, account_id: _Optional[str] = ..., customer_id: _Optional[str] = ..., kind: _Optional[str] = ..., hold_id: _Optional[str] = ..., order_id: _Optional[str] = ..., amount: _Optional[_Union[_money_pb2.Money, _Mapping]] = ..., balance_after: _Optional[_Union[_money_pb2.Money, _Mapping]] = ...) -> None: ...

class StoredValueReleased(_message.Message):
    __slots__ = ("account_id", "hold_id", "order_id", "amount", "reason")
    ACCOUNT_ID_FIELD_NUMBER: _ClassVar[int]
    HOLD_ID_FIELD_NUMBER: _ClassVar[int]
    ORDER_ID_FIELD_NUMBER: _ClassVar[int]
    AMOUNT_FIELD_NUMBER: _ClassVar[int]
    REASON_FIELD_NUMBER: _ClassVar[int]
    account_id: str
    hold_id: str
    order_id: str
    amount: _money_pb2.Money
    reason: str
    def __init__(self, account_id: _Optional[str] = ..., hold_id: _Optional[str] = ..., order_id: _Optional[str] = ..., amount: _Optional[_Union[_money_pb2.Money, _Mapping]] = ..., reason: _Optional[str] = ...) -> None: ...
