from ecommerce.common.v1 import money_pb2 as _money_pb2
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from collections.abc import Mapping as _Mapping
from typing import ClassVar as _ClassVar, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class DisputeOpened(_message.Message):
    __slots__ = ("dispute_id", "provider", "provider_dispute_id", "payment_id", "order_id", "customer_id", "amount", "fee", "reason", "evidence_due_by")
    DISPUTE_ID_FIELD_NUMBER: _ClassVar[int]
    PROVIDER_FIELD_NUMBER: _ClassVar[int]
    PROVIDER_DISPUTE_ID_FIELD_NUMBER: _ClassVar[int]
    PAYMENT_ID_FIELD_NUMBER: _ClassVar[int]
    ORDER_ID_FIELD_NUMBER: _ClassVar[int]
    CUSTOMER_ID_FIELD_NUMBER: _ClassVar[int]
    AMOUNT_FIELD_NUMBER: _ClassVar[int]
    FEE_FIELD_NUMBER: _ClassVar[int]
    REASON_FIELD_NUMBER: _ClassVar[int]
    EVIDENCE_DUE_BY_FIELD_NUMBER: _ClassVar[int]
    dispute_id: str
    provider: str
    provider_dispute_id: str
    payment_id: str
    order_id: str
    customer_id: str
    amount: _money_pb2.Money
    fee: _money_pb2.Money
    reason: str
    evidence_due_by: str
    def __init__(self, dispute_id: _Optional[str] = ..., provider: _Optional[str] = ..., provider_dispute_id: _Optional[str] = ..., payment_id: _Optional[str] = ..., order_id: _Optional[str] = ..., customer_id: _Optional[str] = ..., amount: _Optional[_Union[_money_pb2.Money, _Mapping]] = ..., fee: _Optional[_Union[_money_pb2.Money, _Mapping]] = ..., reason: _Optional[str] = ..., evidence_due_by: _Optional[str] = ...) -> None: ...

class DisputeEvidenceSubmitted(_message.Message):
    __slots__ = ("dispute_id", "order_id", "submitted_by")
    DISPUTE_ID_FIELD_NUMBER: _ClassVar[int]
    ORDER_ID_FIELD_NUMBER: _ClassVar[int]
    SUBMITTED_BY_FIELD_NUMBER: _ClassVar[int]
    dispute_id: str
    order_id: str
    submitted_by: str
    def __init__(self, dispute_id: _Optional[str] = ..., order_id: _Optional[str] = ..., submitted_by: _Optional[str] = ...) -> None: ...

class DisputeClosed(_message.Message):
    __slots__ = ("dispute_id", "provider", "order_id", "customer_id", "outcome", "reason", "amount", "fee")
    DISPUTE_ID_FIELD_NUMBER: _ClassVar[int]
    PROVIDER_FIELD_NUMBER: _ClassVar[int]
    ORDER_ID_FIELD_NUMBER: _ClassVar[int]
    CUSTOMER_ID_FIELD_NUMBER: _ClassVar[int]
    OUTCOME_FIELD_NUMBER: _ClassVar[int]
    REASON_FIELD_NUMBER: _ClassVar[int]
    AMOUNT_FIELD_NUMBER: _ClassVar[int]
    FEE_FIELD_NUMBER: _ClassVar[int]
    dispute_id: str
    provider: str
    order_id: str
    customer_id: str
    outcome: str
    reason: str
    amount: _money_pb2.Money
    fee: _money_pb2.Money
    def __init__(self, dispute_id: _Optional[str] = ..., provider: _Optional[str] = ..., order_id: _Optional[str] = ..., customer_id: _Optional[str] = ..., outcome: _Optional[str] = ..., reason: _Optional[str] = ..., amount: _Optional[_Union[_money_pb2.Money, _Mapping]] = ..., fee: _Optional[_Union[_money_pb2.Money, _Mapping]] = ...) -> None: ...
