-- Phòng chờ ảo: xếp hàng theo thứ tự đến và cho vào dần theo tốc độ admit_rate (người/giây) kể từ
-- giờ mở. Số người được vào tính từ đồng hồ nên mọi pod cho cùng kết quả, không cần tiến trình nền.
-- KEYS[1] = hash trạng thái sale, KEYS[2] = zset phòng chờ
-- ARGV: 1 customer_id, 2 join (1 = xếp hàng nếu chưa có, 0 = chỉ xem), 3 now_ms
-- Trả {rank (0-based, -1 chưa xếp hàng), số người đã được vào, độ dài hàng, còn lại, opens_at, rate};
--     {-3} nếu sale chưa nạp/đã đóng
local remaining = redis.call('HGET', KEYS[1], 'remaining')
if remaining == false then return {-3} end
local now = tonumber(ARGV[3])
local opens = tonumber(redis.call('HGET', KEYS[1], 'opens_at') or '0')
local rate = tonumber(redis.call('HGET', KEYS[1], 'admit_rate') or '0')
local burst = tonumber(redis.call('HGET', KEYS[1], 'admit_burst') or '0')
local rank = redis.call('ZRANK', KEYS[2], ARGV[1])
if rank == false and ARGV[2] == '1' and rate > 0 then
  redis.call('ZADD', KEYS[2], redis.call('HINCRBY', KEYS[1], 'qseq', 1), ARGV[1])
  local ttl = redis.call('PTTL', KEYS[1])
  if ttl > 0 then redis.call('PEXPIRE', KEYS[2], ttl) end
  rank = redis.call('ZRANK', KEYS[2], ARGV[1])
end
if rank == false then rank = -1 end
local admitted = 0
if now >= opens then admitted = burst + math.floor((now - opens) * rate / 1000) end
return {rank, admitted, redis.call('ZCARD', KEYS[2]), tonumber(remaining), opens, rate}
