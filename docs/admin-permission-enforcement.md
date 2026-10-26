# 기능 권한의 실제 적용

권한 상세는 서버의 20개 `PermissionCode`를 표시한다. 각 항목의 이름·분야·설명·ROOT 전용 여부·선행 권한은 `/api/admin/permissions`의 검증된 사전을 사용한다. 기능 검색·변경 사유 입력은 제공하지 않으며 역할 저장에서도 사유를 전송하지 않는다. 기존 API의 선택 사유 필드는 이전 클라이언트와 호환한다.

| 기능 코드 | 실제 관리 API의 대표 경로 |
| --- | --- |
| ADMIN_ACCESS | GET `/api/admin/me`, 모든 관리자 경로의 진입 검사 |
| DASHBOARD_READ | GET `/api/admin/dashboard` |
| USER_READ | GET `/api/admin/users`, 회원 상세·닉네임 이력 |
| USER_STATUS_UPDATE | PUT `/api/admin/users/{id}/status` |
| USER_SESSION_REVOKE | POST `/api/admin/users/{id}/sessions/revoke` |
| USER_SENSITIVE_READ | GET `/api/admin/users/{id}/sensitive` |
| USER_ROLE_UPDATE | PUT `/api/admin/users/{id}/role` |
| ROLE_READ | GET `/api/admin/roles`, 역할 상세·기능 사전 |
| ROLE_WRITE | POST `/api/admin/roles`, DELETE `/api/admin/roles/{code}` |
| ROLE_PERMISSION_UPDATE | PUT `/api/admin/roles/{code}` (ROLE_WRITE도 필요) |
| ACCESS_POLICY_READ | GET `/api/admin/access-policy` |
| ACCESS_POLICY_UPDATE | PUT `/api/admin/access-policy` |
| ACCESS_EXEMPTION_UPDATE | PUT `/api/admin/access-policy/exemptions` |
| CHAT_HISTORY_READ | GET `/api/admin/chat/messages`, 저장 메시지 상세 |
| ACTIVITY_LOG_READ | GET `/api/admin/logs/activities` |
| ADMIN_AUDIT_READ | GET `/api/admin/logs/audits` |
| GAME_READ | GET `/api/admin/games`, 게임·모드·필터 조회 |
| GAME_WRITE | 게임 정보·모드·필터 CRUD와 표시 순서 저장 |
| GAME_AVAILABILITY_UPDATE | PUT `/api/admin/games/{code}/availability`, 모드 이용 상태 |
| SERVICE_STATUS_READ | GET `/api/admin/service-status` |

모든 일반 기능은 ADMIN_ACCESS가 필요하다. 회원 제한·세션 종료·개인정보 열람은 USER_READ, 운영 정책 수정은 ACCESS_POLICY_READ, 게임 정보·이용 상태 수정은 GAME_READ를 함께 사용한다. UI는 기능 선택 시 선행 권한을 추가하며 선행 권한 해제 시 연결된 기능도 해제한다. 서버도 저장 시 같은 사전으로 선행 권한을 보완해 UI를 거치지 않은 요청에서도 실행 불가능한 조합을 저장하지 않는다.

ROOT 전용 기능은 일반 역할에 직접·간접 배정할 수 없다. ROOT는 모든 기능을 사용하며 자체 역할 수정·삭제·일반 배정에 의한 승격이나 강등을 허용하지 않는다. 메뉴와 버튼의 표시 외에 `AdminAccessFilter`·`AdminPermissionInterceptor`와 서비스가 현재 DB 역할을 검사한다. 기존 로그인·이전 화면의 버튼을 유지해도 제거한 권한으로 새 요청을 실행할 수 없다.

검증은 서비스 DB와 분리한 임시 MySQL에 전체 Flyway 마이그레이션을 적용한 뒤 실제 RoleRepository·RoleManagementService·AdminAuthorizationService와 MVC 핸들러 권한 검사를 연결해 수행했다. 20개 권한별 저장/재조회/허용/제거 후 차단, ROOT 전용 배정 거절, 관리자 경로의 명시적 권한 누락 검사 등 22개 테스트를 통과했다. 프론트의 서버 사전 기반 선택·의존성·잠금·다른 분야 보존 검사 90개도 통과했다. 임시 테스트 소스·브라우저 검증 페이지·DB 컨테이너는 검증 후 제거한다.
