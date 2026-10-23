-- SQL 공통 잠금 안에서만 실행한다. 새 스냅샷을 한 번에 교체해 요청이 중간 상태를 읽지 않게 한다.
-- KEYS[1]: 정책 Hash / ARGV: version, enabled, message, (필드, 값)...
redis.call('DEL', KEYS[1])
redis.call('HSET', KEYS[1], 'state', 'ready', 'version', ARGV[1], 'enabled', ARGV[2], 'message', ARGV[3])
for i = 4, #ARGV, 2 do redis.call('HSET', KEYS[1], ARGV[i], ARGV[i + 1]) end
-- 알림에는 안내 문구나 사용자 정보를 담지 않는다. 구독자는 원본 정책을 다시 검증한다.
redis.call('PUBLISH', 'auth:access-policy:changed:v1', ARGV[1])
return 1
