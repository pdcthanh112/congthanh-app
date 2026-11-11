from ecommerce.common.v1 import money_pb2 as _money_pb2
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from collections.abc import Mapping as _Mapping
from typing import ClassVar as _ClassVar, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class ProductUpserted(_message.Message):
    __slots__ = ("product_id", "sku", "name", "slug", "category_id", "price", "status")
    PRODUCT_ID_FIELD_NUMBER: _ClassVar[int]
    SKU_FIELD_NUMBER: _ClassVar[int]
    NAME_FIELD_NUMBER: _ClassVar[int]
    SLUG_FIELD_NUMBER: _ClassVar[int]
    CATEGORY_ID_FIELD_NUMBER: _ClassVar[int]
    PRICE_FIELD_NUMBER: _ClassVar[int]
    STATUS_FIELD_NUMBER: _ClassVar[int]
    product_id: str
    sku: str
    name: str
    slug: str
    category_id: str
    price: _money_pb2.Money
    status: str
    def __init__(self, product_id: _Optional[str] = ..., sku: _Optional[str] = ..., name: _Optional[str] = ..., slug: _Optional[str] = ..., category_id: _Optional[str] = ..., price: _Optional[_Union[_money_pb2.Money, _Mapping]] = ..., status: _Optional[str] = ...) -> None: ...

class ProductDeleted(_message.Message):
    __slots__ = ("product_id",)
    PRODUCT_ID_FIELD_NUMBER: _ClassVar[int]
    product_id: str
    def __init__(self, product_id: _Optional[str] = ...) -> None: ...
