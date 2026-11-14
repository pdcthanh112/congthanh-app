"""Tạo kết nối Trino, dataset, chart và dashboard cho Superset (idempotent, chạy lại sẽ ghi đè cùng UUID).

Sinh một bundle YAML đúng định dạng export của Superset rồi nạp bằng `superset import-dashboards`,
nên thêm chart chỉ cần sửa danh sách CHARTS/DASHBOARDS bên dưới. Superset đọc qua catalog Trino
lakehouse_ro (principal analyst trong Polaris): chỉ thấy silver và gold, không đọc được bronze/ods.

Dashboard:
  Doanh thu   doanh thu thuần theo ngày, số đơn, AOV, top sản phẩm, phân khúc khách hàng (RFM)
  Funnel      phễu phiên xem → giỏ → checkout → mua, tỉ lệ chuyển đổi theo ngày
  Realtime    sản phẩm đang trending, sản phẩm sắp hết hàng, khách hàng có tín hiệu rủi ro (Flink)
"""

from __future__ import annotations

import subprocess
import sys
import uuid
import zipfile
from pathlib import Path

import yaml

TRINO_URI = "trino://superset@trino:8080/lakehouse_ro"
NS = uuid.UUID("6f1c2a9e-4d0b-4c7e-9a51-3b8f0d2e7c11")  # UUID cố định theo tên → import lại là cập nhật


def uid(name: str) -> str:
    return str(uuid.uuid5(NS, name))


DATABASE = {
    "database_name": "Lakehouse (Trino)",
    "sqlalchemy_uri": TRINO_URI,
    "cache_timeout": None,
    "expose_in_sqllab": True,
    "allow_run_async": False,
    "allow_ctas": False,
    "allow_cvas": False,
    "allow_dml": False,
    "allow_file_upload": False,
    "extra": {"allows_virtual_table_explore": True},
    "uuid": uid("database"),
    "version": "1.0.0",
}

FUNNEL_SQL = """
SELECT event_date, 1 AS step_order, '1. Xem sản phẩm' AS step, sessions_product_view AS sessions FROM gold.funnel_daily
UNION ALL SELECT event_date, 2, '2. Thêm vào giỏ', sessions_add_to_cart FROM gold.funnel_daily
UNION ALL SELECT event_date, 3, '3. Bắt đầu checkout', sessions_begin_checkout FROM gold.funnel_daily
UNION ALL SELECT event_date, 4, '4. Mua hàng', sessions_purchase FROM gold.funnel_daily
""".strip()

DATASETS = {
    "daily_sales": ("gold", "order_date", None),
    "product_daily_sales": ("gold", "order_date", None),
    "funnel_daily": ("gold", "event_date", None),
    "funnel_steps": ("gold", "event_date", FUNNEL_SQL),
    "customer_features": ("gold", None, None),
    "product_trending_current": ("gold", "window_end", None),
    "stock_cover_current": ("gold", "signal_at", None),
    "customer_risk_current": ("gold", "signal_at", None),
}


def metric(sql: str, label: str) -> dict:
    return {"expressionType": "SQL", "sqlExpression": sql, "label": label}


BASE = {"adhoc_filters": [], "time_range": "No filter", "row_limit": 10000}


def big_number(m: dict, fmt: str = "SMART_NUMBER") -> dict:
    return {
        "viz_type": "big_number_total",
        "metric": m,
        "y_axis_format": fmt,
        "header_font_size": 0.4,
        "subheader_font_size": 0.15,
    }


def timeseries(viz: str, x: str, metrics: list[dict], fmt: str = "SMART_NUMBER") -> dict:
    return {
        "viz_type": viz,
        "x_axis": x,
        "time_grain_sqla": "P1D",
        "metrics": metrics,
        "groupby": [],
        "order_desc": True,
        "show_legend": True,
        "rich_tooltip": True,
        "y_axis_format": fmt,
        "truncate_metric": True,
        "x_axis_time_format": "%d/%m",
    }


def table_raw(columns: list[str], order_by: str, limit: int = 20) -> dict:
    return {
        "viz_type": "table",
        "query_mode": "raw",
        "all_columns": columns,
        "order_by_cols": [f'["{order_by}", false]'],
        "row_limit": limit,
        "server_pagination": False,
    }


def table_agg(groupby: list[str], metrics: list[dict], limit: int = 10) -> dict:
    return {
        "viz_type": "table",
        "query_mode": "aggregate",
        "groupby": groupby,
        "metrics": metrics,
        "timeseries_limit_metric": metrics[0],
        "order_desc": True,
        "row_limit": limit,
    }


NET = metric("SUM(net_revenue)", "Doanh thu thuần")

CHARTS = [
    ("rev_total", "Tổng doanh thu thuần", "daily_sales", big_number(NET)),
    ("rev_orders", "Đơn đã xác nhận", "daily_sales", big_number(metric("SUM(orders_confirmed)", "Đơn xác nhận"))),
    (
        "rev_aov",
        "Giá trị đơn trung bình",
        "daily_sales",
        big_number(metric("SUM(net_revenue) / NULLIF(SUM(orders_confirmed), 0)", "AOV")),
    ),
    (
        "rev_daily",
        "Doanh thu thuần theo ngày",
        "daily_sales",
        timeseries("echarts_timeseries_bar", "order_date", [NET, metric("SUM(discount)", "Giảm giá")]),
    ),
    (
        "rev_orders_daily",
        "Đơn đặt / xác nhận / huỷ theo ngày",
        "daily_sales",
        timeseries(
            "echarts_timeseries_line",
            "order_date",
            [
                metric("SUM(orders_placed)", "Đặt"),
                metric("SUM(orders_confirmed)", "Xác nhận"),
                metric("SUM(orders_cancelled)", "Huỷ"),
            ],
        ),
    ),
    (
        "rev_top_products",
        "Top sản phẩm theo doanh thu",
        "product_daily_sales",
        table_agg(["product_name"], [metric("SUM(revenue)", "Doanh thu"), metric("SUM(units_sold)", "Số lượng")]),
    ),
    (
        "rev_segments",
        "Khách hàng theo phân khúc RFM",
        "customer_features",
        {
            "viz_type": "pie",
            "groupby": ["segment"],
            "metric": metric("COUNT(*)", "Khách hàng"),
            "show_labels": True,
            "label_type": "key_percent",
            "donut": True,
        },
    ),
    ("fun_sessions", "Số phiên", "funnel_daily", big_number(metric("SUM(sessions)", "Phiên"))),
    (
        "fun_conv",
        "Tỉ lệ chuyển đổi",
        "funnel_daily",
        big_number(metric("SUM(sessions_purchase) * 1.0 / NULLIF(SUM(sessions), 0)", "Chuyển đổi"), ".2%"),
    ),
    (
        "fun_funnel",
        "Phễu mua hàng",
        "funnel_steps",
        {
            "viz_type": "funnel",
            "groupby": ["step"],
            "metric": metric("SUM(sessions)", "Phiên"),
            "sort_by_metric": True,
            "show_labels": True,
            "label_type": "key_value_percent",
        },
    ),
    (
        "fun_rates",
        "Tỉ lệ theo ngày",
        "funnel_daily",
        timeseries(
            "echarts_timeseries_line",
            "event_date",
            [
                metric("AVG(view_to_cart_rate)", "Xem → giỏ"),
                metric("AVG(cart_to_purchase_rate)", "Giỏ → mua"),
                metric("AVG(conversion_rate)", "Chuyển đổi"),
            ],
            ".1%",
        ),
    ),
    (
        "rt_trending",
        "Sản phẩm đang trending (cửa sổ 1 giờ)",
        "product_trending_current",
        table_raw(
            ["product_id", "product_name", "score", "cart_adds", "orders", "units", "is_hot", "window_end"], "score"
        ),
    ),
    (
        "rt_stock",
        "Tồn kho: giờ còn bán được",
        "stock_cover_current",
        table_raw(
            ["product_id", "product_name", "available", "units_sold_24h", "hours_of_cover", "at_risk"], "at_risk"
        ),
    ),
    (
        "rt_risk",
        "Khách hàng có tín hiệu rủi ro",
        "customer_risk_current",
        table_raw(
            [
                "customer_id",
                "orders_1h",
                "order_value_1h",
                "payment_failures_1h",
                "cancellations_24h",
                "distinct_addresses_24h",
                "signal_at",
            ],
            "payment_failures_1h",
        ),
    ),
]

DASHBOARDS = {
    "revenue": (
        "Doanh thu",
        [
            [("rev_total", 4), ("rev_orders", 4), ("rev_aov", 4)],
            [("rev_daily", 12)],
            [("rev_orders_daily", 6), ("rev_segments", 6)],
            [("rev_top_products", 12)],
        ],
    ),
    "funnel": ("Funnel", [[("fun_sessions", 6), ("fun_conv", 6)], [("fun_funnel", 6), ("fun_rates", 6)]]),
    "realtime": ("Realtime (Flink)", [[("rt_trending", 12)], [("rt_stock", 6), ("rt_risk", 6)]]),
}


def dataset_yaml(name: str) -> dict:
    schema, dttm, sql = DATASETS[name]
    return {
        "table_name": name,
        "main_dttm_col": dttm,
        "description": None,
        "default_endpoint": None,
        "offset": 0,
        "cache_timeout": None,
        "schema": schema,
        "sql": sql,
        "params": None,
        "template_params": None,
        "filter_select_enabled": True,
        "fetch_values_predicate": None,
        "extra": None,
        "normalize_columns": False,
        "always_filter_main_dttm": False,
        "uuid": uid(f"dataset:{name}"),
        "metrics": [
            {
                "metric_name": "count",
                "verbose_name": "COUNT(*)",
                "metric_type": "count",
                "expression": "COUNT(*)",
                "description": None,
                "d3format": None,
                "extra": None,
                "warning_text": None,
            }
        ],
        "columns": [],
        "version": "1.0.0",
        "database_uuid": DATABASE["uuid"],
    }


def chart_yaml(key: str, title: str, dataset: str, form: dict) -> dict:
    params = {**BASE, **form, "datasource": f"__{dataset}__table"}
    return {
        "slice_name": title,
        "description": None,
        "certified_by": None,
        "certification_details": None,
        "viz_type": form["viz_type"],
        "params": params,
        "query_context": None,
        "cache_timeout": None,
        "uuid": uid(f"chart:{key}"),
        "version": "1.0.0",
        "dataset_uuid": uid(f"dataset:{dataset}"),
    }


def dashboard_yaml(slug: str, title: str, rows: list[list[tuple[str, int]]]) -> dict:
    titles = {k: t for k, t, _, _ in CHARTS}
    position = {
        "DASHBOARD_VERSION_KEY": "v2",
        "ROOT_ID": {"type": "ROOT", "id": "ROOT_ID", "children": ["GRID_ID"]},
        "GRID_ID": {"type": "GRID", "id": "GRID_ID", "children": [], "parents": ["ROOT_ID"]},
        "HEADER_ID": {"id": "HEADER_ID", "type": "HEADER", "meta": {"text": title}},
    }
    for i, row in enumerate(rows):
        row_id = f"ROW-{i}"
        position["GRID_ID"]["children"].append(row_id)
        position[row_id] = {
            "type": "ROW",
            "id": row_id,
            "children": [],
            "parents": ["ROOT_ID", "GRID_ID"],
            "meta": {"background": "BACKGROUND_TRANSPARENT"},
        }
        for j, (key, width) in enumerate(row):
            cid = f"CHART-{i}-{j}"
            position[row_id]["children"].append(cid)
            position[cid] = {
                "type": "CHART",
                "id": cid,
                "children": [],
                "parents": ["ROOT_ID", "GRID_ID", row_id],
                "meta": {
                    "width": width,
                    "height": 50,
                    "chartId": 0,
                    "uuid": uid(f"chart:{key}"),
                    "sliceName": titles[key],
                },
            }
    return {
        "dashboard_title": title,
        "description": None,
        "css": "",
        "slug": slug,
        "certified_by": None,
        "certification_details": None,
        "published": True,
        "uuid": uid(f"dashboard:{slug}"),
        "position": position,
        "metadata": {
            "color_scheme": "supersetColors",
            "refresh_frequency": 300,
            "expanded_slices": {},
            "label_colors": {},
            "timed_refresh_immune_slices": [],
            "cross_filters_enabled": True,
            "native_filter_configuration": [],
        },
        "version": "1.0.0",
    }


def build_bundle(path: Path) -> None:
    files = {
        "metadata.yaml": {"version": "1.0.0", "type": "Dashboard", "timestamp": "2026-11-13T00:00:00+00:00"},
        "databases/Lakehouse_Trino.yaml": DATABASE,
    }
    for name in DATASETS:
        files[f"datasets/Lakehouse_Trino/{name}.yaml"] = dataset_yaml(name)
    for key, title, dataset, form in CHARTS:
        files[f"charts/{key}.yaml"] = chart_yaml(key, title, dataset, form)
    for slug, (title, rows) in DASHBOARDS.items():
        files[f"dashboards/{slug}.yaml"] = dashboard_yaml(slug, title, rows)
    with zipfile.ZipFile(path, "w") as z:
        for name, content in files.items():
            z.writestr(f"lakehouse_bundle/{name}", yaml.safe_dump(content, allow_unicode=True, sort_keys=False))


def sync_columns() -> None:
    """Đọc cột thật của bảng/SQL ảo từ Trino vào dataset (bundle không khai báo cột)."""
    from superset.app import create_app

    app = create_app()
    with app.app_context():
        from superset.connectors.sqla.models import SqlaTable
        from superset.extensions import db

        for ds in db.session.query(SqlaTable).filter(SqlaTable.table_name.in_(list(DATASETS))):
            ds.fetch_metadata()
            print(f"  dataset {ds.schema}.{ds.table_name}: {len(ds.columns)} cột")
        db.session.commit()


def main() -> int:
    bundle = Path("/tmp/lakehouse_dashboards.zip")
    build_bundle(bundle)
    result = subprocess.run(["superset", "import-dashboards", "-p", str(bundle), "-u", "admin"], check=False)
    if result.returncode != 0:
        return result.returncode

    try:
        sync_columns()
    except Exception as exc:  
        print(f"Chưa đọc được cột từ Trino ({exc}); chạy dbt rồi chạy lại superset-init.")
    print(f"Superset: {len(DATASETS)} dataset, {len(CHARTS)} chart, {len(DASHBOARDS)} dashboard: OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
