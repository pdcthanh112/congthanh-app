from ecommerce.common.v1 import money_pb2 as _money_pb2
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from collections.abc import Mapping as _Mapping
from typing import ClassVar as _ClassVar, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class PointsEarned(_message.Message):
    __slots__ = ("customer_id", "order_id", "points", "lot_id")
    CUSTOMER_ID_FIELD_NUMBER: _ClassVar[int]
    ORDER_ID_FIELD_NUMBER: _ClassVar[int]
    POINTS_FIELD_NUMBER: _ClassVar[int]
    LOT_ID_FIELD_NUMBER: _ClassVar[int]
    customer_id: str
    order_id: str
    points: int
    lot_id: str
    def __init__(self, customer_id: _Optional[str] = ..., order_id: _Optional[str] = ..., points: _Optional[int] = ..., lot_id: _Optional[str] = ...) -> None: ...

class PointsConfirmed(_message.Message):
    __slots__ = ("customer_id", "lot_id", "order_id", "points", "value")
    CUSTOMER_ID_FIELD_NUMBER: _ClassVar[int]
    LOT_ID_FIELD_NUMBER: _ClassVar[int]
    ORDER_ID_FIELD_NUMBER: _ClassVar[int]
    POINTS_FIELD_NUMBER: _ClassVar[int]
    VALUE_FIELD_NUMBER: _ClassVar[int]
    customer_id: str
    lot_id: str
    order_id: str
    points: int
    value: _money_pb2.Money
    def __init__(self, customer_id: _Optional[str] = ..., lot_id: _Optional[str] = ..., order_id: _Optional[str] = ..., points: _Optional[int] = ..., value: _Optional[_Union[_money_pb2.Money, _Mapping]] = ...) -> None: ...

class PointsRedeemed(_message.Message):
    __slots__ = ("customer_id", "redemption_id", "order_id", "points", "value")
    CUSTOMER_ID_FIELD_NUMBER: _ClassVar[int]
    REDEMPTION_ID_FIELD_NUMBER: _ClassVar[int]
    ORDER_ID_FIELD_NUMBER: _ClassVar[int]
    POINTS_FIELD_NUMBER: _ClassVar[int]
    VALUE_FIELD_NUMBER: _ClassVar[int]
    customer_id: str
    redemption_id: str
    order_id: str
    points: int
    value: _money_pb2.Money
    def __init__(self, customer_id: _Optional[str] = ..., redemption_id: _Optional[str] = ..., order_id: _Optional[str] = ..., points: _Optional[int] = ..., value: _Optional[_Union[_money_pb2.Money, _Mapping]] = ...) -> None: ...

class PointsReversed(_message.Message):
    __slots__ = ("customer_id", "order_id", "points", "confirmed", "value", "reason")
    CUSTOMER_ID_FIELD_NUMBER: _ClassVar[int]
    ORDER_ID_FIELD_NUMBER: _ClassVar[int]
    POINTS_FIELD_NUMBER: _ClassVar[int]
    CONFIRMED_FIELD_NUMBER: _ClassVar[int]
    VALUE_FIELD_NUMBER: _ClassVar[int]
    REASON_FIELD_NUMBER: _ClassVar[int]
    customer_id: str
    order_id: str
    points: int
    confirmed: bool
    value: _money_pb2.Money
    reason: str
    def __init__(self, customer_id: _Optional[str] = ..., order_id: _Optional[str] = ..., points: _Optional[int] = ..., confirmed: _Optional[bool] = ..., value: _Optional[_Union[_money_pb2.Money, _Mapping]] = ..., reason: _Optional[str] = ...) -> None: ...

class PointsExpired(_message.Message):
    __slots__ = ("customer_id", "lot_id", "points", "value")
    CUSTOMER_ID_FIELD_NUMBER: _ClassVar[int]
    LOT_ID_FIELD_NUMBER: _ClassVar[int]
    POINTS_FIELD_NUMBER: _ClassVar[int]
    VALUE_FIELD_NUMBER: _ClassVar[int]
    customer_id: str
    lot_id: str
    points: int
    value: _money_pb2.Money
    def __init__(self, customer_id: _Optional[str] = ..., lot_id: _Optional[str] = ..., points: _Optional[int] = ..., value: _Optional[_Union[_money_pb2.Money, _Mapping]] = ...) -> None: ...

class TierChanged(_message.Message):
    __slots__ = ("customer_id", "from_tier", "to_tier")
    CUSTOMER_ID_FIELD_NUMBER: _ClassVar[int]
    FROM_TIER_FIELD_NUMBER: _ClassVar[int]
    TO_TIER_FIELD_NUMBER: _ClassVar[int]
    customer_id: str
    from_tier: str
    to_tier: str
    def __init__(self, customer_id: _Optional[str] = ..., from_tier: _Optional[str] = ..., to_tier: _Optional[str] = ...) -> None: ...
