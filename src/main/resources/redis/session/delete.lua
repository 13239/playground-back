-- Refresh Token을 확인한 뒤 세션을 원자적으로 삭제한다.

local sessionKey = KEYS[1]
local userSessionsKey = KEYS[2]

-- ARGV[1]: sessionId
-- ARGV[2]: 사용자 ID
-- ARGV[3]: Refresh Token 해시
local sessionId = ARGV[1]
local userId = ARGV[2]
local tokenHash = ARGV[3]

if redis.call('HGET', sessionKey, 'id') ~= userId then
    return 0
end

if redis.call('HGET', sessionKey, 'sessionId') ~= sessionId then
    return 0
end

-- 다른 토큰이나 이미 회전된 옛 토큰으로는 삭제할 수 없다.
if redis.call('HGET', sessionKey, 'refreshToken') ~= tokenHash then
    return 0
end

local expiresAt = redis.call('HGET', sessionKey, 'expiresAt')

if expiresAt then
    local member = sessionId .. '|' .. expiresAt
    redis.call('ZREM', userSessionsKey, member)
end

redis.call('DEL', sessionKey)

return 1