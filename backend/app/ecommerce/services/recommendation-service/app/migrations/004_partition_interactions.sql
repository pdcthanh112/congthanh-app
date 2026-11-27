-- interactions là clickstream gợi ý (VIEW, CART, WISHLIST, PURCHASE): bảng ghi thêm lớn nhất của service, mọi truy vấn
-- đều lọc theo khoảng thời gian `at` (trending 14 ngày, lịch sử 90 ngày, dọn > 180 ngày). Partition RANGE theo tháng
-- của `at` để truy vấn chỉ quét vài partition gần nhất và lệnh dọn chỉ đụng partition cũ.
-- PK phải chứa cột partition nên đổi thành (id, at); id vẫn lấy từ cùng sequence nên vẫn không trùng.
-- Hàng PURCHASE được giữ mãi (prune bỏ qua) nên chưa DROP partition cũ được; muốn vậy phải chuyển PURCHASE sang
-- product_orders trước.

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

ALTER TABLE interactions RENAME TO interactions_unpartitioned;
ALTER TABLE interactions_unpartitioned RENAME CONSTRAINT interactions_pkey TO interactions_unpartitioned_pkey;
ALTER INDEX interactions_subject RENAME TO interactions_unpartitioned_subject;
ALTER INDEX interactions_recent RENAME TO interactions_unpartitioned_recent;
ALTER SEQUENCE interactions_id_seq OWNED BY NONE;

CREATE TABLE interactions (
    id         BIGINT      NOT NULL DEFAULT nextval('interactions_id_seq'),
    subject    TEXT        NOT NULL,
    product_id TEXT        NOT NULL,
    kind       TEXT        NOT NULL,
    weight     REAL        NOT NULL,
    at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT interactions_pkey PRIMARY KEY (id, at),
    CONSTRAINT interactions_kind_check CHECK (kind IN ('VIEW', 'CART', 'WISHLIST', 'PURCHASE'))
) PARTITION BY RANGE (at);
ALTER SEQUENCE interactions_id_seq OWNED BY interactions.id;
CREATE INDEX interactions_subject ON interactions (subject, at DESC);
CREATE INDEX interactions_recent ON interactions (at DESC, product_id);
-- Lưới an toàn: job tạo partition ngừng chạy thì hàng vẫn ghi được; lần chạy sau chuyển chúng về partition tháng.
CREATE TABLE interactions_default PARTITION OF interactions DEFAULT;

INSERT INTO interactions (id, subject, product_id, kind, weight, at)
SELECT id, subject, product_id, kind, weight, at
  FROM interactions_unpartitioned;
DROP TABLE interactions_unpartitioned;

-- Batch rebuild của service (RecommendationService.rebuild) gọi định kỳ để luôn có sẵn partition 3 tháng tới.
CREATE FUNCTION maintain_partitions() RETURNS int
    LANGUAGE sql
    SET search_path FROM CURRENT
AS $$
    SELECT ensure_monthly_partitions('interactions', 3)
$$;

SELECT maintain_partitions();
