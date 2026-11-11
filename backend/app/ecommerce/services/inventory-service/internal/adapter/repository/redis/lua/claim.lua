-- Giành hàng flash sale, nguyên tử trên Redis. Lượt giành là một "claim" có hạn: khách phải đặt đơn
-- trước deadline, không thì sweep.lua trả suất lại.
-- KEYS[1] = hash trạng thái sale  (remaining, limit, open, opens_at, admit_rate, admit_burst)
-- KEYS[2] = hash số đã mua theo khách
-- KEYS[3] = hash claim: claim_id -> "STATE|qty|customer"
-- KEYS[4] = zset claim PENDING theo deadline (ms)
-- KEYS[5] = zset phòng chờ: customer -> số thứ tự
-- ARGV: 1 customer_id, 2 qty, 3 claim_id, 4 deadline_ms, 5 now_ms
-- Trả: số còn lại (>= 0), -1 hết hàng, -2 vượt giới hạn mỗi khách, -3 sale chưa nạp/đã đóng,
--      -4 chưa vào phòng chờ, -5 chưa tới lượt, -6 chưa tới giờ mở
local remaining = tonumber(redis.call('HGET', KEYS[1], 'remaining'))
if remaining == nil then return -3 end
local now = tonumber(ARGV[5])
local opens = tonumber(redis.call('HGET', KEYS[1], 'opens_at') or '0')
if now < opens then return -6 end
local rate = tonumber(redis.call('HGET', KEYS[1], 'admit_rate') or '0')
if rate > 0 then
  local rank = redis.call('ZRANK', KEYS[5], ARGV[1])
  if rank == false then return -4 end
  local burst = tonumber(redis.call('HGET', KEYS[1], 'admit_burst') or '0')
  if rank >= burst + math.floor((now - opens) * rate / 1000) then return -5 end
end
local limit = tonumber(redis.call('HGET', KEYS[1], 'limit'))
local qty = tonumber(ARGV[2])
local bought = tonumber(redis.call('HGET', KEYS[2], ARGV[1]) or '0')
if bought + qty > limit then return -2 end
if remaining < qty then return -1 end
redis.call('HINCRBY', KEYS[2], ARGV[1], qty)
redis.call('HSET', KEYS[3], ARGV[3], 'PENDING|' .. qty .. '|' .. ARGV[1])
redis.call('ZADD', KEYS[4], tonumber(ARGV[4]), ARGV[3])
redis.call('HINCRBY', KEYS[1], 'open', 1)
local ttl = redis.call('PTTL', KEYS[1])
if ttl > 0 then
  redis.call('PEXPIRE', KEYS[2], ttl)
  redis.call('PEXPIRE', KEYS[3], ttl)
  redis.call('PEXPIRE', KEYS[4], ttl)
end
return redis.call('HINCRBY', KEYS[1], 'remaining', -qty)
