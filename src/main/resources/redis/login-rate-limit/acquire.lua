-- 로그인 시도 횟수 확인, 증가, 최초 만료 설정을 원자적으로 처리한다.
--
-- KEYS[1]: 계정 또는 IP의 로그인 시도 카운터 Key
-- ARGV[1]: 허용할 최대 인증 시도 횟수
-- ARGV[2]: 최초 시도부터 계산할 제한 시간 (milliseconds)
--
-- 반환값:
-- 0: 이번 인증 시도 허용, 카운터 증가 완료
-- 양수: 제한 초과, 재시도까지 남은 시간 (milliseconds)

local key = KEYS[1]
local maxAttempts = tonumber(ARGV[1])
local windowMillis = tonumber(ARGV[2])

-- 잘못된 인자로 Redis 상태를 변경하지 않는다.
if not maxAttempts or maxAttempts < 1
        or maxAttempts ~= math.floor(maxAttempts)
        or not windowMillis or windowMillis < 1
        or windowMillis ~= math.floor(windowMillis) then
    return redis.error_reply('Invalid login rate limit arguments')
end

local rawCount = redis.call('GET', key)

-- 최초 요청은 카운터와 만료 시간을 함께 설정한다.
if not rawCount then
    redis.call('SET', key, '1', 'PX', ARGV[2])
    return 0
end

local count = tonumber(rawCount)

-- 손상된 카운터를 정상 상태로 간주하여 인증을 허용하지 않는다.
if not count or count < 1 or count ~= math.floor(count) then
    return redis.error_reply('Invalid login rate limit counter')
end

local remainingMillis = redis.call('PTTL', key)

-- 정상 카운터에는 반드시 만료 시간이 존재해야 한다.
if remainingMillis < 0 then
    return redis.error_reply('Missing login rate limit expiration')
end

-- 제한 초과 요청은 카운터와 만료 시간을 변경하지 않는다.
if count >= maxAttempts then
    return math.max(remainingMillis, 1)
end

-- 최초 설정한 만료 시간을 유지하면서 인증 시도 횟수만 증가시킨다.
redis.call('INCR', key)

return 0