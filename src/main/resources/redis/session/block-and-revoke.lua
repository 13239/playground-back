-- 사용자 계정을 차단하고 해당 사용자의 모든 로그인 세션을 무효화한다.
-- 계정 차단과 세션 삭제를 Redis에서 원자적으로 처리한다.

-- KEYS[1]: 사용자별 세션 목록 Sorted Set Key
-- KEYS[2]: 사용자 계정 차단 Key
local userSessionsKey = KEYS[1]
local accountBlockedKey = KEYS[2]

-- ARGV[1]: 사용자 ID
-- ARGV[2]: 개별 로그인 세션 Hash Key 접두사
local userId = ARGV[1]
local sessionKeyPrefix = ARGV[2]

-- 사용자별 Sorted Set에 등록된 모든 로그인 세션을 조회한다.
-- Member 형식: {sessionId}|{expiresAt}
local members = redis.call('ZRANGE', userSessionsKey, 0, -1)

-- 해당 사용자 계정의 로그인 및 JWT 인증을 차단한다.
-- 차단 Key에는 TTL을 설정하지 않는다.
redis.call('SET', accountBlockedKey, '1')

local revokedCount = 0

-- 사용자별 세션 목록을 기준으로 개별 로그인 세션 Hash를 삭제한다.
for _, member in ipairs(members) do
    local sessionId = string.match(member, '^([^|]+)|%d+$')

    if sessionId then
        local sessionKey = sessionKeyPrefix .. sessionId

        -- 해당 사용자 소유의 세션만 삭제한다.
        if redis.call('HGET', sessionKey, 'id') == userId then
            revokedCount = revokedCount + redis.call('DEL', sessionKey)
        end
    end
end

-- 사용자별 로그인 세션 목록도 삭제한다.
-- 개별 Hash가 남아 있더라도 목록에 없으면
-- 기존 find.lua에서는 유효하지 않은 세션으로 처리한다.
redis.call('DEL', userSessionsKey)

-- 실제 삭제된 개별 로그인 세션 Hash 개수를 반환한다.
return revokedCount
