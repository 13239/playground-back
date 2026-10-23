-- 역할 변경은 정책의 SQL 쓰기 잠금에서 실행하므로 새 로그인과 경합하지 않는다. 계정 차단은 보존한다.
local count = 0
for _, member in ipairs(redis.call('ZRANGE', KEYS[1], 0, -1)) do
    local sid = string.match(member, '^([^|]+)|%d+$')
    if sid and redis.call('HGET', ARGV[2] .. sid, 'id') == ARGV[1] then
        count = count + redis.call('DEL', ARGV[2] .. sid, ARGV[3] .. sid)
    end
end
redis.call('DEL', KEYS[1])
return count
