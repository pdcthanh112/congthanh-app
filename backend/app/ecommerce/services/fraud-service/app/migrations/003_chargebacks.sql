-- Nhãn chargeback từ dispute-service (ADR 0025): khách từng thua chargeback gian lận là tín hiệu rủi ro mạnh nhất.
CREATE TABLE chargebacks (
    dispute_id  TEXT PRIMARY KEY,
    order_id    TEXT        NOT NULL,
    customer_id TEXT,
    outcome     TEXT        NOT NULL,
    reason      TEXT        NOT NULL,
    closed_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX chargebacks_customer ON chargebacks (customer_id);
