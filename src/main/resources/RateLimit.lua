-- 滑动窗口限流Lua脚本（独立文件版）
-- 功能：基于Redis ZSet实现滑动窗口限流，修复类型错误+空值兜底

-- 1. 参数类型强制转换 + 空值兜底（核心修复）
local currentTime = tonumber(ARGV[1]) or 0.0    -- 当前时间戳（毫秒）
local windowStartTime = tonumber(ARGV[2]) or 0.0-- 窗口起始时间戳（毫秒）
local requestId = ARGV[3]                       -- 请求唯一标识（无需转换）
local maxRequests = tonumber(ARGV[4]) or 0.0    -- 窗口最大请求数
local expireSeconds = tonumber(ARGV[5]) or 60.0 -- ZSet键过期时间（秒）

-- 2. 清理窗口外的过期请求（ZREMRANGEBYSCORE要求分值为浮点型，已兜底）
redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', windowStartTime)

-- 3. 统计当前窗口内的请求数
local count = tonumber(redis.call('ZCOUNT', KEYS[1], windowStartTime, currentTime)) or 0.0

-- 4. 限流判断：超过阈值返回1（限流），否则返回0（放行）
if count >= maxRequests then
    return 1  -- 触发限流
end

-- 5. 未限流：记录当前请求到ZSet（分值=当前时间戳，确保浮点型）
redis.call('ZADD', KEYS[1], currentTime, requestId)

-- 6. 设置ZSet过期时间，避免内存泄漏
redis.call('EXPIRE', KEYS[1], expireSeconds)

-- 7. 核心补充：必须返回0表示放行（缺少这行脚本无返回值，Java端会报错）
return 0