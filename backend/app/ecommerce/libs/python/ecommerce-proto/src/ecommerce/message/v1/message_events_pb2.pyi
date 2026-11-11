import datetime

from google.protobuf import timestamp_pb2 as _timestamp_pb2
from google.protobuf.internal import containers as _containers
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from collections.abc import Iterable as _Iterable, Mapping as _Mapping
from typing import ClassVar as _ClassVar, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class MessageSent(_message.Message):
    __slots__ = ("conversation_id", "message_id", "seq", "sender_id", "recipient_ids", "preview", "sent_at")
    CONVERSATION_ID_FIELD_NUMBER: _ClassVar[int]
    MESSAGE_ID_FIELD_NUMBER: _ClassVar[int]
    SEQ_FIELD_NUMBER: _ClassVar[int]
    SENDER_ID_FIELD_NUMBER: _ClassVar[int]
    RECIPIENT_IDS_FIELD_NUMBER: _ClassVar[int]
    PREVIEW_FIELD_NUMBER: _ClassVar[int]
    SENT_AT_FIELD_NUMBER: _ClassVar[int]
    conversation_id: str
    message_id: str
    seq: int
    sender_id: str
    recipient_ids: _containers.RepeatedScalarFieldContainer[str]
    preview: str
    sent_at: _timestamp_pb2.Timestamp
    def __init__(self, conversation_id: _Optional[str] = ..., message_id: _Optional[str] = ..., seq: _Optional[int] = ..., sender_id: _Optional[str] = ..., recipient_ids: _Optional[_Iterable[str]] = ..., preview: _Optional[str] = ..., sent_at: _Optional[_Union[datetime.datetime, _timestamp_pb2.Timestamp, _Mapping]] = ...) -> None: ...
