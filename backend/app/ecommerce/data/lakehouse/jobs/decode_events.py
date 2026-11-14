"""Job batch (Airflow chạy trước dbt): giải mã protobuf EventEnvelope trong bronze thành bảng silver.

- bronze.outbox_events  → silver.domain_events   event nghiệp vụ của các service (outbox → Kafka)
- bronze.stream_signals → silver.stream_signals  đầu ra Flink stream-processor (trending, tồn kho, rủi ro)

Envelope giải mã bằng from_protobuf với descriptor set build từ api/proto (buf build, nằm sẵn trong image
Spark). Trường payload kiểu google.protobuf.Any được đổi sang JSON (convert.any.fields.to.json), nên dbt
đọc tiếp bằng json_extract mà không cần biết trước mọi kiểu event. Dòng không giải mã được bị bỏ qua và
đếm vào log. MERGE theo event_id trong cửa sổ LAKE_LOOKBACK_HOURS, nên chạy lại không nhân đôi.
"""

from __future__ import annotations

import os

from common import LOOKBACK_HOURS, TABLE_PROPS, ensure_namespaces, spark_session, table
from pyspark.sql import SparkSession

DESCRIPTOR = os.getenv("LAKE_PROTO_DESCRIPTOR", "/opt/lakehouse/proto/descriptors.binpb")
ENVELOPE = "ecommerce.common.v1.EventEnvelope"

COLUMNS = """
    event_id string, event_type string, source string, aggregate_type string, aggregate_id string,
    occurred_at timestamp, event_date date, traceparent string, payload_type string, payload_json string,
    kafka_topic string, kafka_partition int, kafka_offset bigint, kafka_ts timestamp"""


def decoded_view(spark: SparkSession, source: str, view: str) -> int:
    """Tạo temp view các envelope đã giải mã, khử trùng lặp; trả về số dòng không giải mã được."""
    # Hàm SQL from_protobuf đọc descriptor trên Spark server, client (Airflow) không cần có file.
    spark.sql(f"""
        CREATE OR REPLACE TEMP VIEW {view}_raw AS
        SELECT from_protobuf(value, '{ENVELOPE}', '{DESCRIPTOR}',
                             map('mode', 'PERMISSIVE', 'convert.any.fields.to.json', 'true')) AS e,
               topic, `partition`, `offset`, kafka_ts
        FROM {source}
        WHERE kafka_ts >= current_timestamp() - INTERVAL {LOOKBACK_HOURS} HOURS""")
    bad = spark.sql(f"SELECT count(*) FROM {view}_raw WHERE e IS NULL OR e.id IS NULL OR e.id = ''").first()[0]
    spark.sql(f"""
        CREATE OR REPLACE TEMP VIEW {view} AS
        SELECT event_id, event_type, source, aggregate_type, aggregate_id, occurred_at,
               CAST(occurred_at AS date) AS event_date, traceparent,
               nullif(regexp_extract(payload_json, '"@type":"[^"]*/([^"]+)"', 1), '') AS payload_type,
               payload_json, kafka_topic, kafka_partition, kafka_offset, kafka_ts
        FROM (
            SELECT e.id AS event_id, e.type AS event_type, e.source, e.aggregate_type, e.aggregate_id,
                   coalesce(e.occurred_at, kafka_ts) AS occurred_at, nullif(e.traceparent, '') AS traceparent,
                   e.payload AS payload_json,
                   topic AS kafka_topic, `partition` AS kafka_partition, `offset` AS kafka_offset, kafka_ts,
                   row_number() OVER (PARTITION BY e.id ORDER BY kafka_ts, `offset`) AS rn
            FROM {view}_raw
            WHERE e.id IS NOT NULL AND e.id <> '')
        WHERE rn = 1""")
    return bad


def decode(spark: SparkSession, source: str, target: str, partition: str) -> None:
    spark.sql(f"""
        CREATE TABLE IF NOT EXISTS {target} ({COLUMNS})
        USING iceberg PARTITIONED BY ({partition}, days(occurred_at)) TBLPROPERTIES ({TABLE_PROPS})""")
    view = target.split(".")[-1] + "_src"
    bad = decoded_view(spark, source, view)
    spark.sql(f"""
        MERGE INTO {target} t
        USING {view} s
        ON t.event_id = s.event_id AND t.event_date = s.event_date
        WHEN NOT MATCHED THEN INSERT *""")
    total = spark.table(target).count()
    print(f"{target}: {total} dòng; {bad} bản ghi bronze không giải mã được (bỏ qua)")


def main() -> None:
    spark = spark_session("decode-events")
    ensure_namespaces(spark)
    decode(spark, table("bronze", "outbox_events"), table("silver", "domain_events"), "source")
    decode(spark, table("bronze", "stream_signals"), table("silver", "stream_signals"), "kafka_topic")


if __name__ == "__main__":
    main()
