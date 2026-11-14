{# Dùng đúng tên namespace silver/gold (mặc định dbt sẽ ghép thành silver_gold). #}
{% macro generate_schema_name(custom_schema_name, node) -%}
  {{ custom_schema_name | trim if custom_schema_name else target.schema }}
{%- endmacro %}
