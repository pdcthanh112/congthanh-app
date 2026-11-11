import datetime

from google.protobuf import timestamp_pb2 as _timestamp_pb2
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from collections.abc import Mapping as _Mapping
from typing import ClassVar as _ClassVar, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class CustomerRiskFeatures(_message.Message):
    __slots__ = ("customer_id", "orders_1h", "order_value_1h", "currency", "payment_failures_1h", "cancellations_24h", "distinct_addresses_24h", "computed_at")
    CUSTOMER_ID_FIELD_NUMBER: _ClassVar[int]
    ORDERS_1H_FIELD_NUMBER: _ClassVar[int]
    ORDER_VALUE_1H_FIELD_NUMBER: _ClassVar[int]
    CURRENCY_FIELD_NUMBER: _ClassVar[int]
    PAYMENT_FAILURES_1H_FIELD_NUMBER: _ClassVar[int]
    CANCELLATIONS_24H_FIELD_NUMBER: _ClassVar[int]
    DISTINCT_ADDRESSES_24H_FIELD_NUMBER: _ClassVar[int]
    COMPUTED_AT_FIELD_NUMBER: _ClassVar[int]
    customer_id: str
    orders_1h: int
    order_value_1h: int
    currency: str
    payment_failures_1h: int
    cancellations_24h: int
    distinct_addresses_24h: int
    computed_at: _timestamp_pb2.Timestamp
    def __init__(self, customer_id: _Optional[str] = ..., orders_1h: _Optional[int] = ..., order_value_1h: _Optional[int] = ..., currency: _Optional[str] = ..., payment_failures_1h: _Optional[int] = ..., cancellations_24h: _Optional[int] = ..., distinct_addresses_24h: _Optional[int] = ..., computed_at: _Optional[_Union[datetime.datetime, _timestamp_pb2.Timestamp, _Mapping]] = ...) -> None: ...

class ProductTrending(_message.Message):
    __slots__ = ("product_id", "score", "cart_adds", "orders", "units", "wishlist_adds", "window_start", "window_end")
    PRODUCT_ID_FIELD_NUMBER: _ClassVar[int]
    SCORE_FIELD_NUMBER: _ClassVar[int]
    CART_ADDS_FIELD_NUMBER: _ClassVar[int]
    ORDERS_FIELD_NUMBER: _ClassVar[int]
    UNITS_FIELD_NUMBER: _ClassVar[int]
    WISHLIST_ADDS_FIELD_NUMBER: _ClassVar[int]
    WINDOW_START_FIELD_NUMBER: _ClassVar[int]
    WINDOW_END_FIELD_NUMBER: _ClassVar[int]
    product_id: str
    score: float
    cart_adds: int
    orders: int
    units: int
    wishlist_adds: int
    window_start: _timestamp_pb2.Timestamp
    window_end: _timestamp_pb2.Timestamp
    def __init__(self, product_id: _Optional[str] = ..., score: _Optional[float] = ..., cart_adds: _Optional[int] = ..., orders: _Optional[int] = ..., units: _Optional[int] = ..., wishlist_adds: _Optional[int] = ..., window_start: _Optional[_Union[datetime.datetime, _timestamp_pb2.Timestamp, _Mapping]] = ..., window_end: _Optional[_Union[datetime.datetime, _timestamp_pb2.Timestamp, _Mapping]] = ...) -> None: ...

class StockCover(_message.Message):
    __slots__ = ("product_id", "available", "units_sold_24h", "hours_of_cover", "at_risk", "risk_changed", "computed_at")
    PRODUCT_ID_FIELD_NUMBER: _ClassVar[int]
    AVAILABLE_FIELD_NUMBER: _ClassVar[int]
    UNITS_SOLD_24H_FIELD_NUMBER: _ClassVar[int]
    HOURS_OF_COVER_FIELD_NUMBER: _ClassVar[int]
    AT_RISK_FIELD_NUMBER: _ClassVar[int]
    RISK_CHANGED_FIELD_NUMBER: _ClassVar[int]
    COMPUTED_AT_FIELD_NUMBER: _ClassVar[int]
    product_id: str
    available: int
    units_sold_24h: int
    hours_of_cover: float
    at_risk: bool
    risk_changed: bool
    computed_at: _timestamp_pb2.Timestamp
    def __init__(self, product_id: _Optional[str] = ..., available: _Optional[int] = ..., units_sold_24h: _Optional[int] = ..., hours_of_cover: _Optional[float] = ..., at_risk: _Optional[bool] = ..., risk_changed: _Optional[bool] = ..., computed_at: _Optional[_Union[datetime.datetime, _timestamp_pb2.Timestamp, _Mapping]] = ...) -> None: ...
