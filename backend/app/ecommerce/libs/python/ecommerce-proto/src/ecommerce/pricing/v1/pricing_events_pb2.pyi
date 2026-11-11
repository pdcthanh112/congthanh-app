import datetime

from ecommerce.common.v1 import money_pb2 as _money_pb2
from google.protobuf import timestamp_pb2 as _timestamp_pb2
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from collections.abc import Mapping as _Mapping
from typing import ClassVar as _ClassVar, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class PriceChanged(_message.Message):
    __slots__ = ("product_id", "price_list", "old_price", "new_price", "reason", "effective_at")
    PRODUCT_ID_FIELD_NUMBER: _ClassVar[int]
    PRICE_LIST_FIELD_NUMBER: _ClassVar[int]
    OLD_PRICE_FIELD_NUMBER: _ClassVar[int]
    NEW_PRICE_FIELD_NUMBER: _ClassVar[int]
    REASON_FIELD_NUMBER: _ClassVar[int]
    EFFECTIVE_AT_FIELD_NUMBER: _ClassVar[int]
    product_id: str
    price_list: str
    old_price: _money_pb2.Money
    new_price: _money_pb2.Money
    reason: str
    effective_at: _timestamp_pb2.Timestamp
    def __init__(self, product_id: _Optional[str] = ..., price_list: _Optional[str] = ..., old_price: _Optional[_Union[_money_pb2.Money, _Mapping]] = ..., new_price: _Optional[_Union[_money_pb2.Money, _Mapping]] = ..., reason: _Optional[str] = ..., effective_at: _Optional[_Union[datetime.datetime, _timestamp_pb2.Timestamp, _Mapping]] = ...) -> None: ...
