# 관리자 3단계: 게임 DB·관리 API·로비 연결

게임 기본 정보·장르·실행 모드를 분리하고 기존 로비가 DB의 공개 목록을 읽도록 연결했다. 오목의 방·대국·채팅 식별자는 유지한다. 새 관리자 화면과 관리자 BFF, 헤더 설정 아이콘은 다음 단계다.

## 확정한 이용 정책

- 사용자가 선택한 정책대로 숨김 게임은 목록에서 제외하고 직접 주소를 통한 신규 실행·입장도 차단한다. 모드 숨김에도 같은 기준을 적용한다.
- 게임 전체와 개별 모드의 AVAILABLE/MAINTENANCE/DISABLED 상태를 분리한다. 부모 게임이 제한 상태이면 모드만 열어 우회할 수 없다.
- 새 방 생성·신규 참가·첫 대국·재대국을 검사한다. 이미 참가한 사용자의 복귀와 진행 중인 대국의 착수·종료 처리는 유지한다.
- AI 대국은 워커/판을 만들기 전에 인증된 시작 API를 확인하고 재대국 때도 다시 검사한다. 이미 시작한 로컬 대국을 설정 변경 때문에 지우지 않는다.
- 로컬 AI 판정은 브라우저에서 실행하므로 이 시작 검사는 정상 서비스 흐름을 제어한다. 내려받은 JavaScript 자체를 외부에서 실행하는 것을 막는 서버 판정 체계로 취급하지 않는다.
- 전체 사이트 접속 제한은 상위 정책이며 2단계의 세션 종료 규칙이 계속 적용된다.

## DB와 확장 구조

Flyway V13은 다음 네 테이블을 추가한다. 기존 회원·오목·채팅 데이터를 변경하거나 삭제하지 않는다.

| 테이블 | 책임 |
|---|---|
| tbl_game | 고정 코드, 이름·소개·설명·썸네일 키, 정렬·공개·전체 운영 상태·변경 버전 |
| tbl_game_genre | 데이터로 추가하는 장르 사전, 사용 여부·정렬·변경 버전 |
| tbl_game_genre_mapping | 게임의 복수 장르, 양쪽 FK와 복합 PK |
| tbl_game_mode | 고정 모드 코드, 2D/3D·LOCAL/ONLINE·SOLO/COMPETITIVE/COOPERATIVE, 사람 인원 범위·구현 버전·개별 운영 상태 |

장르는 고정 enum이 아니다. 게임마다 여러 모드를 등록할 수 있으므로 한 게임을 단일/멀티 중 하나로 고정하지 않는다. AI와 관전자 수는 사람 인원에 합산하지 않는다. 인원 범위·운영 상태·정렬 범위에는 DB CHECK를 두고 모드 코드에는 게임 내 고유 제약을 적용한다.

초기 데이터는 기존 오목 하나와 AI(2D·로컬·1인), ONLINE(2D·온라인 경쟁·2인) 모드다. 기존 장르 버튼에 대응하는 여섯 장르를 등록하고 오목은 BOARD/CASUAL에 연결한다. 가상의 출시 게임이나 접속 통계는 생성하지 않는다.

게임/모드/장르는 물리 삭제 API를 제공하지 않는다. 비공개·이용 중지·장르 비활성화로 이력을 유지한다. 새 게임과 모드는 숨김·이용 중지로 생성한다. 최초 공개 시각은 최초 공개 때 한 번 기록하며 재공개로 신규 표시를 갱신하지 않는다.

## 구현 등록과 설정 검증

새 게임은 전용 GameModule 구현을 추가해 코드·허용 썸네일 키·지원 모드와 인원 범위를 등록한다. GameModuleRegistry가 DB 선언을 실제 배포 구현과 대조한다. 관리자는 구현의 지원 범위 안에서만 설정할 수 있다. 모드의 그래픽·접속·상호작용 유형은 등록 정의에서 가져오고 요청 본문에서 임의로 바꾸지 않는다.

프론트의 gameHref와 썸네일 렌더러에도 해당 게임을 연결한다. DB에 URL·JavaScript를 저장해 실행하지 않는다. 프론트에 아직 연결되지 않은 게임은 실행 링크를 활성화하지 않는다. 등록 코드의 이름을 바꿔도 게임/채팅 이력을 다시 연결할 필요가 없다.

현재 게임에 필요하지 않은 범용 설정 JSON은 추가하지 않았다. 향후 실제 게임별 맵·라운드·팀 구성이 필요하면 모듈의 버전 있는 검증 규칙과 시작 시 설정 보존을 함께 추가한다. 불연속 허용 인원은 범위로 허위 선언하지 않고 해당 게임 도입 시 연결 테이블 등으로 확장한다. 채팅의 GOMOKU 식별자 일반화는 두 번째 게임의 실제 방/채팅 구현을 추가할 때 호환 계획과 함께 진행한다.

## 검색과 성능

목록은 sortOrder+id 커서이며 최대 100개, 기본 40개다. 장르도 검색·커서로 확장한다. 전체 count나 무제한 offset 조회를 하지 않는다. 게임 페이지를 먼저 읽고 모드를 일괄 조회하며 장르 연결을 배치로 읽어 컬렉션 fetch join의 메모리 페이징과 게임별 추가 쿼리를 피한다. 두 게임 조회의 SQL 수가 네 개 이하인지 검증했다.

그래픽·인원 조건은 같은 모드의 EXISTS 안에서 함께 검사한다. 1인 2D 모드와 다인 3D 모드를 가진 게임이 1인+3D 검색에 잘못 걸리지 않는다. SINGLE, TWO, GROUP(3인 이상), MULTIPLAYER(2인 이상) 및 정확한 humanPlayers 검색을 지원한다. 공개 검색은 공개된 호환 모드가 있는 게임만 반환하고 비활성 장르는 제외한다. 점검 중 게임/모드는 목록에 상태를 제공하되 실제 시작은 거절한다.

LIKE 와일드카드는 일반 문자로 취급한다. ASCII 코드에는 ASCII 검색어만 비교해 한글 장르 이름 검색의 문자셋 충돌을 피한다. 신규 필터는 최초 공개 후 30일 기준이다. 인기 집계 기능은 추가하지 않았으며 기존 인기 필터의 빈 결과 동작을 유지한다.

정책은 캐시 없이 DB 원본에서 검사한다. 새 실행 트랜잭션은 부모 게임→모드를 공유 잠금으로 확인하고, 운영 편집은 부모 게임 쓰기 잠금을 얻는다. 정책 커밋과 새 방/대국 생성 사이의 경쟁 구간을 막는다. 진행 중인 판의 폴링마다 운영 정책을 새로 읽어 게임을 중단하지 않는다.

## API

모든 Spring 경로는 기존 BFF 서버 인증이 필요하다. 아래 공개 GET만 브라우저 개인키·로그인 없이 조회하며, admission POST는 기존 로그인·Redis·브라우저 증명이 필요하다.

| 메서드 | 경로 | 용도 |
|---|---|---|
| GET | /api/games | 공개 게임 검색. query, genre, graphics, players, humanPlayers, newOnly, cursor, limit |
| GET | /api/games/{code} | 공개 상세 |
| GET | /api/game-genres | 공개 장르 검색 |
| POST | /api/games/{code}/modes/{mode}/admission | 인증된 새 실행 확인 |

관리자 API는 ADMIN_ACCESS와 각 기능 권한을 현재 DB에서 검사한다. 요청 본문에서 행위자 ID/역할을 받지 않는다.

| 메서드 | 경로 | 권한 |
|---|---|---|
| GET | /api/admin/games, /api/admin/games/{code} | GAME_READ |
| GET | /api/admin/game-implementations | GAME_READ |
| POST | /api/admin/games | GAME_WRITE |
| PUT | /api/admin/games/{code} | GAME_WRITE |
| PUT | /api/admin/games/{code}/availability | GAME_AVAILABILITY_UPDATE |
| POST/PUT | /api/admin/games/{code}/modes/{mode} | GAME_WRITE |
| PUT | /api/admin/games/{code}/modes/{mode}/availability | GAME_AVAILABILITY_UPDATE |
| GET | /api/admin/game-genres | GAME_READ |
| POST/PUT | /api/admin/game-genres/{code} | GAME_WRITE |

메타데이터 요청에는 공개·점검 필드를 두지 않는다. 운영 권한이 없는 편집자가 GAME_WRITE로 이용 상태를 바꿀 수 없다. 변경 버전과 감사 기록을 같은 트랜잭션으로 저장한다. 권한 편집과도 공통 역할 잠금으로 순서를 맞춘다. 감사 저장 실패는 업무 변경도 롤백한다.

메타데이터 수정 예:

```json
{"metadata":{"name":"오목","summary":"한 수 한 수, 함께 즐기는 두뇌 대결","description":"AI·온라인 대국을 즐겨 보세요.","thumbnailKey":"GOMOKU","genreCodes":["BOARD","CASUAL"],"sortOrder":0},"expectedVersion":0,"reason":"소개 수정"}
```

운영 상태 수정 예:

```json
{"published":true,"availability":"MAINTENANCE","message":"온라인 대국 점검 중입니다.","expectedVersion":0,"reason":"정기 점검"}
```

모드 생성/수정은 name, minHumanPlayers, maxHumanPlayers, sortOrder, expectedVersion, 선택 reason을 받는다. 신규 모드·장르의 expectedVersion은 0이다. 장르는 name, enabled, sortOrder와 버전을 받는다. 게임 생성은 code와 metadata, 선택 reason을 받는다. 오래된 편집은 GAME_CATALOG_CHANGED/409다.

## 프론트와 검증 범위

로비의 정적 게임 데이터는 제거했다. DB 검색·동적 장르·이어 읽기·로딩·장애·취소된 검색 처리를 연결하고 기존 카드·포커스·반응형 스타일을 유지한다. BFF는 허용된 검색 조건과 공개 필드만 재구성하며 내부 필드가 자동 노출되지 않게 한다. 게임 점검은 GAME_UNAVAILABLE/409로 안내하고 로그인 종료나 토큰 갱신 루프로 바꾸지 않는다.

실제 검증은 별도 임시 MySQL·Redis와 전용 포트의 MVC/Next.js를 사용했다. 실제 local/dev/prod DB를 이 작업에서 직접 조작하지 않았다. 배포 때 MVC와 front를 함께 반영하고 MVC 시작 시 V13을 적용한다. 관리자 화면은 아직 없고 다음 단계에서 BFF와 연결한다. 사용자 요구대로 **관리자 진입 설정 아이콘은 헤더 알림 왼쪽에 놓고 ADMIN_ACCESS가 있는 사용자에게만 표시**한다.

결과와 화면은 [3단계 검증 기록](../../../artifacts/admin-development-stage3/verification.md)에 보관한다. 현재 브라우저의 최대 실측 너비는 4096px이므로 5K 실기기 검증은 별도로 남는다. 기존 로비의 콘텐츠 최대 너비 2560px는 유지한다.

참고: [MySQL CHECK](https://dev.mysql.com/doc/refman/8.4/en/create-table-check-constraints.html), [MySQL FK](https://dev.mysql.com/doc/refman/8.4/en/create-table-foreign-keys.html), [Spring Data JPA locking](https://docs.spring.io/spring-data/jpa/reference/4.2/jpa/locking.html). 설치된 Next.js 16.4.2의 인증·Route Handler·fetch 문서도 확인했다.
