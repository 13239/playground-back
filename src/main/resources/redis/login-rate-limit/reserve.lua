-- IP별 실패·진행 중 요청을 함께 제한한다. 성공한 요청은 자신의 무작위 필드만 HDEL로 반환한다.
-- KEYS[1]: IP 예약 Hash, ARGV[1]: 상한, ARGV[2]: 시간 창(ms), ARGV[3]: 서버가 생성한 예약 UUID
local maximum = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
if not maximum or maximum < 1 or maximum ~= math.floor(maximum)
        or not window or window < 1 or window ~= math.floor(window) or string.len(ARGV[3]) ~= 36 then
    return redis.error_reply('Invalid login reservation arguments')
end
local count = redis.call('HLEN', KEYS[1])
if count > 0 then
    local remaining = redis.call('PTTL', KEYS[1])
    if remaining < 0 then return redis.error_reply('Missing login reservation expiration') end
    if count >= maximum then return math.max(remaining, 1) end
end
redis.call('HSET', KEYS[1], ARGV[3], '1')
-- 재요청으로 시간 창을 늘리지 않으며 마지막 예약이 반환되면 Redis가 빈 Hash를 제거한다.
if count == 0 then redis.call('PEXPIRE', KEYS[1], ARGV[2]) end
return 0
