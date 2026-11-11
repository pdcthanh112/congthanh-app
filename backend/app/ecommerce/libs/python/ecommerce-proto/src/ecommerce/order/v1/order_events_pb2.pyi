from ecommerce.common.v1 import money_pb2 as _money_pb2
from google.protobuf.internal import containers as _containers
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from collections.abc import Iterable as _Iterable, Mapping as _Mapping
from typing import ClassVar as _ClassVar, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class OrderLine(_message.Message):
    __slots__ = ("product_id", "quantity", "unit_price")
    PRODUCT_ID_FIELD_NUMBER: _ClassVar[int]
    QUANTITY_FIELD_NUMBER: _ClassVar[int]
    UNIT_PRICE_FIELD_NUMBER: _ClassVar[int]
    product_id: str
    quantity: int
    unit_price: _money_pb2.Money
    def __init__(self, product_id: _Optional[str] = ..., quantity: _Optional[int] = ..., unit_price: _Optional[_Union[_money_pb2.Money, _Mapping]] = ...) -> None: ...

class OrderPlaced(_message.Message):
    __slots__ = ("order_id", "customer_id", "lines", "total", "checkout_workflow_id", "shipping_address")
    ORDER_ID_FIELD_NUMBER: _ClassVar[int]
    CUSTOMER_ID_FIELD_NUMBER: _ClassVar[int]
    LINES_FIELD_NUMBER: _ClassVar[int]
    TOTAL_FIELD_NUMBER: _ClassVar[int]
    CHECKOUT_WORKFLOW_ID_FIELD_NUMBER: _ClassVar[int]
    SHIPPING_ADDRESS_FIELD_NUMBER: _ClassVar[int]
    order_id: str
    customer_id: str
    lines: _containers.RepeatedCompositeFieldContainer[OrderLine]
    total: _money_pb2.Money
    checkout_workflow_id: str
    shipping_address: ShippingAddress
    def __init__(self, order_id: _Optional[str] = ..., customer_id: _Optional[str] = ..., lines: _Optional[_Iterable[_Union[OrderLine, _Mapping]]] = ..., total: _Optional[_Union[_money_pb2.Money, _Mapping]] = ..., checkout_workflow_id: _Optional[str] = ..., shipping_address: _Optional[_Union[ShippingAddress, _Mapping]] = ...) -> None: ...

class ShippingAddress(_message.Message):
    __slots__ = ("recipient_name", "phone", "line1", "line2", "ward", "district", "region_code", "postal_code", "country_code")
    RECIPIENT_NAME_FIELD_NUMBER: _ClassVar[int]
    PHONE_FIELD_NUMBER: _ClassVar[int]
    LINE1_FIELD_NUMBER: _ClassVar[int]
    LINE2_FIELD_NUMBER: _ClassVar[int]
    WARD_FIELD_NUMBER: _ClassVar[int]
    DISTRICT_FIELD_NUMBER: _ClassVar[int]
    REGION_CODE_FIELD_NUMBER: _ClassVar[int]
    POSTAL_CODE_FIELD_NUMBER: _ClassVar[int]
    COUNTRY_CODE_FIELD_NUMBER: _ClassVar[int]
    recipient_name: str
    phone: str
    line1: str
    line2: str
    ward: str
    district: str
    region_code: str
    postal_code: str
    country_code: str
    def __init__(self, recipient_name: _Optional[str] = ..., phone: _Optional[str] = ..., line1: _Optional[str] = ..., line2: _Optional[str] = ..., ward: _Optional[str] = ..., district: _Optional[str] = ..., region_code: _Optional[str] = ..., postal_code: _Optional[str] = ..., country_code: _Optional[str] = ...) -> None: ...

class OrderConfirmed(_message.Message):
    __slots__ = ("order_id", "payment_id")
    ORDER_ID_FIELD_NUMBER: _ClassVar[int]
    PAYMENT_ID_FIELD_NUMBER: _ClassVar[int]
    order_id: str
    payment_id: str
    def __init__(self, order_id: _Optional[str] = ..., payment_id: _Optional[str] = ...) -> None: ...

class OrderCancelled(_message.Message):
    __slots__ = ("order_id", "reason", "reservation_id")
    ORDER_ID_FIELD_NUMBER: _ClassVar[int]
    REASON_FIELD_NUMBER: _ClassVar[int]
    RESERVATION_ID_FIELD_NUMBER: _ClassVar[int]
    order_id: str
    reason: str
    reservation_id: str
    def __init__(self, order_id: _Optional[str] = ..., reason: _Optional[str] = ..., reservation_id: _Optional[str] = ...) -> None: ...
