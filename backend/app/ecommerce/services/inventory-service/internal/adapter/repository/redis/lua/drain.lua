-- Đóng sale: trả {initial, remaining} và xoá trạng thái để không ai giành thêm.
-- Còn claim chưa chốt (đang checkout/chờ thanh toán) thì không đóng: trả {-2, số claim đang mở}.
-- KEYS[1..5] như claim.lua
local initial = redis.call('HGET', KEYS[1], 'initial')
if initial == false then return {-1, -1} end
local open = tonumber(redis.call('HGET', KEYS[1], 'open') or '0')
if open > 0 then return {-2, open} end
local remaining = redis.call('HGET', KEYS[1], 'remaining')
redis.call('DEL', KEYS[1], KEYS[2], KEYS[3], KEYS[4], KEYS[5])
return {tonumber(initial), tonumber(remaining)}
