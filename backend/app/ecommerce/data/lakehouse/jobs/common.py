from __future__ import annotations

import os

from pyspark.sql import SparkSession

CATALOG = os.getenv("LAKE_CATALOG", "lakehouse")
LAYERS = ("bronze", "ods", "silver", "gold")
KAFKA_BOOTSTRAP = os.getenv("KAFKA_BOOTSTRAP", "kafka:9092")
CHECKPOINT_ROOT = os.getenv("LAKE_CHECKPOINT_ROOT", "/checkpoints")
TRIGGER_SECONDS = int(os.getenv("LAKE_TRIGGER_SECONDS", "30"))
LOOKBACK_HOURS = int(os.getenv("LAKE_LOOKBACK_HOURS", "48"))

TABLE_PROPS = (
    "'format-version'='2', 'write.parquet.compression-codec'='zstd', "
    "'write.metadata.delete-after-commit.enabled'='true', 'write.metadata.previous-versions-max'='50'"
)


def spark_session(app_name: str) -> SparkSession:
    builder = SparkSession.builder
    remote = os.getenv("SPARK_REMOTE")
    builder = builder.remote(remote) if remote else builder.appName(app_name)
    return builder.getOrCreate()


def table(layer: str, name: str) -> str:
    return f"{CATALOG}.{layer}.{name}"


def ensure_namespaces(spark: SparkSession) -> None:
    for layer in LAYERS:
        spark.sql(f"CREATE NAMESPACE IF NOT EXISTS {CATALOG}.{layer}")


def table_exists(spark: SparkSession, layer: str, name: str) -> bool:
    return spark.sql(f"SHOW TABLES IN {CATALOG}.{layer} LIKE '{name}'").count() > 0
