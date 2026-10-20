-- nonce 발급도 공개 로그인 IP별 또는 검증된 세션·IP별로 제한한다. 고정 창의 TTL을 재요청으로 연장하지 않는다.
-- KEYS[1]: 범위·접속 IP 해시 Key. 원문 IP나 토큰을 저장하지 않는다. ARGV[1]: 해당 범위의 분당 상한
local maximum = tonumber(ARGV[1])
if not maximum or maximum < 1 or maximum > 10000 or maximum ~= math.floor(maximum) then
    return redis.error_reply('Invalid proof challenge limit')
end
local count = tonumber(redis.call('GET', KEYS[1]) or '0')
if count >= maximum then return 0 end
if count == 0 then redis.call('SET', KEYS[1], '1', 'PX', 60000)
else redis.call('INCR', KEYS[1]) end
return 1
