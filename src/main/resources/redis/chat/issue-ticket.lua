-- 검증된 로그인에서만 30초짜리 증명을 만들며 세션·발급 예산·대기 상한을 원자적으로 확인한다.
-- KEYS: 로그인 Hash, 사용자 세션 ZSET, 계정 차단, 증명 Hash, 세션별 대기 ZSET, 사용자별 발급 예산
-- ARGV: sid, userId, 브라우저 키 지문, 증명 해시, 닉네임, 이미지 ID, Origin, WS URL, TTL(ms), 최대 대기, 분당 발급 수
local fields = redis.call('HMGET', KEYS[1], 'sessionId', 'id', 'username', 'refreshToken',
    'keepLoggedIn', 'createdAt', 'expiresAt', 'browserKeyThumbprint')
for i = 1, 8 do
    if not fields[i] then return {'SESSION', '0', '0'} end
end
local clock = redis.call('TIME')
local now = tonumber(clock[1]) * 1000 + math.floor(tonumber(clock[2]) / 1000)
local sessionExpiry = tonumber(fields[7])
if redis.call('EXISTS', KEYS[3]) == 1 or fields[1] ~= ARGV[1] or fields[2] ~= ARGV[2]
    or fields[8] ~= ARGV[3] or not sessionExpiry or sessionExpiry <= now
    or not redis.call('ZSCORE', KEYS[2], ARGV[1] .. '|' .. fields[7]) then
    return {'SESSION', '0', '0'}
end

-- 사용이 끝난 대기 목록은 정리하되 아직 유효한 증명을 조기 제거하지 않는다.
redis.call('ZREMRANGEBYSCORE', KEYS[5], '-inf', now)
if redis.call('ZCARD', KEYS[5]) >= tonumber(ARGV[10]) then
    local oldest = redis.call('ZRANGE', KEYS[5], 0, 0, 'WITHSCORES')
    return {'LIMIT', tostring(math.max(1, math.ceil((tonumber(oldest[2]) - now) / 1000))), '0'}
end
local used = tonumber(redis.call('GET', KEYS[6]) or '0')
if used >= tonumber(ARGV[11]) then
    return {'LIMIT', tostring(math.max(1, math.ceil(redis.call('PTTL', KEYS[6]) / 1000))), '0'}
end
-- 난수 충돌 시 기존 증명을 덮어쓰지 않는다.
if redis.call('EXISTS', KEYS[4]) == 1 then return {'COLLISION', '0', '0'} end

local expires = math.min(now + tonumber(ARGV[9]), sessionExpiry)
local ttl = expires - now
-- 기존 로그인도 최초 발급 이후에는 같은 경계를 사용한다. 세션 TTL·로그인 시각은 변경하지 않는다.
-- 새 로그인은 create.lua에서 경계를 기록하므로 로그인 이전의 지연 저장 메시지도 공개되지 않는다.
local started = redis.call('HGET', KEYS[1], 'chatStartedAtMicros')
if not started then
    started = string.format('%.0f', tonumber(clock[1]) * 1000000 + tonumber(clock[2]))
    redis.call('HSET', KEYS[1], 'chatStartedAtMicros', started)
end
if not tonumber(started) or tonumber(started) <= 0 then return {'SESSION', '0', '0'} end
redis.call('HSET', KEYS[4], 'sessionId', ARGV[1], 'userId', ARGV[2],
    'keyThumbprint', ARGV[3], 'nickname', ARGV[5], 'avatarId', ARGV[6],
    'origin', ARGV[7], 'websocketUrl', ARGV[8], 'issuedAt', tostring(now), 'expiresAt', tostring(expires),
    'chatStartedAtMicros', started)
redis.call('PEXPIRE', KEYS[4], ttl)
redis.call('ZADD', KEYS[5], expires, ARGV[4])
redis.call('PEXPIRE', KEYS[5], tonumber(ARGV[9]))
local count = redis.call('INCR', KEYS[6])
if count == 1 then redis.call('PEXPIRE', KEYS[6], 60000) end
return {'OK', tostring(now), tostring(expires)}
