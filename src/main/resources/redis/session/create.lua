-- KEYS[1]: 새 로그인 세션 Hash Key
-- KEYS[2]: 사용자별 세션 목록 Sorted Set Key
-- KEYS[3]: 사용자 계정 차단 Key
-- KEYS[4]: 교체할 기존 세션 Hash Key
-- KEYS[5]: 기존 계정의 세션 목록 Sorted Set Key
-- KEYS[6]: 기존 세션·토큰 해시에 결합한 로그인 결과 Key
-- KEYS[7]: 로그인 직전 토큰 회전이 발생한 경우의 세션별 최신 갱신 결과 Hash Key

local sessionKey = KEYS[1]
local userSessionsKey = KEYS[2]
local accountBlockedKey = KEYS[3]
local previousSessionKey = KEYS[4]
local previousUserSessionsKey = KEYS[5]
local resultKey = KEYS[6]

-- ARGV[1]: sessionId
-- ARGV[2]: 사용자 ID
-- ARGV[3]: username
-- ARGV[4]: Refresh Token 해시
-- ARGV[5]: 로그인 유지 여부
-- ARGV[6]: createdAt (milliseconds)
-- ARGV[7]: expiresAt (milliseconds)
-- ARGV[8]: 개별 로그인 세션 Hash Key 접두사
-- ARGV[9]: 기존 sessionId (없으면 빈 문자열)
-- ARGV[10]: 기존 사용자 ID
-- ARGV[11]: 기존 Refresh Token 해시
-- ARGV[12]: 로그인 응답의 인증 암호문
-- ARGV[13]: 동시 로그인 결과 공유 기간 (milliseconds)
-- ARGV[14]: 기존 갱신 식별자 해시 (없으면 빈 문자열)
-- ARGV[15]: 세션별 갱신 결과 Key 접두사
-- ARGV[16]: 로그인에서 소유를 증명한 브라우저 공개키 지문. dev HTTP 예외는 공개키와 구분한 dev-http 표시

local sessionId = ARGV[1]
local expiresAt = tonumber(ARGV[7])
local maxSessions = 3
local previousSessionId = ARGV[9]
local resultMillis = tonumber(ARGV[13])

-- 잘못된 공유 기간으로 세션을 먼저 변경하지 않는다.
if not resultMillis or resultMillis < 1 or resultMillis > 60000 then
    return redis.error_reply('Invalid login result lifetime')
end

-- 차단된 계정은 새로운 로그인 세션을 생성할 수 없다.
-- 차단 여부 확인과 세션 생성을 동일한 Lua Script에서
-- 처리하여 Redis 명령 사이의 동시성 문제를 방지한다.
if redis.call('EXISTS', accountBlockedKey) == 1 then
    return 'BLOCKED'
end

-- Redis 서버 시각을 기준으로 만료 여부와 로그인 순서를 판단한다.
local time = redis.call('TIME')
local now = tonumber(time[1]) * 1000
        + math.floor(tonumber(time[2]) / 1000)

if expiresAt <= now then
    return 'EXPIRED'
end

-- 같은 이전 쿠키로 들어온 동시 요청은 비밀번호 인증 후 동일한 한 번의 로그인 결과를 공유한다.
-- 로그인 대상·유지 선택이 다르거나 새 세션이 이미 종료·갱신되었으면 새 세션을 더 만들지 않는다.
if previousSessionId ~= '' and redis.call('EXISTS', resultKey) == 1 then
    local cached = redis.call('HMGET', resultKey, 'id', 'keepLoggedIn', 'sessionId', 'refreshToken', 'encryptedResult', 'browserKeyThumbprint')
    if cached[1] ~= ARGV[2] or cached[2] ~= ARGV[5] or cached[6] ~= ARGV[16] or not cached[3] or not cached[4] or not cached[5] then
        return 'REPLACED'
    end
    local currentKey = ARGV[8] .. cached[3]
    local current = redis.call('HMGET', currentKey, 'sessionId', 'id', 'refreshToken', 'expiresAt')
    if current[1] ~= cached[3] or current[2] ~= ARGV[2] or current[3] ~= cached[4]
            or not tonumber(current[4]) or tonumber(current[4]) <= now
            or not redis.call('ZSCORE', userSessionsKey, cached[3] .. '|' .. current[4]) then
        return 'REPLACED'
    end
    return cached[5]
end

-- 동일한 sessionId를 중복 생성하지 않는다.
if redis.call('EXISTS', sessionKey) == 1 then
    return 'DUPLICATE'
end

-- 세션 ID만으로 삭제하지 않는다. 기존 토큰 해시·소유자·만료·목록을 검증한 뒤 같은 Lua에서 교체한다.
-- 다른 계정으로 로그인하더라도 이 브라우저가 증명한 이전 세션 하나만 종료한다.
if previousSessionId ~= '' then
    local previous = redis.call('HMGET', previousSessionKey, 'sessionId', 'id', 'refreshToken', 'expiresAt', 'browserKeyThumbprint')
    local matchesToken = previous[3] and previous[3] == ARGV[11]
    if previous[3] and not matchesToken and ARGV[14] ~= '' then
        -- 기존 갱신의 동일 결과이며 그 이후 다시 회전되지 않은 경우에만 이전 토큰을 인정한다.
        local refreshed = redis.call('HMGET', KEYS[7], 'previousHash', 'requestHash', 'refreshToken')
        matchesToken = refreshed[1] == ARGV[11] and refreshed[2] == ARGV[14] and refreshed[3] == previous[3]
    end
    -- 같은 브라우저 키를 증명했거나 같은 계정의 비밀번호를 다시 인증한 경우에만 이전 세션을 교체한다.
    -- 다른 계정의 비밀번호와 훔친 쿠키만으로 피해자의 세션을 삭제하지 못하게 한다.
    if previous[1] == previousSessionId and previous[2] == ARGV[10] and matchesToken
            -- dev-http는 공개키가 아니므로 같은 값이라도 기존 브라우저의 소유 증명으로 사용하지 않는다.
            and (previous[2] == ARGV[2] or previous[5] == ARGV[16] and ARGV[16] ~= 'dev-http')
            and tonumber(previous[4]) and tonumber(previous[4]) > now then
        local previousMember = previousSessionId .. '|' .. previous[4]
        if redis.call('ZSCORE', previousUserSessionsKey, previousMember) then
            redis.call('ZREM', previousUserSessionsKey, previousMember)
            redis.call('DEL', previousSessionKey, KEYS[7])
        end
    end
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

        -- Sorted Set과 기존 세션 Hash를 같은 Lua 실행에서 제거한다.
        -- 기존 Java 후속 삭제 호출에서 발생할 수 있는 경쟁 구간을 없앤다.
        if evictedSessionId then
            local evictedSessionKey = ARGV[8] .. evictedSessionId

            if redis.call('HGET', evictedSessionKey, 'id') == ARGV[2] then
                redis.call('DEL', evictedSessionKey)
                -- 제거한 세션의 짧은 갱신 결과도 함께 정리한다.
                redis.call('DEL', ARGV[15] .. evictedSessionId)
            end
        else
            return redis.error_reply('Invalid session index member')
        end
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
    -- 새 로그인은 항상 0세대부터 시작하며 이후 회전만 이 값을 증가시킨다.
    'refreshGeneration', '0',
    'browserKeyThumbprint', ARGV[16],
    'keepLoggedIn', ARGV[5],
    'createdAt', ARGV[6],
    -- 홈 채팅 공개 범위는 애플리케이션 시계와 분리한 Redis 접수 시각(마이크로초)으로 고정한다.
    'chatStartedAtMicros', string.format('%.0f', tonumber(time[1]) * 1000000 + tonumber(time[2])),
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

-- 이전 쿠키가 있는 로그인만 짧게 공유하며 토큰 원문 대신 인증 암호문과 검증용 해시를 저장한다.
if previousSessionId ~= '' then
    redis.call('HSET', resultKey, 'id', ARGV[2], 'keepLoggedIn', ARGV[5],
            'sessionId', sessionId, 'refreshToken', ARGV[4], 'encryptedResult', ARGV[12], 'browserKeyThumbprint', ARGV[16])
    redis.call('PEXPIRE', resultKey, math.min(resultMillis, expiresAt - now))
end

-- Lua에서 이미 제거한 기존 세션은 Java에서 다시 삭제하지 않는다.
-- 교체 여부와 관계없이 먼저 성공한 로그인 응답의 인증 암호문을 반환한다.
-- 차단된 계정은 위에서 BLOCKED를 반환한다.
return ARGV[12]
