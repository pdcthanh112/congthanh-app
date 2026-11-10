-- Đóng sale: trả {initial, remaining} và xoá trạng thái để không ai giành thêm.
-- KEYS[1] = hash trạng thái sale, KEYS[2] = hash số đã mua theo khách
local initial = redis.call('HGET', KEYS[1], 'initial')
if initial == false then return {-1, -1} end
local remaining = redis.call('HGET', KEYS[1], 'remaining')
redis.call('DEL', KEYS[1], KEYS[2])
return {tonumber(initial), tonumber(remaining)}
