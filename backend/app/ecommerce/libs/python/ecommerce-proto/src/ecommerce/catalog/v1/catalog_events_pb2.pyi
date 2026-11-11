import datetime

from google.protobuf import timestamp_pb2 as _timestamp_pb2
from google.protobuf.internal import containers as _containers
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from collections.abc import Iterable as _Iterable, Mapping as _Mapping
from typing import ClassVar as _ClassVar, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class CategoryUpserted(_message.Message):
    __slots__ = ("category_id", "name", "slug", "parent_id", "status", "path", "updated_at")
    CATEGORY_ID_FIELD_NUMBER: _ClassVar[int]
    NAME_FIELD_NUMBER: _ClassVar[int]
    SLUG_FIELD_NUMBER: _ClassVar[int]
    PARENT_ID_FIELD_NUMBER: _ClassVar[int]
    STATUS_FIELD_NUMBER: _ClassVar[int]
    PATH_FIELD_NUMBER: _ClassVar[int]
    UPDATED_AT_FIELD_NUMBER: _ClassVar[int]
    category_id: str
    name: str
    slug: str
    parent_id: str
    status: str
    path: _containers.RepeatedScalarFieldContainer[str]
    updated_at: _timestamp_pb2.Timestamp
    def __init__(self, category_id: _Optional[str] = ..., name: _Optional[str] = ..., slug: _Optional[str] = ..., parent_id: _Optional[str] = ..., status: _Optional[str] = ..., path: _Optional[_Iterable[str]] = ..., updated_at: _Optional[_Union[datetime.datetime, _timestamp_pb2.Timestamp, _Mapping]] = ...) -> None: ...

class BrandUpserted(_message.Message):
    __slots__ = ("brand_id", "name", "slug", "image")
    BRAND_ID_FIELD_NUMBER: _ClassVar[int]
    NAME_FIELD_NUMBER: _ClassVar[int]
    SLUG_FIELD_NUMBER: _ClassVar[int]
    IMAGE_FIELD_NUMBER: _ClassVar[int]
    brand_id: int
    name: str
    slug: str
    image: str
    def __init__(self, brand_id: _Optional[int] = ..., name: _Optional[str] = ..., slug: _Optional[str] = ..., image: _Optional[str] = ...) -> None: ...

class ProductTagsChanged(_message.Message):
    __slots__ = ("product_id", "tag_ids")
    PRODUCT_ID_FIELD_NUMBER: _ClassVar[int]
    TAG_IDS_FIELD_NUMBER: _ClassVar[int]
    product_id: str
    tag_ids: _containers.RepeatedScalarFieldContainer[int]
    def __init__(self, product_id: _Optional[str] = ..., tag_ids: _Optional[_Iterable[int]] = ...) -> None: ...
