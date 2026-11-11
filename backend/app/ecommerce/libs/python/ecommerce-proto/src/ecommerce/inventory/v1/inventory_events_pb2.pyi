from google.protobuf.internal import containers as _containers
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from collections.abc import Iterable as _Iterable, Mapping as _Mapping
from typing import ClassVar as _ClassVar, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class ReservedLine(_message.Message):
    __slots__ = ("product_id", "warehouse_id", "quantity")
    PRODUCT_ID_FIELD_NUMBER: _ClassVar[int]
    WAREHOUSE_ID_FIELD_NUMBER: _ClassVar[int]
    QUANTITY_FIELD_NUMBER: _ClassVar[int]
    product_id: str
    warehouse_id: str
    quantity: int
    def __init__(self, product_id: _Optional[str] = ..., warehouse_id: _Optional[str] = ..., quantity: _Optional[int] = ...) -> None: ...

class InventoryReserved(_message.Message):
    __slots__ = ("reservation_id", "customer_id", "lines", "expires_at")
    RESERVATION_ID_FIELD_NUMBER: _ClassVar[int]
    CUSTOMER_ID_FIELD_NUMBER: _ClassVar[int]
    LINES_FIELD_NUMBER: _ClassVar[int]
    EXPIRES_AT_FIELD_NUMBER: _ClassVar[int]
    reservation_id: str
    customer_id: str
    lines: _containers.RepeatedCompositeFieldContainer[ReservedLine]
    expires_at: int
    def __init__(self, reservation_id: _Optional[str] = ..., customer_id: _Optional[str] = ..., lines: _Optional[_Iterable[_Union[ReservedLine, _Mapping]]] = ..., expires_at: _Optional[int] = ...) -> None: ...

class InventoryReservationFailed(_message.Message):
    __slots__ = ("reservation_id", "reason")
    RESERVATION_ID_FIELD_NUMBER: _ClassVar[int]
    REASON_FIELD_NUMBER: _ClassVar[int]
    reservation_id: str
    reason: str
    def __init__(self, reservation_id: _Optional[str] = ..., reason: _Optional[str] = ...) -> None: ...

class InventoryReleased(_message.Message):
    __slots__ = ("reservation_id", "lines", "reason", "expired")
    RESERVATION_ID_FIELD_NUMBER: _ClassVar[int]
    LINES_FIELD_NUMBER: _ClassVar[int]
    REASON_FIELD_NUMBER: _ClassVar[int]
    EXPIRED_FIELD_NUMBER: _ClassVar[int]
    reservation_id: str
    lines: _containers.RepeatedCompositeFieldContainer[ReservedLine]
    reason: str
    expired: bool
    def __init__(self, reservation_id: _Optional[str] = ..., lines: _Optional[_Iterable[_Union[ReservedLine, _Mapping]]] = ..., reason: _Optional[str] = ..., expired: _Optional[bool] = ...) -> None: ...

class InventoryCommitted(_message.Message):
    __slots__ = ("reservation_id", "order_id", "lines")
    RESERVATION_ID_FIELD_NUMBER: _ClassVar[int]
    ORDER_ID_FIELD_NUMBER: _ClassVar[int]
    LINES_FIELD_NUMBER: _ClassVar[int]
    reservation_id: str
    order_id: str
    lines: _containers.RepeatedCompositeFieldContainer[ReservedLine]
    def __init__(self, reservation_id: _Optional[str] = ..., order_id: _Optional[str] = ..., lines: _Optional[_Iterable[_Union[ReservedLine, _Mapping]]] = ...) -> None: ...

class StockLevelChanged(_message.Message):
    __slots__ = ("product_id", "warehouse_id", "quantity", "available", "reason")
    PRODUCT_ID_FIELD_NUMBER: _ClassVar[int]
    WAREHOUSE_ID_FIELD_NUMBER: _ClassVar[int]
    QUANTITY_FIELD_NUMBER: _ClassVar[int]
    AVAILABLE_FIELD_NUMBER: _ClassVar[int]
    REASON_FIELD_NUMBER: _ClassVar[int]
    product_id: str
    warehouse_id: str
    quantity: int
    available: int
    reason: str
    def __init__(self, product_id: _Optional[str] = ..., warehouse_id: _Optional[str] = ..., quantity: _Optional[int] = ..., available: _Optional[int] = ..., reason: _Optional[str] = ...) -> None: ...

class LowStockDetected(_message.Message):
    __slots__ = ("product_id", "available", "reorder_point")
    PRODUCT_ID_FIELD_NUMBER: _ClassVar[int]
    AVAILABLE_FIELD_NUMBER: _ClassVar[int]
    REORDER_POINT_FIELD_NUMBER: _ClassVar[int]
    product_id: str
    available: int
    reorder_point: int
    def __init__(self, product_id: _Optional[str] = ..., available: _Optional[int] = ..., reorder_point: _Optional[int] = ...) -> None: ...

class FlashSaleClaimed(_message.Message):
    __slots__ = ("sale_id", "product_id", "customer_id", "quantity", "claim_id", "expires_at")
    SALE_ID_FIELD_NUMBER: _ClassVar[int]
    PRODUCT_ID_FIELD_NUMBER: _ClassVar[int]
    CUSTOMER_ID_FIELD_NUMBER: _ClassVar[int]
    QUANTITY_FIELD_NUMBER: _ClassVar[int]
    CLAIM_ID_FIELD_NUMBER: _ClassVar[int]
    EXPIRES_AT_FIELD_NUMBER: _ClassVar[int]
    sale_id: str
    product_id: str
    customer_id: str
    quantity: int
    claim_id: str
    expires_at: int
    def __init__(self, sale_id: _Optional[str] = ..., product_id: _Optional[str] = ..., customer_id: _Optional[str] = ..., quantity: _Optional[int] = ..., claim_id: _Optional[str] = ..., expires_at: _Optional[int] = ...) -> None: ...
