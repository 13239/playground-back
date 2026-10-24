# Playground Backend

운영 대시보드·서비스 상태 조회는 [8단계 안내](docs/admin-monitoring-stage8.md)를 따른다. V19에서 접속 최대값 집계 테이블과 회원 생성 시각 인덱스를 추가한다. 현재 접속은 사용자가 선택한 최근 60초 확인 회원 기준이며 기존 로그인 세션 수명·홈 채팅 연결 범위는 유지한다.

사용자 활동 로그·관리자 작업 기록 조회는 [6단계 안내](docs/admin-logs-stage6.md)를 따른다. `/admin/logs` 화면과 함께 반영하고 백엔드 재시작으로 검색 인덱스 Flyway V18을 적용한다.

유저 관리·권한에 따른 개인정보 조회/감사·본인 닉네임 변경 이력은 [5단계 안내](docs/user-management-stage5.md)를 따른다. 백엔드 재시작 시 Flyway V16을 적용하며 기존 이름을 과거 변경 이력으로 소급 생성하지 않는다.

관리자 UI와 BFF 연결, 관리자 검색·세션 서명 변경은 [4단계 안내](docs/admin-ui-stage4.md)를 따른다. 기존 권한·접속 제한·게임 카탈로그 API와 감사/버전 검증을 재사용한다.

관리자 3단계로 게임·장르·실행 모드 DB, 게임 관리 API, 신규 실행 정책을 추가했다. V13과 로비 DB 연결의 계약·확장 방법은 [게임 카탈로그 안내](docs/game-catalog-stage3.md)를 따른다. 숨김 게임의 직접 신규 실행·입장도 차단하며 진행 중인 판은 유지한다.

관리자 2단계로 전역 접속 제한, 예외 역할 지정, 기존 로그인·채팅·게임 스트림 종료와 사용자 안내 전달을 추가했다. 신규 Flyway V12를 사용하며, 반영·복구 및 기존 세션 재로그인 기준은 [접속 제한 안내](docs/access-policy-stage2.md)를 따른다. 실제 운영 DB 적용과 관리자 화면 연결은 아직 수행하지 않았다.

관리자 1단계로 DB 기반 역할·기능 권한, 역할 편집/회원 배정 API, 동기 감사 기록, `admin` 최고 관리자 초기 설정 절차를 추가했다. 기존 인증은 유지하고 신규 Flyway V11을 사용한다. 일반 시작은 최고 권한을 자동 배정하지 않으며 최초 설정과 API 계약은 [관리자 권한 안내](docs/authorization-stage1.md)를 따른다.

오목 실제 대국을 추가했다. 신규 Flyway V6에서 대국·착수를 별도로 저장하며 기존 SQL과 사용자 데이터를 변경하지 않는다. 두 명의 준비 완료가 첫판 제비뽑기로 이어지고, 같은 참가자의 재대국은 직전 패자가 흑이다. 30초 초과는 패스, 상대 수락 무르기, 기권·연결 종료, 서버 마감 시각과 Redis 변경 알림을 지원한다. 새 계약을 사용하는 프론트와 함께 반영한다. 자세한 동작과 접속 복구 기준은 [대국 안내](../../front/playground/docs/gomoku-game.md)에 정리했다.

대국 시간 처리는 활성 판의 다음 마감을 메모리에 예약하고 마감이 된 방만 DB에서 확인한다. 대국 유무와 관계없이 매초 수행하던 전체 후보 조회는 제거했다. 서버 시작·Redis 재구독 시에만 미종료 판의 예약을 배치 복구한다. 변경 후 MVC 백엔드를 재시작하면 적용된다. 기존 대기실의 30초 만료 정리는 별도 작업이다.

Flyway V7과 `/api/gomoku/rooms/lifecycle`은 뒤로가기·브라우저 닫기를 참가자 퇴장으로 연결한다. 대국 중에는 기존 기권 규칙을 적용한다. 새로고침은 유지하며 브라우저가 새로고침을 직접 구분하지 못한 경우 5초 종료 유예 안에 새 문서가 참가를 이어받는다. 종료 요청에도 기존 브라우저 서명·JWT·참가 식별자를 검증하고 이전 문서·재입장의 늦은 퇴장을 무시한다. 종료 알림이 유실된 대국 참가도 기존 접속 만료 정리에서 제거한다. 프론트와 MVC 백엔드를 함께 재시작해야 적용된다.

Spring Boot 4.1.1·Java 25 기반이다. 브라우저는 Next.js BFF에만 연결한다. local/prod는 브라우저 키 증명을 요구하고 원격 HTTP의 dev만 명시적으로 생략한다.

## 환경 구분

`application-local.properties` 없이 `application-local.properties`, `application-dev.properties`, `application-prod.properties` 세 파일만 사용한다. 각 파일이 독립된 전체 설정이며 `SPRING_PROFILES_ACTIVE`로 하나만 선택한다. 프로필을 혼용하면 시작을 거절한다.

| 환경 | Spring 연결 | 브라우저 개인키 | 연결 정보 |
| --- | --- | --- | --- |
| local | 루프백 HTTP | 필수, localhost의 보안 컨텍스트 예외 | 기존 로컬 값 보존 |
| dev | Docker 내부 HTTP + 별도 BFF 인증 키 | 생략, 일반 BFF+JWT | 개발 서버 환경 변수 |
| prod | HTTPS·mTLS | 필수 | 운영 환경 변수·인증서 |

## 로컬 실행

모든 환경에서 서버 시작 중 Redis에 `PING`을 보내 `PONG` 응답을 확인한다. Redis 미실행·접속 거부·인증 실패·시간 초과 또는 비정상 응답이면 웹 서버가 요청을 받기 전에 시작을 실패 처리한다. 연결 주소와 시간 제한은 각 환경의 기존 Redis 설정을 사용한다.

기존 로컬 DB·Redis·JWT 값과 주석을 `src/main/resources/application-local.properties`로 옮겼다. 이 파일은 비밀 정보가 있으므로 버전 관리하지 않으며 배포 JAR에서도 제외한다. `bootRun`은 로컬 리소스를 사용한다.

DB 변경은 Flyway가 서버 시작 시 적용하고 Hibernate는 결과를 검증한다. 현재 로컬 DB는 프로필 컬럼을 포함한 `V1` 기준 등록을 완료했다. 별도의 수동 프로필 DDL을 다시 실행하지 않는다. 다른 기존 DB의 최초 전환 절차는 아래 Flyway 안내를 따른다.

```powershell
.\gradlew.bat bootRun --args='--spring.profiles.active=local'
```

`local` 프로필은 Spring을 `127.0.0.1`에 바인딩하고 실제 루프백 연결에만 mTLS 개발 예외를 허용한다. 브라우저 키 검증은 개발에서도 생략하지 않는다. BFF는 `http://localhost:9080`, 공개 Origin은 `http://localhost:3000`을 사용한다. 포트·공개 Origin을 바꾸면 양쪽 설정을 함께 변경한다.

## Flyway 스키마 관리

Spring Boot가 관리하는 `spring-boot-starter-flyway`와 MySQL 모듈 `flyway-mysql`을 사용한다. local/dev/prod 모두 같은 [버전 SQL 폴더](src/main/resources/db/migration)를 읽는다. 서버 시작 순서는 Flyway 마이그레이션·이력 검증 → Hibernate 스키마 검증(`ddl-auto=validate`) → 요청 수신이다. DB 연결 실패, SQL 실행 오류, 적용된 SQL의 체크섬 불일치 또는 엔티티와 스키마 불일치가 발생하면 시작을 실패 처리한다. `schema.sql` 자동 실행은 비활성화했다. [Spring Boot 초기화 안내](https://docs.spring.io/spring-boot/how-to/data-initialization.html)

### 새 DB

MySQL에 빈 데이터베이스와 접속 계정을 먼저 준비하고 해당 환경의 연결 정보를 지정한다. 앱 계정에는 마이그레이션 SQL에 필요한 테이블 생성·변경 권한이 있어야 한다. 평소와 같은 명령으로 서버를 시작하면 [V1 초기 SQL](src/main/resources/db/migration/V1__initial_schema.sql)이 회원 테이블과 프로필 컬럼을 생성하고 `flyway_schema_history`에 실행 이력을 저장한다. 데이터베이스 자체나 DB 계정은 Flyway에서 생성하지 않는다.

### 기존 DB의 최초 전환

현재 `localhost:3306/playground`는 기존 회원 테이블과 데이터를 유지한 채 `V1` baseline 등록을 완료했다. 재시작부터 추가 설정 없이 사용한다.

다른 dev/prod 기존 DB는 먼저 백업하고, 프로필 컬럼·기본값·고유 인덱스 등을 포함한 실제 테이블 구조가 `V1`과 일치하는지 확인한다. baseline은 기존 SQL을 실행하거나 테이블 구조를 자동 대조하지 않으므로, 일부 변경만 적용된 DB를 그대로 등록하지 않는다. 확인한 DB에 한해 첫 실행에만 다음 옵션을 추가한다. `target=1`로 최초 등록 중 후속 버전의 적용을 막는다.

```powershell
# local 예시. 기존 dev/prod는 해당 실행 프로필과 배포 방식에 같은 일회성 옵션을 사용한다.
.\gradlew.bat bootRun --args='--spring.profiles.active=local --spring.flyway.baseline-on-migrate=true --spring.flyway.target=1'
```

Docker/JAR 배포에서는 첫 실행에만 `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true`, `SPRING_FLYWAY_TARGET=1`을 공급해도 된다. `flyway_schema_history`의 성공한 `BASELINE` 버전 `1`을 확인한 다음 서버를 종료하고 **두 옵션을 모두 제거한 일반 설정으로 다시 실행**한다. 일반 설정은 `baseline-on-migrate=false`이므로 이력이 없는 기존 DB를 임의로 초기 기준으로 인정하지 않는다. 이미 등록된 현재 로컬 DB에는 이 절차를 반복하지 않는다. [Flyway baseline 설정 안내](https://documentation.red-gate.com/fd/flyway-baseline-on-migrate-setting-277578974.html)

### 이후 DB 변경

1. 다음 순번인 `V2__설명.sql`, `V3__설명.sql`을 `src/main/resources/db/migration`에 추가한다. 버전은 중복 없이 증가시키고 SQL에 변경 목적을 주석으로 작성한다.
2. 적용 전 검토·백업 후 서버를 시작한다. 적용하지 않은 새 버전만 순서대로 실행된다. 여러 인스턴스는 같은 버전 SQL을 포함한 빌드를 배포한다.
3. 한 번 적용한 SQL은 수정·삭제하지 않는다. 정정도 새 버전으로 추가한다. 체크섬 검증, 순서 검증, `clean-disabled=true`를 유지하며 자동 `repair`나 데이터 삭제는 수행하지 않는다.

`V1`에는 이미 수동 반영한 `profile_avatar`까지 포함되어 있다. 중복된 수동 생성 SQL과 프로필 컬럼 추가 SQL은 제거했으며 DB 변경 SQL은 `src/main/resources/db/migration`에서 관리한다. 현재 적용된 구조를 기준으로 관리하므로 프로필 `ALTER TABLE`을 `V2`로 다시 추가하지 않는다. MySQL DDL은 오류 시 전체가 자동 롤백되지 않을 수 있으므로 실패한 변경은 실제 테이블 상태를 확인하고 복구한 뒤 이력을 정리한다.

## 개발 서버 HTTP·Docker

Spring에는 `SPRING_PROFILES_ACTIVE=dev`, BFF에는 `APP_ENV=dev`를 지정한다. Next는 개발 서버에서도 `next build` 후 `next start`로 실행하며 `NODE_ENV=dev`로 설정하지 않는다.

사용자에게 주는 `http://121.182.203.136:9081`은 **사이트의 공개 주소**다. 호스트 9081 → Nginx → 프론트/BFF 3000 → Spring 9080으로 연결한다. BFF의 `BACKEND_ORIGIN`은 `http://backend:9080`이다. Nginx가 실제 접속 IP를 덮어써 전달하고 Spring은 고정된 BFF 내부 IP만 신뢰한다. 기존 개발 서버의 MySQL·Redis에 연결하는 이미지 묶음을 PC에서 만들고 서버에서 직접 실행한다. 순서는 [Linux 직접 배포 안내](../deploy/README.md)를 따른다. 앱 포트는 호스트에 공개하지 않고 기존 DB 연결은 서버의 3306·6379를 사용한다.

dev 필수 환경 변수는 다음과 같다.

| 설정 | 대상·용도 |
| --- | --- |
| `APP_ORIGIN=http://121.182.203.136:9081` | Spring·BFF 공통. 실제 공개 IP/포트와 일치 |
| `BFF_DEV_SHARED_SECRET` | Spring·BFF 공통. JWT·쿠키 키와 다른 무작위 32바이트 Base64 |
| `JWT_SECRET` | Spring 전용. 로컬·운영과 다른 무작위 32바이트 이상 Base64 |
| `AUTH_COOKIE_SECRET` | BFF 전용. 무작위 32바이트 Base64 |
| `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | Spring의 개발 DB 연결. URL은 Docker DB 서비스 이름 사용 |
| `SPRING_DATA_REDIS_HOST` | Spring의 Redis 주소. 기본값 `redis` |
| `SPRING_DATA_REDIS_PASSWORD` | Redis 인증을 설정했을 때 같은 비밀번호 공급 |

브라우저의 Web Crypto·Web Locks는 원격 HTTP에서 사용할 수 없다. 사용자가 승인한 dev는 개인키 증명을 생략하고, BFF의 HttpOnly 암호화 쿠키·Origin 검사와 Spring의 JWT·Redis 세션·토큰 회전·재사용 탐지를 유지한다. 탭 사이 쿠키 변경은 브라우저 IndexedDB의 짧은 임대로 직렬화한다. HTTP는 통신을 암호화하지 않으며 dev에는 쿠키만 탈취한 공격자를 막는 개인키 방어가 없다. dev 키·DB·Redis·사용자 데이터는 운영과 분리한다.

Docker 내부 HTTP의 `X-BFF-Dev-Secret`은 서버 사이에서만 사용한다. 누락·불일치·중복 헤더는 거절하고, 공유 키 없는 HTTP 예외를 만들지 않는다. IP 전달은 아래 신뢰 프록시 규칙을 동일하게 따른다. 별도 프록시 없이 BFF에 직접 접속할 때는 신뢰 IP 설정을 비워 두며 Spring은 BFF 연결 IP를 기준으로 제한한다.

## 운영 연결

운영은 `SPRING_PROFILES_ACTIVE=prod`로 실행하고 다음 값을 비밀 관리 시스템/배포 환경에서 공급한다.

| 설정 | 용도 |
| --- | --- |
| `BACKEND_TLS_CERT` | Spring 서버 인증서 PEM의 `file:` 위치. BFF가 접속하는 내부 DNS가 SAN에 있어야 함 |
| `BACKEND_TLS_KEY` | Spring 서버 개인키 PEM의 `file:` 위치 |
| `BFF_CLIENT_CA` | BFF 클라이언트 인증서 발급 전용 CA PEM의 `file:` 위치 |
| `AUTH_BFF_ALLOWED_CERT_SHA256` | 허용 BFF 리프 인증서 DER의 SHA-256 지문. 콜론 없는 64자리 hex, 교체 시 쉼표로 복수 지정 |
| `APP_ORIGIN` | 공개 HTTPS Origin. BFF의 설정과 정확히 일치 |
| `AUTH_TRUSTED_PROXY_IPS` | Spring에 직접 접속하는 BFF의 실제 IP. 쉼표로 구분하며 CIDR/호스트명은 사용하지 않음 |
| `JWT_SECRET` | 최소 32바이트 무작위 값의 Base64. BFF 쿠키 키와 분리 |
| `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | 운영 DB 연결 |
| `SPRING_DATA_REDIS_HOST`, `SPRING_DATA_REDIS_PORT` 및 필요한 TLS/인증 설정 | 운영 Redis 연결 |

TLS 1.3과 클라이언트 인증서 검증을 사용하고, 서블릿 컨테이너가 검증한 인증서 지문도 필터에서 대조한다. 인증서 헤더·`X-Forwarded-Proto`는 서버 인증 수단으로 사용하지 않는다. 인증서나 필수 설정이 없으면 인증을 완화하지 않는다. `prod`에서 mTLS 또는 브라우저 키 증명을 끄면 시작을 거절하며 dev 공유 키도 운영 인증 수단으로 인정하지 않는다.

방화벽·보안 그룹은 **BFF→Spring 내부 포트만** 허용해야 한다. 실제 인프라 접근 제한은 이 저장소의 코드만으로 적용되지 않는다. BFF 앞의 신뢰 프록시는 사용자가 보낸 IP 헤더를 제거하고 실제 IP 하나로 덮어써야 하며, 프록시를 우회한 접근을 제한한다. BFF의 `TRUSTED_CLIENT_IP_HEADER`와 위 `AUTH_TRUSTED_PROXY_IPS`를 함께 설정한다.

prod는 신뢰 BFF 주소 목록이 비어 있으면 시작을 거절하고, 실제 연결 주소가 목록에 없거나 단일 `X-Real-IP`가 없으면 요청을 거절한다. 사용자 전체를 BFF IP 하나로 대체하지 않는다. BFF도 운영 IP 헤더 이름을 시작 시 검증한다.

로그인 제한은 계정별 5회/15분을 유지한다. IP별 기본 예산은 실패·진행 중 요청 120회/분으로, 검증 전에 무작위 ID로 예약하고 정상 비밀번호 인증 성공 시 해당 예약만 반환한다. 같은 공인 IP의 정상 로그인이 실패 횟수에 쌓이지 않으며 다른 사람의 실패·다음 시간 창의 예약도 지우지 않는다. IP 예약은 별도 v2 Key를 사용해 기존 문자열 카운터와 충돌하지 않는다.

서버 인증서/CA·클라이언트 인증서·개인키는 저장소나 이미지에 포함하지 않는다. 인증서 교체 시 Spring에 새 지문을 먼저 허용하고 BFF 인증서를 교체·재시작한 뒤 이전 지문을 제거한다. 이 교체는 브라우저 세션 키를 변경하지 않는다. 운영은 P6Spy SQL 출력과 `show-sql`을 끈다. 비밀번호·Cookie·Authorization·브라우저 증명·개인키를 프록시/애플리케이션 로그에 남기지 않는다.

## 회원가입

`POST /api/user/sign-up`은 닉네임(1~50자), 아이디(4~50자), 비밀번호(6~72자)만으로 가입할 수 있다. 기존 선택 개인정보는 생략 가능하며 새 가입 화면은 필수 세 항목만 전달한다. 비밀번호는 기존 `argon2_v1` 정책으로 해시하고 계정 상태는 서버에서 `ACTIVE`로 정한다. 성공 응답은 본문 없는 `201 Created`이며 로그인 세션은 별도 로그인으로 발급한다.

아이디 중복 사전 확인과 DB 고유 제약을 함께 적용한다. 동시 가입에서 발생한 아이디 제약 충돌도 `409 DUPLICATE_USERNAME`으로 반환한다. 기존 DDL의 `username` 및 신규 명시 이름 `uk_tbl_user_username`을 지원하므로 기존 DB 인덱스를 바꿀 필요는 없다. 다른 무결성 오류를 중복 아이디로 숨기지 않는다.

가입은 기존 BFF 연결 인증·신뢰 IP 판별을 공유한다. 비밀번호 계산 전에 별도 Redis 키 `auth:sign-up:rate-limit:ip:<IP 지문>`으로 IP별 기본 120회/60초를 제한한다. 성공·중복 요청을 포함하며 로그인 실패 카운터·15분 계정 제한에는 영향을 주지 않는다. 최초 요청부터 만료를 계산하고 제한 초과 요청이 대기 시간을 연장하지 않는다. `429 SIGN_UP_RATE_LIMIT_EXCEEDED`는 `Retry-After`를 제공하며 Redis 장애는 `503 SIGN_UP_SERVICE_UNAVAILABLE`로 처리한다.

`auth.sign-up.rate-limit.ip-max-attempts`와 `auth.sign-up.rate-limit.ip-window-seconds`로 조정할 수 있다. 환경 변수는 각각 `AUTH_SIGN_UP_RATE_LIMIT_IP_MAX_ATTEMPTS`, `AUTH_SIGN_UP_RATE_LIMIT_IP_WINDOW_SECONDS`다. 운영에서도 실제 사용자 IP 전달 설정을 적용해야 한다.

## 브라우저 키와 요청 검증

- 브라우저는 P-256 개인키를 추출 불가 `CryptoKey`로 IndexedDB에 저장한다. 공개키만 ES256 요청 증명 헤더에 담는다. 토큰은 BFF의 HttpOnly 암호화 쿠키에서만 운반한다.
- 로그인 nonce는 등록 전 키 지문에, 세션 nonce는 서명된 Refresh Token으로 확인한 현재 세션·등록된 키 지문에 결합한다. nonce 발급은 토큰을 회전하거나 사용자 활동을 연장하지 않는다.
- local/prod의 nonce는 갱신/Access Token/결과 암호화와 용도를 분리한 HKDF 키로 서명하며 60초 동안 유효하다. BFF가 실제 브라우저 본문에서 계산한 해시·요청 메서드·공개 Origin과 경로·타입·ES256 서명·nonce·증명 ID를 검증한다. 로그인 본문의 비밀번호는 증명의 빠른 해시에 포함하지 않는다. 로그인 대상·유지 선택만 서명에 결합한다. 운영 비밀번호는 HTTPS/mTLS 안에서 인증하며 dev HTTP의 기밀성을 보장하지 않는다.
- Bearer 인증이 성공한 조회·변경·로그아웃·활동 연장에도 키 검증을 적용한다. Refresh는 회전이나 재사용 판별 전에 키와 nonce의 세션을 대조한다. 증명 실패만으로 세션을 삭제하지 않는다.
- 키는 세션 생성 시 고정한다. 키 분실은 비밀번호로 다시 로그인하여 새 세션을 만든다. 같은 계정의 재인증 또는 기존 브라우저 키 증명 없이 다른 계정의 세션을 교체할 수 없다.
- JWT 키 교체 등으로 이전 교체 토큰을 검증할 수 없으면 기존 세션 교체만 생략한다. 비밀번호 인증과 브라우저 증명을 통과한 신규 로그인은 허용하며 무효 토큰으로 기존 세션을 삭제하지 않는다.
- 현재 세션 응답은 화면의 변경 요청을 결합할 공개 `sessionId`를 포함한다. BFF는 활동·로그아웃 본문의 ID와 쿠키의 세션이 다르면 변경을 실행하지 않는다. 이 ID는 인증 토큰이 아니며 기존 인증 필터 검증을 대체하지 않는다. 로그인 화면과 홈은 탭 변경·복귀 때 최신 세션을 다시 확인한다.
- 이 방식은 HttpOnly BFF 세션의 요청 서명이며, 브라우저가 OAuth Access Token의 `ath`를 만드는 표준 OAuth DPoP와는 구분한다. 같은 Origin에서 XSS가 이미 실행되면 추출 불가 키로 서명을 대신 요청할 수 있으므로 CSP·안전한 출력 처리가 함께 필요하다. 하드웨어에 고정된 기기 인증을 보장하지 않는다.

## 메모리와 세션 수명

Refresh Token은 서명된 세션 ID·세대를 포함한다. Redis에는 현재 해시·세대와 세션별 최신 암호화 결과 한 건만 저장한다. 같은 직전 토큰·갱신 식별자의 60초 결과 공유만 예외로 허용하며, 그 밖의 검증된 이전 세대 재사용은 해당 세션·목록·결과를 원자적으로 폐기한다. 기존 Access Token도 다음 인증부터 거절한다.

재전송 방지 ID는 nonce 만료까지 Sorted Set에 보관한다. 기본 상한은 세션당 120건이며 가득 차면 유효 항목을 버리지 않고 `429`를 반환한다. `auth.proof.max-pending`으로 조정할 수 있다. 공개 로그인은 IP별 600건(`auth.proof.login-max-pending`)으로 분리한다. 정상 완료/실패 여부와 관계없이 소비한 증명은 다시 쓸 수 없다. nonce 발급은 공개 로그인 IP별 600회/분, 검증된 세션·IP별 120회/분이며 카운터는 60초 뒤 만료한다. 공유 IP의 다른 계정이 세션 nonce 예산까지 함께 소모하지 않는다. 요청량에 따른 짧은 저장 비용은 있지만 로그인 기간이나 갱신 횟수에 따른 무한 이력은 없다.

의미 있는 활동은 ON 14일·OFF 3시간을 **마지막 활동부터** 다시 계산한다. 절대 최대 수명은 없으며 Refresh 자체는 활동으로 취급하지 않는다. 현재 Lua의 세션/사용자별 다중 Key 처리는 단일 Redis 기준이다.

## 배포 전환

Spring과 BFF를 함께 배포한다. 기존 키 미등록 세션/쿠키에는 브라우저 키를 자동 등록하지 않으며, 기존 사용자는 한 번 다시 로그인해야 한다. 이후 정상 활동에 대한 주기적 강제 로그아웃 정책은 없다. 현재 공개 회원 API는 가입만 제공한다. 비밀번호·이메일 변경/계정 삭제 API를 연결할 때는 브라우저 증명에 더해 해당 작업의 현재 비밀번호/패스키 재확인을 필수로 적용해야 한다.

## 프로필 캐릭터

`GET /api/auth/profile`은 검증된 본인의 닉네임·캐릭터를 조회하고 `PUT /api/auth/profile`은 `{ "avatarId": "FOX" }`처럼 식별자 하나로 변경한다. JWT·Redis·브라우저 서명은 기존 보호 API와 동일하게 검증하며 회원 ID는 요청 대신 인증 Principal에서 가져온다.

허용 식별자는 `CAT`, `DOG`, `RABBIT`, `FOX`, `BEAR`, `PANDA`, `OTTER`, `PENGUIN`, `FROG`, `DUCK`다. 숫자 순번·외부 URL·다른 식별자는 거절한다. `tbl_user.profile_avatar`에 저장하며 기본값은 `CAT`다. 프로필 변경은 계정 상태 변경과 같은 행 잠금을 사용해 비활성화·삭제 상태가 오래된 수정으로 덮어써지지 않게 한다. 프로필 응답과 조회는 인증 세션 갱신을 대신하지 않는다.

## 오목 1단계: 방 목록·생성

[V2 방 테이블 SQL](src/main/resources/db/migration/V2__create_gomoku_rooms.sql)을 Flyway가 적용한다. V1과 회원 테이블을 변경하지 않는다. 공개 API는 모두 기존 BFF 인증·JWT·Redis 세션·브라우저 증명을 요구하며 `Cache-Control: no-store`로 응답한다.

| 메서드·경로 | 동작 |
| --- | --- |
| `POST /api/gomoku/rooms/search` | `query`, `publicOnly`, `waitingOnly`, `page`로 검색. 최신순·6개 고정 크기, 제목 부분 검색·6자리 초대 코드 정확 검색 |
| `POST /api/gomoku/rooms` | `requestId`, `title`, `visibility`, `password`, `undoLimit`, `forbiddenRule`로 생성. 방장과 코드는 서버에서 결정 |
| `POST /api/gomoku/rooms/close` | `roomId`의 소유자만 본인의 빈 대기방을 닫음 |

검색어는 30자, 페이지 번호는 0~1000, 제목은 공백만 있는 값과 제어 문자를 제외한 1~30자다. `visibility`는 `PUBLIC`/`PRIVATE`, 무르기는 `1`/`3`/`5`, 금수는 `NONE`/`STANDARD`만 허용한다. 비공개 방 비밀번호는 영문·숫자 4~8자로 받고 기존 Argon2 정책으로 해시한다. 공개 방에는 비밀번호를 저장하지 않는다. 회원 ID·판 크기·시간·인원·선공 방식·관전 옵션을 입력으로 받지 않는다. 검색은 사용자 입력을 LIKE 패턴으로 해석하지 않으며 비ASCII 검색어는 ASCII 초대 코드 컬럼과 비교하지 않는다.

목록은 필요한 표시 컬럼만 조인 조회하고 비밀번호·해시·회원 아이디를 반환하지 않는다. 초대 코드는 생성 응답과 본인이 만든 방 정보에만 포함한다. 열린 방은 계정당 하나이며 회원 행 잠금과 생성 컬럼의 고유 제약으로 동시 생성을 막는다. 같은 UUID v4 `requestId`의 재시도는 기존 열린 방을 반환하고 닫힌 요청을 재사용하면 `409 ROOM_REQUEST_EXPIRED`다. 닫기는 소프트 삭제로 이력을 보존한다.

별도 Redis 계정 예산으로 검색 120회/분, 생성 10회/10분, 닫기 30회/분을 허용한다. 초과는 `429 ROOM_RATE_LIMIT_EXCEEDED`와 `Retry-After`, Redis 제한 상태 확인 실패는 `503 ROOM_SERVICE_UNAVAILABLE`다. 일반 로그인 제한과 별도 키를 사용한다. 모든 환경에서 `spring.jpa.open-in-view=false`로 서비스 트랜잭션 밖의 지연 로딩을 막는다.

입장·참가자 관리·빠른 매칭·실시간 대기실·착수·금수 판정·채팅은 이 단계에 구현하지 않는다. 금수 설정과 대국 고정 규칙은 이후 단계에서 사용할 계약으로 보존한다. 관전은 범위에서 제외한다.

## 채팅 2단계: 일회용 접속 증명

`POST /api/chat/tickets`는 기존 BFF 연결 인증·JWT·Redis 세션·브라우저 증명을 모두 통과한 본인에게만 30초짜리 무작위 증명을 발급한다. 닉네임·이미지는 ACTIVE 계정의 실제 프로필에서 읽는다. 로그인 수명을 연장하거나 기존 로그인 키를 변경하지 않는다.

Redis에는 원문 대신 SHA-256 해시를 사용하며 세션 재검증·세션당 미사용 5개·사용자당 분당 발급 시도 30회 제한을 Lua에서 원자적으로 처리한다. 제한 초과는 `429 CHAT_TICKET_RATE_LIMIT_EXCEEDED`와 `Retry-After`다. 증명 기한은 현재 로그인 세션 만료를 넘지 않는다.

프로필 DB 조회 전에 `reservation:` 접두사의 10초 예약으로 발급 예산과 대기 자리를 확보한다. 예약과 미사용 티켓은 기존 세션별 대기 ZSET에서 합산하므로 동시 요청도 5개 상한을 넘지 않는다. 프로필 조회에는 3초 읽기 전용 트랜잭션 예산을 적용하고, 조회 후 세션·계정 차단·브라우저 키·예약 만료를 다시 확인한 뒤 예약을 실제 티켓으로 전환한다. 티켓의 30초 수명은 이 최종 발급 시점부터 시작한다. 실패 시에는 해당 예약만 반환하고 시도 예산은 유지한다. 정리 실패나 프로세스 종료로 남은 예약도 유효기간 후 다음 요청에서 제거된다. 원문 증명과 로그인 세션 수명은 변경하지 않는다.

local 설정은 `CHAT_WEBSOCKET_URL=ws://localhost:8081/ws/chat`을 기본으로 쓴다. dev/prod는 `APP_ORIGIN`과 같은 공개 호스트의 `/ws/chat`을 사용하고 필요하면 `CHAT_WEBSOCKET_URL`을 공급한다. back과 chat의 공개 Origin·WebSocket 주소는 같아야 한다. 로그인 아이디·장기 토큰·내부 세션 상세를 발급 응답에 넣지 않는다.

접속·브라우저 키·Redis 공유 계약은 [chat 안내](../chat/README.md)를 따른다. back과 chat을 재시작하면 새 인증 경로가 적용된다.

## 오목 2단계: 대기실과 참가 권한

Flyway V4는 참가자·좌석·준비·입장 경계와 변경 순서를 추가하고 기존 열린 방의 방장을 참가자로 등록한다. V5는 방장 위임과 별개인 최초 생성자를 보존한다. 과거 생성 요청으로 이미 위임한 방의 코드를 다시 받지 못한다. 기존 V1~V3와 회원 데이터는 변경하지 않는다.

`POST /api/gomoku/rooms/join`, `/state`, `/ready`, `/leave`를 추가했다. 번호·6자리 코드로 입장하며 비공개 비밀번호와 2명 정원을 서버에서 확인한다. 준비는 본인의 명시적 boolean만 반영한다. 실제 시작·흑백 배정은 수행하지 않는다. 방장이 나가면 남은 참가자에게 위임하고 마지막 퇴장 시 방을 닫는다.

READ_COMMITTED와 방 행 잠금으로 동시 입장을 직렬화한다. 최초 입장과 생성은 계정 잠금을 공유하고 고유 제약으로 중복 참여를 막는다. 현재 참가한 방도 목록 응답에 본인에게만 포함한다. 조회는 접속 확인을 15초 단위로 저장하며 90초 이상 확인하지 못한 상대를 정리하고 준비를 해제한다. 아무도 다시 조회하지 않는 방도 30초 주기로 최대 100개 후보를 확인해 정리한다.

참가 변경은 커밋 후 Redis에 최대 25초 권한 리스로 투영한다. 대기실 조회가 리스를 갱신하고 chat은 이를 연장하지 않는다. 순서 번호로 늦은 상태의 복원을 막고 재입장마다 새 참가 식별자와 Redis 시각 경계를 발급한다. Redis 장애 시 방 채팅은 닫히며 사용자 메시지를 다른 방에 공개하지 않는다. 새 기능 적용 시 back·chat·front를 함께 재시작한다.

# 관리자 채팅 내역 (7단계)

`GET /api/admin/chat/messages`, `/messages/{24자리 소문자 ObjectId}`, `/games`는 기존 세션 검증과 DB의 `ADMIN_ACCESS`·`CHAT_HISTORY_READ`를 통과해야 한다. Spring Security 역할 기능은 사용하지 않는다. 역할 변경 후의 요청은 DB 권한을 다시 검사하며 `USER_READ`를 추가로 요구하지 않는다.

채팅 데이터는 chat 서버가 MongoDB에서 읽는다. back은 JDBC 트랜잭션 밖에서 고정된 `/internal/admin/chat/messages` 경로만 호출한다. 기존 공유 Redis에 요청 경로·인코딩된 쿼리의 해시를 결합한 10초 일회용 증명을 저장하고 내부 헤더로 전달한다. 원문 증명·브라우저 쿠키·사용자 토큰은 공유하지 않는다. 읽기 연결 2초·응답 5초, 응답 본문 512KiB로 제한하고 자동 재시도·리다이렉트는 하지 않는다.

- **local**: 현재 `chat.auth.websocket-url`의 호스트·포트를 HTTP(S) 주소로 재사용하므로 기존 로컬 설정으로 실행할 수 있다. 별도 내부 주소를 쓸 때는 `CHAT_ADMIN_ORIGIN`을 지정한다.
- **dev**: back에 `CHAT_ADMIN_ORIGIN`을 chat의 내부 HTTP 주소로 공급한다. 예: `http://chat:9082`는 해당 서비스 이름으로 내부 통신이 되는 배포에서 사용한다.
- **prod**: chat 서버의 HTTPS 설정에 맞춰 인증서 호스트명과 일치하고 JVM이 신뢰하는 내부 HTTPS 주소를 공급한다. 평문 HTTP 설정은 기동 시 거절한다. 공개 WebSocket 프록시 주소와 구분하며 `/internal/`을 외부에 라우팅하지 않는다. dev/prod에서 주소가 미설정이면 다른 기능은 실행되고 채팅 기록 조회만 503을 반환한다.
- back과 chat은 기존과 같이 같은 Redis 서버·논리 DB를 사용한다. 새 Redis 서비스나 MySQL 마이그레이션은 필요하지 않다. 적용 시 back과 chat을 재시작한다.

목록은 KST 기준 시작일~종료일(기본 최근 7일), 저장된 닉네임 앞부분 또는 회원 번호, 공용/게임방, 게임 코드, 방 번호를 지원한다. 10/20/30/50개(기본 10개)와 페이지 번호를 사용한다. 메시지 작성자의 닉네임·아바타는 **전송 당시 값**, 게임 이름만 현재 DB 카탈로그에서 한 번에 보충한다. 숨김·중단 게임의 과거 기록도 조회할 수 있다. 일반 사용자용 채팅 이력의 로그인 이후 조회 범위는 바꾸지 않는다.
