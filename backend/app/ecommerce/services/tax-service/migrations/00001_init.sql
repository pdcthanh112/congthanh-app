-- +goose Up
CREATE TABLE tax_classes (
    id         BIGSERIAL PRIMARY KEY,
    name       TEXT        NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Thuế suất theo vùng. region_code/postal_code trống = áp dụng cho cả cấp trên.
-- Tra cứu chọn dòng cụ thể nhất: postal_code > region_code > chỉ country_code.
CREATE TABLE tax_rates (
    id            BIGSERIAL PRIMARY KEY,
    tax_class_id  BIGINT       NOT NULL REFERENCES tax_classes (id) ON DELETE CASCADE,
    country_code  CHAR(2)      NOT NULL,
    region_code   TEXT         NOT NULL DEFAULT '',
    postal_code   TEXT         NOT NULL DEFAULT '',
    rate_percent  NUMERIC(7,3) NOT NULL CHECK (rate_percent >= 0 AND rate_percent <= 100),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (tax_class_id, country_code, region_code, postal_code)
);
CREATE INDEX tax_rates_lookup ON tax_rates (country_code, tax_class_id);

-- Mặc định cho Việt Nam: VAT 10% (hàng thông thường), 5% (thiết yếu), 0% (miễn thuế).
INSERT INTO tax_classes (name) VALUES ('Standard'), ('Reduced'), ('Exempt');
INSERT INTO tax_rates (tax_class_id, country_code, rate_percent)
SELECT id, 'VN', CASE name WHEN 'Standard' THEN 10 WHEN 'Reduced' THEN 5 ELSE 0 END FROM tax_classes;

-- +goose Down
DROP TABLE tax_rates;
DROP TABLE tax_classes;
