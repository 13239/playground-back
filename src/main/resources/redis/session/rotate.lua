-- Refresh Token을 검증하고 새 해시로 원자적으로 교체한다.

local sessionKey = KEYS[1]
local userSessionsKey = KEYS[2]

-- ARGV[1]: sessionId
-- ARGV[2]: 사용자 ID
-- ARGV[3]: 기존 Refresh Token 해시
-- ARGV[4]: 새로운 Refresh Token 해시
local sessionId = ARGV[1]
local userId = ARGV[2]
local oldHash = ARGV[3]
local newHash = ARGV[4]

-- 해당 사용자의 세션인지 확인
if redis.call('HGET', sessionKey, 'id') ~= userId then
    return 0
end

if redis.call('HGET', sessionKey, 'sessionId') ~= sessionId then
    return 0
end

-- 기존 Refresh Token과 일치하는 경우에만 회전 가능
if redis.call('HGET', sessionKey, 'refreshToken') ~= oldHash then
    return 0
end

local expiresAt = tonumber(
    redis.call('HGET', sessionKey, 'expiresAt')
)

if not expiresAt then
    return 0
end

local time = redis.call('TIME')
local now = tonumber(time[1]) * 1000
        + math.floor(tonumber(time[2]) / 1000)

if expiresAt <= now then
    return 0
end

-- 동시 로그인 제한으로 제거된 세션인지 확인
local member = sessionId .. '|' .. expiresAt

if not redis.call('ZSCORE', userSessionsKey, member) then
    return 0
end

-- Refresh Token 해시만 교체한다.
-- 토큰 갱신 자체는 사용자 활동으로 취급하지 않는다.
redis.call('HSET', sessionKey, 'refreshToken', newHash)

return 1