from google.protobuf.internal import containers as _containers
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from collections.abc import Iterable as _Iterable, Mapping as _Mapping
from typing import ClassVar as _ClassVar, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class SubscriptionLine(_message.Message):
    __slots__ = ("product_id", "quantity")
    PRODUCT_ID_FIELD_NUMBER: _ClassVar[int]
    QUANTITY_FIELD_NUMBER: _ClassVar[int]
    product_id: str
    quantity: int
    def __init__(self, product_id: _Optional[str] = ..., quantity: _Optional[int] = ...) -> None: ...

class SubscriptionCreated(_message.Message):
    __slots__ = ("subscription_id", "customer_id", "lines", "interval_days", "next_delivery_date")
    SUBSCRIPTION_ID_FIELD_NUMBER: _ClassVar[int]
    CUSTOMER_ID_FIELD_NUMBER: _ClassVar[int]
    LINES_FIELD_NUMBER: _ClassVar[int]
    INTERVAL_DAYS_FIELD_NUMBER: _ClassVar[int]
    NEXT_DELIVERY_DATE_FIELD_NUMBER: _ClassVar[int]
    subscription_id: str
    customer_id: str
    lines: _containers.RepeatedCompositeFieldContainer[SubscriptionLine]
    interval_days: int
    next_delivery_date: str
    def __init__(self, subscription_id: _Optional[str] = ..., customer_id: _Optional[str] = ..., lines: _Optional[_Iterable[_Union[SubscriptionLine, _Mapping]]] = ..., interval_days: _Optional[int] = ..., next_delivery_date: _Optional[str] = ...) -> None: ...

class SubscriptionRenewalUpcoming(_message.Message):
    __slots__ = ("subscription_id", "customer_id", "next_delivery_date", "cycle")
    SUBSCRIPTION_ID_FIELD_NUMBER: _ClassVar[int]
    CUSTOMER_ID_FIELD_NUMBER: _ClassVar[int]
    NEXT_DELIVERY_DATE_FIELD_NUMBER: _ClassVar[int]
    CYCLE_FIELD_NUMBER: _ClassVar[int]
    subscription_id: str
    customer_id: str
    next_delivery_date: str
    cycle: int
    def __init__(self, subscription_id: _Optional[str] = ..., customer_id: _Optional[str] = ..., next_delivery_date: _Optional[str] = ..., cycle: _Optional[int] = ...) -> None: ...

class SubscriptionOrderPlaced(_message.Message):
    __slots__ = ("subscription_id", "customer_id", "cycle", "order_id")
    SUBSCRIPTION_ID_FIELD_NUMBER: _ClassVar[int]
    CUSTOMER_ID_FIELD_NUMBER: _ClassVar[int]
    CYCLE_FIELD_NUMBER: _ClassVar[int]
    ORDER_ID_FIELD_NUMBER: _ClassVar[int]
    subscription_id: str
    customer_id: str
    cycle: int
    order_id: str
    def __init__(self, subscription_id: _Optional[str] = ..., customer_id: _Optional[str] = ..., cycle: _Optional[int] = ..., order_id: _Optional[str] = ...) -> None: ...

class RenewalFailed(_message.Message):
    __slots__ = ("subscription_id", "customer_id", "cycle", "attempt", "reason", "next_attempt_at")
    SUBSCRIPTION_ID_FIELD_NUMBER: _ClassVar[int]
    CUSTOMER_ID_FIELD_NUMBER: _ClassVar[int]
    CYCLE_FIELD_NUMBER: _ClassVar[int]
    ATTEMPT_FIELD_NUMBER: _ClassVar[int]
    REASON_FIELD_NUMBER: _ClassVar[int]
    NEXT_ATTEMPT_AT_FIELD_NUMBER: _ClassVar[int]
    subscription_id: str
    customer_id: str
    cycle: int
    attempt: int
    reason: str
    next_attempt_at: str
    def __init__(self, subscription_id: _Optional[str] = ..., customer_id: _Optional[str] = ..., cycle: _Optional[int] = ..., attempt: _Optional[int] = ..., reason: _Optional[str] = ..., next_attempt_at: _Optional[str] = ...) -> None: ...

class SubscriptionStatusChanged(_message.Message):
    __slots__ = ("subscription_id", "customer_id", "from_status", "to_status", "reason")
    SUBSCRIPTION_ID_FIELD_NUMBER: _ClassVar[int]
    CUSTOMER_ID_FIELD_NUMBER: _ClassVar[int]
    FROM_STATUS_FIELD_NUMBER: _ClassVar[int]
    TO_STATUS_FIELD_NUMBER: _ClassVar[int]
    REASON_FIELD_NUMBER: _ClassVar[int]
    subscription_id: str
    customer_id: str
    from_status: str
    to_status: str
    reason: str
    def __init__(self, subscription_id: _Optional[str] = ..., customer_id: _Optional[str] = ..., from_status: _Optional[str] = ..., to_status: _Optional[str] = ..., reason: _Optional[str] = ...) -> None: ...
