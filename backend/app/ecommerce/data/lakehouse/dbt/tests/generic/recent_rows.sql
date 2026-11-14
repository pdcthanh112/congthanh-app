{# Bảng phải có dòng trong N giờ gần nhất (dùng cho tín hiệu stream: Flink ngừng thì báo). #}
{% test recent_rows(model, column_name, hours=24) %}
SELECT 1 FROM (SELECT max({{ column_name }}) AS latest FROM {{ model }})
WHERE latest IS NULL OR latest < current_timestamp - INTERVAL '{{ hours }}' HOUR
{% endtest %}
