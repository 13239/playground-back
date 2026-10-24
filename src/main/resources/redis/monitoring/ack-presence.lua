-- SQL 커밋 이후 같은 관측값만 제거한다. 읽은 뒤 더 높은 접속/새 관측이 생기면 다음 수집에 남긴다.
-- KEYS: 미저장 최대값 HASH, 미저장 분 순서 ZSET. ARGV: 분·읽은 값의 반복 쌍.
for index = 1, #ARGV, 2 do
    if redis.call('HGET', KEYS[1], ARGV[index]) == ARGV[index + 1] then
        redis.call('HDEL', KEYS[1], ARGV[index])
        redis.call('ZREM', KEYS[2], ARGV[index])
    end
end
return 1
