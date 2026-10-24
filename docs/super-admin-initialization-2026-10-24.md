# 실제 admin 계정 최고 관리자 최초 배정

2026-10-24 (Asia/Seoul), 사용자가 지정한 로그인 아이디 `admin`에 기존 `SuperAdminBootstrapService.initialize`를 실행했다.

| 확인 항목 | 결과 |
| --- | --- |
| 환경 | local, 기존 localhost:3306/playground |
| 대상 | 기존 활성 회원 admin, 회원 ID 1 |
| 배정 전 | USER, role_version 0, SUPER_ADMIN 0명 |
| 배정 후 | SUPER_ADMIN, role_version 1, SUPER_ADMIN 정확히 1명 |
| 감사 기록 | SUPER_ADMIN_INITIALIZED 1건 |
| 이전 로그인 | 기존 로직으로 종료, Redis의 대상 회원 세션 수 0 확인 |

새 계정이나 비밀번호를 만들지 않았다. 기존 역할 행·회원 행 잠금, DB의 최고 관리자 고유 제약, 동일 트랜잭션의 감사 저장을 그대로 사용했다. 지정한 초기 배정 옵션은 이번 실행에만 적용했다. 배정용 프로세스는 DB 커밋 확인 후 종료했고 기존 애플리케이션은 계속 실행 중임을 확인했다. 별도 Redis를 만들지 않았다.

`admin`은 다시 로그인해야 한다. 현재 권한 조회가 성공하면 헤더 알림 왼쪽 설정 아이콘으로 관리자 화면에 들어갈 수 있다. 일반 시작에서는 초기 배정 플래그를 계속 켤 필요가 없다.

DB 확인 결과는 [배정 확인 JSON](../../../artifacts/admin-initialization-2026-10-24/verification.json)에 보관한다. 1~4단계의 격리된 테스트 기록과 이 실제 계정 배정은 시점을 구분한다.
