-- 검증한 증명 ID를 짧은 유효 기간에만 보관한다. 세션별 상한에 도달하면 유효한 이력을 버리지 않고 거절한다.
-- KEYS[1]: 세션별(로그인은 접속 IP별) 증명 Sorted Set
-- ARGV[1]: 증명 ID, ARGV[2]: nonce 만료(ms), ARGV[3]: 최대 항목 수
local time = redis.call('TIME')
local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
local expiresAt = tonumber(ARGV[2])
if not expiresAt or expiresAt <= now or expiresAt > now + 65000 then return -1 end
redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now)
if redis.call('ZSCORE', KEYS[1], ARGV[1]) then return 0 end
if redis.call('ZCARD', KEYS[1]) >= tonumber(ARGV[3]) then return -2 end
redis.call('ZADD', KEYS[1], expiresAt, ARGV[1])
-- 새 요청이 이전 항목보다 일찍 만료되더라도 기존 유효 항목의 TTL을 줄이지 않는다.
local latest = redis.call('ZRANGE', KEYS[1], -1, -1, 'WITHSCORES')
redis.call('PEXPIREAT', KEYS[1], latest[2])
return 1
