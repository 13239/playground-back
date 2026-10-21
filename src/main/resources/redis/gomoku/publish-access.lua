-- DB 커밋 뒤 전달한 최신 참가 상태만 반영한다. 늦은 조회가 퇴장·재입장을 되돌릴 수 없다.
-- KEYS: 권한 Hash, 순서 키 / ARGV: 변경 순서, 짧은 권한 리스(ms), 사용자 필드·입장 경계|참가 식별자 쌍
local previous = tonumber(redis.call('GET', KEYS[2]) or '-1')
local version = tonumber(ARGV[1])
if not version or version < previous then return 0 end
redis.call('SET', KEYS[2], ARGV[1], 'EX', 86400)
redis.call('DEL', KEYS[1])
redis.call('HSET', KEYS[1], 'version', ARGV[1])
for i = 3, #ARGV, 2 do redis.call('HSET', KEYS[1], ARGV[i], ARGV[i + 1]) end
redis.call('PEXPIRE', KEYS[1], ARGV[2])
return 1
