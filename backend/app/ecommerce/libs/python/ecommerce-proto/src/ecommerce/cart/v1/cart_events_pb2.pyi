from google.protobuf.internal import containers as _containers
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from collections.abc import Iterable as _Iterable
from typing import ClassVar as _ClassVar, Optional as _Optional

DESCRIPTOR: _descriptor.FileDescriptor

class CartItemAdded(_message.Message):
    __slots__ = ("cart_id", "customer_id", "product_id", "quantity")
    CART_ID_FIELD_NUMBER: _ClassVar[int]
    CUSTOMER_ID_FIELD_NUMBER: _ClassVar[int]
    PRODUCT_ID_FIELD_NUMBER: _ClassVar[int]
    QUANTITY_FIELD_NUMBER: _ClassVar[int]
    cart_id: int
    customer_id: str
    product_id: str
    quantity: int
    def __init__(self, cart_id: _Optional[int] = ..., customer_id: _Optional[str] = ..., product_id: _Optional[str] = ..., quantity: _Optional[int] = ...) -> None: ...

class CartItemRemoved(_message.Message):
    __slots__ = ("cart_id", "customer_id", "product_id")
    CART_ID_FIELD_NUMBER: _ClassVar[int]
    CUSTOMER_ID_FIELD_NUMBER: _ClassVar[int]
    PRODUCT_ID_FIELD_NUMBER: _ClassVar[int]
    cart_id: int
    customer_id: str
    product_id: str
    def __init__(self, cart_id: _Optional[int] = ..., customer_id: _Optional[str] = ..., product_id: _Optional[str] = ...) -> None: ...

class CartCleared(_message.Message):
    __slots__ = ("cart_id", "customer_id", "product_ids")
    CART_ID_FIELD_NUMBER: _ClassVar[int]
    CUSTOMER_ID_FIELD_NUMBER: _ClassVar[int]
    PRODUCT_IDS_FIELD_NUMBER: _ClassVar[int]
    cart_id: int
    customer_id: str
    product_ids: _containers.RepeatedScalarFieldContainer[str]
    def __init__(self, cart_id: _Optional[int] = ..., customer_id: _Optional[str] = ..., product_ids: _Optional[_Iterable[str]] = ...) -> None: ...
