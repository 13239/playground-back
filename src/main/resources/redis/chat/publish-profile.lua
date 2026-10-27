-- 순서가 뒤바뀐 커밋 후 알림이 최신 닉네임을 덮어쓰지 않도록 예약한 세대를 비교한다.
local revision = tonumber(ARGV[4])
local previous = tonumber(redis.call('HGET', KEYS[1], 'revision') or '-1')
if previous >= revision then return 0 end
local authorKey = redis.call('HGET', KEYS[1], 'authorKey') or ARGV[1]
local changed = redis.call('HGET', KEYS[1], 'nickname') ~= ARGV[2]
    or redis.call('HGET', KEYS[1], 'avatarId') ~= ARGV[3]
    or redis.call('HGET', KEYS[1], 'publicUserId') ~= ARGV[6]
redis.call('HSET', KEYS[1], 'authorKey', authorKey, 'nickname', ARGV[2], 'avatarId', ARGV[3], 'revision', ARGV[4])
-- 영속 친구 식별자는 기존 표시용 authorKey와 분리하여 이전 메시지 갱신도 유지한다.
redis.call('HSET', KEYS[1], 'publicUserId', ARGV[6])
-- 내부 계정 ID 대신 무작위 공개 작성자 키만 전파한다.
if changed then
    redis.call('PUBLISH', ARGV[5], cjson.encode({authorKey = authorKey, publicUserId = ARGV[6], nickname = ARGV[2], avatarId = ARGV[3], revision = revision}))
end
return 1
