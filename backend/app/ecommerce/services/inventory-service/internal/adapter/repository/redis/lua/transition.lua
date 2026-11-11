-- Chuyển trạng thái claim theo saga đặt hàng: PENDING -> CONFIRMED (đơn đã giữ suất, không còn bị
-- sweep) -> COMMITTED (đã thanh toán, tính là đã bán). Idempotent.
-- KEYS[1..4] như claim.lua. ARGV: 1 claim_id, 2 target (CONFIRMED|COMMITTED), 3 customer_id, 4 qty
-- Trả: 1 ok, -1 không còn claim (hết hạn/đã trả), -2 sai khách, -3 sai số lượng
local v = redis.call('HGET', KEYS[3], ARGV[1])
if v == false then return -1 end
local state, qty, customer = string.match(v, '^(%u+)|(%d+)|(.*)$')
if ARGV[2] == 'CONFIRMED' then
  if customer ~= ARGV[3] then return -2 end
  if qty ~= ARGV[4] then return -3 end
  if state == 'PENDING' then
    redis.call('HSET', KEYS[3], ARGV[1], 'CONFIRMED|' .. qty .. '|' .. customer)
    redis.call('ZREM', KEYS[4], ARGV[1])
  end
  return 1
end
if state ~= 'COMMITTED' then
  redis.call('HSET', KEYS[3], ARGV[1], 'COMMITTED|' .. qty .. '|' .. customer)
  redis.call('ZREM', KEYS[4], ARGV[1])
  redis.call('HINCRBY', KEYS[1], 'open', -1)
end
return 1
