-- 仅清除本次已提交的版本；同步期间的新变化留到下一轮。
if redis.call('HGET', KEYS[1], ARGV[1]) == ARGV[2] then
    return redis.call('HDEL', KEYS[1], ARGV[1])
end
return 0
