-- 세션 상세 정보와 사용자별 세션 목록을 함께 확인한다.

-- KEYS[1]: 개별 세션 Hash Key
-- KEYS[2]: 사용자별 세션 목록 Sorted Set Key
local sessionKey = KEYS[1]
local userSessionsKey = KEYS[2]

-- ARGV[1]: sessionId
-- ARGV[2]: 사용자 ID
local sessionId = ARGV[1]
local userId = ARGV[2]

-- Redis Hash에서 세션 정보를 읽는다.
local fields = redis.call(
    'HMGET',
    sessionKey,
    'sessionId',
    'id',
    'username',
    'refreshToken',
    'keepLoggedIn',
    'createdAt',
    'expiresAt'
)

-- 필수 정보가 없으면 유효하지 않은 세션이다.
for i = 1, 7 do
    if not fields[i] then
        return {}
    end
end

-- 요청한 세션 ID와 사용자 ID가 일치해야 한다.
if fields[1] ~= sessionId or fields[2] ~= userId then
    return {}
end

-- Redis 서버 시각으로 만료 여부를 확인한다.
local time = redis.call('TIME')
local now = tonumber(time[1]) * 1000
        + math.floor(tonumber(time[2]) / 1000)

local expiresAt = tonumber(fields[7])

if not expiresAt or expiresAt <= now then
    return {}
end

-- 사용자별 세션 목록에서도 현재 세션이 유효한지 확인한다.
local member = sessionId .. '|' .. fields[7]

if not redis.call('ZSCORE', userSessionsKey, member) then
    return {}
end

-- 모든 검증에 성공하면 세션 정보를 반환한다.
return fields