from ecommerce.common.v1 import money_pb2 as _money_pb2
from google.protobuf.internal import containers as _containers
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from collections.abc import Iterable as _Iterable, Mapping as _Mapping
from typing import ClassVar as _ClassVar, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class FraudReviewRequested(_message.Message):
    __slots__ = ("assessment_id", "checkout_id", "customer_id", "amount", "score", "reasons")
    ASSESSMENT_ID_FIELD_NUMBER: _ClassVar[int]
    CHECKOUT_ID_FIELD_NUMBER: _ClassVar[int]
    CUSTOMER_ID_FIELD_NUMBER: _ClassVar[int]
    AMOUNT_FIELD_NUMBER: _ClassVar[int]
    SCORE_FIELD_NUMBER: _ClassVar[int]
    REASONS_FIELD_NUMBER: _ClassVar[int]
    assessment_id: str
    checkout_id: str
    customer_id: str
    amount: _money_pb2.Money
    score: int
    reasons: _containers.RepeatedScalarFieldContainer[str]
    def __init__(self, assessment_id: _Optional[str] = ..., checkout_id: _Optional[str] = ..., customer_id: _Optional[str] = ..., amount: _Optional[_Union[_money_pb2.Money, _Mapping]] = ..., score: _Optional[int] = ..., reasons: _Optional[_Iterable[str]] = ...) -> None: ...

class FraudReviewDecided(_message.Message):
    __slots__ = ("assessment_id", "checkout_id", "customer_id", "decision", "decided_by", "note")
    ASSESSMENT_ID_FIELD_NUMBER: _ClassVar[int]
    CHECKOUT_ID_FIELD_NUMBER: _ClassVar[int]
    CUSTOMER_ID_FIELD_NUMBER: _ClassVar[int]
    DECISION_FIELD_NUMBER: _ClassVar[int]
    DECIDED_BY_FIELD_NUMBER: _ClassVar[int]
    NOTE_FIELD_NUMBER: _ClassVar[int]
    assessment_id: str
    checkout_id: str
    customer_id: str
    decision: str
    decided_by: str
    note: str
    def __init__(self, assessment_id: _Optional[str] = ..., checkout_id: _Optional[str] = ..., customer_id: _Optional[str] = ..., decision: _Optional[str] = ..., decided_by: _Optional[str] = ..., note: _Optional[str] = ...) -> None: ...
