from ecommerce.common.v1 import money_pb2 as _money_pb2
from google.protobuf.internal import containers as _containers
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from collections.abc import Iterable as _Iterable, Mapping as _Mapping
from typing import ClassVar as _ClassVar, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class ProductUpserted(_message.Message):
    __slots__ = ("product_id", "sku", "name", "slug", "category_id", "price", "status", "parent_id", "has_variant", "brand_id", "category_ids", "attributes", "variants", "options", "featured")
    PRODUCT_ID_FIELD_NUMBER: _ClassVar[int]
    SKU_FIELD_NUMBER: _ClassVar[int]
    NAME_FIELD_NUMBER: _ClassVar[int]
    SLUG_FIELD_NUMBER: _ClassVar[int]
    CATEGORY_ID_FIELD_NUMBER: _ClassVar[int]
    PRICE_FIELD_NUMBER: _ClassVar[int]
    STATUS_FIELD_NUMBER: _ClassVar[int]
    PARENT_ID_FIELD_NUMBER: _ClassVar[int]
    HAS_VARIANT_FIELD_NUMBER: _ClassVar[int]
    BRAND_ID_FIELD_NUMBER: _ClassVar[int]
    CATEGORY_IDS_FIELD_NUMBER: _ClassVar[int]
    ATTRIBUTES_FIELD_NUMBER: _ClassVar[int]
    VARIANTS_FIELD_NUMBER: _ClassVar[int]
    OPTIONS_FIELD_NUMBER: _ClassVar[int]
    FEATURED_FIELD_NUMBER: _ClassVar[int]
    product_id: str
    sku: str
    name: str
    slug: str
    category_id: str
    price: _money_pb2.Money
    status: str
    parent_id: str
    has_variant: bool
    brand_id: int
    category_ids: _containers.RepeatedScalarFieldContainer[str]
    attributes: _containers.RepeatedCompositeFieldContainer[AttributeValue]
    variants: _containers.RepeatedCompositeFieldContainer[VariantSummary]
    options: _containers.RepeatedCompositeFieldContainer[OptionValue]
    featured: bool
    def __init__(self, product_id: _Optional[str] = ..., sku: _Optional[str] = ..., name: _Optional[str] = ..., slug: _Optional[str] = ..., category_id: _Optional[str] = ..., price: _Optional[_Union[_money_pb2.Money, _Mapping]] = ..., status: _Optional[str] = ..., parent_id: _Optional[str] = ..., has_variant: _Optional[bool] = ..., brand_id: _Optional[int] = ..., category_ids: _Optional[_Iterable[str]] = ..., attributes: _Optional[_Iterable[_Union[AttributeValue, _Mapping]]] = ..., variants: _Optional[_Iterable[_Union[VariantSummary, _Mapping]]] = ..., options: _Optional[_Iterable[_Union[OptionValue, _Mapping]]] = ..., featured: _Optional[bool] = ...) -> None: ...

class AttributeValue(_message.Message):
    __slots__ = ("code", "name", "value", "number", "unit", "filterable", "searchable")
    CODE_FIELD_NUMBER: _ClassVar[int]
    NAME_FIELD_NUMBER: _ClassVar[int]
    VALUE_FIELD_NUMBER: _ClassVar[int]
    NUMBER_FIELD_NUMBER: _ClassVar[int]
    UNIT_FIELD_NUMBER: _ClassVar[int]
    FILTERABLE_FIELD_NUMBER: _ClassVar[int]
    SEARCHABLE_FIELD_NUMBER: _ClassVar[int]
    code: str
    name: str
    value: str
    number: float
    unit: str
    filterable: bool
    searchable: bool
    def __init__(self, code: _Optional[str] = ..., name: _Optional[str] = ..., value: _Optional[str] = ..., number: _Optional[float] = ..., unit: _Optional[str] = ..., filterable: _Optional[bool] = ..., searchable: _Optional[bool] = ...) -> None: ...

class OptionValue(_message.Message):
    __slots__ = ("option_id", "option_name", "value_id", "value")
    OPTION_ID_FIELD_NUMBER: _ClassVar[int]
    OPTION_NAME_FIELD_NUMBER: _ClassVar[int]
    VALUE_ID_FIELD_NUMBER: _ClassVar[int]
    VALUE_FIELD_NUMBER: _ClassVar[int]
    option_id: int
    option_name: str
    value_id: int
    value: str
    def __init__(self, option_id: _Optional[int] = ..., option_name: _Optional[str] = ..., value_id: _Optional[int] = ..., value: _Optional[str] = ...) -> None: ...

class VariantSummary(_message.Message):
    __slots__ = ("product_id", "sku", "price", "status", "options")
    PRODUCT_ID_FIELD_NUMBER: _ClassVar[int]
    SKU_FIELD_NUMBER: _ClassVar[int]
    PRICE_FIELD_NUMBER: _ClassVar[int]
    STATUS_FIELD_NUMBER: _ClassVar[int]
    OPTIONS_FIELD_NUMBER: _ClassVar[int]
    product_id: str
    sku: str
    price: _money_pb2.Money
    status: str
    options: _containers.RepeatedCompositeFieldContainer[OptionValue]
    def __init__(self, product_id: _Optional[str] = ..., sku: _Optional[str] = ..., price: _Optional[_Union[_money_pb2.Money, _Mapping]] = ..., status: _Optional[str] = ..., options: _Optional[_Iterable[_Union[OptionValue, _Mapping]]] = ...) -> None: ...

class ProductDeleted(_message.Message):
    __slots__ = ("product_id",)
    PRODUCT_ID_FIELD_NUMBER: _ClassVar[int]
    product_id: str
    def __init__(self, product_id: _Optional[str] = ...) -> None: ...
