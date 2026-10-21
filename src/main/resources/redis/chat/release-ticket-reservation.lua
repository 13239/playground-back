-- KEYS[1]: 세션별 대기 ZSET / ARGV[1]: 이 요청에서 서버가 생성한 증명 해시
-- 실제 티켓 해시와 구분한 예약만 제거하며 발급 시도 예산은 환급하지 않는다.
local removed = redis.call('ZREM', KEYS[1], 'reservation:' .. ARGV[1])
if redis.call('ZCARD', KEYS[1]) == 0 then redis.call('DEL', KEYS[1]) end
return removed
