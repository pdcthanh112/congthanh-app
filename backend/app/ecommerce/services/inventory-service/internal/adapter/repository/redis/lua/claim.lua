-- Giành hàng flash sale, nguyên tử trên Redis.
-- KEYS[1] = hash trạng thái sale  (fields: remaining, limit)
-- KEYS[2] = hash số đã mua theo khách
-- ARGV[1] = customer_id, ARGV[2] = qty
-- Trả: số còn lại (>= 0), -1 hết hàng, -2 vượt giới hạn mỗi khách, -3 sale chưa mở/đã đóng
local remaining = tonumber(redis.call('HGET', KEYS[1], 'remaining'))
if remaining == nil then return -3 end
local limit = tonumber(redis.call('HGET', KEYS[1], 'limit'))
local qty = tonumber(ARGV[2])
local bought = tonumber(redis.call('HGET', KEYS[2], ARGV[1]) or '0')
if bought + qty > limit then return -2 end
if remaining < qty then return -1 end
redis.call('HINCRBY', KEYS[2], ARGV[1], qty)
return redis.call('HINCRBY', KEYS[1], 'remaining', -qty)
