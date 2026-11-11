import datetime

from google.protobuf import timestamp_pb2 as _timestamp_pb2
from google.protobuf.internal import containers as _containers
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from collections.abc import Iterable as _Iterable, Mapping as _Mapping
from typing import ClassVar as _ClassVar, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class ReviewSubmitted(_message.Message):
    __slots__ = ("review_id", "product_id", "customer_id", "order_id", "rating", "submitted_at")
    REVIEW_ID_FIELD_NUMBER: _ClassVar[int]
    PRODUCT_ID_FIELD_NUMBER: _ClassVar[int]
    CUSTOMER_ID_FIELD_NUMBER: _ClassVar[int]
    ORDER_ID_FIELD_NUMBER: _ClassVar[int]
    RATING_FIELD_NUMBER: _ClassVar[int]
    SUBMITTED_AT_FIELD_NUMBER: _ClassVar[int]
    review_id: str
    product_id: str
    customer_id: str
    order_id: str
    rating: int
    submitted_at: _timestamp_pb2.Timestamp
    def __init__(self, review_id: _Optional[str] = ..., product_id: _Optional[str] = ..., customer_id: _Optional[str] = ..., order_id: _Optional[str] = ..., rating: _Optional[int] = ..., submitted_at: _Optional[_Union[datetime.datetime, _timestamp_pb2.Timestamp, _Mapping]] = ...) -> None: ...

class ReviewModerated(_message.Message):
    __slots__ = ("review_id", "product_id", "status", "reason", "moderator_id")
    REVIEW_ID_FIELD_NUMBER: _ClassVar[int]
    PRODUCT_ID_FIELD_NUMBER: _ClassVar[int]
    STATUS_FIELD_NUMBER: _ClassVar[int]
    REASON_FIELD_NUMBER: _ClassVar[int]
    MODERATOR_ID_FIELD_NUMBER: _ClassVar[int]
    review_id: str
    product_id: str
    status: str
    reason: str
    moderator_id: str
    def __init__(self, review_id: _Optional[str] = ..., product_id: _Optional[str] = ..., status: _Optional[str] = ..., reason: _Optional[str] = ..., moderator_id: _Optional[str] = ...) -> None: ...

class ReviewReplied(_message.Message):
    __slots__ = ("review_id", "product_id", "customer_id", "seller_id")
    REVIEW_ID_FIELD_NUMBER: _ClassVar[int]
    PRODUCT_ID_FIELD_NUMBER: _ClassVar[int]
    CUSTOMER_ID_FIELD_NUMBER: _ClassVar[int]
    SELLER_ID_FIELD_NUMBER: _ClassVar[int]
    review_id: str
    product_id: str
    customer_id: str
    seller_id: str
    def __init__(self, review_id: _Optional[str] = ..., product_id: _Optional[str] = ..., customer_id: _Optional[str] = ..., seller_id: _Optional[str] = ...) -> None: ...

class ProductRatingChanged(_message.Message):
    __slots__ = ("product_id", "review_count", "average_rating", "star_counts")
    PRODUCT_ID_FIELD_NUMBER: _ClassVar[int]
    REVIEW_COUNT_FIELD_NUMBER: _ClassVar[int]
    AVERAGE_RATING_FIELD_NUMBER: _ClassVar[int]
    STAR_COUNTS_FIELD_NUMBER: _ClassVar[int]
    product_id: str
    review_count: int
    average_rating: float
    star_counts: _containers.RepeatedScalarFieldContainer[int]
    def __init__(self, product_id: _Optional[str] = ..., review_count: _Optional[int] = ..., average_rating: _Optional[float] = ..., star_counts: _Optional[_Iterable[int]] = ...) -> None: ...
