# Playground Backend

Spring Boot 4.1.1·Java 25 기반이다. 브라우저는 Next.js BFF에만 연결한다. local/prod는 브라우저 키 증명을 요구하고 원격 HTTP의 dev만 명시적으로 생략한다.

## 환경 구분

`application.properties` 없이 `application-local.properties`, `application-dev.properties`, `application-prod.properties` 세 파일만 사용한다. 각 파일이 독립된 전체 설정이며 `SPRING_PROFILES_ACTIVE`로 하나만 선택한다. 프로필을 혼용하면 시작을 거절한다.

| 환경 | Spring 연결 | 브라우저 개인키 | 연결 정보 |
| --- | --- | --- | --- |
| local | 루프백 HTTP | 필수, localhost의 보안 컨텍스트 예외 | 기존 로컬 값 보존 |
| dev | Docker 내부 HTTP + 별도 BFF 인증 키 | 생략, 일반 BFF+JWT | 개발 서버 환경 변수 |
| prod | HTTPS·mTLS | 필수 | 운영 환경 변수·인증서 |

## 로컬 실행

기존 로컬 DB·Redis·JWT 값과 주석을 `src/main/resources/application-local.properties`로 옮겼다. 이 파일은 비밀 정보가 있으므로 버전 관리하지 않으며 배포 JAR에서도 제외한다. `bootRun`은 로컬 리소스를 사용한다.

```powershell
.\gradlew.bat bootRun --args='--spring.profiles.active=local'
```

`local` 프로필은 Spring을 `127.0.0.1`에 바인딩하고 실제 루프백 연결에만 mTLS 개발 예외를 허용한다. 브라우저 키 검증은 개발에서도 생략하지 않는다. BFF는 `http://localhost:9080`, 공개 Origin은 `http://localhost:3000`을 사용한다. 포트·공개 Origin을 바꾸면 양쪽 설정을 함께 변경한다.

## 개발 서버 HTTP·Docker

Spring에는 `SPRING_PROFILES_ACTIVE=dev`, BFF에는 `APP_ENV=dev`를 지정한다. Next는 개발 서버에서도 `next build` 후 `next start`로 실행하며 `NODE_ENV=dev`로 설정하지 않는다.

사용자에게 주는 `http://123.123.234.123:9080`은 **Spring이 아닌 프론트/BFF의 공개 주소**다. Docker에서 프론트의 3000 포트를 호스트 9080으로 연결하고 Spring 9080·DB·Redis는 내부 네트워크에만 둔다. 서로 다른 컨테이너의 내부 9080과 호스트의 공개 9080은 충돌하지 않는다. 예를 들어 BFF의 `BACKEND_ORIGIN`은 `http://backend:9080`, Redis 서비스 이름은 `redis`로 둔다. 실제 배포 Compose/Dockerfile은 아직 생성하지 않았다.

dev 필수 환경 변수는 다음과 같다.

| 설정 | 대상·용도 |
| --- | --- |
| `APP_ORIGIN=http://123.123.234.123:9080` | Spring·BFF 공통. 실제 공개 IP/포트와 일치 |
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
