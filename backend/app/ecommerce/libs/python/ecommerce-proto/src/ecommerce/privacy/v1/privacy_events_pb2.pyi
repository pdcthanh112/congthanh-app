from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from typing import ClassVar as _ClassVar, Optional as _Optional

DESCRIPTOR: _descriptor.FileDescriptor

class ConsentChanged(_message.Message):
    __slots__ = ("subject_id", "purpose", "granted", "source", "policy_version")
    SUBJECT_ID_FIELD_NUMBER: _ClassVar[int]
    PURPOSE_FIELD_NUMBER: _ClassVar[int]
    GRANTED_FIELD_NUMBER: _ClassVar[int]
    SOURCE_FIELD_NUMBER: _ClassVar[int]
    POLICY_VERSION_FIELD_NUMBER: _ClassVar[int]
    subject_id: str
    purpose: str
    granted: bool
    source: str
    policy_version: str
    def __init__(self, subject_id: _Optional[str] = ..., purpose: _Optional[str] = ..., granted: _Optional[bool] = ..., source: _Optional[str] = ..., policy_version: _Optional[str] = ...) -> None: ...

class DataSubjectRequestOpened(_message.Message):
    __slots__ = ("request_id", "subject_id", "type", "due_at")
    REQUEST_ID_FIELD_NUMBER: _ClassVar[int]
    SUBJECT_ID_FIELD_NUMBER: _ClassVar[int]
    TYPE_FIELD_NUMBER: _ClassVar[int]
    DUE_AT_FIELD_NUMBER: _ClassVar[int]
    request_id: str
    subject_id: str
    type: str
    due_at: str
    def __init__(self, request_id: _Optional[str] = ..., subject_id: _Optional[str] = ..., type: _Optional[str] = ..., due_at: _Optional[str] = ...) -> None: ...

class ErasureRequested(_message.Message):
    __slots__ = ("request_id", "subject_id", "email_sha256")
    REQUEST_ID_FIELD_NUMBER: _ClassVar[int]
    SUBJECT_ID_FIELD_NUMBER: _ClassVar[int]
    EMAIL_SHA256_FIELD_NUMBER: _ClassVar[int]
    request_id: str
    subject_id: str
    email_sha256: str
    def __init__(self, request_id: _Optional[str] = ..., subject_id: _Optional[str] = ..., email_sha256: _Optional[str] = ...) -> None: ...

class ErasureCompleted(_message.Message):
    __slots__ = ("request_id", "subject_id", "processor", "partially_retained", "retention_reason")
    REQUEST_ID_FIELD_NUMBER: _ClassVar[int]
    SUBJECT_ID_FIELD_NUMBER: _ClassVar[int]
    PROCESSOR_FIELD_NUMBER: _ClassVar[int]
    PARTIALLY_RETAINED_FIELD_NUMBER: _ClassVar[int]
    RETENTION_REASON_FIELD_NUMBER: _ClassVar[int]
    request_id: str
    subject_id: str
    processor: str
    partially_retained: bool
    retention_reason: str
    def __init__(self, request_id: _Optional[str] = ..., subject_id: _Optional[str] = ..., processor: _Optional[str] = ..., partially_retained: _Optional[bool] = ..., retention_reason: _Optional[str] = ...) -> None: ...

class AccessExportRequested(_message.Message):
    __slots__ = ("request_id", "subject_id")
    REQUEST_ID_FIELD_NUMBER: _ClassVar[int]
    SUBJECT_ID_FIELD_NUMBER: _ClassVar[int]
    request_id: str
    subject_id: str
    def __init__(self, request_id: _Optional[str] = ..., subject_id: _Optional[str] = ...) -> None: ...

class AccessExportReady(_message.Message):
    __slots__ = ("request_id", "subject_id", "processor", "object_key")
    REQUEST_ID_FIELD_NUMBER: _ClassVar[int]
    SUBJECT_ID_FIELD_NUMBER: _ClassVar[int]
    PROCESSOR_FIELD_NUMBER: _ClassVar[int]
    OBJECT_KEY_FIELD_NUMBER: _ClassVar[int]
    request_id: str
    subject_id: str
    processor: str
    object_key: str
    def __init__(self, request_id: _Optional[str] = ..., subject_id: _Optional[str] = ..., processor: _Optional[str] = ..., object_key: _Optional[str] = ...) -> None: ...

class DataSubjectRequestCompleted(_message.Message):
    __slots__ = ("request_id", "subject_id", "type", "outcome")
    REQUEST_ID_FIELD_NUMBER: _ClassVar[int]
    SUBJECT_ID_FIELD_NUMBER: _ClassVar[int]
    TYPE_FIELD_NUMBER: _ClassVar[int]
    OUTCOME_FIELD_NUMBER: _ClassVar[int]
    request_id: str
    subject_id: str
    type: str
    outcome: str
    def __init__(self, request_id: _Optional[str] = ..., subject_id: _Optional[str] = ..., type: _Optional[str] = ..., outcome: _Optional[str] = ...) -> None: ...
