-- Trả lại lượt đã giành (khi không ghi được Kafka).
-- KEYS[1] = hash trạng thái sale, KEYS[2] = hash số đã mua theo khách
-- ARGV[1] = customer_id, ARGV[2] = qty
if redis.call('EXISTS', KEYS[1]) == 0 then return -3 end
local qty = tonumber(ARGV[2])
local bought = tonumber(redis.call('HGET', KEYS[2], ARGV[1]) or '0')
if bought < qty then qty = bought end
if qty <= 0 then return 0 end
redis.call('HINCRBY', KEYS[2], ARGV[1], -qty)
return redis.call('HINCRBY', KEYS[1], 'remaining', qty)
