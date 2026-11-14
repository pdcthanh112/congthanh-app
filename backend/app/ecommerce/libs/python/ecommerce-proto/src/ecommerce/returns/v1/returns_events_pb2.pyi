from ecommerce.common.v1 import money_pb2 as _money_pb2
from google.protobuf.internal import containers as _containers
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from collections.abc import Iterable as _Iterable, Mapping as _Mapping
from typing import ClassVar as _ClassVar, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class ReturnLine(_message.Message):
    __slots__ = ("product_id", "quantity", "reason")
    PRODUCT_ID_FIELD_NUMBER: _ClassVar[int]
    QUANTITY_FIELD_NUMBER: _ClassVar[int]
    REASON_FIELD_NUMBER: _ClassVar[int]
    product_id: str
    quantity: int
    reason: str
    def __init__(self, product_id: _Optional[str] = ..., quantity: _Optional[int] = ..., reason: _Optional[str] = ...) -> None: ...

class ReturnRequested(_message.Message):
    __slots__ = ("return_id", "order_id", "customer_id", "lines", "rma_code")
    RETURN_ID_FIELD_NUMBER: _ClassVar[int]
    ORDER_ID_FIELD_NUMBER: _ClassVar[int]
    CUSTOMER_ID_FIELD_NUMBER: _ClassVar[int]
    LINES_FIELD_NUMBER: _ClassVar[int]
    RMA_CODE_FIELD_NUMBER: _ClassVar[int]
    return_id: str
    order_id: str
    customer_id: str
    lines: _containers.RepeatedCompositeFieldContainer[ReturnLine]
    rma_code: str
    def __init__(self, return_id: _Optional[str] = ..., order_id: _Optional[str] = ..., customer_id: _Optional[str] = ..., lines: _Optional[_Iterable[_Union[ReturnLine, _Mapping]]] = ..., rma_code: _Optional[str] = ...) -> None: ...

class ReturnApproved(_message.Message):
    __slots__ = ("return_id", "order_id", "customer_id", "rma_code", "approved_by")
    RETURN_ID_FIELD_NUMBER: _ClassVar[int]
    ORDER_ID_FIELD_NUMBER: _ClassVar[int]
    CUSTOMER_ID_FIELD_NUMBER: _ClassVar[int]
    RMA_CODE_FIELD_NUMBER: _ClassVar[int]
    APPROVED_BY_FIELD_NUMBER: _ClassVar[int]
    return_id: str
    order_id: str
    customer_id: str
    rma_code: str
    approved_by: str
    def __init__(self, return_id: _Optional[str] = ..., order_id: _Optional[str] = ..., customer_id: _Optional[str] = ..., rma_code: _Optional[str] = ..., approved_by: _Optional[str] = ...) -> None: ...

class ReturnRejected(_message.Message):
    __slots__ = ("return_id", "order_id", "customer_id", "reason")
    RETURN_ID_FIELD_NUMBER: _ClassVar[int]
    ORDER_ID_FIELD_NUMBER: _ClassVar[int]
    CUSTOMER_ID_FIELD_NUMBER: _ClassVar[int]
    REASON_FIELD_NUMBER: _ClassVar[int]
    return_id: str
    order_id: str
    customer_id: str
    reason: str
    def __init__(self, return_id: _Optional[str] = ..., order_id: _Optional[str] = ..., customer_id: _Optional[str] = ..., reason: _Optional[str] = ...) -> None: ...

class ReturnReceived(_message.Message):
    __slots__ = ("return_id", "order_id", "lines")
    RETURN_ID_FIELD_NUMBER: _ClassVar[int]
    ORDER_ID_FIELD_NUMBER: _ClassVar[int]
    LINES_FIELD_NUMBER: _ClassVar[int]
    return_id: str
    order_id: str
    lines: _containers.RepeatedCompositeFieldContainer[ReceivedLine]
    def __init__(self, return_id: _Optional[str] = ..., order_id: _Optional[str] = ..., lines: _Optional[_Iterable[_Union[ReceivedLine, _Mapping]]] = ...) -> None: ...

class ReceivedLine(_message.Message):
    __slots__ = ("product_id", "quantity", "condition", "restock")
    PRODUCT_ID_FIELD_NUMBER: _ClassVar[int]
    QUANTITY_FIELD_NUMBER: _ClassVar[int]
    CONDITION_FIELD_NUMBER: _ClassVar[int]
    RESTOCK_FIELD_NUMBER: _ClassVar[int]
    product_id: str
    quantity: int
    condition: str
    restock: bool
    def __init__(self, product_id: _Optional[str] = ..., quantity: _Optional[int] = ..., condition: _Optional[str] = ..., restock: _Optional[bool] = ...) -> None: ...

class RefundIssued(_message.Message):
    __slots__ = ("return_id", "order_id", "customer_id", "amount", "restocking_fee", "method", "payment_id")
    RETURN_ID_FIELD_NUMBER: _ClassVar[int]
    ORDER_ID_FIELD_NUMBER: _ClassVar[int]
    CUSTOMER_ID_FIELD_NUMBER: _ClassVar[int]
    AMOUNT_FIELD_NUMBER: _ClassVar[int]
    RESTOCKING_FEE_FIELD_NUMBER: _ClassVar[int]
    METHOD_FIELD_NUMBER: _ClassVar[int]
    PAYMENT_ID_FIELD_NUMBER: _ClassVar[int]
    return_id: str
    order_id: str
    customer_id: str
    amount: _money_pb2.Money
    restocking_fee: _money_pb2.Money
    method: str
    payment_id: str
    def __init__(self, return_id: _Optional[str] = ..., order_id: _Optional[str] = ..., customer_id: _Optional[str] = ..., amount: _Optional[_Union[_money_pb2.Money, _Mapping]] = ..., restocking_fee: _Optional[_Union[_money_pb2.Money, _Mapping]] = ..., method: _Optional[str] = ..., payment_id: _Optional[str] = ...) -> None: ...

class ReturnClosed(_message.Message):
    __slots__ = ("return_id", "order_id", "outcome")
    RETURN_ID_FIELD_NUMBER: _ClassVar[int]
    ORDER_ID_FIELD_NUMBER: _ClassVar[int]
    OUTCOME_FIELD_NUMBER: _ClassVar[int]
    return_id: str
    order_id: str
    outcome: str
    def __init__(self, return_id: _Optional[str] = ..., order_id: _Optional[str] = ..., outcome: _Optional[str] = ...) -> None: ...
