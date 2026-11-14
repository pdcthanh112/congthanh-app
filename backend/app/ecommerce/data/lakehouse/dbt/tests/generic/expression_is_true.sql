{# Test chung: mọi dòng phải thoả biểu thức (vd. total = subtotal - discount). #}
{% test expression_is_true(model, expression, column_name=None, where=None) %}
SELECT * FROM {{ model }}
WHERE NOT ({{ expression }}){% if where %} AND ({{ where }}){% endif %}
{% endtest %}
