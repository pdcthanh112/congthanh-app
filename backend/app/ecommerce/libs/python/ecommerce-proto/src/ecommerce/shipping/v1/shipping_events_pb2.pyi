from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from typing import ClassVar as _ClassVar, Optional as _Optional

DESCRIPTOR: _descriptor.FileDescriptor

class ShipmentCreated(_message.Message):
    __slots__ = ("shipment_id", "order_id", "customer_id")
    SHIPMENT_ID_FIELD_NUMBER: _ClassVar[int]
    ORDER_ID_FIELD_NUMBER: _ClassVar[int]
    CUSTOMER_ID_FIELD_NUMBER: _ClassVar[int]
    shipment_id: str
    order_id: str
    customer_id: str
    def __init__(self, shipment_id: _Optional[str] = ..., order_id: _Optional[str] = ..., customer_id: _Optional[str] = ...) -> None: ...

class ShipmentStatusChanged(_message.Message):
    __slots__ = ("shipment_id", "order_id", "customer_id", "from_status", "to_status", "tracking_number", "carrier")
    SHIPMENT_ID_FIELD_NUMBER: _ClassVar[int]
    ORDER_ID_FIELD_NUMBER: _ClassVar[int]
    CUSTOMER_ID_FIELD_NUMBER: _ClassVar[int]
    FROM_STATUS_FIELD_NUMBER: _ClassVar[int]
    TO_STATUS_FIELD_NUMBER: _ClassVar[int]
    TRACKING_NUMBER_FIELD_NUMBER: _ClassVar[int]
    CARRIER_FIELD_NUMBER: _ClassVar[int]
    shipment_id: str
    order_id: str
    customer_id: str
    from_status: str
    to_status: str
    tracking_number: str
    carrier: str
    def __init__(self, shipment_id: _Optional[str] = ..., order_id: _Optional[str] = ..., customer_id: _Optional[str] = ..., from_status: _Optional[str] = ..., to_status: _Optional[str] = ..., tracking_number: _Optional[str] = ..., carrier: _Optional[str] = ...) -> None: ...
