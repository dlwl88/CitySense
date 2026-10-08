-- KEYS: 点赞用户 ZSet、count/version Hash、待同步 Hash。
-- ARGV: userId、blogId、点赞时间戳。
local count = tonumber(redis.call('HGET', KEYS[2], 'count'))
local version = redis.call('HGET', KEYS[2], 'version')
if count == nil or version == false then
    return -1
end

local exists = redis.call('ZSCORE', KEYS[1], ARGV[1])
local delta = exists and -1 or 1
if count + delta < 0 or count + delta > 2147483647 then
    return -2
end

if exists then
    redis.call('ZREM', KEYS[1], ARGV[1])
else
    redis.call('ZADD', KEYS[1], ARGV[3], ARGV[1])
end
redis.call('HINCRBY', KEYS[2], 'count', delta)
redis.call('HINCRBY', KEYS[2], 'version', 1)
-- 再读一次字符串版本，避免 Lua 数值转换损失 64 位整数精度。
redis.call('HSET', KEYS[3], ARGV[2], redis.call('HGET', KEYS[2], 'version'))
return 1
