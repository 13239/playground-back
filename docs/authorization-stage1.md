# 관리자 1단계: 역할과 기능 권한 기반

후속 [2단계 접속 제한](access-policy-stage2.md)이 구현되었다. 현재 구현에서는 실제 회원 역할 변경과 최고 관리자 최초 배정 시 기존 로그인 세션을 종료하며 새 로그인에 현재 역할을 결합한다. 관리자 UI/BFF와 게임 DB화는 후속 범위다.

이 단계는 MVC 백엔드의 권한 기반과 API를 구현한다. 관리자 UI/BFF, 전체 접속 제한, 게임 DB화, 로그·채팅 조회 화면은 후속 단계다. Spring Security의 역할/authority 판단은 사용하지 않고 기존 JWT·Redis·브라우저 증명 인증을 유지한다.

## 적용되는 DB 변경

Flyway V11은 다음을 추가한다. 기존 V1~V10 파일과 회원 자격 증명은 수정하지 않는다.

- tbl_role: 역할 코드·이름·설명·편집 버전. V11에서 만든 기본 역할 구분 컬럼은 [V28](role-deletion-policy.md)에서 제거한다.
- tbl_permission: 기능 권한 사전.
- tbl_role_permission: 역할별 허용 기능.
- tbl_user.role_code: 기존 및 신규 회원의 기본값 USER.
- tbl_user.role_version: 회원 역할 편집 충돌 방지.
- tbl_user.root_guard: ROOT 계정을 최대 한 명으로 제한하는 고유 생성 칼럼. V27에서 기존 `super_admin_guard`를 이전한다.
- tbl_admin_audit_log: 권한 변경과 함께 저장하는 중요 작업 기록.

DB 고유 제약은 최고 관리자 **최대 한 명**을 보장한다. 명시적인 초기 배정 후 계정 비활성화·소프트 삭제·일반 역할 변경을 서비스/엔티티에서 막아 한 명을 유지한다. 초기 설정 전에는 최고 관리자 계정이 없다.

## admin 최초 배정

사용자가 지정한 초기 계정은 기존 로그인 아이디 `admin`이다. 이 계정이 이미 존재하고 활성 상태여야 한다. 초기 설정은 계정이나 비밀번호를 새로 만들지 않는다.

일반 서버 시작의 기본값은 `authorization.bootstrap.enabled=false`다. 다음 옵션을 **최초 한 번의 실행에만** 추가한다.

~~~powershell
.\gradlew.bat bootRun --args='--spring.profiles.active=local --authorization.bootstrap.enabled=true --authorization.bootstrap.username=admin'
~~~

JAR/Docker 실행은 해당 환경의 기존 설정에 다음을 일시적으로 공급할 수 있다.

~~~text
AUTHORIZATION_BOOTSTRAP_ENABLED=true
AUTHORIZATION_BOOTSTRAP_USERNAME=admin
~~~

성공하면 초기 설정 옵션/환경 변수를 제거하고 평소 설정으로 재시작한다. 같은 계정에 대한 반복 실행은 멱등이며 감사 기록도 중복 생성하지 않는다. 다른 최고 관리자가 이미 존재하거나 지정 계정이 없거나 비활성이면 시작을 실패 처리한다. 정상 시작과 회원가입은 최고 권한을 자동 배정하지 않는다.

1단계 개발 검증은 별도의 임시 MySQL에서 수행했다. 이후 2026-10-24에 사용자가 지정한 실제 local DB의 `admin` 계정에 위 초기 배정을 실행했고, SUPER_ADMIN 1명과 감사 기록 1건을 확인했다. [실제 배정 기록](super-admin-initialization-2026-10-24.md)을 참조한다.

## 기본 역할과 확장

- ROOT: 모든 구현된 관리 기능을 사용할 수 있다. 기능 매핑 편집 없이 전체 기능 권한을 얻는다. V27에서 기존 `SUPER_ADMIN` 코드·회원 배정을 이전하며 [이름·순서·적용 절차](root-role-migration.md)를 따른다.
- ADMIN: 일반 운영 기능을 허용하되 최고 관리자 전용 기능과 개인정보 상세 열람은 기본 허용하지 않는다.
- MANAGER: 관리자 진입, 사용자 조회, 채팅 내역 조회.
- USER: 관리자 기능 없음.
- 추가 역할은 문자열 코드 데이터로 생성한다. 역할을 추가할 때 Java enum을 변경하지 않는다.
- ROOT 코드로 역할 삭제·편집을 막고 유일 계정의 강등/삭제/비활성화도 허용하지 않는다. 다른 역할은 최초 제공 여부와 관계없이 미사용이면 삭제할 수 있다.
- 회원이 참조하는 역할은 삭제하지 않는다. 탈퇴 회원의 FK도 포함한다. 접속 허용 설정과 신규 가입 기본값인 USER도 사용 중으로 판단하며 [사용처별 삭제 정책](role-deletion-policy.md)을 따른다.

역할/권한 정책과 회원 역할 배정은 현재 ROOT 전용이다. 최고 관리자 전용 기능은 일반 역할에 배정할 수 없으며 DB 매핑이 잘못 추가되어도 권한 검사에서 제외한다.

## 보호 흐름

1. 기존 BFF 인증 → JWT·Redis 세션 → 브라우저 증명.
2. AdminAccessFilter가 /api/admin 및 하위 경로의 현재 DB 관리자 접근을 확인.
3. 핸들러의 RequireAdminPermission 선언과 필요한 기능 확인. 선언이 없으면 최고 관리자도 거부.
4. 서비스에서 현재 권한을 다시 확인. 변경 작업은 보호 역할 행과 행위자 행의 잠금을 확보한 뒤 검사.
5. 업무 변경과 감사 기록을 같은 MySQL 트랜잭션으로 커밋.

권한 정보는 한 요청에서만 재사용한다. 권한 회수는 다음 요청부터 반영되며 JWT 만료나 재로그인을 기다리지 않는다. 관리자 응답은 no-store다. 중요 변경은 같은 정책 잠금으로 직렬화하고 역할/회원 역할 버전으로 오래된 편집을 거절한다.

권한 조회에는 회원 ID·역할·버전 프로젝션을 사용한다. 비밀번호·연락처·이메일을 권한 판단 목적으로 읽거나 반환하지 않는다. 감사 기록에는 명시적으로 만든 변경 스냅샷만 저장한다.

## API 계약

모든 경로는 기존 서버 인증과 브라우저 증명 체계를 통과해야 한다. 다음 단계에서 Next.js BFF와 관리자 화면을 연결한다. 클라이언트가 보내는 행위자 ID/역할은 사용하지 않는다.

| 메서드 | 경로 | 기능 |
|---|---|---|
| GET | /api/admin/me | 현재 관리자 역할·유효 기능·버전 |
| GET | /api/admin/permissions | 구현된 기능 사전 |
| GET | /api/admin/roles?query=&page=1&size=10 | 검색 가능한 역할 그리드. 페이지 번호·전체 페이지·전체 역할 수 반환 |
| GET | /api/admin/roles/options?query=&cursor=&limit=30 | 운영 설정 선택기용 역할 코드 커서 목록. 회원 수/전체 count 없음 |
| GET | /api/admin/roles/{code} | 선택한 역할의 현재 정보·기능·버전 |
| POST | /api/admin/roles | 새 역할 생성 |
| PUT | /api/admin/roles/{code} | 이름·설명·기능을 함께 수정 |
| DELETE | /api/admin/roles/{code}?expectedVersion=0 | 사용하지 않는 추가 역할 삭제 |
| GET | /api/admin/users/{userId}/role | 대상 회원 역할과 편집 버전. 비활성 회원도 조회 가능 |
| PUT | /api/admin/users/{userId}/role | 회원 역할 변경 |

역할 코드: 대문자 영문으로 시작하는 영문 대문자·숫자·밑줄 1~64자. 생성 후 코드는 변경하지 않는다. 이름 50자, 설명 255자, 선택 사유 300자 이내다. 목록의 limit은 1~100이다. 검색의 %, _, !는 와일드카드로 해석하지 않는다.

생성 요청 예시:

~~~json
{
  "code": "SUPPORT",
  "name": "지원 담당자",
  "description": "사용자 정보를 조회하는 추가 역할",
  "permissions": ["ADMIN_ACCESS", "USER_READ"],
  "reason": "운영 업무 분리"
}
~~~

수정 요청은 조회 응답의 version을 expectedVersion으로 보내고, name·description·permissions·선택 reason을 전달한다. 회원 역할 변경은 대상 역할 조회의 version과 새 roleCode를 전달한다. 같은 회원 역할로의 재요청은 버전을 불필요하게 올리지 않는다.

~~~json
{
  "roleCode": "MANAGER",
  "expectedVersion": 0,
  "reason": "운영 담당 역할 배정"
}
~~~

주요 오류: 400(형식·위임 불가 기능), 401(미인증), 403(권한 없음), 404(대상 없음), 409(버전 충돌·보호 대상·사용 중 역할·코드 중복), 503(권한 저장소 일시 장애). 내부 예외 원문은 응답하지 않는다.

## 기능 권한 사전

후속 단계의 기능은 코드만 등록한 상태다. 존재하지 않는 업무 API를 이 목록만으로 제공하지 않는다.

| 코드 | 이름 | 분류 | 최고 관리자 전용 |
|---|---|---|---|
| ADMIN_ACCESS | 관리자 페이지 접근 | ACCESS | 아니오 |
| DASHBOARD_READ | 운영 대시보드 조회 | DASHBOARD | 아니오 |
| USER_READ | 사용자 조회 | USER | 아니오 |
| USER_STATUS_UPDATE | 계정 제한·해제 | USER | 아니오 |
| USER_SESSION_REVOKE | 사용자 세션 종료 | USER | 아니오 |
| USER_SENSITIVE_READ | 사용자 개인정보 열람 | USER | 아니오 |
| USER_ROLE_UPDATE | 회원 역할 변경 | AUTHORIZATION | 예 |
| ROLE_READ | 역할·권한 조회 | AUTHORIZATION | 예 |
| ROLE_WRITE | 역할 추가·수정·삭제 | AUTHORIZATION | 예 |
| ROLE_PERMISSION_UPDATE | 역할별 기능 권한 변경 | AUTHORIZATION | 예 |
| ACCESS_POLICY_READ | 접속 제한 설정 조회 | OPERATIONS | 아니오 |
| ACCESS_POLICY_UPDATE | 접속 제한·안내 문구 변경 | OPERATIONS | 아니오 |
| ACCESS_EXEMPTION_UPDATE | 접속 허용 역할 지정 | OPERATIONS | 예 |
| CHAT_HISTORY_READ | 채팅 내역 조회 | CHAT | 아니오 |
| ACTIVITY_LOG_READ | 사용자 활동 로그 조회 | LOG | 아니오 |
| ADMIN_AUDIT_READ | 관리자 작업 기록 조회 | LOG | 아니오 |
| GAME_READ | 게임 목록·설정 조회 | GAME | 아니오 |
| GAME_WRITE | 게임 정보·장르·모드 변경 | GAME | 아니오 |
| GAME_AVAILABILITY_UPDATE | 게임 공개·점검 설정 | GAME | 아니오 |
| SERVICE_STATUS_READ | 서비스 상태 조회 | OPERATIONS | 아니오 |

## 후속 작업

2단계는 전체 접속 제한과 허용 역할 예외를 구현한다. 비허용 기존 세션 폐기, 신규 로그인·갱신 차단, 실시간 연결 종료, 안내 문구 전달을 함께 다룬다. 이후 게임 카탈로그 DB와 관리자 UI를 단계적으로 연결한다.

