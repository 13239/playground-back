-- 홈 채팅 접수 시계와 같은 Redis 시각으로 입장 이전 메시지의 공개를 막는다.
local clock = redis.call('TIME')
return tonumber(clock[1]) * 1000000 + tonumber(clock[2])
