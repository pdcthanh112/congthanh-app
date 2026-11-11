from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from typing import ClassVar as _ClassVar, Optional as _Optional

DESCRIPTOR: _descriptor.FileDescriptor

class PromotionActivated(_message.Message):
    __slots__ = ("promotion_id", "code", "starts_at", "ends_at")
    PROMOTION_ID_FIELD_NUMBER: _ClassVar[int]
    CODE_FIELD_NUMBER: _ClassVar[int]
    STARTS_AT_FIELD_NUMBER: _ClassVar[int]
    ENDS_AT_FIELD_NUMBER: _ClassVar[int]
    promotion_id: str
    code: str
    starts_at: int
    ends_at: int
    def __init__(self, promotion_id: _Optional[str] = ..., code: _Optional[str] = ..., starts_at: _Optional[int] = ..., ends_at: _Optional[int] = ...) -> None: ...

class PromotionExpired(_message.Message):
    __slots__ = ("promotion_id",)
    PROMOTION_ID_FIELD_NUMBER: _ClassVar[int]
    promotion_id: str
    def __init__(self, promotion_id: _Optional[str] = ...) -> None: ...

class PromotionRedeemed(_message.Message):
    __slots__ = ("promotion_id", "code", "order_id", "customer_id")
    PROMOTION_ID_FIELD_NUMBER: _ClassVar[int]
    CODE_FIELD_NUMBER: _ClassVar[int]
    ORDER_ID_FIELD_NUMBER: _ClassVar[int]
    CUSTOMER_ID_FIELD_NUMBER: _ClassVar[int]
    promotion_id: str
    code: str
    order_id: str
    customer_id: str
    def __init__(self, promotion_id: _Optional[str] = ..., code: _Optional[str] = ..., order_id: _Optional[str] = ..., customer_id: _Optional[str] = ...) -> None: ...

class RedemptionCancelled(_message.Message):
    __slots__ = ("promotion_id", "order_id", "reason")
    PROMOTION_ID_FIELD_NUMBER: _ClassVar[int]
    ORDER_ID_FIELD_NUMBER: _ClassVar[int]
    REASON_FIELD_NUMBER: _ClassVar[int]
    promotion_id: str
    order_id: str
    reason: str
    def __init__(self, promotion_id: _Optional[str] = ..., order_id: _Optional[str] = ..., reason: _Optional[str] = ...) -> None: ...
