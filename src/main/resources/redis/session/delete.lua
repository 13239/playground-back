-- 인증된 principal의 세션 ID와 사용자 ID로 세션을 삭제한다.

-- 반환값:
--  1: 세션 삭제 완료
--  0: 이미 종료된 세션
-- -1: 세션 정보 불일치 또는 필수 정보 누락

-- KEYS[1]: 개별 세션 Hash Key
-- KEYS[2]: 사용자별 세션 목록 Sorted Set Key
local sessionKey = KEYS[1]
local userSessionsKey = KEYS[2]

-- ARGV[1]: 인증된 세션 ID
-- ARGV[2]: 인증된 사용자 ID
local sessionId = ARGV[1]
local userId = ARGV[2]

if redis.call('EXISTS', sessionKey) == 0 then
    return 0
end

local fields = redis.call(
    'HMGET',
    sessionKey,
    'sessionId',
    'id',
    'expiresAt'
)

if fields[1] ~= sessionId
        or fields[2] ~= userId
        or not fields[3] then
    return -1
end

local member = sessionId .. '|' .. fields[3]

redis.call('ZREM', userSessionsKey, member)

return redis.call('DEL', sessionKey)