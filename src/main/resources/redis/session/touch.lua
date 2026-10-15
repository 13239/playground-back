-- 사용자 활동 시 로그인 세션의 만료 시간을 연장한다.
-- 로그인 유지 ON: 마지막 활동 + 14일
-- 로그인 유지 OFF: 마지막 활동 + 3시간

-- KEYS[1]: 개별 세션 Hash Key
-- KEYS[2]: 사용자별 세션 목록 Sorted Set Key
-- KEYS[3]: 사용자 계정 차단 Key
local sessionKey = KEYS[1]
local userSessionsKey = KEYS[2]
local accountBlockedKey = KEYS[3]

-- ARGV[1]: sessionId
-- ARGV[2]: 사용자 ID
-- ARGV[3]: 로그인 유지 ON의 TTL (milliseconds)
-- ARGV[4]: 로그인 유지 OFF의 TTL (milliseconds)
local sessionId = ARGV[1]
local userId = ARGV[2]
local rememberTtl = tonumber(ARGV[3])
local idleTtl = tonumber(ARGV[4])

-- 계정 차단 후에는 활동 요청으로 기존 세션을 연장할 수 없다.
if redis.call('EXISTS', accountBlockedKey) == 1 then
    return 0
end

-- 세션이 없거나 다른 사용자의 세션이면 연장하지 않는다.
if redis.call('HGET', sessionKey, 'id') ~= userId then
    return 0
end

-- 저장된 로그인 유지 여부에 따라 연장 시간을 선택한다.
local keepLoggedIn = redis.call('HGET', sessionKey, 'keepLoggedIn')
local ttl

if keepLoggedIn == 'true' then
    ttl = rememberTtl
elseif keepLoggedIn == 'false' then
    ttl = idleTtl
else
    return 0
end

if not ttl or ttl <= 0 then
    return 0
end

local oldExpiresAt = tonumber(
    redis.call('HGET', sessionKey, 'expiresAt')
)

if not oldExpiresAt then
    return 0
end

-- Redis 서버의 현재 시각을 가져온다.
local time = redis.call('TIME')
local now = tonumber(time[1]) * 1000
        + math.floor(tonumber(time[2]) / 1000)

-- 이미 만료된 세션은 연장하지 않는다.
if oldExpiresAt <= now then
    return 0
end

-- 사용자별 로그인 세션 목록에도 존재하는지 확인한다.
local oldMember = sessionId .. '|' .. oldExpiresAt
local score = redis.call('ZSCORE', userSessionsKey, oldMember)

-- 로그아웃되었거나 동시 로그인 제한으로 제거된 세션은 연장하지 않는다.
if not score then
    return 0
end

-- 마지막 사용자 활동 시각을 기준으로 새로운 만료 시각을 계산한다.
local newExpiresAt = now + ttl
local newMember = sessionId .. '|' .. newExpiresAt

-- 세션 상세 정보(Hash)의 만료 시각과 TTL을 갱신한다.
redis.call('HSET', sessionKey, 'expiresAt', newExpiresAt)
redis.call('PEXPIREAT', sessionKey, newExpiresAt)

-- 사용자별 세션 목록에 기록된 만료 시각도 갱신한다.
-- 기존 Score를 유지하므로 최초 로그인 순서는 바뀌지 않는다.
redis.call('ZREM', userSessionsKey, oldMember)
redis.call('ZADD', userSessionsKey, score, newMember)

-- Sorted Set이 새로운 세션 만료 시각보다 먼저 만료되지 않도록 한다.
local indexExpiresAt = redis.call('PEXPIRETIME', userSessionsKey)

if indexExpiresAt < newExpiresAt then
    redis.call('PEXPIREAT', userSessionsKey, newExpiresAt)
end

-- 갱신된 만료 시각을 반환한다.
return newExpiresAt
