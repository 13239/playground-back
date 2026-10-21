-- 개구리 프로필을 아기 양으로 교체한다. 기존 선택값을 이전해 서버 열거형 변환 오류를 방지한다.
-- 다른 캐릭터를 선택한 회원과 나머지 계정 정보는 변경하지 않는다.
UPDATE tbl_user
SET profile_avatar = 'SHEEP'
WHERE profile_avatar = 'FROG';
