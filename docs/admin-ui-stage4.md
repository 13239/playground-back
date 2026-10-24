# 관리자 UI 연결 (4단계)

Next.js의 `/admin`과 공통 헤더 설정 아이콘을 권한·접속 정책·게임 API에 연결했다. [화면/BFF 계약](../../../front/playground/docs/admin-stage4.md)을 함께 따른다.

## 이번 서버 변경

- 관리자 경로의 브라우저 서명 대상에 원본 쿼리를 포함하고 `sid`를 현재 세션과 대조한다. `htm`, 본문 해시, 키 지문, nonce와 재전송 검사는 유지한다. 기존 프로필/게임/인증 요청에 관리자 전용 claim을 요구하지 않는다.
- 역할 검색에 한글이 있으면 UTF-8 이름 컬럼만 비교한다. ASCII 검색은 코드와 이름을 검색한다. ASCII 코드 컬럼에 한글 LIKE가 전달되면서 발생한 MySQL 문자셋 충돌을 수정했다. 공백 검색과 코드 커서, 와일드카드 이스케이프는 유지한다.
- 새 DB 마이그레이션·Redis 서비스·보안 authority는 추가하지 않는다. 게임/접속 정책의 기존 버전·감사·세션 폐기 계약을 그대로 사용한다.

local/prod에서 관리자 API를 직접 호출하는 내부 클라이언트도 현재 세션의 `sid`를 브라우저 서명에 포함해야 한다. dev의 명시적 HTTP 예외는 기존 설정대로 키 서명만 생략하며 BFF/JWT/Redis/DB 권한 검증은 유지한다. 프록시가 서명된 쿼리의 순서나 인코딩을 임의로 바꾸지 않게 한다.

프론트와 서버를 함께 배포하고 재시작한다. 신규 설정값이나 schema 변경은 없다. 기존 `admin`의 최초 지정은 [1단계 초기 설정](authorization-stage1.md)을 따른다. 4단계 검증은 격리된 환경에서 수행했고, 후속 작업인 2026-10-24 [실제 초기 배정](super-admin-initialization-2026-10-24.md)에서 local DB의 `admin`에 SUPER_ADMIN을 적용했다.

12개의 실제 ES256/HKDF 서명 검증, 49개의 실제 HTTP 연동 검증과 브라우저 결과는 [검증 결과](../../../artifacts/admin-development-stage4/verification.md)를 참조한다.
