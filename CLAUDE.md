# CLAUDE.md — 슥밥 팀 공용

## 1. 프로젝트 소개

슥밥: 식사 메뉴 추천 및 식사팟 모집 서비스 (삼성 SDS SCSA 팀 프로젝트)
- 기간: 2026-11-17 ~ 11-26 (평일 8일), 팀 3명
- 사용자: 사원, 전 화면 로그인 필수

전체 컨텍스트(기능, API 42개, ERD, 역할 분담, 결정 기록): @docs/PROJECT_CONTEXT.md

> 원본은 노션 문서다. 이 파일이나 PROJECT_CONTEXT와 노션이 다르면 노션을 따르고, 차이를 사용자에게 알린다.

---

## 2. Claude 작업 원칙

- 코드 작성·파일 수정 전에 **계획(만들/바꿀 파일과 이유)을 먼저 보여 주고 승인**받는다.
- 불명확한 점은 추측하지 말고 질문한다.
- **git 명령(commit, push, merge, checkout 등)은 실행하지 않는다.** 사람이 직접 한다.
- 팀원은 입문자다. 작업 후 무엇을 왜 했는지, 어떻게 확인하는지 짧게 설명한다.
- 한 번에 한 기능(브랜치 하나 분량)만 작업한다.

---

## 3. 기술 스택 & 실행

| 구분 | 버전 |
| --- | --- |
| Java | 17 |
| Spring Boot | 4.1.2 (Hibernate 7) |
| 빌드 | Gradle (Groovy DSL) |
| DB | Oracle XE 11g |
| JDBC | ojdbc8 19.33.0.0 (버전 고정, 바꾸지 말 것) |
| ORM | Spring Data JPA + `org.hibernate.community.dialect.OracleLegacyDialect` |
| 비밀번호 | spring-security-crypto (BCrypt만, Spring Security 전체 사용 안 함) |
| 크롤링 | Jsoup |
| 외부 API | 네이버 API (장소 조회·좌표, 지도) |
| 프론트 | HTML/CSS/JS, `src/main/resources/static/` (프레임워크 없음) |

실행 준비
1. `docs/sql/`의 DDL·초기 데이터를 Oracle에 실행
2. `application-local.yml.example`을 복사해 `application-local.yml` 작성 (DB 계정, 네이버 키)
3. `SseukbapApplication` 실행 → http://localhost:8080

명령어
- 빌드: `./gradlew build` (Windows: `gradlew.bat build`)
- 테스트: `./gradlew test`

의존성 추가·버전 변경은 팀 합의가 필요하다. 필요하면 제안만 한다.

---

## 4. 폴더 구조와 담당

```
com.sseukbap
├─ common/      공통 응답·예외·설정·인터셉터         C
├─ auth/        회원가입·로그인                      C
├─ member/      회원·마이페이지                      C
├─ cafeteria/   구내식당 메뉴·크롤링                  C
├─ restaurant/  식당·관심 B / 랭킹·룰렛 C
├─ review/      리뷰·태그                            B
└─ party/       식사팟·대화·따봉·스케줄러              A

각 도메인: controller/ service/ repository/ dto/ entity/
```

프론트
```
static/
├─ css/        tokens.css, base.css (B 담당 공통) + 화면별 css
├─ js/api/     client.js (B 담당 공통) + 도메인별 api 파일
├─ js/pages/   화면별 js
└─ *.html
```

**수정 금지 영역**: 대화 중 사용자가 밝힌 본인 담당 외의 도메인, `common/`(C), `tokens.css`·`base.css`·`js/api/client.js`(B).
필요한 변경이 있으면 직접 고치지 말고 **"○에게 요청할 내용"으로 정리**한다.
담당을 모르면 먼저 물어본다.

---

## 5. 코드 컨벤션

이름
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

계층
- Controller → Service → Repository. Controller에 비즈니스 로직을 넣지 않는다.
- 쓰기 작업은 Service 메서드에 `@Transactional`.
- 요청 값 검증은 DTO에 Bean Validation(`@NotBlank`, `@Size` 등) + `@Valid`.

JPA
- `@Setter` 금지. 값 변경은 엔티티 메서드로 (`party.close()`).
- `@NoArgsConstructor(access = AccessLevel.PROTECTED)`
- `@ManyToOne(fetch = FetchType.LAZY)`
- enum: `@Enumerated(EnumType.STRING)`
- Y/N 컬럼: `boolean` + `@Convert(converter = org.hibernate.type.YesNoConverter.class)`
- PK: `@GeneratedValue(strategy = GenerationType.SEQUENCE)` + `@SequenceGenerator(sequenceName = "SEQ_테이블명", allocationSize = 1)`
- 엔티티를 Controller 응답으로 직접 반환하지 않는다. 항상 Response DTO로 변환.

응답·예외
- 모든 Controller는 `ApiResponse<T>`로 감싸서 반환한다.
- 오류는 `throw new BusinessException(ErrorCode.XXX)`. 처리는 `GlobalExceptionHandler`에서만.
- `ErrorCode`의 이름·HTTP 상태·메시지는 API 명세서 오류 표와 같게.

---

## 6. API·DB 기준

- **API 명세서가 기준**이다. URI, 요청·응답 필드명, 처리 순서, 오류 코드를 명세와 똑같이 맞춘다. 명세에 없는 필드·API를 임의로 추가하지 않는다.
- 공통 응답: `{statusCode, timestamp, path, message, data, error}`
- 인증: 세션(JSESSIONID). 회원가입·아이디/닉네임 중복 확인·로그인 외 모든 API는 로그인 필요, 미로그인 401.
- 날짜 `YYYY-MM-DD`, 시간 `HH:mm`, 일시 ISO-8601.
- **테이블은 `docs/sql/` DDL로 관리**한다 (`ddl-auto: none`). 테이블·컬럼이 필요하면 엔티티로 만들지 말고 DDL 변경을 제안한다.

자주 틀리는 규칙
- 식사팟 상태: `RECRUITING` / `SCHEDULED` / `FULL`(정원 도달·자동) / `CLOSED`(방장 마감) / `FINISHED` / `DELETED`
- 식사팟 삭제는 **소프트 삭제**(`STATUS = 'DELETED'`). 모든 조회·집계에 `STATUS <> 'DELETED'` 조건 필수. 식사팟 API는 DELETED면 404 `PARTY_DELETED`.
- 따봉은 `PARTY_MEMBER.THUMBS_UP_YN`에 저장 (THUMBS_UP 테이블 없음).
- 최근 30일 따봉 수 = `THUMBS_UP_YN='Y'`인 PARTY_MEMBER ⋈ MEAL_PARTY, `PARTY_DATE`가 오늘-29 ~ 오늘, `RESTAURANT_ID`별 COUNT.
- 기준 시간 = `CONFIRMED_TIME`, 없으면 `MEAL_TIME`. 확정 취소·확정자 나가기는 기준 시간 30분 전까지.
- 참여·나가기·일정·상태 변경·따봉은 `SELECT ... FOR UPDATE`(비관적 락)로 동시성 처리.
- 복합 PK 없음. 연결 테이블(INTEREST, PARTY_MEMBER, REVIEW_TAG)은 단일 PK + UK.
- 리뷰 태그 13개는 고정값(TAG_ID 1~13). 태그 조회 API 없음, FE 상수.
- 페이징은 리뷰 목록만: 요청 `page`(1부터)·`size`(기본 10, 최대 50), 응답 `{page, size, totalCount, hasNext, items}`.

---

## 7. 프론트 규칙

| 대상 | 규칙 | 예시 |
| --- | --- | --- |
| HTML | kebab-case | `party-detail.html` |
| 화면 JS | `js/pages/화면.js` | `js/pages/party-detail.js` |
| API 호출 | `js/api/도메인.js`에서만 (화면 JS에서 fetch 직접 사용 금지) | `partyApi.join(31)` |
| CSS 클래스 | kebab-case | `party-card__title` |
| 색·간격·글꼴 | `tokens.css` 변수만 사용 | `var(--color-primary)` |

- `js/api/client.js`가 공통 응답 해석, 401 처리(현재 주소 저장 후 로그인 화면 이동), 오류 문구 변환을 담당한다.
- 백엔드 완성 전에는 `USE_MOCK` 플래그로 목업 사용. 목업 데이터는 API 명세서 응답 예시와 똑같은 구조.
- 디자인: 보라·파랑 그라데이션, 이모지 아이콘, 의미 없는 히어로 배너 같은 흔한 AI 스타일을 피한다. 빈 상태·오류·로딩 화면도 만든다.

---

## 8. Git 규칙 (참고용, Claude는 git 명령 실행 안 함)

- 브랜치: `main`(발표) ← `dev`(통합) ← `feature/도메인-기능` (예: `feature/party-join`)
- 커밋: `타입: 한글 내용` — feat / fix / refactor / style / docs / test / chore
- PR: feature → dev, 리뷰 짝 1명 승인 후 Squash and merge (A→B, B→C, C→A)
- 작업이 끝나면 Claude는 **추천 커밋 메시지**와 **PR 본문 초안**(`.github/pull_request_template.md` 형식)을 제시한다.

---

## 9. 보안·금지 사항

- DB 비밀번호, 네이버 API 키 등 비밀값을 코드·설정 파일·커밋에 넣지 않는다. `application-local.yml`에서만 읽는다.
- `application-local.yml`, `/uploads/`는 Git에 올리지 않는다.
- 노션 문서와 WBS는 Claude가 수정하지 않는다.
- 다른 팀원의 코드를 지우거나 크게 고치지 않는다. 필요하면 요청 목록으로 정리한다.

---

## 10. 문서 위치

| 문서 | 위치 |
| --- | --- |
| 전체 컨텍스트 | `docs/PROJECT_CONTEXT.md` |
| DDL·초기 데이터·더미데이터 | `docs/sql/` |
| 서비스 기획 (노션) | https://app.notion.com/p/4173b5c5989f8097932de4eb6960384b |
| 기능 명세서 (노션) | https://app.notion.com/p/e683b5c5989f837b9b46811b6ec2b89b |
| API 명세서 (노션) | https://app.notion.com/p/7983b5c5989f831d86fe81a0310e8296 |
| ERD (노션) | https://app.notion.com/p/41b3b5c5989f803183acc0ef52322a9f |
| 화면설계서 (노션) | https://app.notion.com/p/41a3b5c5989f8118891de054666b795a |
| 역할 분담 (노션) | https://app.notion.com/p/41c3b5c5989f8122a380df292a655657 |
| 와이어프레임 | https://claude.ai/artifact/XUpDBpvV6QRXDe5f81DxbP |

API별 요청·응답 상세는 노션 API 명세서 개별 페이지에 있다. 구현할 때는 사용자가 해당 페이지 내용을 붙여 넣어 준다.
