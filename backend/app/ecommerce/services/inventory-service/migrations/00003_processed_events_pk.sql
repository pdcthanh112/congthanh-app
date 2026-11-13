-- +goose Up
-- Khoá chính (event_id, consumer) giống Java/TS: hai consumer group cùng ghi một bảng không chặn nhau.
ALTER TABLE processed_events DROP CONSTRAINT processed_events_pkey;
ALTER TABLE processed_events ADD PRIMARY KEY (event_id, consumer);

-- +goose Down
ALTER TABLE processed_events DROP CONSTRAINT processed_events_pkey;
ALTER TABLE processed_events ADD PRIMARY KEY (event_id);
