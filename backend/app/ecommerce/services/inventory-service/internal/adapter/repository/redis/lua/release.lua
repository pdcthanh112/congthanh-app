-- Trả lại một claim (không ghi được Kafka, saga bù trừ, khách huỷ). Idempotent.
-- KEYS[1..4] như claim.lua. ARGV[1] = claim_id
-- Trả: 1 đã trả, 0 không còn claim (đã trả/hết hạn/sale đã đóng), -1 claim đã chốt (không trả được)
local v = redis.call('HGET', KEYS[3], ARGV[1])
if v == false then return 0 end
local state, qty, customer = string.match(v, '^(%u+)|(%d+)|(.*)$')
if state == 'COMMITTED' then return -1 end
redis.call('HDEL', KEYS[3], ARGV[1])
redis.call('ZREM', KEYS[4], ARGV[1])
redis.call('HINCRBY', KEYS[2], customer, -tonumber(qty))
redis.call('HINCRBY', KEYS[1], 'open', -1)
redis.call('HINCRBY', KEYS[1], 'remaining', tonumber(qty))
return 1
