-- +goose Up
-- movements là nhật ký biến động tồn kho chỉ ghi thêm (mỗi lần giữ, nhả, trừ, chỉnh), tăng theo lượng đơn và chỉ
-- đọc theo (product_id, created_at). Partition RANGE theo tháng của created_at để index nhỏ theo tháng, vacuum từng
-- phần và sau này bỏ dữ liệu cũ bằng DETACH/DROP partition thay vì DELETE hàng loạt.
-- PK phải chứa cột partition nên đổi thành (id, created_at); id vẫn lấy từ cùng sequence nên vẫn không trùng.

-- +goose StatementBegin
-- Tạo partition tháng (UTC) cho bảng partition RANGE một cột thời gian, từ tháng cũ nhất còn nằm trong partition
-- DEFAULT (hoặc tháng hiện tại) tới months_ahead tháng sau. Hàng đã rơi vào DEFAULT thuộc khoảng của partition mới
-- được chuyển sang trước khi ATTACH. Idempotent, an toàn khi nhiều replica gọi cùng lúc; trả số partition đã tạo.
CREATE FUNCTION ensure_monthly_partitions(parent regclass, months_ahead int) RETURNS int
    LANGUAGE plpgsql
    SET search_path FROM CURRENT
AS $$
DECLARE
    nsp          text;
    rel          text;
    key_column   text;
    default_part regclass;
    oldest       timestamptz;
    month_start  timestamp;
    last_month   timestamp := date_trunc('month', now() AT TIME ZONE 'UTC') + make_interval(months => months_ahead);
    lo           timestamptz;
    hi           timestamptz;
    child        text;
    created      int := 0;
BEGIN
    SELECT n.nspname, c.relname INTO STRICT nsp, rel
      FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
     WHERE c.oid = parent;
    SELECT a.attname INTO key_column
      FROM pg_partitioned_table pt
      JOIN pg_attribute a ON a.attrelid = pt.partrelid AND a.attnum = pt.partattrs[0]
     WHERE pt.partrelid = parent AND pt.partstrat = 'r' AND pt.partnatts = 1;
    IF key_column IS NULL THEN
        RAISE EXCEPTION 'table %.% is not range-partitioned by a single column', nsp, rel;
    END IF;
    -- Hai replica cùng chạy thì một bên chờ, rồi thấy partition đã có và bỏ qua.
    PERFORM pg_advisory_xact_lock(hashtext(nsp || '.' || rel || ':partitions'));

    SELECT c.oid::regclass INTO default_part
      FROM pg_inherits i JOIN pg_class c ON c.oid = i.inhrelid
     WHERE i.inhparent = parent AND pg_get_expr(c.relpartbound, c.oid) = 'DEFAULT';
    month_start := date_trunc('month', now() AT TIME ZONE 'UTC');
    IF default_part IS NOT NULL THEN
        EXECUTE format('SELECT min(%I) FROM %s', key_column, default_part) INTO oldest;
        IF oldest IS NOT NULL THEN
            month_start := least(month_start, date_trunc('month', oldest AT TIME ZONE 'UTC'));
        END IF;
    END IF;

    WHILE month_start <= last_month LOOP
        lo := month_start AT TIME ZONE 'UTC';
        hi := (month_start + interval '1 month') AT TIME ZONE 'UTC';
        child := format('%s_p%s', rel, to_char(month_start, 'YYYYMM'));
        IF to_regclass(format('%I.%I', nsp, child)) IS NULL THEN
            -- Tạo bảng rời rồi ATTACH (khoá SHARE UPDATE EXCLUSIVE trên bảng cha) thay vì PARTITION OF (ACCESS
            -- EXCLUSIVE), để không chặn đọc/ghi; index, PK, FK của bảng cha được tạo cho partition khi ATTACH.
            EXECUTE format('CREATE TABLE %I.%I (LIKE %s INCLUDING DEFAULTS INCLUDING CONSTRAINTS)', nsp, child, parent);
            IF default_part IS NOT NULL THEN
                -- SELECT * hợp lệ ở đây: bảng mới tạo bằng LIKE bảng cha nên cùng cột, cùng thứ tự với DEFAULT.
                EXECUTE format('WITH moved AS (DELETE FROM %s WHERE %I >= $1 AND %I < $2 RETURNING *) '
                                   'INSERT INTO %I.%I SELECT * FROM moved',
                               default_part, key_column, key_column, nsp, child)
                    USING lo, hi;
            END IF;
            EXECUTE format('ALTER TABLE %s ATTACH PARTITION %I.%I FOR VALUES FROM (%L) TO (%L)',
                           parent, nsp, child, lo, hi);
            created := created + 1;
        END IF;
        month_start := month_start + interval '1 month';
    END LOOP;
    RETURN created;
END
$$;
-- +goose StatementEnd

ALTER TABLE movements RENAME TO movements_unpartitioned;
ALTER TABLE movements_unpartitioned RENAME CONSTRAINT movements_pkey TO movements_unpartitioned_pkey;
ALTER TABLE movements_unpartitioned RENAME CONSTRAINT movements_stock_id_fkey TO movements_unpartitioned_stock_id_fkey;
ALTER INDEX movements_product_idx RENAME TO movements_unpartitioned_product_idx;
ALTER SEQUENCE movements_id_seq OWNED BY NONE;

CREATE TABLE movements (
    id           BIGINT      NOT NULL DEFAULT nextval('movements_id_seq'),
    stock_id     BIGINT      NOT NULL,
    product_id   TEXT        NOT NULL,
    warehouse_id TEXT        NOT NULL,
    kind         TEXT        NOT NULL,   -- RESERVE | RELEASE | COMMIT | ADJUST:<type> | SET
    change       INTEGER     NOT NULL,
    reference_id TEXT        NOT NULL DEFAULT '',
    reason       TEXT        NOT NULL DEFAULT '',
    actor        TEXT        NOT NULL DEFAULT '',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT movements_pkey PRIMARY KEY (id, created_at),
    CONSTRAINT movements_stock_id_fkey FOREIGN KEY (stock_id) REFERENCES stocks (id) ON DELETE CASCADE
) PARTITION BY RANGE (created_at);
ALTER SEQUENCE movements_id_seq OWNED BY movements.id;
CREATE INDEX movements_product_idx ON movements (product_id, created_at DESC);
-- Lưới an toàn: job tạo partition ngừng chạy thì hàng vẫn ghi được; lần chạy sau chuyển chúng về partition tháng.
CREATE TABLE movements_default PARTITION OF movements DEFAULT;

INSERT INTO movements (id, stock_id, product_id, warehouse_id, kind, change, reference_id, reason, actor, created_at)
SELECT id, stock_id, product_id, warehouse_id, kind, change, reference_id, reason, actor, created_at
  FROM movements_unpartitioned;
DROP TABLE movements_unpartitioned;

-- +goose StatementBegin
-- Janitor của service (libs/go/platform/outbox) gọi định kỳ để luôn có sẵn partition 3 tháng tới.
CREATE FUNCTION maintain_partitions() RETURNS int
    LANGUAGE sql
    SET search_path FROM CURRENT
AS $$
    SELECT ensure_monthly_partitions('movements', 3)
$$;
-- +goose StatementEnd

SELECT maintain_partitions();

-- +goose Down
DROP FUNCTION maintain_partitions();

ALTER SEQUENCE movements_id_seq OWNED BY NONE;
ALTER TABLE movements RENAME TO movements_partitioned;
ALTER TABLE movements_partitioned RENAME CONSTRAINT movements_pkey TO movements_partitioned_pkey;
ALTER TABLE movements_partitioned RENAME CONSTRAINT movements_stock_id_fkey TO movements_partitioned_stock_id_fkey;
ALTER INDEX movements_product_idx RENAME TO movements_partitioned_product_idx;

CREATE TABLE movements (
    id           BIGINT      NOT NULL DEFAULT nextval('movements_id_seq'),
    stock_id     BIGINT      NOT NULL,
    product_id   TEXT        NOT NULL,
    warehouse_id TEXT        NOT NULL,
    kind         TEXT        NOT NULL,   -- RESERVE | RELEASE | COMMIT | ADJUST:<type> | SET
    change       INTEGER     NOT NULL,
    reference_id TEXT        NOT NULL DEFAULT '',
    reason       TEXT        NOT NULL DEFAULT '',
    actor        TEXT        NOT NULL DEFAULT '',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT movements_pkey PRIMARY KEY (id),
    CONSTRAINT movements_stock_id_fkey FOREIGN KEY (stock_id) REFERENCES stocks (id) ON DELETE CASCADE
);
ALTER SEQUENCE movements_id_seq OWNED BY movements.id;
CREATE INDEX movements_product_idx ON movements (product_id, created_at DESC);

INSERT INTO movements (id, stock_id, product_id, warehouse_id, kind, change, reference_id, reason, actor, created_at)
SELECT id, stock_id, product_id, warehouse_id, kind, change, reference_id, reason, actor, created_at
  FROM movements_partitioned;
DROP TABLE movements_partitioned;
DROP FUNCTION ensure_monthly_partitions(regclass, int);
