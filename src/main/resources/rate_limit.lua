-- 固定窗口限流：原子计数 + 判断
-- KEYS[1]: 限流 key
-- ARGV[1]: 窗口内最大请求数
-- ARGV[2]: 窗口秒数
-- 返回: 1=允许, 0=拒绝
local count = redis.call('INCR', KEYS[1])
if count == 1 then
    redis.call('EXPIRE', KEYS[1], ARGV[2])
end
if count > tonumber(ARGV[1]) then
    return 0
end
return 1
