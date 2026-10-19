-- Refresh Token을 검증하고 새 해시로 원자적으로 교체하며 동일 갱신 결과를 짧게 공유한다.

local sessionKey = KEYS[1]
local userSessionsKey = KEYS[2]
-- KEYS[3]: 사용자 계정 차단 Key
local accountBlockedKey = KEYS[3]
-- KEYS[4]: 기존 토큰 해시와 BFF 갱신 식별자에 결합한 암호화 결과 Key
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

-- 기존 Refresh Token과 일치하는 경우에만 회전 가능. 불일치는 아래에서 동일 갱신인지 따로 확인한다.
local currentHash = redis.call('HGET', sessionKey, 'refreshToken')
local keepLoggedIn = redis.call('HGET', sessionKey, 'keepLoggedIn')
if not currentHash or (keepLoggedIn ~= 'true' and keepLoggedIn ~= 'false') then
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
if currentHash ~= oldHash then
    local cached = redis.call('GET', resultKey)
    if cached and string.sub(cached, 1, 64) == currentHash and string.sub(cached, 65, 65) == ':' then
        return {string.sub(cached, 66), tostring(expiresAt), keepLoggedIn}
    end
    return {}
end

if not graceMillis or graceMillis < 1 or graceMillis > 60000 then
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
redis.call('SET', resultKey, newHash .. ':' .. encryptedTokens, 'PX', resultTtl)

-- Refresh Token 해시만 교체한다.
-- 토큰 갱신 자체는 사용자 활동으로 취급하지 않는다.
redis.call('HSET', sessionKey, 'refreshToken', newHash)

return {encryptedTokens, tostring(expiresAt), keepLoggedIn}