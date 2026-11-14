from google.protobuf.internal import containers as _containers
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from collections.abc import Iterable as _Iterable
from typing import ClassVar as _ClassVar, Optional as _Optional

DESCRIPTOR: _descriptor.FileDescriptor

class ModerationDecided(_message.Message):
    __slots__ = ("item_id", "content_type", "content_id", "author_id", "decision", "categories", "decided_by", "policy_version", "redacted_text")
    ITEM_ID_FIELD_NUMBER: _ClassVar[int]
    CONTENT_TYPE_FIELD_NUMBER: _ClassVar[int]
    CONTENT_ID_FIELD_NUMBER: _ClassVar[int]
    AUTHOR_ID_FIELD_NUMBER: _ClassVar[int]
    DECISION_FIELD_NUMBER: _ClassVar[int]
    CATEGORIES_FIELD_NUMBER: _ClassVar[int]
    DECIDED_BY_FIELD_NUMBER: _ClassVar[int]
    POLICY_VERSION_FIELD_NUMBER: _ClassVar[int]
    REDACTED_TEXT_FIELD_NUMBER: _ClassVar[int]
    item_id: str
    content_type: str
    content_id: str
    author_id: str
    decision: str
    categories: _containers.RepeatedScalarFieldContainer[str]
    decided_by: str
    policy_version: str
    redacted_text: str
    def __init__(self, item_id: _Optional[str] = ..., content_type: _Optional[str] = ..., content_id: _Optional[str] = ..., author_id: _Optional[str] = ..., decision: _Optional[str] = ..., categories: _Optional[_Iterable[str]] = ..., decided_by: _Optional[str] = ..., policy_version: _Optional[str] = ..., redacted_text: _Optional[str] = ...) -> None: ...

class UserContentStrikeIssued(_message.Message):
    __slots__ = ("author_id", "strikes", "last_item_id")
    AUTHOR_ID_FIELD_NUMBER: _ClassVar[int]
    STRIKES_FIELD_NUMBER: _ClassVar[int]
    LAST_ITEM_ID_FIELD_NUMBER: _ClassVar[int]
    author_id: str
    strikes: int
    last_item_id: str
    def __init__(self, author_id: _Optional[str] = ..., strikes: _Optional[int] = ..., last_item_id: _Optional[str] = ...) -> None: ...
