-- CacheUtil 原子解锁脚本
-- KEYS[1]: 锁的 key
-- ARGV[1]: 锁的 value（用于校验持有者）
-- 返回 1 表示解锁成功，0 表示锁不属于当前持有者
if (redis.call('get', KEYS[1]) == ARGV[1]) then
    return redis.call('del', KEYS[1])
end
return 0
