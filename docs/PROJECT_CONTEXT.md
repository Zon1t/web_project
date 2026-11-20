# 슥밥 프로젝트 컨텍스트 (Claude Code 인계용)

> 작성일: 2026-11-18 · 작성 기준: Claude(Cowork)와 진행한 기획·설계 대화 전체
> 이 문서는 요약본이다. **원본(Source of Truth)은 노션 문서**이며, 내용이 다르면 노션을 따른다. (노션 링크는 맨 아래 "참고 문서")
>
> **Claude Code 사용 팁**
> - 저장소 최상위에 `CLAUDE.md`로 두거나, `docs/PROJECT_CONTEXT.md`로 두고 `CLAUDE.md`에서 참조한다.
> - 작업 요청 시 "이 문서의 API 목록/ERD/컨벤션을 따를 것"을 함께 지시한다.

---

## 0. 작업 원칙 (Claude Code가 지켜야 할 것)

- 상세 답변이나 실행 전에 사용자에게 먼저 확인한다. 불명확한 점은 반드시 질문해서 명확히 한다.
- **WBS는 절대 수정하지 않는다.** (사용자가 직접 관리)
- 노션 문서를 고칠 때는 **댓글·메모를 훼손하지 않는다.** 페이지 전체 덮어쓰기 금지, 필요한 문장만 부분 교체.
- DB 비밀번호·네이버 API 키는 Git에 올리지 않는다 (`application-local.yml`만 사용).
- 사용자는 비전공 입문자 수준이다. 설명은 쉬운 말로, 코드에는 필요한 import와 위치(경로)를 함께 안내한다.

---

## 1. 프로젝트 개요

| 항목 | 내용 |
| --- | --- |
| 서비스명 | 슥밥 — 식사 메뉴 추천 및 식사팟 모집 서비스 |
| 과정 | 삼성 SDS SCSA (삼성 멀티캠퍼스 세종점) 팀 프로젝트 |
| 팀 | 3명 (입문자 수준) |
| 기간 | 평일 8일, 2026-11-17(화) ~ 11-26(목), 1인 1일 7h, 총 168h |
| 구내식당 | 세종대학교 계절밥상 |
| 사용자 | 사원 대상, **전 화면 로그인 필수** (비회원 없음) |

### 핵심 기능

1. **구내식당 메뉴**: 계절밥상 메뉴를 Jsoup으로 크롤링, 당일 점심·저녁 표시 (실패 시 안내 문구, 불가 시 관리자 수동 입력)
2. **식당 아카이빙**: 네이버 지도 URL로 식당 등록(네이버 API로 주소·좌표 저장), 목록+지도, 리뷰(한줄평·태그·사진), 관심
3. **식사팟**: 오늘~14일 후 점심/저녁 식사 모임 생성·참여, 2단계 참여(참여하기 → 방장 일정 확정 후 참여 확정), 대화(폴링)
4. **따봉·랭킹**: 식사 완료 후 참여 확정자가 식당에 따봉, 최근 30일 따봉 수 랭킹
5. **메뉴 룰렛**: 카테고리별 최대 8곳 후보 → 서버가 1곳 무작위 선정

### 일정 (평일 8일)

| 구간 | 일정 |
| --- | --- |
| 설계·문서 | 1~2일차 (11/17~18) |
| Must 기능 개발 | ~5일차 (11/23) |
| Should 기능 개발 | ~7일차 (11/25) |
| 통합 테스트·발표 준비 | 7~8일차 (11/25~26) |
| 중간 점검 | 11/23 (5일차) |

---

## 2. 진행 상황 (2026-11-18 기준)

| 단계 | 상태 |
| --- | --- |
| 기획서, 기능 명세서(42행+), 화면설계서(S-01~S-12), 와이어프레임 | 완료 |
| ERD·테이블 정의서, API 명세서(42개) | 완료 |
| 1단계: 기술 스택 통일 + Oracle 11g/JPA 연결 테스트 | **완료 (빌드·연결·시퀀스·페이징·한글 모두 통과)** |
| 2단계: 도메인 나누기 + 역할 분담 | 분담안 작성 완료, **담당자(A/B/C) 배정 전** |
| 3단계: 팀 컨벤션 | 초안 작성 완료 (아래 9장) — 팀 확정 필요 |
| 4단계: Git 뼈대 | 가이드 작성 완료 (아래 10장), 공통 코드는 다음 작업 |
| 5단계: 프론트 뼈대 | 미착수 |

### 다음 작업 (우선순위 순)

1. 팀 회의에서 컨벤션 확정, A/B/C 담당자 배정
2. C: Git 저장소·Spring 뼈대 생성 (10장 Step 1~8)
3. C: `common/` 공통 코드 — `ApiResponse`, `ErrorCode`, `BusinessException`, `GlobalExceptionHandler`, 로그인 체크 인터셉터 + `WebConfig`
4. A: `docs/sql/`에 DDL·시퀀스·초기 데이터(태그 13건, 구내식당)·더미데이터
5. B: 프론트 뼈대 — `tokens.css`, 공통 헤더, `js/api/client.js`(fetch 래퍼), 목업 구조

---

## 3. 기술 스택 (확정·검증 완료)

| 구분 | 확정값 | 비고 |
| --- | --- | --- |
| Java | 17 | |
| Spring Boot | **4.1.2** | |
| Hibernate | 7.4.13.Final | Boot가 관리 |
| 빌드 | Gradle (Groovy DSL) | |
| IDE | IntelliJ IDEA | UTF-8, annotation processing ON |
| DB | Oracle XE 11g | SQL Developer 사용 |
| JDBC | **ojdbc8 19.33.0.0 (고정)** | Boot 기본 드라이버(23.x)는 구버전 DB 미지원 |
| ORM | Spring Data JPA + **`OracleLegacyDialect`** | `hibernate-community-dialects`. 기본 OracleDialect는 Oracle 19+만 지원 |
| 비밀번호 | `spring-security-crypto` (BCrypt만) | Spring Security 전체는 사용 안 함 |
| 크롤링 | Jsoup | |
| 외부 API | 네이버 API (장소 조회·좌표, 지도 표시) | 실패 시 502 `MAP_API_ERROR` |
| 프론트 | HTML/CSS/JS (프레임워크 없음) | Spring `static/`에서 제공 → CORS 불필요 |
| 형상관리 | Git + GitHub | |

### 연결 테스트로 확인된 사항

- `Database dialect: OracleLegacyDialect`, `Database version: 11.2` 로 정상 인식
- `@SequenceGenerator(allocationSize = 1)` 시퀀스 채번 정상
- `Pageable` 페이징 시 `rownum` 방식 SQL 생성 정상
- 한글 저장·조회 정상
- 주의: 메인 클래스(`@SpringBootApplication`) 패키지 하위에 있어야 Repository가 스캔됨

### build.gradle (dependencies)

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-webmvc'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-validation'

    runtimeOnly 'com.oracle.database.jdbc:ojdbc8:19.33.0.0'
    implementation 'org.hibernate.orm:hibernate-community-dialects'

    implementation 'org.jsoup:jsoup:<최신버전>'
    implementation 'org.springframework.security:spring-security-crypto'

    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'

    testImplementation 'org.springframework.boot:spring-boot-starter-data-jpa-test'
    testImplementation 'org.springframework.boot:spring-boot-starter-webmvc-test'
    testCompileOnly 'org.projectlombok:lombok'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
    testAnnotationProcessor 'org.projectlombok:lombok'
}
```

### application.yml

```yaml
spring:
  application:
    name: sseukbap
  profiles:
    active: local
  jpa:
    database-platform: org.hibernate.community.dialect.OracleLegacyDialect
    hibernate:
      ddl-auto: none          # 테이블은 DDL 스크립트로 생성
    show-sql: true
    open-in-view: false
    properties:
      hibernate.format_sql: true
  servlet:
    multipart:
      max-file-size: 5MB
```

### application-local.yml (Git 제외, 각자 PC에만)

```yaml
spring:
  datasource:
    url: jdbc:oracle:thin:@localhost:1521:XE
    username: <DB_계정>
    password: <DB_비밀번호>
naver:
  client-id: <키>
  client-secret: <시크릿>
```

> Mac 사용자: Oracle XE 11g는 macOS에 설치 불가. Windows PC 공용 DB 사용 또는 Windows에서 개발.

---

## 4. 기능 목록 (기획서 기준)

| ID | 기능 | 우선순위 | 공수 |
| --- | --- | --- | --- |
| F-01 | 프로젝트 기본 구성 | P0 | 8h |
| F-02 | 회원 (가입·로그인·로그아웃, 아이디·닉네임 중복 확인) | P0 | 10h |
| F-03 | 식당 정보 관리 (네이버 URL 등록, 목록, 상세, 수정 제안) | P0 | 12h |
| F-04 | 식당 필터·검색 (카테고리 단일, 이름 부분 일치) | P0 | 4h |
| F-05 | 구내식당 메뉴 조회 (Jsoup) | P0 | 10h |
| F-06 | 식사팟 | P0 | 16h |
| F-07 | 리뷰 (한줄평 100자, 중복 작성 허용, 작성자만 삭제) | P0 | 6h |
| F-08 | 관심·랭킹 (관심은 랭킹 미반영, 랭킹은 최근 30일 따봉) | P0 | 7h |
| F-09 | 리뷰 사진 (1장, jpg/png, 5MB) | P1 | 6h |
| F-10 | 리뷰 태그 (13개 고정, TOP 3) | P1 | 6h |
| F-11 | 마이페이지 | P1 | 5h |
| F-12 | 메뉴 룰렛 | P1 | 5h |
| F-13 | 식사 완료 자동 처리 (스케줄러) | P1 | 3h |
| F-14 | 식사팟 초대 링크 | 미정 | 4h |
| F-15 | 따봉 | 미정 | 5h |
| F-16 | 식사팟 상세 (대화·일정 확정) | 미정 | 18h |
| F-17 | 식당 지도 (네이버 지도, 핀·말풍선) | 미정 | 6h |

### 주요 규칙

- 회원: 아이디 영문·숫자 4~20자, 비밀번호 8~20자, 닉네임 2~10자. **아이디·닉네임 모두 중복 불가**(중복 확인 API + 가입 시 서버 재검사 + DB UK). 닉네임은 앞뒤 공백 제거 후 비교, 대소문자 구분. 가입 후 자동 로그인 없음.
- 식당 카테고리: 구내식당/한식/중식/일식/양식/분식/기타. 식당 이름 최대 30자, 등록 식당 자동완성으로 중복 방지. 등록자 수정·삭제 없음 → "식당 정보 수정 제안"(저장만, 처리 방식 미정).
- 식사팟: 오늘 ~ 14일 후(11/20 변경, 기존 당일만. 날짜 미지정 시 오늘), 끼니(점심 11:00~13:30 / 저녁 17:00~19:30, 30분 단위, 기본 11:00/17:00), 정원 2~12(기본 4, 방장 포함), 한마디 50자. 생성자 = 방장 + 첫 참여자.
- 참여 2단계: [참여하기](1차, 수락 없이 대화 입장) → 방장 일정 확정 후 [참여 확정](2차). 일정 수정 시 모든 확정 초기화.
- 확정 취소·확정자 나가기: 기준 시간 30분 전까지. 기준 시간 = `confirmed_time`, 없으면 `meal_time`.
- 방장이 나가거나 삭제 → 식사팟 DELETED(소프트 삭제).
- 대화: 참여자 전용, 200자, 3초 폴링(afterId), 시스템 메시지(참여·나가기·일정·확정 등), 식사 완료 후 읽기 전용, 다음 날 0시 삭제.
- 따봉: FINISHED 팟의 참여 확정자만, 팟 1건당 1회, 대상 = 팟의 식당. 메인 배너로 요청, [다음에] 시 당일 숨김(FE 브라우저 저장소). 요청 대상은 식사 후 7일 이내.
- 랭킹: 오늘 포함 최근 30일(오늘-29 ~ 오늘, **식사일 PARTY_DATE 기준**) 따봉 수 내림차순 → 리뷰 수 → 등록 순. 랭킹 TOP 10, 메인 TOP 3. 따봉 1건 이상만.
- 룰렛: 후보 조회(최대 8, 무작위) → spin(candidateIds 2~8개 중 1곳 선정), 2곳 미만이면 실행 불가.

---

## 5. 화면 목록

| ID | 화면 | 관련 기능 | 비고 |
| --- | --- | --- | --- |
| S-01 | 메인 | F-05, F-06, F-08, F-12, F-15, F-16 | 따봉 배너, 오늘의 구내식당, 참여 중인 식사팟(최대 2), 오늘의 식사팟(최대 3), 이달의 따봉 TOP 3, 룰렛 배너 |
| S-02 | 회원가입 | F-02 | 아이디·닉네임 [중복 확인] |
| S-03 | 로그인 | F-02 | 실패 시 단일 오류 문구, 성공 시 직전 화면 복귀 |
| S-04 | 식당 목록 (목록+지도) | F-03, F-04, F-17 | 좌측 목록(최근 30일 따봉순), 우측 네이버 지도 |
| S-05 | 식당 상세 | F-03, F-06, F-07~F-10 | 태그 TOP 3, 따봉(30일), 관심, 리뷰(페이징), 수정 제안 링크 |
| S-06 | 식당 등록 | F-03 | 네이버 URL(최상단) → 이름(자동완성) → 카테고리 |
| S-07 | 식사팟 목록 | F-06, F-16 | 날짜 선택(오늘~14일 후, 기본 오늘) + 점심/저녁 탭(15시 전 점심 기본) |
| S-08 | 식사팟 생성 | F-06 | 식당 상세에서 진입 시 식당 고정, 날짜 선택(오늘~14일 후) |
| S-09 | 랭킹 | F-08, F-15 | |
| S-10 | 마이페이지 | F-11, F-08 | 탭: 내 리뷰 / 참여한 식사팟 / 등록한 식당 / 관심 식당 |
| S-11 | 메뉴 룰렛 | F-12 | |
| S-12 | 식사팟 상세 | F-06, F-16 | 참여자, 일정, 대화, 방장 메뉴 |

와이어프레임: Claude 캔버스 "슥밥 화면설계서 와이어프레임" (S-01 디자인 시안 A 포함)

---

## 6. API 공통 규칙

| 항목 | 내용 |
| --- | --- |
| 구조 | REST API + JS fetch. 서버는 JSON만 응답 |
| Base URL | `/api` |
| 인증 | 세션(JSESSIONID). 회원가입·아이디 중복 확인·닉네임 중복 확인·로그인 제외 모두 로그인 필요 |
| 요청 형식 | `application/json` (리뷰 작성만 `multipart/form-data`) |
| 날짜·시간 | 날짜 `YYYY-MM-DD`, 시간 `HH:mm`, 일시 ISO-8601 |
| 페이징 | 요청 `page`(1부터, 기본 1), `size`(기본 10, 최대 50) / 응답 `{page, size, totalCount, hasNext, items}` — 현재 리뷰 목록만 |

### 공통 응답 구조

```json
{
  "statusCode": 200,
  "timestamp": "2026-11-17T03:00:00Z",
  "path": "/api/...",
  "message": "요청이 처리되었습니다.",
  "data": { },
  "error": null
}
```

오류 시 `data: null`, `error: "PARTY_FULL"` 형식.

### 공통 오류 코드

| HTTP | error | 의미 |
| --- | --- | --- |
| 400 | `INVALID_REQUEST` | 필수값 누락, 형식·길이 오류 |
| 401 | `UNAUTHORIZED` | 미로그인·세션 만료 (FE는 로그인 화면으로) |
| 403 | `FORBIDDEN` | 권한 없음 |
| 404 | `NOT_FOUND` | 대상 없음 |
| 409 | `CONFLICT` 계열 | 중복·상태 충돌 (API별 상세 코드) |
| 500 | `INTERNAL_SERVER_ERROR` | 서버 오류 |
| 502 | `MAP_API_ERROR` | 네이버 API 호출 실패 |

### API별 상세 오류 코드 (명세서에 등장한 것, 전체 목록은 노션 확인)

`PASSWORD_MISMATCH`, `LOGIN_ID_ALREADY_EXISTS`, `NICKNAME_ALREADY_EXISTS`, `RESTAURANT_NOT_FOUND`, `ALREADY_INTERESTED`, `INTEREST_NOT_FOUND`, `PARTY_NOT_FOUND`, `PARTY_DELETED`, `PARTY_FINISHED`, `PARTY_FULL`, `ALREADY_JOINED`, `NOT_JOINED`, `NOT_HOST`, `INVALID_MEAL_TIME`, `INVALID_STATUS`, `SCHEDULE_NOT_SET`, `ALREADY_CONFIRMED`, `NOT_CONFIRMED`, `CANCEL_DEADLINE_PASSED`, `LEAVE_DEADLINE_PASSED`, `CHAT_READ_ONLY`, `NOT_CONFIRMED_MEMBER`, `PARTY_NOT_FINISHED`, `ALREADY_THUMBED`, `NOT_ENOUGH_CANDIDATES`

### 코드 값

| 구분 | 값 |
| --- | --- |
| 식당 카테고리 | `CAFETERIA`, `KOREAN`, `CHINESE`, `JAPANESE`, `WESTERN`, `SNACK`, `ETC` |
| 끼니 | `LUNCH`, `DINNER` |
| 식사팟 상태 | `RECRUITING`(모집중), `SCHEDULED`(일정 확정), `FULL`(정원 마감·자동), `CLOSED`(모집 마감·방장), `FINISHED`(식사 완료), `DELETED`(삭제·응답에 노출 안 함) |
| 내 역할 | `HOST`, `MEMBER`, `NONE` |
| 대화 메시지 유형 | `TEXT`, `SYSTEM` |

### 리뷰 태그 (13개 고정, FE 상수)

| tagId | 태그 | 분류 |
| --- | --- | --- |
| 1 | 점심추천 | MEAL_TYPE (식사 상황) |
| 2 | 저녁추천 | MEAL_TYPE |
| 3 | 퇴근후식사 | MEAL_TYPE |
| 4 | 해장추천 | MEAL_TYPE |
| 5 | 혼밥가능 | GROUP_TYPE (인원 및 모임) |
| 6 | 단체가능 | GROUP_TYPE |
| 7 | 소규모모임 | GROUP_TYPE |
| 8 | 가성비 | PRICE |
| 9 | 웨이팅많음 | WAITING |
| 10 | 도보5분이내 | ACCESSIBILITY (접근성 및 편의시설) |
| 11 | 주차가능 | ACCESSIBILITY |
| 12 | 포장가능 | ACCESSIBILITY |
| 13 | 단체석 | ACCESSIBILITY |

- 분류 무관 복수 선택, 분류별 개수 제한 없음. TOP 3는 13개 전체 중 선택 횟수 기준(동수면 tagId 오름차순).
- 태그 이름에 '#' 없이 저장, 화면에서 붙임. 태그 분류 `MEAL_TYPE`은 식사팟 끼니(mealType)와 별개.

### 식사팟 상태 전이

| 현재 상태 | 이벤트 | 다음 상태 |
| --- | --- | --- |
| (없음) | 생성 | RECRUITING |
| RECRUITING | 방장 일정 확정 | SCHEDULED |
| FULL, CLOSED | 방장 일정 확정·수정 | 유지 (일정만 저장) |
| RECRUITING, SCHEDULED | 참여로 정원 도달 | FULL |
| RECRUITING, SCHEDULED, FULL | 방장 모집 마감 | CLOSED |
| FULL | 나가기로 정원 미만 | 일정 있으면 SCHEDULED, 없으면 RECRUITING |
| CLOSED | 나가기로 정원 미만 | CLOSED 유지 |
| CLOSED | 방장 모집 재개 | 인원=정원이면 FULL, 아니면 일정 유무로 SCHEDULED/RECRUITING |
| RECRUITING, SCHEDULED, FULL, CLOSED | 기준 시간 경과 (스케줄러) | FINISHED |
| RECRUITING, SCHEDULED, FULL, CLOSED | 방장 삭제 / 방장 나가기 | DELETED (소프트 삭제) |

- FINISHED: 참여·나가기·확정·일정 변경·마감·삭제·대화 전송 불가 (409 `PARTY_FINISHED`), 삭제 불가.
- DELETED: 모든 목록·집계에서 제외, 식사팟 API는 404 `PARTY_DELETED`.
- 참여·나가기·일정·상태 변경·따봉 시 `SELECT ... FOR UPDATE` 행 잠금.

### 따봉 집계

- 저장: `PARTY_MEMBER.THUMBS_UP_YN = 'Y'`
- 식당별 최근 30일 따봉 수 = `PARTY_MEMBER(THUMBS_UP_YN='Y')` ⋈ `MEAL_PARTY` 에서 `PARTY_DATE`가 오늘-29 ~ 오늘인 행을 `RESTAURANT_ID`별로 COUNT
- 적용: 랭킹, 식당 목록·상세, 따봉 등록 응답, 룰렛 결과

### 서버 스케줄러 (API 없음)

| 작업 | 주기 | 내용 |
| --- | --- | --- |
| 식사 완료 처리 | 5분마다 | 기준 시간 지난 팟 → FINISHED, 시스템 메시지 '식사가 완료되었습니다' |
| 대화 기록 삭제 | 매일 00:00 | 전날까지 FINISHED·DELETED된 팟의 PARTY_CHAT 삭제 (팟·참여 이력 유지) |

---

## 7. API 목록 (42개)

| 담당 | 메서드 | URI | 이름 | Query |
| --- | --- | --- | --- | --- |
| C | GET | `/api/auth/check-id` | 아이디 중복 확인 | loginId |
| C | GET | `/api/auth/check-nickname` | 닉네임 중복 확인 | nickname |
| C | POST | `/api/auth/signup` | 회원가입 | |
| C | POST | `/api/auth/login` | 로그인 | |
| C | POST | `/api/auth/logout` | 로그아웃 | |
| C | GET | `/api/auth/me` | 로그인 사용자 조회 | |
| C | GET | `/api/users/me/summary` | 마이페이지 프로필 조회 | |
| C | GET | `/api/users/me/reviews` | 내 리뷰 목록 | |
| C | GET | `/api/users/me/parties` | 참여한 식사팟 목록 (DELETED 제외) | |
| C | GET | `/api/users/me/restaurants` | 등록한 식당 목록 | |
| C | GET | `/api/users/me/interests` | 관심 식당 목록 | |
| C | GET | `/api/cafeteria/menus/today` | 오늘의 구내식당 메뉴 | |
| C | GET | `/api/restaurants/rankings` | 따봉 랭킹 | limit(기본 10, 1~10) |
| C | GET | `/api/restaurants/roulette/candidates` | 룰렛 후보 조회 | category |
| C | POST | `/api/restaurants/roulette/spin` | 룰렛 실행 `{candidateIds}` | |
| B | GET | `/api/restaurants/autocomplete` | 식당 이름 자동완성 | keyword |
| B | GET | `/api/restaurants/check-map-url` | 네이버 지도 URL 검증 | url |
| B | GET | `/api/restaurants/list` | 식당 목록 (검색·필터·지도) | keyword, category |
| B | POST | `/api/restaurants/register` | 식당 등록 | |
| B | GET | `/api/restaurants/{restaurantId}` | 식당 상세 | |
| B | POST | `/api/restaurants/{restaurantId}/interest` | 관심 등록 | |
| B | DELETE | `/api/restaurants/{restaurantId}/interest` | 관심 해제 | |
| B | GET | `/api/restaurants/{restaurantId}/reviews` | 리뷰 목록 (페이징) | page, size |
| B | POST | `/api/restaurants/{restaurantId}/reviews` | 리뷰 작성 (multipart) | |
| B | POST | `/api/restaurants/{restaurantId}/suggestions` | 식당 정보 수정 제안 | |
| B | DELETE | `/api/reviews/{reviewId}` | 리뷰 삭제 | |
| A | POST | `/api/parties/create` | 식사팟 생성 | |
| A | GET | `/api/parties/list` | 식사팟 목록 | date, mealType, status, excludeJoined, limit |
| A | GET | `/api/parties/my/today` | 참여 중인 식사팟 (오늘~14일 후, 최대 2) | |
| A | GET | `/api/parties/my/thumbs-pending` | 따봉 요청 대상 조회 (최근 1건) | |
| A | GET | `/api/parties/{partyId}` | 식사팟 상세 | |
| A | DELETE | `/api/parties/{partyId}` | 식사팟 삭제 (방장, 소프트 삭제) | |
| A | GET | `/api/parties/{partyId}/chats` | 대화 조회 (폴링) | afterId |
| A | POST | `/api/parties/{partyId}/chats` | 대화 전송 | |
| A | GET | `/api/parties/{partyId}/invite-link` | 초대 링크 조회 | |
| A | POST | `/api/parties/{partyId}/members` | 참여하기 (1차) | |
| A | DELETE | `/api/parties/{partyId}/members/me` | 나가기 | |
| A | POST | `/api/parties/{partyId}/members/me/confirm` | 참여 확정 (2차) | |
| A | DELETE | `/api/parties/{partyId}/members/me/confirm` | 참여 확정 취소 | |
| A | PATCH | `/api/parties/{partyId}/schedule` | 일정 확정·수정 (방장) | |
| A | PATCH | `/api/parties/{partyId}/status` | 모집 마감·재개 (방장) `{action: CLOSE/REOPEN}` | |
| A | POST | `/api/parties/{partyId}/thumbs` | 따봉 등록 | |

> 각 API의 Request/Response/시스템 처리/오류 표는 노션 API 명세서 DB의 개별 페이지에 있다.
> 2026-11-18 URI 이동: `/api/rankings/thumbs` → `/api/restaurants/rankings`, `/api/roulette/*` → `/api/restaurants/roulette/*`, `/api/thumbs/pending` → `/api/parties/my/thumbs-pending`

---

## 8. ERD (Oracle XE 11g, 11개 테이블)

### 작성 규칙

- 테이블·컬럼: 대문자 스네이크, 테이블 단수형
- PK: 모든 테이블 숫자 대리키 단일 PK (복합 PK 사용 안 함). 연결 테이블 중복은 UK로 방지
- SEQUENCE: 대리키 테이블마다 1개 `SEQ_테이블명`, `START WITH 1 INCREMENT BY 1 NOCACHE`. TAG는 1~13 고정값이라 없음
- JPA: `@GeneratedValue(strategy = SEQUENCE)` + `@SequenceGenerator(sequenceName = "SEQ_...", allocationSize = 1)`
- 문자열: `VARCHAR2(byte)`, 한글 1자 = 3byte (예: 30자 → 90)
- Y/N: `CHAR(1)` + CHECK('Y','N'), 일시: `DATE DEFAULT SYSDATE`, 식사 시간: `VARCHAR2(5)` 'HH:mm'
- 제약조건 이름: `PK_테이블`, `FK_자식_부모`, `UK_테이블_컬럼`, `CK_테이블_컬럼`

### 테이블 정의

**MEMBER (회원)**
| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| MEMBER_ID | NUMBER | PK, SEQ_MEMBER | |
| LOGIN_ID | VARCHAR2(20) | UK, NN | 영문·숫자 4~20 |
| PASSWORD | VARCHAR2(60) | NN | BCrypt |
| NICKNAME | VARCHAR2(30) | UK, NN | 2~10자 |
| CREATED_AT | DATE | NN, SYSDATE | |

**RESTAURANT (식당)**
| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| RESTAURANT_ID | NUMBER | PK, SEQ_RESTAURANT | |
| NAME | VARCHAR2(90) | NN | 30자 |
| CATEGORY | VARCHAR2(20) | NN, CHECK | 카테고리 코드 |
| MAP_URL | VARCHAR2(500) | UK, NN | 네이버 지도 URL |
| ADDRESS | VARCHAR2(300) | NULL | 네이버 API |
| LAT | NUMBER(10,7) | NULL | 위도 |
| LNG | NUMBER(10,7) | NULL | 경도 |
| MEMBER_ID | NUMBER | FK, NULL | 등록자, 구내식당 초기 데이터는 NULL (MEMBER 0..1 : N) |
| CREATED_AT | DATE | NN, SYSDATE | |

**REVIEW (리뷰)**
| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| REVIEW_ID | NUMBER | PK, SEQ_REVIEW | |
| RESTAURANT_ID | NUMBER | FK, NN | |
| MEMBER_ID | NUMBER | FK, NN | 작성자 |
| CONTENT | VARCHAR2(300) | NN | 100자 |
| IMAGE_PATH | VARCHAR2(300) | NULL | 사진 경로 |
| CREATED_AT | DATE | NN, SYSDATE | |

**TAG (리뷰 태그, 초기 데이터 13건)**
| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| TAG_ID | NUMBER | PK | 1~13 고정 |
| TAG_NAME | VARCHAR2(30) | UK, NN | '#' 없이 |
| TAG_CATEGORY | VARCHAR2(20) | NN, CHECK | MEAL_TYPE/GROUP_TYPE/PRICE/WAITING/ACCESSIBILITY |

**REVIEW_TAG (리뷰-태그 매핑)**
| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| REVIEW_TAG_ID | NUMBER | PK, SEQ_REVIEW_TAG | |
| REVIEW_ID | NUMBER | FK, NN | 리뷰 삭제 시 CASCADE |
| TAG_ID | NUMBER | FK, NN | |
- UK_REVIEW_TAG (REVIEW_ID, TAG_ID)

**INTEREST (관심)**
| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| INTEREST_ID | NUMBER | PK, SEQ_INTEREST | |
| RESTAURANT_ID | NUMBER | FK, NN | |
| MEMBER_ID | NUMBER | FK, NN | |
| CREATED_AT | DATE | NN, SYSDATE | |
- UK_INTEREST (MEMBER_ID, RESTAURANT_ID), 해제 시 행 삭제

**MEAL_PARTY (식사팟)**
| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| PARTY_ID | NUMBER | PK, SEQ_MEAL_PARTY | |
| RESTAURANT_ID | NUMBER | FK, NN | |
| HOST_ID | NUMBER | FK, NN | 방장 → MEMBER |
| PARTY_DATE | DATE | NN | 식사 날짜(오늘~14일 후) |
| MEAL_TYPE | VARCHAR2(10) | NN, CHECK | LUNCH/DINNER |
| MEAL_TIME | VARCHAR2(5) | NN | 예정 시간 |
| CAPACITY | NUMBER(2) | NN, 기본 4, CHECK 2~12 | 방장 포함 |
| STATUS | VARCHAR2(20) | NN, 기본 'RECRUITING', CHECK | RECRUITING/SCHEDULED/FULL/CLOSED/FINISHED/DELETED |
| MEMO | VARCHAR2(150) | NULL | 한마디 50자 |
| CONFIRMED_TIME | VARCHAR2(5) | NULL | 방장 확정 시간 |
| MEET_PLACE | VARCHAR2(90) | NULL | 30자 |
| CONFIRM_MEMO | VARCHAR2(150) | NULL | 50자 |
| CREATED_AT | DATE | NN, SYSDATE | |
- CLOSED_BY_HOST 컬럼은 삭제됨 (FULL/CLOSED 상태값으로 구분)
- 소프트 삭제: 모든 조회·집계에 `STATUS <> 'DELETED'` 필수

**PARTY_MEMBER (식사팟 참여자, 따봉 포함)**
| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| PARTY_MEMBER_ID | NUMBER | PK, SEQ_PARTY_MEMBER | |
| PARTY_ID | NUMBER | FK, NN | |
| MEMBER_ID | NUMBER | FK, NN | |
| CONFIRMED_YN | CHAR(1) | NN, 기본 'N' | 참여 확정(2차). 확정 취소 시 N |
| THUMBS_UP_YN | CHAR(1) | NN, 기본 'N' | 따봉 완료 |
| JOINED_AT | DATE | NN, SYSDATE | 참여하기(1차) 일시 |
- UK_PARTY_MEMBER (PARTY_ID, MEMBER_ID). 방장 여부는 MEAL_PARTY.HOST_ID로 판단
- 나가기 시 행 삭제 (대화는 PARTY_CHAT이 MEMBER를 직접 참조하므로 유지)
- THUMBS_UP 테이블은 삭제되어 이 테이블로 통합됨

**PARTY_CHAT (식사팟 대화)**
| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| CHAT_ID | NUMBER | PK, SEQ_PARTY_CHAT | 폴링 afterId 기준 |
| PARTY_ID | NUMBER | FK, NN | |
| MEMBER_ID | NUMBER | FK, NULL | 시스템 메시지는 NULL |
| CONTENT | VARCHAR2(600) | NN | 200자 |
| MSG_TYPE | VARCHAR2(10) | NN, 기본 'TEXT' | TEXT/SYSTEM |
| CREATED_AT | DATE | NN, SYSDATE | |

**CAFETERIA_MENU (구내식당 메뉴)**
| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| MENU_ID | NUMBER | PK, SEQ_CAFETERIA_MENU | |
| MENU_DATE | DATE | NN | |
| MEAL_TYPE | VARCHAR2(10) | NN, CHECK | LUNCH/DINNER |
| MENU_NAME | VARCHAR2(150) | NN | 메뉴 1개 = 1행 |
| CREATED_AT | DATE | NN, SYSDATE | 수집일시 |
- UK_CAFETERIA_MENU (MENU_DATE, MEAL_TYPE, MENU_NAME)

**SUGGESTION (식당 정보 수정 제안)**
| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| SUGGESTION_ID | NUMBER | PK, SEQ_SUGGESTION | |
| RESTAURANT_ID | NUMBER | FK, NN | |
| MEMBER_ID | NUMBER | FK, NN | |
| CONTENT | VARCHAR2(1500) | NN | 500자 |
| CREATED_AT | DATE | NN, SYSDATE | |

### SEQUENCE (10개)

`SEQ_MEMBER`, `SEQ_RESTAURANT`, `SEQ_REVIEW`, `SEQ_REVIEW_TAG`, `SEQ_INTEREST`, `SEQ_MEAL_PARTY`, `SEQ_PARTY_MEMBER`, `SEQ_PARTY_CHAT`, `SEQ_CAFETERIA_MENU`, `SEQ_SUGGESTION`

### FK 삭제 규칙

- REVIEW_TAG → REVIEW: CASCADE
- 그 외: 제한(기본). 식사팟은 소프트 삭제라 PARTY_MEMBER·PARTY_CHAT에 CASCADE 불필요

### 권장 인덱스

| 인덱스 | 컬럼 | 용도 |
| --- | --- | --- |
| IX_REVIEW_RESTAURANT | REVIEW(RESTAURANT_ID, CREATED_AT) | 식당별 리뷰 페이징 |
| IX_MEAL_PARTY_RESTAURANT | MEAL_PARTY(RESTAURANT_ID, PARTY_DATE) | 30일 따봉 집계 |
| IX_MEAL_PARTY_DATE | MEAL_PARTY(PARTY_DATE, MEAL_TYPE) | 날짜별 식사팟 목록 |
| IX_PARTY_CHAT_PARTY | PARTY_CHAT(PARTY_ID, CHAT_ID) | 대화 폴링 |
| IX_PARTY_MEMBER_MEMBER | PARTY_MEMBER(MEMBER_ID) | 내가 참여한 식사팟 |

---

## 9. 역할 분담 (담당자 배정 전)

| 구분 | A: 식사팟 | B: 식당 | C: 회원·메뉴·공통 |
| --- | --- | --- | --- |
| 기능 | F-06, F-13, F-14, F-15, F-16 | F-03, F-04, F-07, F-08(관심), F-09, F-10, F-17 | F-01, F-02, F-05, F-08(랭킹), F-11, F-12 |
| 공수 | 약 46h | 약 43h | 약 42h + 공통 조율 |
| API | 16개 (`/api/parties/**`) | 11개 | 15개 |
| 화면 | S-07, S-08, S-12 + 메인 따봉 배너·식사팟 카드(부품) | S-04, S-05, S-06 | S-01(조립), S-02, S-03, S-09, S-10, S-11 |
| 어려운 일 | 상태 전이, 동시 참여, 폴링, 스케줄러 | 네이버 API, 사진 업로드, 태그 집계 | 크롤링, 세션 로그인, 메인 조립 |
| 테이블 | MEAL_PARTY, PARTY_MEMBER, PARTY_CHAT | RESTAURANT, REVIEW, TAG, REVIEW_TAG, INTEREST, SUGGESTION | MEMBER, CAFETERIA_MENU |
| 뼈대 작업 | DDL·시퀀스·더미데이터 SQL | 프론트 뼈대(토큰, 헤더, fetch, 목업) | Spring 뼈대 + common 공통 코드 |

규칙
1. 메인(S-01)은 C가 틀, A는 식사팟 카드·따봉 배너를 부품으로 전달
2. 중간 점검 11/23: 3h 이상 밀리면 이동 (룰렛 C→B, 지도 B→C, 초대 링크 A→C)
3. 리뷰 짝: A→B, B→C, C→A

---

## 10. 팀 컨벤션 (초안, 팀 확정 필요)

### 패키지 (도메인별)

```
com.sseukbap
├─ common/      공통 응답, 예외, 설정, 인터셉터 (C)
├─ auth/        회원가입·로그인 (C)
├─ member/      회원, 마이페이지 (C)
├─ cafeteria/   구내식당 메뉴·크롤링 (C)
├─ restaurant/  식당·관심 (B), 랭킹·룰렛 (C)
├─ review/      리뷰·태그 (B)
└─ party/       식사팟·대화·따봉·스케줄러 (A)

각 도메인: controller/ service/ repository/ dto/ entity/
```

### 이름 규칙

| 대상 | 규칙 | 예시 |
| --- | --- | --- |
| 클래스 | PascalCase | `PartyService` |
| 메서드·변수 | camelCase | `joinParty` |
| 상수·enum | UPPER_SNAKE | `RECRUITING` |
| 엔티티 | 테이블명 PascalCase | `MEAL_PARTY` → `MealParty` |
| 요청 DTO | 도메인+동작+Request | `PartyCreateRequest` |
| 응답 DTO | 도메인+내용+Response | `PartyDetailResponse` |
| 서비스 메서드 | create/get/getList/update/delete + 대상 | `getPartyList` |
| boolean | is/has | `isHost` |

### API·예외

- Controller는 `ApiResponse<T>`로 감싸서 반환
- 오류는 `throw new BusinessException(ErrorCode.XXX)`, 처리는 `GlobalExceptionHandler` 한 곳
- `ErrorCode`의 이름·HTTP 상태·메시지는 API 명세서 오류 표와 동일하게

### JPA

- `@Setter` 금지, 값 변경은 엔티티 메서드로 (`party.close()`)
- `@NoArgsConstructor(access = AccessLevel.PROTECTED)`
- `@ManyToOne(fetch = FetchType.LAZY)`
- enum은 `@Enumerated(EnumType.STRING)`
- Y/N 컬럼은 `boolean` + `@Convert(converter = org.hibernate.type.YesNoConverter.class)`
- PK는 ERD 시퀀스, `allocationSize = 1`

### 프론트

| 대상 | 규칙 | 예시 |
| --- | --- | --- |
| HTML | kebab-case | `party-detail.html` |
| 화면 JS | `js/pages/화면.js` | `js/pages/party-detail.js` |
| API 호출 | `js/api/도메인.js`에만 | `partyApi.join(31)` |
| CSS 클래스 | kebab-case (BEM 느낌) | `party-card__title` |
| 색·간격 | `tokens.css` 변수만 | `var(--color-primary)` |

- fetch 공통 함수: 공통 응답 구조 해석, 401이면 로그인 화면으로(복귀 주소 저장), error 코드 → 문구
- 백엔드 완성 전에는 `USE_MOCK` 플래그로 목업 데이터 사용 (목업 = API 명세서 응답 예시)

### Git

- 브랜치: `main`(발표) ← `dev`(통합) ← `feature/도메인-기능` (예: `feature/party-join`)
- 커밋: `타입: 한글 내용` — feat / fix / refactor / style / docs / test / chore
- PR: feature → dev, 리뷰 짝 1명 승인 후 **Squash and merge**
- 매일 작업 전 dev pull
- 공통 파일(`common/`, `tokens.css`, `js/api/client.js`)은 담당자(C/B)만 수정, 나머지는 PR로 요청

### 포맷·보안

- IntelliJ 자동 정렬, 들여쓰기 4칸(html/css/js/yml 2칸), UTF-8, LF (`.editorconfig`)
- `application-local.yml`, `/uploads/` 는 `.gitignore`

---

## 11. Git 뼈대 만들기 가이드 (C 담당)

1. **GitHub 저장소** `sseukbap` 생성 (README·.gitignore 체크 해제), 팀원 2명 Collaborator 초대
   - ⚠️ 무료 계정 Private 저장소는 브랜치 보호 불가 → Public 추천 (비밀정보는 Git에 없음)
2. **IntelliJ 프로젝트 생성**: Group `com.sseukbap`, Artifact `sseukbap`, Gradle-Groovy, Java 17, Boot 4.1.2, 의존성 Web·JPA·Validation·Lombok → `build.gradle`은 3장 내용으로
3. **패키지 뼈대**: 10장 패키지 구조대로 폴더 생성, 빈 폴더마다 `.gitkeep`
   - `src/main/resources/static/{css, js/api, js/pages, images}`, `docs/sql/`
4. **설정 파일**: `application.yml`, `application-local.yml.example`(견본, Git 포함), `.gitignore`에 `application-local.yml`·`/uploads/` 추가, `.editorconfig`
5. **문서**: `README.md`(실행 방법·스택·브랜치), `.github/pull_request_template.md`(작업 내용 / 관련 기능·API / 확인 체크리스트 / 리뷰어에게)
6. **첫 업로드**
   ```bash
   git init
   git add .            # 전에 git status로 application-local.yml 제외 확인
   git commit -m "chore: 프로젝트 초기 설정"
   git branch -M main
   git remote add origin https://github.com/<계정>/sseukbap.git
   git push -u origin main
   git checkout -b dev
   git push -u origin dev
   ```
7. **브랜치 보호**(Public): main·dev에 PR 필수, 승인 1명, force push 금지. Pull Requests 설정에서 Squash merge만 허용
8. **팀원 받기**: clone → `git checkout dev` → `application-local.yml.example` 복사해 `application-local.yml` 작성 → 실행 → `git checkout -b feature/...`

### 다음 작업: common 공통 코드 (C)

- `common/response/ApiResponse<T>` — statusCode, timestamp, path, message, data, error
- `common/exception/ErrorCode` (enum: HttpStatus, code, message) — 6장 오류 코드 반영
- `common/exception/BusinessException`
- `common/exception/GlobalExceptionHandler` (`@RestControllerAdvice`) — BusinessException, 검증 오류(400), 그 외(500)
- `common/interceptor/LoginCheckInterceptor` + `common/config/WebConfig` — `/api/**` 중 회원가입·로그인·중복 확인 제외하고 세션 확인, 미로그인 401
- (선택) 세션 로그인 회원을 꺼내는 헬퍼 또는 `@LoginMember` 리졸버

---

## 12. 주요 결정 기록

| 날짜 | 결정 |
| --- | --- |
| 11/16 | 비회원 제거, 전 화면 로그인 필수. 추천 → '관심'으로 변경(랭킹 미반영), 랭킹은 따봉 기준 |
| 11/17 | 식사팟 정원 2~12(기본 4), 시간대 점심 11:00~13:30 / 저녁 17:00~19:30 |
| 11/17 | 리뷰 태그 13개(5개 분류)로 확정, 태그 조회 API 삭제(FE 상수) |
| 11/17 | 리뷰 중복 작성 허용, 네이버 API로 장소·좌표 조회 |
| 11/17 | URI 분리(create/list, register/list), 룰렛 후보 조회 API 추가, 리뷰 목록 페이징 |
| 11/17 | 닉네임 중복 검사 추가 (API·DB UK·화면) |
| 11/18 | 복합 PK 제거 → 모든 테이블 단일 대리키 + UK (JPA 기준) |
| 11/18 | THUMBS_UP 테이블 삭제 → PARTY_MEMBER.THUMBS_UP_YN, 30일 집계는 PARTY_DATE 기준 |
| 11/18 | CLOSED_BY_HOST 삭제 → 상태값 FULL(자동)/CLOSED(방장) 분리, DELETED 소프트 삭제 추가 |
| 11/18 | thumbs·roulette 도메인 정리 → 랭킹·룰렛은 restaurants, 따봉 요청 대상은 parties/my 하위로 이동 |
| 11/18 | 기술 스택 확정: Boot 4.1.2 + Hibernate 7 + OracleLegacyDialect + ojdbc8 19.33 (연결 테스트 통과) |
| 11/18 | 역할 분담안(A 식사팟 / B 식당 / C 회원·메뉴·공통) 작성, 컨벤션 초안 작성 |
| 11/19 | 구내식당은 초기 데이터로만 제공, 식당 등록 시 CAFETERIA 선택 불가 (룰렛 카테고리 반영은 C와 확인) |
| 11/19 | check-map-url에 name 추가: 네이버에 URL·장소 ID 조회 공식 API가 없어 식당 이름으로 지역 검색, 사용자가 주소 확인 후 등록. map_url은 장소 ID 기준 표준 URL(`https://map.naver.com/p/entry/place/{id}`)로 저장·중복 판단 |
| 11/19 | 네이버 검색 API 발급처 변경: 개발자센터 → 네이버 클라우드 NAVER API HUB (키 이름 `naver.client-id/secret` 유지). 지도 키(Maps)는 서버 yml에 넣지 않음 |
| 11/20 | 식사팟 날짜 확대: 당일만 → 오늘 ~ 14일 후(2주) (팀 합의, 처음 7일 → 같은 날 14일로 조정). 날짜는 달력에서 고른다(만들기·목록). 생성 API에 partyDate(선택, 기본 오늘), 목록 API에 date(선택, 기본 오늘) 추가. `/api/parties/my/today`는 URI 유지(메인 호환)하고 오늘~14일 후 중 가까운 2개 + 응답에 partyDate 추가. DB·스케줄러·대화 삭제·따봉·랭킹은 변경 없음 |

### 미결 사항

- A/B/C 담당자 배정
- 컨벤션 팀 확정
- 식당 정보 수정 제안 처리 방식 (현재 저장만)
- 구내식당 크롤링 가능 여부 (불가 시 관리자 수동 입력)
- 점심/저녁 기본 탭 전환 시각(현재 15:00), 구내식당 미운영일 문구
- 와이어프레임 상태 뱃지에 '정원 마감' 반영 여부
- Mac 팀원의 Oracle 사용 방식

---

## 13. 참고 문서 (노션 — 원본)

| 문서 | 링크 |
| --- | --- |
| 서비스 기획 | https://app.notion.com/p/4173b5c5989f8097932de4eb6960384b |
| 화면설계서 | https://app.notion.com/p/41a3b5c5989f8118891de054666b795a |
| 기능 명세서 | https://app.notion.com/p/e683b5c5989f837b9b46811b6ec2b89b |
| API 명세서 | https://app.notion.com/p/7983b5c5989f831d86fe81a0310e8296 |
| ERD | https://app.notion.com/p/41b3b5c5989f803183acc0ef52322a9f |
| 역할 분담 | https://app.notion.com/p/41c3b5c5989f8122a380df292a655657 |
| 와이어프레임 (Claude 캔버스) | https://claude.ai/artifact/XUpDBpvV6QRXDe5f81DxbP |

> WBS(노션)는 사용자가 직접 관리 — 수정하지 말 것.
