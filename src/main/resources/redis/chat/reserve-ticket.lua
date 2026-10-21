-- DB 조회 전에 로그인 세션·발급 예산·대기 상한을 원자적으로 확인하고 짧은 예약을 만든다.
-- KEYS: 로그인 Hash, 사용자 세션 ZSET, 계정 차단, 증명 Hash, 세션별 대기 ZSET, 사용자별 발급 예산
-- ARGV: sid, userId, 브라우저 키 지문, 증명 해시, 예약 TTL(ms), 최대 대기, 분당 발급 수, 증명 TTL(ms)
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
-- reservation: 항목도 같은 ZSET에 넣어 구버전 발급자와도 합산 상한을 공유한다.
redis.call('ZREMRANGEBYSCORE', KEYS[5], '-inf', now)
if redis.call('ZCARD', KEYS[5]) >= tonumber(ARGV[6]) then
    local oldest = redis.call('ZRANGE', KEYS[5], 0, 0, 'WITHSCORES')
    return {'LIMIT', tostring(math.max(1, math.ceil((tonumber(oldest[2]) - now) / 1000))), '0'}
end
local used = tonumber(redis.call('GET', KEYS[6]) or '0')
if used >= tonumber(ARGV[7]) then
    return {'LIMIT', tostring(math.max(1, math.ceil(redis.call('PTTL', KEYS[6]) / 1000))), '0'}
end
-- 난수 충돌 시 기존 증명이나 다른 요청의 예약을 덮어쓰지 않는다.
local reservation = 'reservation:' .. ARGV[4]
if redis.call('EXISTS', KEYS[4]) == 1 or redis.call('ZSCORE', KEYS[5], reservation) then
    return {'COLLISION', '0', '0'}
end

local expires = math.min(now + tonumber(ARGV[5]), sessionExpiry)
redis.call('ZADD', KEYS[5], expires, reservation)
-- 10초 예약 때문에 먼저 발급된 30초 티켓의 대기 목록 수명이 짧아지지 않게 한다.
redis.call('PEXPIRE', KEYS[5], math.max(tonumber(ARGV[5]), tonumber(ARGV[8])))
local count = redis.call('INCR', KEYS[6])
if count == 1 then redis.call('PEXPIRE', KEYS[6], 60000) end
-- DB 조회·발급이 실패해도 시도 예산은 환급하지 않아 반복 조회로 제한을 우회하지 못하게 한다.
return {'OK', tostring(now), tostring(expires)}
