-- Trả lại các claim PENDING quá hạn (khách giành được nhưng không đặt đơn).
-- KEYS[1..4] như claim.lua. ARGV: 1 now_ms, 2 limit. Trả số claim đã trả.
local ids = redis.call('ZRANGEBYSCORE', KEYS[4], '-inf', tonumber(ARGV[1]), 'LIMIT', 0, tonumber(ARGV[2]))
for _, id in ipairs(ids) do
  local v = redis.call('HGET', KEYS[3], id)
  if v ~= false then
    local _, qty, customer = string.match(v, '^(%u+)|(%d+)|(.*)$')
    redis.call('HDEL', KEYS[3], id)
    redis.call('HINCRBY', KEYS[2], customer, -tonumber(qty))
    redis.call('HINCRBY', KEYS[1], 'open', -1)
    redis.call('HINCRBY', KEYS[1], 'remaining', tonumber(qty))
  end
  redis.call('ZREM', KEYS[4], id)
end
return #ids
