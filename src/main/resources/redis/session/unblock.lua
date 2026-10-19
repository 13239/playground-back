-- DB에 커밋한 재활성화 요청의 표식이 현재 차단 표식과 일치할 때만 해제한다.
-- 비교와 삭제를 원자적으로 처리하여 이후 비활성화·삭제의 차단을 유지한다.

-- KEYS[1]: 사용자 계정 차단 Key
-- ARGV[1]: 재활성화 트랜잭션에서 저장한 차단 표식
local accountBlockedKey = KEYS[1]
local blockToken = ARGV[1]

if not blockToken or blockToken == '' then
    return redis.error_reply('Account block token is required')
end

if redis.call('GET', accountBlockedKey) ~= blockToken then
    return 0
end

return redis.call('DEL', accountBlockedKey)