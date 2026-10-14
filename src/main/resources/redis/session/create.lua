-- KEYS[1]: 새 로그인 세션 Hash Key
-- KEYS[2]: 사용자별 세션 목록 Sorted Set Key

local sessionKey = KEYS[1]
local userSessionsKey = KEYS[2]

-- ARGV[1]: sessionId
-- ARGV[2]: 사용자 ID
-- ARGV[3]: username
-- ARGV[4]: Refresh Token 해시
-- ARGV[5]: 로그인 유지 여부
-- ARGV[6]: createdAt (milliseconds)
-- ARGV[7]: expiresAt (milliseconds)

local sessionId = ARGV[1]
local expiresAt = tonumber(ARGV[7])
local maxSessions = 3

-- Redis 서버 시각을 기준으로 만료 여부와 로그인 순서를 판단한다.
local time = redis.call('TIME')
local now = tonumber(time[1]) * 1000
        + math.floor(tonumber(time[2]) / 1000)

if expiresAt <= now then
    return 'EXPIRED'
end

-- 동일한 sessionId를 중복 생성하지 않는다.
if redis.call('EXISTS', sessionKey) == 1 then
    return 'DUPLICATE'
end

-- 사용자별 목록에서 만료된 세션 항목 제거
local members = redis.call('ZRANGE', userSessionsKey, 0, -1)

for _, member in ipairs(members) do
    local memberExpiresAt =
            tonumber(string.match(member, '|(%d+)$'))

    if memberExpiresAt and memberExpiresAt <= now then
        redis.call('ZREM', userSessionsKey, member)
    end
end

-- 이미 세 개가 있다면 가장 오래된 세션을 목록에서 제거
local evictedSessionId = ''

if redis.call('ZCARD', userSessionsKey) >= maxSessions then
    local oldest = redis.call('ZRANGE', userSessionsKey, 0, 0)[1]

    if oldest then
        redis.call('ZREM', userSessionsKey, oldest)

        evictedSessionId = string.match(oldest, '^([^|]+)|')
    end
end

-- 신규 세션 Hash 생성
redis.call(
    'HSET',
    sessionKey,
    'sessionId', ARGV[1],
    'id', ARGV[2],
    'username', ARGV[3],
    'refreshToken', ARGV[4],
    'keepLoggedIn', ARGV[5],
    'createdAt', ARGV[6],
    'expiresAt', ARGV[7]
)

redis.call('PEXPIREAT', sessionKey, ARGV[7])

-- 로그인 시각으로 정렬하고, 세션의 만료 시각도 Member에 기록
local member = sessionId .. '|' .. ARGV[7]
redis.call('ZADD', userSessionsKey, now, member)

-- 사용자별 목록은 가장 늦게 만료되는 세션까지 유지
local indexExpiresAt = redis.call('PEXPIRETIME', userSessionsKey)

if indexExpiresAt < expiresAt then
    redis.call('PEXPIREAT', userSessionsKey, ARGV[7])
end

-- 교체된 세션 ID를 Java로 반환한다.
-- 교체가 없었다면 빈 문자열을 반환한다.
return evictedSessionId