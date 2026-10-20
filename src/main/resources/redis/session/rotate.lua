-- Refresh Token의 해시·세대를 검증하고 원자적으로 회전·동일 결과 공유·재사용에 의한 세션 폐기를 처리한다.

local sessionKey = KEYS[1]
local userSessionsKey = KEYS[2]
-- KEYS[3]: 사용자 계정 차단 Key
local accountBlockedKey = KEYS[3]
-- KEYS[4]: 세션별 최신 갱신 결과 Hash Key (회전마다 덮어쓰며 이력을 누적하지 않음)
local resultKey = KEYS[4]

-- ARGV[1]: sessionId
-- ARGV[2]: 사용자 ID
-- ARGV[3]: 기존 Refresh Token 해시
-- ARGV[4]: 새로운 Refresh Token 해시
local sessionId = ARGV[1]
local userId = ARGV[2]
local oldHash = ARGV[3]
local newHash = ARGV[4]
-- ARGV[5]: 새 토큰 쌍의 인증 암호문, ARGV[6]: 공유 기간(ms)
local encryptedTokens = ARGV[5]
local graceMillis = tonumber(ARGV[6])
-- ARGV[7]: Java에서 갱신 전용 서명을 검증한 토큰의 세대, ARGV[8]: BFF 갱신 식별자 해시
local generation = tonumber(ARGV[7])
local requestHash = ARGV[8]

-- 세대는 Lua가 정확하게 비교할 수 있는 정수만 허용한다. 서명 검증은 반드시 Java에서 먼저 수행한다.
if not generation or generation < 0 or generation >= 9007199254740991
        or generation ~= math.floor(generation) or not graceMillis or graceMillis < 1 or graceMillis > 60000 then
    return {}
end

-- 차단된 회원은 이미 조회한 세션이 있어도 Refresh Token을 회전할 수 없다.
-- 세션 검증과 차단 검증을 동일한 Lua 실행에서 처리한다.
if redis.call('EXISTS', accountBlockedKey) == 1 then
    return {}
end

-- 해당 사용자의 세션인지 확인
if redis.call('HGET', sessionKey, 'id') ~= userId then
    return {}
end

if redis.call('HGET', sessionKey, 'sessionId') ~= sessionId then
    return {}
end

-- 기존 Refresh Token과 세대가 일치하는 경우에만 회전 가능. 이전 세대는 아래에서 동일 갱신인지 확인한다.
local currentHash = redis.call('HGET', sessionKey, 'refreshToken')
local currentGeneration = tonumber(redis.call('HGET', sessionKey, 'refreshGeneration'))
local keepLoggedIn = redis.call('HGET', sessionKey, 'keepLoggedIn')
if not currentHash or not currentGeneration or currentGeneration < 0 or currentGeneration > 9007199254740991
        or currentGeneration ~= math.floor(currentGeneration) or (keepLoggedIn ~= 'true' and keepLoggedIn ~= 'false') then
    return {}
end

local expiresAt = tonumber(
    redis.call('HGET', sessionKey, 'expiresAt')
)

if not expiresAt then
    return {}
end

local time = redis.call('TIME')
local now = tonumber(time[1]) * 1000
        + math.floor(tonumber(time[2]) / 1000)

if expiresAt <= now then
    return {}
end

-- 동시 로그인 제한으로 제거된 세션인지 확인
local member = sessionId .. '|' .. expiresAt

if not redis.call('ZSCORE', userSessionsKey, member) then
    return {}
end

-- 이전 결과도 현재 세션이 살아 있고 그 결과의 새 토큰이 아직 최신일 때만 반환한다.
-- 다음 세대까지 회전했거나 로그아웃·계정 차단·강제 종료되면 이전 결과로 복구할 수 없다.
if generation < currentGeneration then
    local cached = redis.call('HMGET', resultKey, 'generation', 'previousHash', 'requestHash', 'refreshToken', 'encryptedTokens')
    -- 직전 세대·동일 토큰·동일 갱신 식별자만 최대 60초 동안 한 번의 결과를 공유하며 TTL을 늘리지 않는다.
    if generation + 1 == currentGeneration and cached[1] == ARGV[7] and cached[2] == oldHash
            and cached[3] == requestHash and cached[4] == currentHash and cached[5] then
        return {cached[5], tostring(expiresAt), keepLoggedIn}
    end

    -- 검증된 이전 세대가 별도 요청으로 재사용되면 해당 세션 하나와 인덱스·공유 결과를 함께 폐기한다.
    -- 기존 Access Token도 이후 인증의 find.lua 검증에서 거절된다. 다른 기기의 세션은 변경하지 않는다.
    redis.call('ZREM', userSessionsKey, member)
    redis.call('DEL', sessionKey, resultKey)
    return {'REUSED'}
end

-- 미래 세대나 같은 세대의 다른 해시는 재사용으로 단정하지 않고 요청만 거절한다.
if generation ~= currentGeneration or currentHash ~= oldHash then
    return {}
end

-- 결과 저장과 회전을 같은 Lua에서 수행하여 여러 서버가 같은 결과를 받게 한다.
-- 세션 자체의 TTL·만료 시각·사용자별 목록은 변경하지 않는다.
local resultTtl = math.min(graceMillis, expiresAt - now)
local sessionTtl = redis.call('PTTL', sessionKey)
if sessionTtl == -2 or sessionTtl == 0 then
    return {}
end
if sessionTtl > 0 then
    resultTtl = math.min(resultTtl, sessionTtl)
end
redis.call('HSET', resultKey, 'generation', ARGV[7], 'previousHash', oldHash,
        'requestHash', requestHash, 'refreshToken', newHash, 'encryptedTokens', encryptedTokens)
redis.call('PEXPIRE', resultKey, resultTtl)

-- Refresh Token 해시와 세대만 교체한다. 정수 문자열을 유지하여 Lua의 지수 표기 변환을 피한다.
-- 토큰 갱신 자체는 사용자 활동으로 취급하지 않는다.
redis.call('HSET', sessionKey, 'refreshToken', newHash)
redis.call('HINCRBY', sessionKey, 'refreshGeneration', 1)

return {encryptedTokens, tostring(expiresAt), keepLoggedIn}
