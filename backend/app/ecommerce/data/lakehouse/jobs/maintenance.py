from __future__ import annotations

import os
from datetime import UTC, datetime, timedelta

from common import CATALOG, LAYERS, spark_session

RETENTION_DAYS = int(os.getenv("LAKE_SNAPSHOT_RETENTION_DAYS", "3"))


def main() -> None:
    spark = spark_session("maintenance")
    # Tham số procedure phải là hằng số (Spark không nhận biểu thức như current_timestamp() - INTERVAL).
    cutoff = (datetime.now(UTC) - timedelta(days=RETENTION_DAYS)).strftime("%Y-%m-%d %H:%M:%S")
    for layer in LAYERS:
        for row in spark.sql(f"SHOW TABLES IN {CATALOG}.{layer}").collect():
            ident = f"{layer}.{row.tableName}"
            if layer in ("bronze", "ods"):
                spark.sql(
                    f"CALL {CATALOG}.system.rewrite_data_files(table => '{ident}', "
                    "options => map('min-input-files', '5'))"
                ).show(truncate=False)
            if layer == "ods":
                spark.sql(f"CALL {CATALOG}.system.rewrite_position_delete_files(table => '{ident}')").show()
            spark.sql(
                f"CALL {CATALOG}.system.expire_snapshots(table => '{ident}', "
                f"older_than => TIMESTAMP '{cutoff}', retain_last => 20)"
            ).show(truncate=False)
            print(f"đã bảo trì {ident}")


if __name__ == "__main__":
    main()
