"""Generated protocol buffer code."""
from google.protobuf import descriptor as _descriptor
from google.protobuf import descriptor_pool as _descriptor_pool
from google.protobuf import runtime_version as _runtime_version
from google.protobuf import symbol_database as _symbol_database
from google.protobuf.internal import builder as _builder
_runtime_version.ValidateProtobufRuntimeVersion(
    _runtime_version.Domain.PUBLIC,
    7,
    35,
    1,
    '',
    'ecommerce/catalog/v1/catalog_events.proto'
)
# @@protoc_insertion_point(imports)

_sym_db = _symbol_database.Default()


from google.protobuf import timestamp_pb2 as google_dot_protobuf_dot_timestamp__pb2


DESCRIPTOR = _descriptor_pool.Default().AddSerializedFile(b'\n)ecommerce/catalog/v1/catalog_events.proto\x12\x14\x65\x63ommerce.catalog.v1\x1a\x1fgoogle/protobuf/timestamp.proto\"\xa4\x01\n\x10\x43\x61tegoryUpserted\x12\x13\n\x0b\x63\x61tegory_id\x18\x01 \x01(\t\x12\x0c\n\x04name\x18\x02 \x01(\t\x12\x0c\n\x04slug\x18\x03 \x01(\t\x12\x11\n\tparent_id\x18\x04 \x01(\t\x12\x0e\n\x06status\x18\x05 \x01(\t\x12\x0c\n\x04path\x18\x06 \x03(\t\x12.\n\nupdated_at\x18\x07 \x01(\x0b\x32\x1a.google.protobuf.Timestamp\"L\n\rBrandUpserted\x12\x10\n\x08\x62rand_id\x18\x01 \x01(\x03\x12\x0c\n\x04name\x18\x02 \x01(\t\x12\x0c\n\x04slug\x18\x03 \x01(\t\x12\r\n\x05image\x18\x04 \x01(\t\"9\n\x12ProductTagsChanged\x12\x12\n\nproduct_id\x18\x01 \x01(\t\x12\x0f\n\x07tag_ids\x18\x02 \x03(\x03\x42%\n!com.congthanh.catalogservice.grpcP\x01\x62\x06proto3')

_globals = globals()
_builder.BuildMessageAndEnumDescriptors(DESCRIPTOR, _globals)
_builder.BuildTopDescriptorsAndMessages(DESCRIPTOR, 'ecommerce.catalog.v1.catalog_events_pb2', _globals)
if not _descriptor._USE_C_DESCRIPTORS:
  _globals['DESCRIPTOR']._loaded_options = None
  _globals['DESCRIPTOR']._serialized_options = b'\n!com.congthanh.catalogservice.grpcP\001'
  _globals['_CATEGORYUPSERTED']._serialized_start=101
  _globals['_CATEGORYUPSERTED']._serialized_end=265
  _globals['_BRANDUPSERTED']._serialized_start=267
  _globals['_BRANDUPSERTED']._serialized_end=343
  _globals['_PRODUCTTAGSCHANGED']._serialized_start=345
  _globals['_PRODUCTTAGSCHANGED']._serialized_end=402
# @@protoc_insertion_point(module_scope)
