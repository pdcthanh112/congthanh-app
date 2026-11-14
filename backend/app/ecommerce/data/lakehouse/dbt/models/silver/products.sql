SELECT
    id AS product_id,
    name AS product_name,
    sku,
    slug,
    gtin,
    upper(status) AS status,
    price,
    has_variant,
    coalesce(is_featured, false) AS is_featured,
    brand AS brand_id,
    parent_id,
    supplier
FROM {{ source('ods', 'products') }}
WHERE coalesce(upper(status), '') <> 'DELETED'
