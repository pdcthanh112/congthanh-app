from ecommerce.common.v1 import money_pb2 as _money_pb2
from google.protobuf.internal import containers as _containers
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from collections.abc import Iterable as _Iterable, Mapping as _Mapping
from typing import ClassVar as _ClassVar, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class Posting(_message.Message):
    __slots__ = ("account", "side", "amount")
    ACCOUNT_FIELD_NUMBER: _ClassVar[int]
    SIDE_FIELD_NUMBER: _ClassVar[int]
    AMOUNT_FIELD_NUMBER: _ClassVar[int]
    account: str
    side: str
    amount: _money_pb2.Money
    def __init__(self, account: _Optional[str] = ..., side: _Optional[str] = ..., amount: _Optional[_Union[_money_pb2.Money, _Mapping]] = ...) -> None: ...

class JournalEntryPosted(_message.Message):
    __slots__ = ("entry_id", "source_type", "source_id", "source_event_id", "description", "postings", "booking_date")
    ENTRY_ID_FIELD_NUMBER: _ClassVar[int]
    SOURCE_TYPE_FIELD_NUMBER: _ClassVar[int]
    SOURCE_ID_FIELD_NUMBER: _ClassVar[int]
    SOURCE_EVENT_ID_FIELD_NUMBER: _ClassVar[int]
    DESCRIPTION_FIELD_NUMBER: _ClassVar[int]
    POSTINGS_FIELD_NUMBER: _ClassVar[int]
    BOOKING_DATE_FIELD_NUMBER: _ClassVar[int]
    entry_id: str
    source_type: str
    source_id: str
    source_event_id: str
    description: str
    postings: _containers.RepeatedCompositeFieldContainer[Posting]
    booking_date: str
    def __init__(self, entry_id: _Optional[str] = ..., source_type: _Optional[str] = ..., source_id: _Optional[str] = ..., source_event_id: _Optional[str] = ..., description: _Optional[str] = ..., postings: _Optional[_Iterable[_Union[Posting, _Mapping]]] = ..., booking_date: _Optional[str] = ...) -> None: ...

class PostingFailed(_message.Message):
    __slots__ = ("source_event_id", "event_type", "reason")
    SOURCE_EVENT_ID_FIELD_NUMBER: _ClassVar[int]
    EVENT_TYPE_FIELD_NUMBER: _ClassVar[int]
    REASON_FIELD_NUMBER: _ClassVar[int]
    source_event_id: str
    event_type: str
    reason: str
    def __init__(self, source_event_id: _Optional[str] = ..., event_type: _Optional[str] = ..., reason: _Optional[str] = ...) -> None: ...
