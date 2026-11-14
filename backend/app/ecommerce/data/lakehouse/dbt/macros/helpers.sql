{# Viết hoa chữ cái đầu mỗi từ (Trino không có initcap), giữ đúng dấu tiếng Việt. #}
{% macro initcap(expr) -%}
  regexp_replace(lower(trim({{ expr }})), '(^|\s)(\p{L})', x -> x[1] || upper(x[2]))
{%- endmacro %}

{# Chia an toàn, trả null khi mẫu bằng 0. #}
{% macro safe_ratio(num, den, digits=4) -%}
  round(CAST({{ num }} AS double) / nullif({{ den }}, 0), {{ digits }})
{%- endmacro %}

{# Lọc dòng mới cho model incremental: chỉ quét lại cửa sổ lookback_hours. #}
{% macro lookback(column) -%}
  {%- if is_incremental() -%}
  {{ column }} >= current_timestamp - INTERVAL '{{ var("lookback_hours") }}' HOUR
  {%- else -%}
  TRUE
  {%- endif -%}
{%- endmacro %}

{# Money (units + nanos) trong JSON payload protobuf → decimal. #}
{% macro money(json, path) -%}
  CAST(
    CAST(coalesce(try_cast(json_extract_scalar({{ json }}, '{{ path }}.units') AS bigint), 0) AS decimal(28,9))
    + CAST(coalesce(try_cast(json_extract_scalar({{ json }}, '{{ path }}.nanos') AS bigint), 0) AS decimal(28,9))
      * DECIMAL '0.000000001'
  AS decimal(19,4))
{%- endmacro %}
