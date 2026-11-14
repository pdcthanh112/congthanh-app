from ecommerce.common.v1 import money_pb2 as _money_pb2
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from collections.abc import Mapping as _Mapping
from typing import ClassVar as _ClassVar, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class ReconciliationMismatchDetected(_message.Message):
    __slots__ = ("run_id", "provider", "kind", "provider_ref", "order_id", "provider_amount", "internal_amount")
    RUN_ID_FIELD_NUMBER: _ClassVar[int]
    PROVIDER_FIELD_NUMBER: _ClassVar[int]
    KIND_FIELD_NUMBER: _ClassVar[int]
    PROVIDER_REF_FIELD_NUMBER: _ClassVar[int]
    ORDER_ID_FIELD_NUMBER: _ClassVar[int]
    PROVIDER_AMOUNT_FIELD_NUMBER: _ClassVar[int]
    INTERNAL_AMOUNT_FIELD_NUMBER: _ClassVar[int]
    run_id: str
    provider: str
    kind: str
    provider_ref: str
    order_id: str
    provider_amount: _money_pb2.Money
    internal_amount: _money_pb2.Money
    def __init__(self, run_id: _Optional[str] = ..., provider: _Optional[str] = ..., kind: _Optional[str] = ..., provider_ref: _Optional[str] = ..., order_id: _Optional[str] = ..., provider_amount: _Optional[_Union[_money_pb2.Money, _Mapping]] = ..., internal_amount: _Optional[_Union[_money_pb2.Money, _Mapping]] = ...) -> None: ...

class ReconciliationRunCompleted(_message.Message):
    __slots__ = ("run_id", "provider", "business_date", "matched", "mismatched", "provider_fees")
    RUN_ID_FIELD_NUMBER: _ClassVar[int]
    PROVIDER_FIELD_NUMBER: _ClassVar[int]
    BUSINESS_DATE_FIELD_NUMBER: _ClassVar[int]
    MATCHED_FIELD_NUMBER: _ClassVar[int]
    MISMATCHED_FIELD_NUMBER: _ClassVar[int]
    PROVIDER_FEES_FIELD_NUMBER: _ClassVar[int]
    run_id: str
    provider: str
    business_date: str
    matched: int
    mismatched: int
    provider_fees: _money_pb2.Money
    def __init__(self, run_id: _Optional[str] = ..., provider: _Optional[str] = ..., business_date: _Optional[str] = ..., matched: _Optional[int] = ..., mismatched: _Optional[int] = ..., provider_fees: _Optional[_Union[_money_pb2.Money, _Mapping]] = ...) -> None: ...
