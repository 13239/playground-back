-- 로그인 세션/TTL을 변경하지 않고 인증된 접속 확인만 60초 리스로 기록한다.
-- KEYS: 회원 ZSET, 초기화 시각, 미저장 분별 최대값 HASH, 미저장 분 순서 ZSET, 직전 관측 시각 (같은 Cluster 슬롯).
-- ARGV: 확인된 회원 번호(조회는 빈 문자열), 반환할 미저장 항목 수(0~120).
local clock = redis.call('TIME')
local now = tonumber(clock[1]) * 1000 + math.floor(tonumber(clock[2]) / 1000)
local started = redis.call('GET', KEYS[2])
if not started then
    started = tostring(now)
    redis.call('SET', KEYS[2], started)
end
local minuteNumber = math.floor(now / 60000) * 60000
local last = tonumber(redis.call('GET', KEYS[5]) or tostring(now))
local function retainPeak(bucket, count, observedAt)
    local field = tostring(bucket)
    local previous = redis.call('HGET', KEYS[3], field)
    local peak = count
    if previous then peak = math.max(peak, tonumber(string.match(previous, '^(%d+):'))) end
    redis.call('HSET', KEYS[3], field, tostring(peak) .. ':' .. tostring(observedAt))
    redis.call('ZADD', KEYS[4], bucket, field)
end
-- 분 경계 직후 마지막 리스가 만료돼도 경계 시각의 최대값을 잃지 않게, 만료 항목 제거 전에 복원한다.
-- 한 리스보다 긴 미관측 구간은 복원/0 채우기를 하지 않는다.
if minuteNumber - math.floor(last / 60000) * 60000 == 60000
        and now - last <= 60000 and minuteNumber >= tonumber(started) + 60000 then
    retainPeak(minuteNumber, redis.call('ZCOUNT', KEYS[1], '(' .. minuteNumber, '+inf'), minuteNumber)
end
redis.call('SET', KEYS[5], tostring(now))
redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now)
if ARGV[1] ~= '' then redis.call('ZADD', KEYS[1], now + 60000, ARGV[1]) end
redis.call('PEXPIRE', KEYS[1], 120000)
local count = redis.call('ZCOUNT', KEYS[1], '(' .. now, '+inf')
-- 초기화 후 한 리스 동안은 아직 재확인되지 않은 회원을 0명으로 단정하지 않는다.
if now - tonumber(started) < 60000 then return {tostring(now), 'WARMING', ''} end
retainPeak(minuteNumber, count, now)
local result = {tostring(now), 'READY', tostring(count)}
local limit = tonumber(ARGV[2])
if limit > 0 then
    for _, bucket in ipairs(redis.call('ZRANGE', KEYS[4], 0, math.min(limit, 120) - 1)) do
        local value = redis.call('HGET', KEYS[3], bucket)
        if value then table.insert(result, bucket); table.insert(result, value) end
    end
end
return result
