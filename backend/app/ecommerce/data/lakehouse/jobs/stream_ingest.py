from __future__ import annotations

from common import (
    CHECKPOINT_ROOT,
    KAFKA_BOOTSTRAP,
    TABLE_PROPS,
    TRIGGER_SECONDS,
    ensure_namespaces,
    spark_session,
    table,
)
from ods_tables import META_COLUMNS, ODS_TABLES, OdsTable
from pyspark.sql import DataFrame, SparkSession, Window
from pyspark.sql import functions as F
from pyspark.sql.types import StringType, StructField, StructType

KAFKA_META = "topic string, `partition` int, `offset` bigint, kafka_ts timestamp, key string"


def create_tables(spark: SparkSession) -> None:
    ensure_namespaces(spark)
    spark.sql(f"""
        CREATE TABLE IF NOT EXISTS {table("bronze", "outbox_events")} (
            {KAFKA_META}, value binary,
            event_id string, event_type string, traceparent string, ingested_at timestamp)
        USING iceberg PARTITIONED BY (days(kafka_ts)) TBLPROPERTIES ({TABLE_PROPS})""")
    spark.sql(f"""
        CREATE TABLE IF NOT EXISTS {table("bronze", "cdc_changes")} (
            {KAFKA_META}, value string, source_table string, ingested_at timestamp)
        USING iceberg PARTITIONED BY (source_table, days(kafka_ts)) TBLPROPERTIES ({TABLE_PROPS})""")
    spark.sql(f"""
        CREATE TABLE IF NOT EXISTS {table("bronze", "clickstream_events")} (
            {KAFKA_META}, value string, ingested_at timestamp)
        USING iceberg PARTITIONED BY (days(kafka_ts)) TBLPROPERTIES ({TABLE_PROPS})""")
    mor = "'write.merge.mode'='merge-on-read', 'write.update.mode'='merge-on-read', 'write.delete.mode'='merge-on-read'"
    for t in ODS_TABLES:
        cols = ", ".join(f"`{c}` {typ}" for c, typ in (*t.columns, *META_COLUMNS))
        spark.sql(f"""
            CREATE TABLE IF NOT EXISTS {table("ods", t.name)} ({cols})
            USING iceberg TBLPROPERTIES ({TABLE_PROPS}, {mor})""")


def read_kafka(spark: SparkSession, pattern: str) -> DataFrame:
    return (
        spark.readStream.format("kafka")
        .option("kafka.bootstrap.servers", KAFKA_BOOTSTRAP)
        .option("subscribePattern", pattern)
        .option("startingOffsets", "earliest")
        .option("failOnDataLoss", "false")
        .option("includeHeaders", "true")
        .option("maxOffsetsPerTrigger", 50_000)
        .option("kafka.metadata.max.age.ms", 30_000)
        .load()
    )


def header(name: str) -> F.Column:
    """Giá trị header Kafka dạng chuỗi, null nếu không có."""
    return F.expr(f"CAST(try_element_at(filter(headers, h -> h.key = '{name}'), 1).value AS STRING)")


def kafka_meta() -> list[F.Column]:
    return [
        F.col("topic"),
        F.col("partition"),
        F.col("offset"),
        F.col("timestamp").alias("kafka_ts"),
        F.col("key").cast("string").alias("key"),
    ]


def start_append(df: DataFrame, target: str, name: str):
    return (
        df.writeStream.format("iceberg")
        .outputMode("append")
        .trigger(processingTime=f"{TRIGGER_SECONDS} seconds")
        .option("checkpointLocation", f"{CHECKPOINT_ROOT}/{name}")
        .option("fanout-enabled", "true")
        .queryName(name)
        .toTable(target)
    )

def _string_schema(t: OdsTable) -> StructType:
    """Đọc mọi cột dạng chuỗi rồi try_cast: Debezium gửi decimal dạng chuỗi, timestamptz dạng ISO-8601."""
    names = [c for c, _ in t.columns] + ["__op", "__source_ts_ms", "__lsn", "__deleted"]
    return StructType([StructField(n, StringType()) for n in names])


def merge_ods(batch: DataFrame, t: OdsTable) -> None:
    spark = batch.sparkSession
    parsed = batch.select(F.from_json("value", _string_schema(t)).alias("r"), "partition", "offset", "kafka_ts")
    data_cols = [F.expr(f"try_cast(r.`{c}` AS {typ})").alias(c) for c, typ in t.columns]
    rows = parsed.select(
        *data_cols,
        F.col("r.__op").alias("_op"),
        F.expr("timestamp_millis(try_cast(r.__source_ts_ms AS bigint))").alias("_source_ts"),
        F.expr("try_cast(r.__lsn AS bigint)").alias("_lsn"),
        F.current_timestamp().alias("_ods_updated_at"),
        F.coalesce(F.col("r.__deleted") == "true", F.lit(False)).alias("_deleted"),
        "partition",
        "offset",
    )

    latest = Window.partitionBy(*t.keys).orderBy(
        F.col("_lsn").desc_nulls_last(), F.col("partition").desc(), F.col("offset").desc()
    )
    src = rows.withColumn("_rn", F.row_number().over(latest)).filter("_rn = 1")
    view = f"ods_src_{t.name}"
    src.createOrReplaceTempView(view)

    all_cols = [c for c, _ in (*t.columns, *META_COLUMNS)]
    on = " AND ".join(f"t.`{k}` = s.`{k}`" for k in t.keys)
    newer = "(t._lsn IS NULL OR s._lsn IS NULL OR s._lsn >= t._lsn)"
    update_set = ", ".join(f"t.`{c}` = s.`{c}`" for c in all_cols)
    insert_cols = ", ".join(f"`{c}`" for c in all_cols)
    insert_vals = ", ".join(f"s.`{c}`" for c in all_cols)
    spark.sql(f"""
        MERGE INTO {table("ods", t.name)} t
        USING {view} s
        ON {on}
        WHEN MATCHED AND s._deleted AND {newer} THEN DELETE
        WHEN MATCHED AND NOT s._deleted AND {newer} THEN UPDATE SET {update_set}
        WHEN NOT MATCHED AND NOT s._deleted THEN INSERT ({insert_cols}) VALUES ({insert_vals})
    """)


def process_cdc_batch(batch: DataFrame, batch_id: int) -> None:
    batch = batch.select(
        *kafka_meta(),
        F.col("value").cast("string").alias("value"),
        F.regexp_extract("topic", r"^ods\.(.+)$", 1).alias("source_table"),
        F.current_timestamp().alias("ingested_at"),
    ).persist()
    try:
        batch.writeTo(table("bronze", "cdc_changes")).append()
        present = {r.source_table for r in batch.select("source_table").distinct().collect()}
        for t in ODS_TABLES:
            if t.source in present:
                merge_ods(batch.filter(F.col("source_table") == t.source).filter("value IS NOT NULL"), t)
    finally:
        batch.unpersist()


def main() -> None:
    spark = spark_session("lake-stream")
    spark.sparkContext.setLogLevel("WARN")
    create_tables(spark)

    outbox = read_kafka(spark, r"ecommerce\..+\.events")
    outbox = outbox.select(
        *kafka_meta(),
        "value",
        header("ce_id").alias("event_id"),
        header("ce_type").alias("event_type"),
        header("traceparent").alias("traceparent"),
        F.current_timestamp().alias("ingested_at"),
    )
    start_append(outbox, table("bronze", "outbox_events"), "bronze_outbox_events")

    clicks = read_kafka(spark, r"clickstream\.events")
    clicks = clicks.select(
        *kafka_meta(), F.col("value").cast("string").alias("value"), F.current_timestamp().alias("ingested_at")
    )
    start_append(clicks, table("bronze", "clickstream_events"), "bronze_clickstream_events")

    (
        read_kafka(spark, r"ods\..+")
        .writeStream.foreachBatch(process_cdc_batch)
        .trigger(processingTime=f"{TRIGGER_SECONDS} seconds")
        .option("checkpointLocation", f"{CHECKPOINT_ROOT}/ods_cdc")
        .queryName("ods_cdc")
        .start()
    )

    spark.streams.awaitAnyTermination()


if __name__ == "__main__":
    main()
