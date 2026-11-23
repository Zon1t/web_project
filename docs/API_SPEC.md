# 슥밥 API 명세서 (Claude Code 전달용)

> 생성일: 2026-11-19 · 노션 API 명세서 전체(공통 규칙 + 42개 API)를 옮긴 문서
> **원본은 노션**이다. 노션이 수정되면 이 문서도 다시 만들어야 한다.
> 노션: https://app.notion.com/p/7983b5c5989f831d86fe81a0310e8296
>
> 사용 방법: 저장소의 `docs/API_SPEC.md`로 두고, 구현 요청 시 "docs/API_SPEC.md의 ○○ API를 명세 그대로 구현해 줘"라고 지시한다.
> 각 API 응답 예시는 공통 응답 구조의 `data` 부분만 적는다.

## 목차

- [공통 규칙](#공통-규칙)
- [1. auth — 인증 (C)](#1-auth--인증-c)
- [2. users/me — 마이페이지 (C)](#2-usersme--마이페이지-c)
- [3. cafeteria — 구내식당 (C)](#3-cafeteria--구내식당-c)
- [4. restaurants — 랭킹·룰렛 (C)](#4-restaurants--랭킹룰렛-c)
- [5. restaurants·reviews — 식당·리뷰·관심 (B)](#5-restaurantsreviews--식당리뷰관심-b)
- [6. parties — 식사팟·대화·따봉 (A)](#6-parties--식사팟대화따봉-a)

---

## 공통 규칙

| 항목 | 내용 |
| --- | --- |
| 구조 | REST API + JS fetch. 서버는 JSON만 응답하고 화면은 JS로 렌더링 |
| Base URL | `/api` |
| 인증 | 세션 방식. 로그인 성공 시 `JSESSIONID` 쿠키 발급, 이후 요청에 쿠키 자동 포함. 회원가입·아이디 중복 확인·닉네임 중복 확인·로그인을 제외한 모든 API는 로그인 필요 |
| 요청 형식 | `Content-Type: application/json` (파일 첨부가 있는 리뷰 작성만 `multipart/form-data`) |
| 날짜·시간 | 날짜 `YYYY-MM-DD`, 시간 `HH:mm`, 일시 ISO-8601 |
| 끼니 값 | `LUNCH`(점심), `DINNER`(저녁) |
| 식사팟 날짜 | 오늘 ~ 14일 후 (2026-11-20 변경, 기존: 당일만). 목록·생성은 날짜를 고르고, 없으면 오늘 |
| 식사팟 상태 값 | `RECRUITING`(모집중), `SCHEDULED`(일정 확정), `FULL`(정원 마감, 자동), `CLOSED`(모집 마감, 방장), `FINISHED`(식사 완료), `DELETED`(삭제) |

### 공통 응답 구조

모든 API는 아래 구조로 응답한다.

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

### 공통 오류 코드

| HTTP | error | 의미 |
| --- | --- | --- |
| 400 | `INVALID_REQUEST` | 필수값 누락, 형식·길이 오류 |
| 401 | `UNAUTHORIZED` | 로그인하지 않음 또는 세션 만료 (FE는 로그인 화면으로 이동) |
| 403 | `FORBIDDEN` | 권한 없음 (예: 방장 전용 기능, 본인 리뷰가 아님) |
| 404 | `NOT_FOUND` | 대상 데이터 없음 |
| 409 | `CONFLICT` 계열 | 중복, 상태 충돌 (API별 상세 코드 사용) |
| 500 | `INTERNAL_SERVER_ERROR` | 서버 오류 |
| 502 | `MAP_API_ERROR` | 네이버 API 호출 실패 (식당 등록·URL 검증) |

### 공통 오류 응답 예시

```json
{
  "statusCode": 409,
  "timestamp": "2026-11-17T03:00:00Z",
  "path": "/api/parties/31/members",
  "message": "정원이 가득 찼습니다.",
  "data": null,
  "error": "PARTY_FULL"
}
```

### 코드 값

| 구분 | 값 |
| --- | --- |
| 식당 카테고리 | `CAFETERIA`(구내식당), `KOREAN`(한식), `CHINESE`(중식), `JAPANESE`(일식), `WESTERN`(양식), `SNACK`(분식), `CAFE`(카페), `ETC`(기타). `CAFE`는 2026-11-23 추가: 룰렛·랭킹 '전체'에서 제외, 식당 목록·등록·식사팟에서는 다른 카테고리와 같음 |
| 끼니 | `LUNCH`, `DINNER` |
| 식사 시간 범위 | LUNCH 11:00~13:30, DINNER 17:00~19:30, 30분 단위 |
| 식사팟 상태 | `RECRUITING`, `SCHEDULED`, `FULL`, `CLOSED`, `FINISHED`, `DELETED` (DELETED는 응답에 나오지 않음) |
| 내 역할 (식사팟) | `HOST`(방장), `MEMBER`(참여자), `NONE`(미참여) |
| 대화 메시지 유형 | `TEXT`, `SYSTEM` |
| 리뷰 태그 (tagId) | 18개 고정값. 아래 '리뷰 태그' 표 참고 (FE에 상수로 정의) |

### 리뷰 태그

> 2026-11-23 변경: 14~18 추가 (가격 2개, 시간·대기 2개, 접근성 1개). 기존 DB는 docs/sql/06_add_tags.sql 실행

| tagId | 태그 | 분류 (category) | 설명 |
| --- | --- | --- | --- |
| 1 | 점심추천 | MEAL_TYPE (식사 상황) | 점심 식사로 추천하는 식당 |
| 2 | 저녁추천 | MEAL_TYPE | 저녁 식사로 추천하는 식당 |
| 3 | 퇴근후식사 | MEAL_TYPE | 퇴근 후 방문하기 좋은 식당 |
| 4 | 해장추천 | MEAL_TYPE | 해장하기 좋은 메뉴가 있는 식당 |
| 5 | 혼밥가능 | GROUP_TYPE (인원 및 모임) | 혼자 식사에 적합 |
| 6 | 단체가능 | GROUP_TYPE | 단체 식사에 적합 |
| 7 | 소규모모임 | GROUP_TYPE | 소규모 인원 모임에 적합 |
| 8 | 가성비 | PRICE (가격 및 가성비) | 가격 대비 만족도가 높은 식당 |
| 9 | 웨이팅많음 | WAITING (식사 시간 및 대기) | 방문 시 대기가 자주 발생하는 식당 |
| 10 | 도보5분이내 | ACCESSIBILITY (접근성 및 편의시설) | 회사에서 도보 5분 이내 |
| 11 | 주차가능 | ACCESSIBILITY | 주차 공간 이용 가능 |
| 12 | 포장가능 | ACCESSIBILITY | 음식 포장 가능 |
| 13 | 단체석 | ACCESSIBILITY | 단체 좌석 보유 |
| 14 | 만원이하 | PRICE | 1인 1만 원 이하로 식사 가능 |
| 15 | 양많음 | PRICE | 양이 넉넉함 |
| 16 | 빨리나와요 | WAITING | 주문 후 음식이 빨리 나옴 |
| 17 | 예약가능 | WAITING | 예약 가능 |
| 18 | 도보15분이상 | ACCESSIBILITY | 회사에서 도보 15분 이상 |

- 분류와 관계없이 여러 개 선택 가능, 분류별 선택 개수 제한 없음
- 식당 상세의 TOP 3는 분류와 관계없이 태그 전체(18개) 중 선택 횟수 기준
- 리뷰 작성 화면은 분류별로 묶어 표시
- 태그 이름에는 '#'을 붙이지 않고 화면에서 붙여 표시
- 태그 분류 값 MEAL_TYPE은 식사팟의 끼니 값(mealType: LUNCH/DINNER)과 별개

### 페이징

- 요청: `page`(1부터 시작, 기본 1), `size`(기본 10, 최대 50)
- 응답: `{ "page": 1, "size": 10, "totalCount": 23, "hasNext": true, "items": [ ] }`
- 현재 적용 API: 리뷰 목록 조회

### 따봉 집계

- 따봉은 PARTY_MEMBER.thumbs_up_yn(Y/N)으로 저장한다. 참여 확정자 1명당 식사팟 1건에 1회
- 식당별 최근 30일 따봉 수 = PARTY_MEMBER(thumbs_up_yn = Y)와 MEAL_PARTY를 조인해, MEAL_PARTY.party_date가 오늘 - 29일 ~ 오늘인 행을 restaurant_id별로 센 값
- 따봉한 날이 아니라 식사한 날(party_date) 기준. 따봉 요청은 식사 후 7일 이내만 뜨므로 차이는 미미함
- 적용 API: 따봉 랭킹, 식당 목록·상세, 따봉 등록, 룰렛 실행

### 식사팟 상태 전이

| 현재 상태 | 이벤트 | 다음 상태 |
| --- | --- | --- |
| (없음) | 식사팟 생성 | RECRUITING |
| RECRUITING | 방장 일정 확정 | SCHEDULED |
| FULL, CLOSED | 방장 일정 확정·수정 | 상태 유지 (일정만 저장) |
| RECRUITING, SCHEDULED | 참여로 정원 도달 | FULL (자동 마감) |
| RECRUITING, SCHEDULED, FULL | 방장 모집 마감 | CLOSED (방장 마감) |
| FULL | 나가기로 정원 미만 | 일정 있으면 SCHEDULED, 없으면 RECRUITING |
| CLOSED | 나가기로 정원 미만 | CLOSED 유지 |
| CLOSED | 방장 모집 재개 | 인원 = 정원이면 FULL, 아니면 일정 있으면 SCHEDULED, 없으면 RECRUITING |
| RECRUITING, SCHEDULED, FULL, CLOSED | 기준 시간 경과 (스케줄러) | FINISHED |
| RECRUITING, SCHEDULED, FULL, CLOSED | 방장 삭제 또는 방장 나가기 | DELETED (소프트 삭제, 행 유지) |

- 기준 시간: `confirmed_time`(방장 확정 시간), 없으면 `meal_time`(생성 시 예정 시간)
- 자동 마감(FULL)과 방장 마감(CLOSED)을 상태값으로 구분한다. (closed_by_host 컬럼은 삭제)
- DELETED는 모든 목록·집계에서 제외하고, 식사팟 API는 404 `PARTY_DELETED`('삭제된 식사팟입니다.')로 응답한다. FINISHED는 삭제할 수 없다
- FINISHED에서는 참여·나가기·확정·일정 변경·마감·삭제·대화 전송 모두 불가 (409 `PARTY_FINISHED`)
- 참여 확정 취소와 확정자의 나가기는 기준 시간 30분 전까지만 가능

### 서버 내부 처리 (스케줄러, API 없음)

| 작업 | 주기 | 내용 |
| --- | --- | --- |
| 식사 완료 처리 | 5분마다 | 기준 시간이 지난 팟을 FINISHED로 변경, 시스템 메시지 '식사가 완료되었습니다' 생성 |
| 대화 기록 삭제 | 매일 00:00 | 전날까지 FINISHED·DELETED된 팟의 PARTY_CHAT 삭제 (팟·참여 이력은 유지) |

### 전체 API 목록 (43개, 11/23 알림 API 추가)

| 담당 | 메서드 | URI | 이름 |
| --- | --- | --- | --- |
| C | GET | `/api/auth/check-id` | 아이디 중복 확인 |
| C | GET | `/api/auth/check-nickname` | 닉네임 중복 확인 |
| C | POST | `/api/auth/signup` | 회원가입 |
| C | POST | `/api/auth/login` | 로그인 |
| C | POST | `/api/auth/logout` | 로그아웃 |
| C | GET | `/api/auth/me` | 로그인 사용자 조회 |
| C | GET | `/api/users/me/summary` | 마이페이지 프로필 조회 |
| C | GET | `/api/users/me/reviews` | 내 리뷰 목록 조회 |
| C | GET | `/api/users/me/parties` | 참여한 식사팟 목록 조회 |
| C | GET | `/api/users/me/restaurants` | 등록한 식당 목록 조회 |
| C | GET | `/api/users/me/interests` | 관심 식당 목록 조회 |
| C | GET | `/api/cafeteria/menus/today` | 오늘의 구내식당 메뉴 조회 |
| C | GET | `/api/restaurants/rankings` | 따봉 랭킹 조회 |
| C | GET | `/api/restaurants/roulette/candidates` | 룰렛 후보 조회 |
| C | POST | `/api/restaurants/roulette/spin` | 메뉴 룰렛 실행 |
| B | GET | `/api/restaurants/autocomplete` | 식당 이름 자동완성 |
| B | GET | `/api/restaurants/check-map-url` | 네이버 지도 URL 검증 |
| B | GET | `/api/restaurants/list` | 식당 목록 조회 (검색·필터·지도) |
| B | POST | `/api/restaurants/register` | 식당 등록 |
| B | GET | `/api/restaurants/{restaurantId}` | 식당 상세 조회 |
| B | POST | `/api/restaurants/{restaurantId}/interest` | 관심 등록 |
| B | DELETE | `/api/restaurants/{restaurantId}/interest` | 관심 해제 |
| B | GET | `/api/restaurants/{restaurantId}/reviews` | 리뷰 목록 조회 |
| B | POST | `/api/restaurants/{restaurantId}/reviews` | 리뷰 작성 |
| B | POST | `/api/restaurants/{restaurantId}/suggestions` | 식당 정보 수정 제안 |
| B | DELETE | `/api/reviews/{reviewId}` | 리뷰 삭제 |
| A | POST | `/api/parties/create` | 식사팟 생성 |
| A | GET | `/api/parties/list` | 식사팟 목록 조회 |
| A | GET | `/api/parties/my/today` | 내가 참여 중인 식사팟 조회 (오늘 ~ 14일 후) |
| A | GET | `/api/parties/my/action-pending` | 내가 할 일이 있는 식사팟 조회 (알림, 11/23 추가) |
| A | GET | `/api/parties/my/thumbs-pending` | 따봉 요청 대상 조회 |
| A | GET | `/api/parties/{partyId}` | 식사팟 상세 조회 |
| A | DELETE | `/api/parties/{partyId}` | 식사팟 삭제 (방장) |
| A | GET | `/api/parties/{partyId}/chats` | 대화 메시지 조회 (폴링) |
| A | POST | `/api/parties/{partyId}/chats` | 대화 메시지 전송 |
| A | GET | `/api/parties/{partyId}/invite-link` | 식사팟 초대 링크 조회 |
| A | POST | `/api/parties/{partyId}/members` | 식사팟 참여하기 (1차) |
| A | DELETE | `/api/parties/{partyId}/members/me` | 식사팟 나가기 |
| A | POST | `/api/parties/{partyId}/members/me/confirm` | 참여 확정 (2차) |
| A | DELETE | `/api/parties/{partyId}/members/me/confirm` | 참여 확정 취소 |
| A | PATCH | `/api/parties/{partyId}/schedule` | 일정 확정·수정 (방장) |
| A | PATCH | `/api/parties/{partyId}/status` | 모집 마감·재개 (방장) |
| A | POST | `/api/parties/{partyId}/thumbs` | 따봉 등록 |

---
## 1. auth — 인증 (C)

### GET /api/auth/check-id — 아이디 중복 확인

**요구사항**: 입력한 아이디를 사용할 수 있는지 반환한다. 로그인 없이 호출할 수 있다. 결과는 회원가입 화면의 입력창 아래에 표시한다.

**Request — Query**

| 이름 | 타입 | 필수 | 규칙 |
| --- | --- | --- | --- |
| loginId | string | O | 영문·숫자 4~20자 |

**시스템 처리**
1. loginId 형식을 검증한다.
2. 같은 loginId의 MEMBER가 있는지 조회한다.
3. 사용 가능 여부를 반환한다.

**Response 200 (data)**
```json
{
  "loginId": "chef01",
  "available": true
}
```

**오류**

| HTTP | error | message | 발생 조건 |
| --- | --- | --- | --- |
| 400 | INVALID_REQUEST | 아이디는 영문·숫자 4~20자로 입력해 주세요. | loginId 누락·형식 오류 |

---

### GET /api/auth/check-nickname — 닉네임 중복 확인

**요구사항**: 입력한 닉네임을 사용할 수 있는지 반환한다. 로그인 없이 호출할 수 있다. 결과는 회원가입 화면의 닉네임 입력창 아래에 표시한다. 비교는 앞뒤 공백을 제거한 값으로 하며, 대소문자는 구분한다(저장값 그대로 비교).

**Request — Query**

| 이름 | 타입 | 필수 | 규칙 |
| --- | --- | --- | --- |
| nickname | string | O | 앞뒤 공백 제거 후 2~10자 |

**시스템 처리**
1. nickname의 앞뒤 공백을 제거하고 길이를 검증한다.
2. 같은 nickname의 MEMBER가 있는지 조회한다.
3. 사용 가능 여부를 반환한다.

**Response 200 (data)**
```json
{
  "nickname": "슥사인",
  "available": true
}
```
- nickname: 앞뒤 공백을 제거한 값

**오류**

| HTTP | error | message | 발생 조건 |
| --- | --- | --- | --- |
| 400 | INVALID_REQUEST | 닉네임은 2~10자로 입력해 주세요. | nickname 누락·길이 오류 |

---

### POST /api/auth/signup — 회원가입

**요구사항**: 아이디·비밀번호·닉네임으로 계정을 생성한다. 로그인 없이 호출할 수 있다. 화면의 중복 확인 여부와 관계없이 서버에서 아이디·닉네임 중복을 다시 검사한다. (중복 확인 후 가입 사이에 다른 사람이 같은 값으로 가입할 수 있음) 가입 후 자동 로그인하지 않으며, FE는 로그인 화면으로 이동한다.

**Request — Body**
```json
{
  "loginId": "chef01",
  "password": "password12",
  "passwordConfirm": "password12",
  "nickname": "슥사인"
}
```

| 필드 | 타입 | 필수 | 규칙 |
| --- | --- | --- | --- |
| loginId | string | O | 영문·숫자 4~20자 |
| password | string | O | 8~20자 |
| passwordConfirm | string | O | password와 동일 |
| nickname | string | O | 2~10자, 앞뒤 공백 제거, 중복 불가 |

**시스템 처리**
1. 필수값·형식·길이를 검증한다.
2. password와 passwordConfirm이 같은지 확인한다.
3. loginId로 MEMBER를 조회해 이미 있으면 중단한다.
4. nickname(공백 제거 값)으로 MEMBER를 조회해 이미 있으면 중단한다.
5. 비밀번호를 BCrypt로 암호화한다.
6. MEMBER를 저장한다. 동시 가입으로 UK 제약(UK_MEMBER_LOGIN_ID, UK_MEMBER_NICKNAME) 위반이 발생하면 해당 409 오류로 응답한다.
7. 생성된 회원 정보를 반환한다.

**Response 200 (data)**
```json
{
  "memberId": 1,
  "loginId": "chef01",
  "nickname": "슥사인"
}
```

**오류**

| HTTP | error | message | 발생 조건 |
| --- | --- | --- | --- |
| 400 | INVALID_REQUEST | 입력값을 확인해 주세요. | 필수값 누락, 형식·길이 오류 |
| 400 | PASSWORD_MISMATCH | 비밀번호가 일치하지 않습니다. | password ≠ passwordConfirm |
| 409 | LOGIN_ID_ALREADY_EXISTS | 이미 사용 중인 아이디입니다. | 같은 loginId 존재 |
| 409 | NICKNAME_ALREADY_EXISTS | 이미 사용 중인 닉네임입니다. | 같은 nickname 존재 |

---

### POST /api/auth/login — 로그인

**요구사항**: 아이디·비밀번호를 확인해 세션을 생성한다. 실패 시 아이디와 비밀번호 중 무엇이 틀렸는지 구분하지 않는다. 로그인 없이 호출할 수 있다. FE는 성공 후 로그인 화면 주소의 redirect 값(없으면 메인)으로 이동한다.

**Request — Body**
```json
{
  "loginId": "chef01",
  "password": "password12"
}
```

| 필드 | 타입 | 필수 |
| --- | --- | --- |
| loginId | string | O |
| password | string | O |

**시스템 처리**
1. 필수값을 검증한다.
2. loginId로 MEMBER를 조회한다. 없으면 로그인 실패로 처리한다.
3. 입력 비밀번호와 저장된 암호화 비밀번호를 비교한다.
4. 기존 세션이 있으면 무효화하고 새 세션을 생성한다.
5. 세션에 memberId를 저장한다.
6. 회원 정보를 반환한다. (응답 헤더로 JSESSIONID 쿠키 발급)

**Response 200 (data)**
```json
{
  "memberId": 1,
  "loginId": "chef01",
  "nickname": "슥사인"
}
```

**오류**

| HTTP | error | message | 발생 조건 |
| --- | --- | --- | --- |
| 400 | INVALID_REQUEST | 아이디와 비밀번호를 입력해 주세요. | 필수값 누락 |
| 401 | LOGIN_FAILED | 아이디 또는 비밀번호가 일치하지 않습니다. | 아이디 없음 또는 비밀번호 불일치 |

---

### POST /api/auth/logout — 로그아웃

**요구사항**: 현재 세션을 무효화한다. FE는 성공 후 로그인 화면으로 이동한다.

**Request**: Body 없음

**시스템 처리**
1. 세션의 로그인 여부를 확인한다.
2. 세션을 무효화한다.

**Response 200 (data)**
```json
null
```

**오류**

| HTTP | error | message | 발생 조건 |
| --- | --- | --- | --- |
| 401 | UNAUTHORIZED | 로그인이 필요합니다. | 세션 없음 |

---

### GET /api/auth/me — 로그인 사용자 조회

**요구사항**: 현재 세션의 사용자 정보를 반환한다. 모든 화면(로그인·회원가입 제외)의 공통 헤더 JS가 진입 시 호출한다. 401이면 FE는 현재 주소를 redirect 값으로 붙여 로그인 화면으로 이동한다.

**Request**: 파라미터 없음

**시스템 처리**
1. 로그인 인터셉터가 세션의 memberId를 확인한다. 없으면 401을 반환한다.
2. memberId로 MEMBER를 조회한다. 탈퇴 등으로 없으면 세션을 무효화하고 401을 반환한다.
3. 회원 정보를 반환한다.

**Response 200 (data)**
```json
{
  "memberId": 1,
  "loginId": "chef01",
  "nickname": "슥사인"
}
```

**오류**

| HTTP | error | message | 발생 조건 |
| --- | --- | --- | --- |
| 401 | UNAUTHORIZED | 로그인이 필요합니다. | 세션 없음, 세션 만료, 회원 정보 없음 |

---
## 2. users/me — 마이페이지 (C)

### GET /api/users/me/summary — 마이페이지 프로필 조회

**요구사항**: 마이페이지 상단 프로필과 탭별 활동 건수를 반환한다.

**Request**: 파라미터 없음

**시스템 처리**
1. 로그인 회원의 MEMBER를 조회한다.
2. 내가 쓴 REVIEW 수, 내가 참여한 PARTY_MEMBER 수(삭제된 팟 제외), 내가 등록한 RESTAURANT 수, 내 INTEREST 수를 집계한다.
3. 결과를 반환한다.

**Response 200 (data)**
```json
{
  "nickname": "슥사인",
  "loginId": "chef01",
  "reviewCount": 3,
  "partyCount": 5,
  "restaurantCount": 2,
  "interestCount": 3
}
```

**오류**: 공통 오류(401, 500) 외 없음

---

### GET /api/users/me/reviews — 내 리뷰 목록 조회

**요구사항**: 마이페이지 '내 리뷰' 탭용. 내가 작성한 리뷰를 최신순으로 반환한다. 삭제는 리뷰 삭제 API를 사용한다.

**Request**: 파라미터 없음

**시스템 처리**
1. member_id = 로그인 회원인 REVIEW를 created_at 내림차순으로 조회한다.
2. 식당 이름과 리뷰별 태그 이름을 함께 조회한다.
3. 결과를 반환한다.

**Response 200 (data)**
```json
[
  {
    "reviewId": 7,
    "restaurantId": 12,
    "restaurantName": "OO국밥 세종대점",
    "content": "국물이 진해요",
    "tags": ["혼밥가능", "점심추천"],
    "imageUrl": "/uploads/reviews/7.jpg",
    "createdAt": "2026-11-16T12:40:00"
  }
]
```
- 없으면 빈 배열 (화면: 탭별 빈 상태 문구)

**오류**: 공통 오류(401, 500) 외 없음

---

### GET /api/users/me/parties — 참여한 식사팟 목록 조회

**요구사항**: 마이페이지 '참여한 식사팟' 탭용. 내가 참여 중이거나 참여했던 식사팟을 최근 순으로 반환한다. 삭제된 팟과 내가 나간 팟은 포함하지 않는다. 항목을 누르면 식사팟 상세로 이동한다(대화 삭제 후에는 정보만 표시).

**Request**: 파라미터 없음

**시스템 처리**
1. PARTY_MEMBER에 로그인 회원이 있고 status ≠ DELETED인 MEAL_PARTY를 조회한다.
2. 정렬: party_date 내림차순 → 기준 시간 내림차순.
3. 식당 이름, 상태, 내 확정 여부, 방장 여부를 함께 반환한다.

**Response 200 (data)**
```json
[
  {
    "partyId": 28,
    "partyDate": "2026-11-16",
    "mealType": "LUNCH",
    "time": "12:00",
    "restaurantId": 12,
    "restaurantName": "OO국밥 세종대점",
    "status": "FINISHED",
    "host": false,
    "confirmed": true
  }
]
```
- time: 확정 시간이 있으면 확정 시간, 없으면 예정 시간

**오류**: 공통 오류(401, 500) 외 없음

---

### GET /api/users/me/restaurants — 등록한 식당 목록 조회

**요구사항**: 마이페이지 '등록한 식당' 탭용. 내가 등록한 식당을 최신순으로 반환한다. 초기 데이터(구내식당)는 포함하지 않는다.

**Request**: 파라미터 없음

**시스템 처리**
1. member_id = 로그인 회원인 RESTAURANT를 created_at 내림차순으로 조회한다.
2. 결과를 반환한다.

**Response 200 (data)**
```json
[
  {
    "restaurantId": 12,
    "name": "OO국밥 세종대점",
    "category": "KOREAN",
    "createdAt": "2026-11-17T10:00:00"
  }
]
```

**오류**: 공통 오류(401, 500) 외 없음

---

### GET /api/users/me/interests — 관심 식당 목록 조회

**요구사항**: 마이페이지 '관심 식당' 탭용. 관심 등록한 식당을 최근 등록순으로 반환한다. 방문하지 않은 식당도 포함하며, 방문 여부를 함께 표시한다. 해제는 관심 해제 API를 사용한다.

**Request**: 파라미터 없음

**시스템 처리**
1. member_id = 로그인 회원인 INTEREST를 created_at 내림차순으로 조회한다.
2. 식당 이름, 카테고리, 최근 30일 따봉 수를 함께 조회한다.
3. visited: 내가 참여 확정(confirmed_yn = Y)한 FINISHED 식사팟 중 해당 식당 팟이 있으면 true.
4. 결과를 반환한다.

**Response 200 (data)**
```json
[
  {
    "restaurantId": 12,
    "name": "OO국밥 세종대점",
    "category": "KOREAN",
    "thumbsCount30d": 42,
    "visited": true,
    "interestedAt": "2026-11-15T09:00:00"
  }
]
```

**오류**: 공통 오류(401, 500) 외 없음

---
## 3. cafeteria — 구내식당 (C)

### GET /api/cafeteria/menus/today — 오늘의 구내식당 메뉴 조회

**요구사항**: 삼성 멀티캠퍼스 세종점 구내식당(세종대 계절밥상)의 당일 점심·저녁 메뉴를 반환한다. DB에 당일 데이터가 없으면 그 자리에서 수집(Jsoup)해 저장한 뒤 반환한다. 수집 실패나 미운영일도 화면 안내용이므로 200으로 응답하고 status로 구분한다.

**Request**: 파라미터 없음 (서버 날짜 기준 오늘)

**시스템 처리**
1. 오늘 날짜의 CAFETERIA_MENU를 조회한다.
2. 있으면 끼니별로 묶어 반환한다.
3. 없으면 계절밥상 메뉴 페이지를 Jsoup으로 수집한다.
4. 수집 결과에서 오늘 날짜의 점심·저녁 메뉴를 추출해 저장한다. (menu_date + meal_type + menu_name 유니크 제약으로 동시 요청 시 중복 저장 방지)
5. 페이지에 오늘 메뉴가 없으면 status = CLOSED(미운영일)로 반환한다.
6. 수집 중 예외가 나면 status = UNAVAILABLE로 반환하고 저장하지 않는다. (다음 요청 때 다시 수집 시도)
7. 수집이 계속 불가능하면 수기로 입력한 데이터를 CAFETERIA_MENU에 넣어 대체한다.

**Response 200 (data)**
```json
{
  "date": "2026-11-17",
  "cafeteriaName": "세종대 계절밥상",
  "status": "OPEN",
  "lunch": ["제육볶음", "된장찌개", "계란말이", "김치"],
  "dinner": ["카레라이스", "미소장국"]
}
```

| status | 의미 | 화면 표시 |
| --- | --- | --- |
| OPEN | 메뉴 있음 | 메뉴 표시 (한 끼만 있으면 다른 끼는 빈 배열) |
| CLOSED | 미운영일 | '운영하지 않습니다' |
| UNAVAILABLE | 수집 실패 | '메뉴 정보를 불러올 수 없습니다' |

**오류**: 공통 오류(401, 500) 외 없음

---
## 4. restaurants — 랭킹·룰렛 (C)

### GET /api/restaurants/rankings — 따봉 랭킹 조회

> 2026-11-18 이동: `GET /api/rankings/thumbs` → `GET /api/restaurants/rankings`
>
> 2026-11-23 변경: 따봉 1건 이상만 → 따봉이 0건인 식당도 리뷰 수·등록 순으로 포함, 구내식당(`CAFETERIA`) 제외
>
> 2026-11-23 변경: 카페(`CAFE`)도 제외. 랭킹 화면 '카페' 칩은 식당 목록 API(category=CAFE)로 그린다

**요구사항**: 오늘 기준 최근 30일(오늘 포함) 따봉 수 내림차순으로 식당 랭킹을 반환한다. 랭킹 화면은 limit=10, 메인 '이달의 따봉 TOP 3'는 limit=3으로 호출한다. 따봉이 없는 식당도 리뷰 수 → 등록 순으로 이어서 포함한다. 구내식당은 제외한다.

**Request — Query**

| 이름 | 타입 | 필수 | 규칙 |
| --- | --- | --- | --- |
| limit | number | X | 1~10, 기본 10 |

**시스템 처리**
1. limit을 검증한다.
2. 기간 from = 오늘 - 29일, to = 오늘로 정한다.
3. PARTY_MEMBER(thumbs_up_yn = Y)와 MEAL_PARTY를 조인해, party_date가 기간 내인 행을 MEAL_PARTY.restaurant_id별로 집계한다. (집계 기준: 공통 규칙 '따봉 집계')
4. 식당별 전체 REVIEW 수를 함께 조회한다.
5. 정렬: 따봉 수 내림차순 → 리뷰 수 내림차순 → 식당 등록 순(restaurant_id 오름차순).
6. 구내식당(`CAFETERIA`)과 카페(`CAFE`)를 제외하고, 순위(rank)는 정렬 순서대로 1부터 매긴다. (동점도 정렬 기준에 따라 다른 순위, 따봉 0건 식당 포함)
7. limit만큼 반환한다.

**Response 200 (data)**
```json
{
  "from": "2026-10-19",
  "to": "2026-11-17",
  "rankings": [
    {
      "rank": 1,
      "restaurantId": 12,
      "name": "OO국밥 세종대점",
      "category": "KOREAN",
      "thumbsCount30d": 42,
      "reviewCount": 12
    }
  ]
}
```
- 대상 식당이 없으면(구내식당 외 등록 식당 없음) rankings는 빈 배열 (화면: '등록된 식당이 아직 없어요')

**오류**

| HTTP | error | message | 발생 조건 |
| --- | --- | --- | --- |
| 400 | INVALID_REQUEST | 조회 조건을 확인해 주세요. | limit 범위 밖 |

---

### GET /api/restaurants/roulette/candidates — 룰렛 후보 조회

> 2026-11-18 이동: `GET /api/roulette/candidates` → `GET /api/restaurants/roulette/candidates`
>
> 2026-11-19 변경: 구내식당(`CAFETERIA`)은 '전체'를 포함해 룰렛 후보에서 항상 제외, `category=CAFETERIA`는 400
>
> 2026-11-23 변경: 카페(`CAFE`)는 '전체'에서 제외, `category=CAFE`로는 카페끼리 조회

**요구사항**: 룰렛 화면에서 카테고리를 고르면(최초 진입 시 '전체') 휠에 그릴 식당을 최대 8곳 무작위로 뽑아 반환한다. FE는 이 후보로 휠을 그리고, [룰렛 돌리기] 시 후보 ID 목록을 룰렛 실행 API로 보낸다.

**Request — Query**

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| category | string | X | 공통 규칙의 식당 카테고리 값. 없으면 전체 |

**시스템 처리**
1. category 값이 있으면 유효한지 확인한다. (`CAFETERIA`는 룰렛 카테고리가 아니므로 오류)
2. 구내식당(`CAFETERIA`)을 제외하고(category가 없으면 카페 `CAFE`도 제외), 조건에 맞는 RESTAURANT를 무작위 순서로 최대 8개 조회한다. (Oracle: ORDER BY DBMS_RANDOM.VALUE)
3. 전체 대상 수와 후보 목록을 반환한다.

**Response 200 (data)**
```json
{
  "category": "KOREAN",
  "totalCount": 14,
  "candidates": [
    { "restaurantId": 12, "name": "OO국밥 세종대점" },
    { "restaurantId": 18, "name": "△△분식" }
  ],
  "spinnable": true
}
```
- spinnable: 후보가 2곳 이상이면 true. false면 FE는 '룰렛을 돌릴 식당이 부족합니다'를 표시하고 버튼을 비활성화
- category를 보내지 않으면 응답의 category는 null
- totalCount도 구내식당(전체면 카페 포함)을 제외한 수
- 룰렛 화면의 카테고리 선택지: 전체 / 한식 / 중식 / 일식 / 양식 / 분식 / 카페 (구내식당 없음, '기타'는 칩 없이 '전체'에 포함, 카페는 '전체'에 미포함)

**오류**

| HTTP | error | message | 발생 조건 |
| --- | --- | --- | --- |
| 400 | INVALID_REQUEST | 카테고리를 확인해 주세요. | 없는 category, `CAFETERIA` |

---

### POST /api/restaurants/roulette/spin — 메뉴 룰렛 실행

> 2026-11-18 이동: `POST /api/roulette/spin` → `POST /api/restaurants/roulette/spin`
>
> 2026-11-19 변경: 구내식당(`CAFETERIA`) ID는 없는 식당과 같이 후보에서 제외
>
> 2026-11-23 변경: '내 룰렛' 추가 — 사용자가 등록된 식당을 검색(식당 이름 자동완성 API)해 2~8곳을 고르면 그 ID를 이 API로 바로 보낸다. 내 룰렛에서는 구내식당도 고를 수 있으므로 구내식당 제외를 없앤다. (카테고리 룰렛 후보 조회는 계속 구내식당 제외) 고른 목록은 서버에 저장하지 않는다(FE 브라우저 저장소).

**요구사항**: 화면에 그려진 룰렛 후보(카테고리 룰렛: 룰렛 후보 조회 API 결과 / 내 룰렛: 사용자가 고른 식당) 중 당첨 식당 1곳을 서버에서 무작위로 정한다. FE는 winnerId 칸에서 멈추도록 약 3초 회전 연출 후 결과 카드를 보여 준다. [다시 돌리기]는 같은 후보로 이 API를 다시 호출한다.

**Request — Body**
```json
{
  "candidateIds": [12, 18, 21, 25, 30]
}
```

| 필드 | 타입 | 필수 | 규칙 |
| --- | --- | --- | --- |
| candidateIds | number[] | O | 2~8개, 중복 없음 |

**시스템 처리**
1. candidateIds 개수(2~8)와 중복 여부를 검증한다.
2. candidateIds 중 RESTAURANT에 없는 ID는 제외한다. (후보 조회 후 삭제된 식당 대비) 남은 후보가 2곳 미만이면 중단한다. 구내식당도 후보로 인정한다. (11/23 변경)
3. 후보 중 1곳을 무작위로 선정한다.
4. 당첨 식당의 요약 정보(결과 카드용)를 함께 반환한다.

**Response 200 (data)**
```json
{
  "winnerId": 12,
  "winner": {
    "restaurantId": 12,
    "name": "OO국밥 세종대점",
    "category": "KOREAN",
    "thumbsCount30d": 42
  }
}
```
- 결과 카드 버튼: [식당 상세 보기] → 식당 상세 / [이 식당으로 식사팟 만들기] → 식사팟 생성(식당 고정) / [다시 돌리기] → 이 API 재호출

**오류**

| HTTP | error | message | 발생 조건 |
| --- | --- | --- | --- |
| 400 | INVALID_REQUEST | 룰렛 후보를 확인해 주세요. | 개수 2~8 위반, 중복 ID |
| 409 | NOT_ENOUGH_CANDIDATES | 룰렛을 돌릴 식당이 부족합니다. | 없는 식당을 제외한 후보가 2곳 미만 |

---
## 5. restaurants·reviews — 식당·리뷰·관심 (B)

### GET /api/restaurants/autocomplete — 식당 이름 자동완성

**요구사항**: 등록된 식당을 이름 부분 일치로 최대 5개 반환한다. 식당 등록(중복 방지)과 식사팟 생성(식당 선택)에서 공통으로 사용한다. FE는 입력이 멈춘 뒤 300ms 후 호출한다(디바운스).

**Request — Query**

| 이름 | 타입 | 필수 | 규칙 |
| --- | --- | --- | --- |
| keyword | string | O | 1~30자, 앞뒤 공백 제거 |

**시스템 처리**
1. keyword를 검증한다.
2. RESTAURANT.name에 keyword가 포함된 식당을 조회한다.
3. 정렬: keyword로 시작하는 이름 우선, 그다음 이름 가나다순.
4. 최대 5개를 반환한다.

**Response 200 (data)**
```json
[
  { "restaurantId": 12, "name": "OO국밥 세종대점", "category": "KOREAN" },
  { "restaurantId": 15, "name": "OO국수", "category": "KOREAN" }
]
```
- 결과가 없으면 빈 배열

**오류**

| HTTP | error | message | 발생 조건 |
| --- | --- | --- | --- |
| 400 | INVALID_REQUEST | 검색어를 입력해 주세요. | keyword 누락 또는 30자 초과 |

---

### GET /api/restaurants/check-map-url — 네이버 지도 URL 검증

> 2026-11-19 변경: `name` 파라미터 추가, 장소는 식당 이름으로 검색(네이버에 URL·장소 ID로 조회하는 공식 API 없음), 중복은 장소 ID로 만든 표준 URL로 판단

**요구사항**: 식당 등록 화면에서 URL과 식당 이름 입력이 끝나면 호출해 입력창 아래에 결과를 표시한다. 형식 → 중복 → 장소 검색 순으로 검사하고, 정상이면 네이버 지역 검색으로 찾은 장소 이름·주소·좌표를 반환한다. 사용자는 표시된 주소를 보고 맞는 식당인지 확인한 뒤 등록한다. 오류 상황도 화면 안내용이므로 200으로 응답하고 reason으로 구분한다.

**Request — Query**

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| url | string | O | 입력한 네이버 지도 URL (URL 인코딩) |
| name | string | O | 입력한 식당 이름, 1~30자, 앞뒤 공백 제거 (지역 검색어로 사용) |

**시스템 처리**
1. url, name 필수값·길이를 검증한다.
2. url 형식을 검사한다. naver.me 또는 map.naver.com 링크에서 장소 ID를 찾을 수 없으면 INVALID_FORMAT. (naver.me 단축 링크는 연결된 주소를 따라가 장소 ID를 찾는다)
3. 장소 ID로 만든 표준 URL(`https://map.naver.com/p/entry/place/{장소ID}`)과 같은 map_url의 RESTAURANT가 있으면 DUPLICATE.
4. 네이버 지역 검색(NAVER API HUB)으로 name을 검색한다. 결과가 없으면 NOT_FOUND.
5. 정상이면 첫 번째 검색 결과의 장소 정보를 반환한다. (이름의 HTML 태그 제거, 주소는 도로명 주소, 없으면 지번 주소)

> 네이버에는 URL·장소 ID로 장소를 조회하는 공식 API가 없어 이름으로 검색한다. 검색 결과가 URL의 장소와 다를 수 있으므로 화면에서 주소를 보여 주고 사용자가 확인한다.
>
> 지역 검색: `GET https://naverapihub.apigw.ntruss.com/search/v1/local` (헤더 `X-NCP-APIGW-API-KEY-ID`, `X-NCP-APIGW-API-KEY`). 키는 네이버 클라우드 플랫폼 콘솔 > NAVER API HUB에서 발급하고 `application-local.yml`의 `naver.client-id`·`naver.client-secret`에 넣는다. 응답 좌표 mapx(경도)·mapy(위도)는 WGS84 값에 10^7을 곱한 정수 문자열이다.

**Response 200 (data) — 정상**
```json
{
  "valid": true,
  "reason": null,
  "message": null,
  "place": {
    "name": "OO국밥 세종대점",
    "address": "서울 광진구 능동로 ...",
    "lat": 37.5502,
    "lng": 127.0731
  }
}
```

**Response 200 (data) — 검증 실패**
```json
{
  "valid": false,
  "reason": "DUPLICATE",
  "message": "이미 등록된 식당입니다.",
  "place": null
}
```

| reason | message |
| --- | --- |
| INVALID_FORMAT | 네이버 지도 URL만 입력할 수 있습니다. |
| DUPLICATE | 이미 등록된 식당입니다. |
| NOT_FOUND | 존재하지 않는 주소입니다. |

**오류**

| HTTP | error | message | 발생 조건 |
| --- | --- | --- | --- |
| 400 | INVALID_REQUEST | URL과 식당 이름을 입력해 주세요. | url·name 누락, name 30자 초과 |
| 502 | MAP_API_ERROR | 지도 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요. | 네이버 API 호출 실패 |

---

### GET /api/restaurants/list — 식당 목록 조회 (검색·필터·지도)

**요구사항**: 검색어·카테고리 조건에 맞는 식당을 최근 30일 따봉 수 순으로 반환한다. 같은 응답으로 식당 목록 화면의 좌측 목록과 우측 지도 핀을 함께 그린다. 페이징 없이 전체를 반환한다.

**Request — Query**

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| keyword | string | X | 식당 이름 부분 일치 (최대 30자) |
| category | string | X | 공통 규칙의 식당 카테고리 값. 없으면 전체 |

**시스템 처리**
1. category 값이 있으면 유효한지 확인한다.
2. 조건(keyword 포함, category 일치)으로 RESTAURANT를 조회한다.
3. 식당별로 최근 30일(오늘 포함) 따봉 수(공통 규칙 '따봉 집계')와 REVIEW 수를 집계한다.
4. 정렬: 따봉 수 내림차순 → 리뷰 수 내림차순 → 등록 순.
5. 목록을 반환한다.

**Response 200 (data)**
```json
[
  {
    "restaurantId": 12,
    "name": "OO국밥 세종대점",
    "category": "KOREAN",
    "cafeteria": false,
    "thumbsCount30d": 42,
    "reviewCount": 12,
    "lat": 37.5502,
    "lng": 127.0731
  }
]
```
- lat·lng: 식당 등록 시 네이버 API로 조회해 저장한 좌표. FE는 네이버 지도 API로 핀을 표시
- cafeteria: category가 CAFETERIA이면 true (DB에 별도 컬럼 없음, ERD 기준)
- 결과가 없으면 빈 배열

**오류**

| HTTP | error | message | 발생 조건 |
| --- | --- | --- | --- |
| 400 | INVALID_REQUEST | 검색 조건을 확인해 주세요. | 없는 카테고리, 검색어 30자 초과 |

---

### POST /api/restaurants/register — 식당 등록

> 2026-11-19 변경: `CAFETERIA` 카테고리 등록 불가(구내식당은 초기 데이터로만 제공), 장소는 식당 이름으로 검색, map_url은 장소 ID로 만든 표준 URL로 저장

**요구사항**: 네이버 지도 URL, 식당 이름, 카테고리로 식당을 등록한다. 등록 직전에 URL 검증을 서버에서 다시 수행하고, 네이버 지역 검색으로 찾은 주소·위도·경도를 함께 저장한다. 사옥 구내식당(세종대 계절밥상)은 초기 데이터로 미리 등록되어 있고, 사용자는 구내식당 카테고리로 등록할 수 없다.

**Request — Body**
```json
{
  "mapUrl": "https://naver.me/xxxx",
  "name": "OO국밥 세종대점",
  "category": "KOREAN"
}
```

| 필드 | 타입 | 필수 | 규칙 |
| --- | --- | --- | --- |
| mapUrl | string | O | naver.me 또는 map.naver.com 링크 |
| name | string | O | 1~30자, 앞뒤 공백 제거 |
| category | string | O | 공통 규칙의 식당 카테고리 값 중 `CAFETERIA` 제외 |

**시스템 처리**
1. 필수값·길이·카테고리 값을 검증한다. (`CAFETERIA`면 중단)
2. mapUrl 형식을 검사하고 장소 ID를 찾는다. 찾을 수 없으면 중단한다.
3. 장소 ID로 만든 표준 URL(`https://map.naver.com/p/entry/place/{장소ID}`)과 같은 map_url의 RESTAURANT가 있으면 중단한다.
4. 네이버 지역 검색으로 name을 검색한다. 결과가 없으면 중단한다.
5. 첫 번째 검색 결과의 address, lat, lng와 요청값으로 RESTAURANT를 저장한다. (map_url = 표준 URL, member_id = 로그인 회원)
6. 생성된 restaurantId를 반환한다. FE는 식당 상세로 이동한다.

> ⚠️ 노션 원문 5번에는 `is_cafeteria = N`이 남아 있으나, ERD에서 IS_CAFETERIA 컬럼은 삭제되었다(구내식당은 category = CAFETERIA로 구분). 이 문서는 ERD 기준으로 적었다.

**Response 200 (data)**
```json
{
  "restaurantId": 12
}
```

**오류**

| HTTP | error | message | 발생 조건 |
| --- | --- | --- | --- |
| 400 | INVALID_REQUEST | 입력값을 확인해 주세요. | 필수값 누락, 길이 초과, 없는 카테고리, CAFETERIA 카테고리 |
| 400 | INVALID_MAP_URL | 네이버 지도 URL만 입력할 수 있습니다. | 형식 오류, 장소 ID를 찾을 수 없음 |
| 404 | MAP_PLACE_NOT_FOUND | 존재하지 않는 주소입니다. | 지역 검색 결과 없음 |
| 409 | RESTAURANT_ALREADY_EXISTS | 이미 등록된 식당입니다. | 같은 표준 URL(장소 ID) 존재 |
| 502 | MAP_API_ERROR | 지도 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요. | 네이버 API 호출 실패 |

---

### GET /api/restaurants/{restaurantId} — 식당 상세 조회

**요구사항**: 식당 정보, 많이 선택된 태그 TOP 3, 최근 30일 따봉 수, 관심 수, 리뷰 수, 내 관심 여부를 반환한다. 리뷰 목록은 별도 API(페이징)로 조회한다.

**Request — Path**

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| restaurantId | number | 식당 ID |

**시스템 처리**
1. restaurantId로 RESTAURANT를 조회한다. 없으면 중단한다.
2. 해당 식당 리뷰들의 REVIEW_TAG를 태그별로 집계해 상위 3개를 구한다. (태그 전체 대상, 분류와 무관. 동수면 tag_id 오름차순, 선택된 적 없는 태그는 제외)
3. 최근 30일 따봉 수(공통 규칙 '따봉 집계'), INTEREST 수, REVIEW 수를 집계한다.
4. 로그인 회원의 INTEREST 존재 여부를 확인한다.
5. 결과를 반환한다.

**Response 200 (data)**
```json
{
  "restaurantId": 12,
  "name": "OO국밥 세종대점",
  "category": "KOREAN",
  "cafeteria": false,
  "mapUrl": "https://naver.me/xxxx",
  "address": "서울 광진구 능동로 ...",
  "topTags": [
    { "tagId": 5, "name": "혼밥가능", "category": "GROUP_TYPE", "count": 18 },
    { "tagId": 1, "name": "점심추천", "category": "MEAL_TYPE", "count": 11 },
    { "tagId": 8, "name": "가성비", "category": "PRICE", "count": 9 }
  ],
  "thumbsCount30d": 42,
  "interestCount": 15,
  "reviewCount": 3,
  "interested": false
}
```
- topTags: 0~3개
- cafeteria: category가 CAFETERIA이면 true

**오류**

| HTTP | error | message | 발생 조건 |
| --- | --- | --- | --- |
| 404 | RESTAURANT_NOT_FOUND | 식당을 찾을 수 없습니다. | 없는 restaurantId |

---

### POST /api/restaurants/{restaurantId}/interest — 관심 등록

**요구사항**: 식당을 내 관심 목록에 추가한다. 방문 여부와 관계없이 등록할 수 있다. 관심 수는 랭킹에 반영하지 않는다.

**Request — Path**: restaurantId (number) / Body 없음

**시스템 처리**
1. RESTAURANT가 있는지 확인한다.
2. (restaurant_id, member_id) INTEREST가 이미 있으면 중단한다.
3. INTEREST를 저장한다. (interest_id는 SEQ_INTEREST. 동시 요청으로 UK_INTEREST 위반 시 ALREADY_INTERESTED)
4. 변경된 관심 수를 반환한다.

**Response 200 (data)**
```json
{
  "interested": true,
  "interestCount": 16
}
```

**오류**

| HTTP | error | message | 발생 조건 |
| --- | --- | --- | --- |
| 404 | RESTAURANT_NOT_FOUND | 식당을 찾을 수 없습니다. | 없는 restaurantId |
| 409 | ALREADY_INTERESTED | 이미 관심 등록한 식당입니다. | 중복 등록 |

---

### DELETE /api/restaurants/{restaurantId}/interest — 관심 해제

**요구사항**: 식당을 내 관심 목록에서 뺀다. 식당 상세의 [관심] 재클릭과 마이페이지 관심 식당 탭의 [관심 해제]에서 사용한다.

**Request — Path**: restaurantId (number)

**시스템 처리**
1. (restaurant_id, member_id) INTEREST를 조회한다. 없으면 중단한다.
2. INTEREST를 삭제한다.
3. 변경된 관심 수를 반환한다.

**Response 200 (data)**
```json
{
  "interested": false,
  "interestCount": 15
}
```

**오류**

| HTTP | error | message | 발생 조건 |
| --- | --- | --- | --- |
| 404 | INTEREST_NOT_FOUND | 관심 등록되지 않은 식당입니다. | 관심 등록 내역 없음 (없는 식당 포함) |

---

### GET /api/restaurants/{restaurantId}/reviews — 리뷰 목록 조회

**요구사항**: 식당의 리뷰를 최신순으로 페이지 단위(기본 10개) 반환한다. 식당 상세 화면에서 처음 1페이지를 불러오고, [더보기]를 누르면 다음 페이지를 이어 붙인다. 식당 상세 API와 분리해 리뷰가 많아져도 상세 응답이 무거워지지 않게 한다.

**Request — Path**: restaurantId (number)

**Request — Query**

| 이름 | 타입 | 필수 | 규칙 |
| --- | --- | --- | --- |
| page | number | X | 1 이상, 기본 1 |
| size | number | X | 1~50, 기본 10 |

**시스템 처리**
1. page, size를 검증한다.
2. RESTAURANT가 있는지 확인한다.
3. 해당 식당 REVIEW를 created_at 내림차순(같으면 review_id 내림차순)으로 페이지 조회한다.
4. 리뷰별 태그 이름과 작성자 닉네임을 함께 조회한다.
5. 로그인 회원이 작성한 리뷰는 mine = true로 표시한다.
6. 전체 개수와 다음 페이지 여부를 함께 반환한다.

**Response 200 (data)**
```json
{
  "page": 1,
  "size": 10,
  "totalCount": 23,
  "hasNext": true,
  "items": [
    {
      "reviewId": 7,
      "nickname": "슥사인",
      "content": "국물이 진해요",
      "tags": ["혼밥가능", "점심추천"],
      "imageUrl": "/uploads/reviews/7.jpg",
      "createdAt": "2026-11-16T12:40:00",
      "mine": true
    }
  ]
}
```
- imageUrl: 사진이 없으면 null / tags: 없으면 빈 배열

**오류**

| HTTP | error | message | 발생 조건 |
| --- | --- | --- | --- |
| 400 | INVALID_REQUEST | 페이지 정보를 확인해 주세요. | page < 1, size 범위 밖 |
| 404 | RESTAURANT_NOT_FOUND | 식당을 찾을 수 없습니다. | 없는 restaurantId |

---

### POST /api/restaurants/{restaurantId}/reviews — 리뷰 작성

**요구사항**: 한줄평(필수)과 태그(선택, 중복 선택 가능), 사진 1장(선택)을 등록한다. 같은 식당에 여러 번 작성할 수 있다.

**Request — Headers**: `Content-Type: multipart/form-data`

**Request — Path**: restaurantId (number)

**Request — Form Data**

| 필드 | 타입 | 필수 | 규칙 |
| --- | --- | --- | --- |
| content | string | O | 1~100자, 앞뒤 공백 제거 후 빈 값 불가 |
| tagIds | number (반복) | X | 공통 규칙 '리뷰 태그' 표의 tagId(1~18). 분류와 관계없이 여러 개 선택 가능. 예: tagIds=1&tagIds=4. 중복 값은 1개로 처리 |
| image | file | X | jpg·png, 5MB 이하, 1장 |

**시스템 처리**
1. content 길이와 빈 값 여부를 검증한다.
2. RESTAURANT가 있는지 확인한다.
3. tagIds가 있으면 중복을 제거하고 모두 TAG(1~18)에 존재하는지 확인한다.
4. image가 있으면 확장자·MIME 타입·용량을 검증하고, 서버 업로드 경로에 고유 파일명으로 저장한다.
5. REVIEW를 저장한다. (image_path = 저장 경로 또는 NULL)
6. REVIEW_TAG를 저장한다.
7. 4~6을 하나의 트랜잭션으로 처리하고, 실패 시 저장한 파일을 삭제한다.
8. 생성된 reviewId를 반환한다. FE는 리뷰 목록 1페이지를 다시 조회한다.

**Response 200 (data)**
```json
{
  "reviewId": 8
}
```

**오류**

| HTTP | error | message | 발생 조건 |
| --- | --- | --- | --- |
| 400 | INVALID_REQUEST | 한줄평을 입력해 주세요. | content 누락·공백만 입력·100자 초과 |
| 400 | INVALID_TAG | 선택할 수 없는 태그입니다. | 존재하지 않는 tagId |
| 400 | INVALID_IMAGE | jpg·png 형식, 5MB 이하 사진만 올릴 수 있습니다. | 형식 또는 용량 오류 |
| 404 | RESTAURANT_NOT_FOUND | 식당을 찾을 수 없습니다. | 없는 restaurantId |

---

### POST /api/restaurants/{restaurantId}/suggestions — 식당 정보 수정 제안

**요구사항**: 식당 상세 하단 '식당 정보 수정 제안' 입력 창에서 작성한 내용을 저장한다. 제안 처리 방식(누가 반영하는지)은 미정이며, 이 API는 저장만 담당한다.

**Request — Path**: restaurantId (number)

**Request — Body**
```json
{
  "content": "카테고리가 일식이 아니라 한식이에요"
}
```

| 필드 | 타입 | 필수 | 규칙 |
| --- | --- | --- | --- |
| content | string | O | 1~500자, 앞뒤 공백 제거 |

**시스템 처리**
1. content를 검증한다.
2. RESTAURANT가 있는지 확인한다.
3. SUGGESTION을 저장한다. (member_id = 로그인 회원)
4. 생성된 suggestionId를 반환한다.

**Response 200 (data)**
```json
{
  "suggestionId": 3
}
```

**오류**

| HTTP | error | message | 발생 조건 |
| --- | --- | --- | --- |
| 400 | INVALID_REQUEST | 제안 내용을 입력해 주세요. | content 누락, 500자 초과 |
| 404 | RESTAURANT_NOT_FOUND | 식당을 찾을 수 없습니다. | 없는 restaurantId |

---

### DELETE /api/reviews/{reviewId} — 리뷰 삭제

**요구사항**: 본인이 작성한 리뷰를 삭제한다. 식당 상세와 마이페이지 '내 리뷰' 탭에서 사용한다. FE는 삭제 전 확인 창을 띄운다.

**Request — Path**: reviewId (number)

**시스템 처리**
1. reviewId로 REVIEW를 조회한다. 없으면 중단한다.
2. 작성자가 로그인 회원인지 확인한다.
3. REVIEW_TAG와 REVIEW를 삭제한다.
4. 첨부 사진 파일이 있으면 서버에서 삭제한다. (파일 삭제 실패는 로그만 남기고 응답은 성공)

**Response 200 (data)**
```json
null
```

**오류**

| HTTP | error | message | 발생 조건 |
| --- | --- | --- | --- |
| 403 | FORBIDDEN | 본인이 작성한 리뷰만 삭제할 수 있습니다. | 작성자가 아님 |
| 404 | REVIEW_NOT_FOUND | 리뷰를 찾을 수 없습니다. | 없는 reviewId |

---
## 6. parties — 식사팟·대화·따봉 (A)

> 공통: 모든 식사팟 API는 팟이 없으면 404 `PARTY_NOT_FOUND`, status가 DELETED면 404 `PARTY_DELETED`('삭제된 식사팟입니다.')로 응답한다. 목록류는 DELETED를 제외한다.

### POST /api/parties/create — 식사팟 생성

**요구사항**: 오늘부터 14일 후까지의 식사팟을 만든다. 날짜를 보내지 않으면 오늘 팟이다. 생성자는 방장(HOST)이자 첫 참여자로 등록되고, 상태는 RECRUITING이다. FE는 성공 후 식사팟 상세로 이동한다. 식당 상세에서 들어온 경우 restaurantId는 화면에서 고정된 값을 보낸다.

> 2026-11-20 변경: 당일만 → 오늘 ~ 14일 후 (partyDate 추가)
>
> 2026-11-23 변경: 한 끼에 팟 하나 — 같은 날짜·같은 끼니의 팟(DELETED 제외)에 이미 들어가 있으면 생성 불가 (409 `ALREADY_JOINED_SAME_MEAL`)

**Request — Body**
```json
{
  "restaurantId": 12,
  "partyDate": "2026-11-21",
  "mealType": "LUNCH",
  "mealTime": "12:00",
  "capacity": 4,
  "memo": "같이 국밥 드실 분!"
}
```

| 필드 | 타입 | 필수 | 규칙 |
| --- | --- | --- | --- |
| restaurantId | number | O | 존재하는 식당 |
| partyDate | string | X | YYYY-MM-DD. 오늘 ~ 오늘+14일. 없으면 오늘 |
| mealType | string | O | LUNCH, DINNER |
| mealTime | string | O | HH:mm. LUNCH 11:00~13:30, DINNER 17:00~19:30, 30분 단위. 오늘 팟이면 현재 시각 이후 |
| capacity | number | O | 2~12 (방장 포함) |
| memo | string | X | 최대 50자 |

**시스템 처리**
1. 필수값·형식·범위를 검증한다. partyDate가 없으면 오늘로 한다.
2. partyDate가 오늘 ~ 오늘+14일인지 확인한다.
3. mealTime이 mealType 시간대에 속하고 30분 단위인지 확인한다.
4. partyDate + mealTime이 현재 시각보다 이후인지 확인한다. (미래 날짜는 항상 통과)
   4-1. 로그인 회원이 같은 partyDate·같은 mealType의 팟(DELETED 제외, 확정 여부·FINISHED 무관)에 이미 PARTY_MEMBER로 있으면 중단한다.
5. RESTAURANT가 있는지 확인한다.
6. MEAL_PARTY를 저장한다. (party_date = partyDate, host_id = 로그인 회원, status = RECRUITING)
7. PARTY_MEMBER에 방장을 저장한다. (confirmed_yn = N)
8. PARTY_CHAT에 시스템 메시지 '[닉네임]님이 식사팟을 만들었습니다'를 저장한다.
9. 6~8을 하나의 트랜잭션으로 처리한다.

**Response 200 (data)**
```json
{
  "partyId": 31
}
```

**오류**

| HTTP | error | message | 발생 조건 |
| --- | --- | --- | --- |
| 400 | INVALID_REQUEST | 입력값을 확인해 주세요. | 필수값 누락, 정원 범위 밖, 한마디 50자 초과, partyDate 형식 오류·범위(오늘~14일 후) 밖 |
| 400 | INVALID_MEAL_TIME | 선택할 수 없는 식사 시간입니다. | 시간대·30분 단위 위반, 지난 시간 |
| 404 | RESTAURANT_NOT_FOUND | 식당을 찾을 수 없습니다. | 없는 restaurantId |
| 409 | ALREADY_JOINED_SAME_MEAL | 같은 시간대에 이미 참여 중인 식사팟이 있습니다. | 같은 날짜·끼니의 다른 팟에 이미 참여 중 |

---

### GET /api/parties/list — 식사팟 목록 조회

**요구사항**: 고른 날짜(기본 오늘)의 식사팟을 식사 시간 빠른 순으로 반환한다. FINISHED·DELETED는 제외한다. 식사팟 목록 화면(날짜 선택 + 끼니 탭)과 메인 '오늘의 식사팟'(date 없음, excludeJoined=true, status=RECRUITING, limit=3)에서 사용한다.

> 2026-11-20 변경: date 파라미터 추가 (오늘 ~ 14일 후)

**Request — Query**

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| date | string | X | YYYY-MM-DD. 오늘 ~ 오늘+14일. 없으면 오늘 |
| mealType | string | X | LUNCH, DINNER. 없으면 전체 |
| status | string | X | RECRUITING, SCHEDULED, FULL, CLOSED 중 하나. 없으면 FINISHED·DELETED를 모두 뺀 전체 |
| excludeJoined | boolean | X | true면 내가 참여한 팟 제외. 기본 false |
| limit | number | X | 1~50. 없으면 전체 |

**시스템 처리**
1. 파라미터 값을 검증한다.
2. party_date = date(없으면 오늘), status NOT IN (FINISHED, DELETED) 조건에 요청 조건을 더해 MEAL_PARTY를 조회한다.
3. 정렬: 기준 시간(confirmed_time, 없으면 meal_time) 오름차순 → party_id 오름차순.
4. 팟별 참여 인원 수와 내 참여 여부를 계산한다.
5. 끼니별 개수(lunchCount, dinnerCount)는 mealType·limit과 무관하게 같은 status·excludeJoined 조건으로 센다. (탭 숫자 표시용)
6. limit만큼 잘라 반환한다.

**Response 200 (data)**
```json
{
  "lunchCount": 4,
  "dinnerCount": 2,
  "parties": [
    {
      "partyId": 31,
      "restaurantId": 12,
      "restaurantName": "OO국밥 세종대점",
      "category": "KOREAN",
      "mealType": "LUNCH",
      "time": "12:00",
      "memberCount": 2,
      "capacity": 4,
      "status": "RECRUITING",
      "memo": "같이 국밥 드실 분!",
      "joined": false
    }
  ]
}
```
- time: 확정 시간이 있으면 확정 시간, 없으면 예정 시간
- FE 버튼: joined=true → [상세 보기] / joined=false이고 RECRUITING·SCHEDULED → [참여하기] / joined=false이고 FULL·CLOSED → [마감] 비활성

**오류**

| HTTP | error | message | 발생 조건 |
| --- | --- | --- | --- |
| 400 | INVALID_REQUEST | 조회 조건을 확인해 주세요. | 잘못된 date·mealType·status, date 범위(오늘~14일 후) 밖, limit 범위 밖 |

---

### GET /api/parties/my/today — 내가 참여 중인 식사팟 조회 (오늘 이후)

**요구사항**: 메인 '참여 중인 식사팟' 카드용. 내가 참여한 오늘 ~ 14일 후 식사팟 중 가까운 것부터 최대 2개 반환한다. 오늘 식사 완료(FINISHED)된 팟도 포함하고, DELETED는 제외한다.

> 2026-11-20 변경: 오늘만 → 오늘 ~ 14일 후, 응답에 partyDate 추가. URI는 메인(C) 호환을 위해 유지

**Request**: 파라미터 없음

**시스템 처리**
1. party_date가 오늘 ~ 오늘+14일, status ≠ DELETED이고 PARTY_MEMBER에 로그인 회원이 있는 MEAL_PARTY를 조회한다.
2. 정렬: FINISHED가 아닌 팟을 먼저, 그 안에서 party_date 오름차순 → 기준 시간 오름차순. FINISHED는 뒤로.
3. 팟별 참여 인원 수, 확정 인원 수, 내 확정 여부를 계산한다.
4. 최대 2개를 반환한다. 없으면 빈 배열(FE는 영역 숨김).

**Response 200 (data)**
```json
[
  {
    "partyId": 31,
    "restaurantName": "OO국밥 세종대점",
    "partyDate": "2026-11-21",
    "mealType": "LUNCH",
    "time": "12:00",
    "status": "SCHEDULED",
    "memberCount": 5,
    "confirmedCount": 3,
    "myConfirmed": false,
    "meetPlace": "1층 로비"
  }
]
```
- time: 확정 시간이 있으면 확정 시간, 없으면 예정 시간 / meetPlace: 미확정이면 null

**오류**: 공통 오류(401, 500) 외 없음

---

### GET /api/parties/my/action-pending — 내가 할 일이 있는 식사팟 조회 (알림)

> 2026-11-23 추가: 헤더 종 알림용 (화면은 B)

**요구사항**: 내가 지금 해야 할 일이 있는 식사팟 목록을 반환한다. 방장은 아직 일정을 정하지 않은 팟("일정을 정해 주세요"), 참여자는 일정이 정해졌는데 아직 참석 확정을 안 한 팟("참석을 확정해 주세요")이 대상이다. 조건이 풀리면(일정 확정·참석 확정·삭제·식사 완료·기준 시간 경과) 목록에서 저절로 빠진다. 읽음 여부는 저장하지 않는다.

**Request**: 파라미터 없음

**시스템 처리**
1. party_date가 오늘 ~ 오늘+14일, status가 FINISHED·DELETED가 아니고 PARTY_MEMBER에 로그인 회원이 있는 MEAL_PARTY를 조회한다.
2. 기준 시간(확정 시간, 없으면 예정 시간)이 이미 지난 팟은 뺀다.
3. 팟마다 할 일을 정한다.
   - 일정(confirmed_time)이 없고 내가 방장 → `SET_SCHEDULE`
   - 일정이 있고 내 confirmed_yn = N → `CONFIRM`
   - 그 외는 뺀다.
4. 정렬: party_date → 기준 시간 → partyId 오름차순. 없으면 빈 배열.

**Response 200 (data)**
```json
[
  {
    "partyId": 31,
    "action": "SET_SCHEDULE",
    "restaurantName": "세종원",
    "partyDate": "2026-11-24",
    "mealType": "LUNCH",
    "time": "11:30",
    "meetPlace": null
  },
  {
    "partyId": 35,
    "action": "CONFIRM",
    "restaurantName": "스시온기",
    "partyDate": "2026-11-24",
    "mealType": "DINNER",
    "time": "18:00",
    "meetPlace": "1층 로비"
  }
]
```
- action: `SET_SCHEDULE`(방장, 일정 정하기) / `CONFIRM`(참석 확정)
- time: 확정 시간이 있으면 확정 시간, 없으면 예정 시간 / meetPlace: 일정 미확정이면 null
- FE: 항목을 누르면 `/party-detail.html?partyId={partyId}`로 이동

**오류**: 공통 오류(401, 500) 외 없음

---

### GET /api/parties/my/thumbs-pending — 따봉 요청 대상 조회

> 2026-11-18 이동: `GET /api/thumbs/pending` → `GET /api/parties/my/thumbs-pending`

**요구사항**: 메인 상단 따봉 배너용. 식사 완료(FINISHED)된 팟 중 내가 참여 확정했고 아직 따봉을 남기지 않은 건을 최근 식사 1건만 반환한다. [다음에]로 오늘 하루 숨기는 처리는 FE(브라우저 저장소)에서 한다.

**Request**: 파라미터 없음

**시스템 처리**
1. 다음 조건의 MEAL_PARTY를 조회한다.
   - status = FINISHED
   - PARTY_MEMBER에 로그인 회원이 있고 confirmed_yn = Y
   - 그 PARTY_MEMBER의 thumbs_up_yn = N
   - party_date가 최근 7일 이내
2. 정렬: party_date 내림차순 → 기준 시간 내림차순.
3. 첫 1건을 반환한다. 없으면 data = null.

**Response 200 (data)**
```json
{
  "partyId": 28,
  "restaurantId": 12,
  "restaurantName": "OO국밥 세종대점",
  "partyDate": "2026-11-16",
  "mealType": "LUNCH"
}
```
- 대상이 없으면 `null` (FE는 배너 숨김)

**오류**: 공통 오류(401, 500) 외 없음

---

### GET /api/parties/{partyId} — 식사팟 상세 조회

**요구사항**: 식사팟 정보, 확정 일정, 참여자 목록, 내 역할을 반환한다. 미참여자도 조회할 수 있지만 대화는 볼 수 없다. FE는 status·myRole·myConfirmed로 버튼을 결정하고, 버튼 처리 후 이 API를 다시 호출해 화면을 갱신한다.

**Request — Path**: partyId (number)

**시스템 처리**
1. partyId로 MEAL_PARTY를 조회한다. 없으면 PARTY_NOT_FOUND, status가 DELETED면 PARTY_DELETED로 중단한다.
2. 식당 정보, 참여자 목록(닉네임, 방장 여부, 확정 여부)을 조회한다.
3. 로그인 회원의 역할(HOST, MEMBER, NONE)과 확정 여부를 계산한다.
4. 확정 취소 가능 시각(기준 시간 - 30분)을 계산한다.
5. 결과를 반환한다.

**Response 200 (data)**
```json
{
  "partyId": 31,
  "restaurantId": 12,
  "restaurantName": "OO국밥 세종대점",
  "category": "KOREAN",
  "partyDate": "2026-11-17",
  "mealType": "LUNCH",
  "mealTime": "12:00",
  "capacity": 12,
  "memberCount": 5,
  "confirmedCount": 3,
  "status": "SCHEDULED",
  "memo": "같이 국밥 드실 분!",
  "schedule": {
    "confirmedTime": "12:00",
    "meetPlace": "1층 로비",
    "confirmMemo": "늦으면 채팅 남겨 주세요"
  },
  "cancelDeadline": "2026-11-17T11:30:00",
  "members": [
    { "memberId": 1, "nickname": "방장닉네임", "host": true, "confirmed": true },
    { "memberId": 5, "nickname": "슥사인", "host": false, "confirmed": false }
  ],
  "myRole": "MEMBER",
  "myConfirmed": false
}
```
- schedule: 방장이 일정을 확정하지 않았으면 null
- cancelDeadline: 기준 시간 30분 전. 이후에는 확정 취소·확정자 나가기 불가

| 조건 | 표시 버튼 |
| --- | --- |
| myRole = NONE, status = RECRUITING·SCHEDULED | [참여하기] |
| myRole = NONE, status = FULL·CLOSED | [마감] 비활성 |
| myRole = MEMBER, schedule = null | [나가기] |
| myRole = MEMBER, schedule 있음, myConfirmed = false | [참여 확정], [나가기] |
| myRole = MEMBER, myConfirmed = true | [확정 취소], [나가기] (cancelDeadline 이후 비활성) |
| myRole = HOST | 방장 메뉴 [일정 확정/수정] [모집 마감 또는 모집 재개] [팟 삭제] + 본인 [참여 확정] |
| status = FINISHED | 버튼 없음 |

**오류**

| HTTP | error | message | 발생 조건 |
| --- | --- | --- | --- |
| 404 | PARTY_NOT_FOUND | 식사팟을 찾을 수 없습니다. | 없는 partyId |
| 404 | PARTY_DELETED | 삭제된 식사팟입니다. | DELETED (초대 링크로 진입한 경우 포함) |

---

### DELETE /api/parties/{partyId} — 식사팟 삭제 (방장)

**요구사항**: 방장이 식사팟을 삭제한다. 행을 지우지 않고 status를 DELETED로 바꾼다(소프트 삭제). 삭제된 팟은 모든 목록에서 빠지고, 상세·초대 링크로 들어오면 '삭제된 식사팟입니다'를 안내한다. 대화 기록은 다음 날 0시 스케줄러가 삭제한다. 식사 완료된 팟은 마이페이지 이력에 남아야 하므로 삭제할 수 없다. FE는 삭제 전 확인 창을 띄우고, 성공 후 식사팟 목록으로 이동한다.

**Request — Path**: partyId (number)

**시스템 처리**
1. 트랜잭션을 시작하고 MEAL_PARTY 행을 잠금 조회한다. 없거나 DELETED면 중단한다.
2. 로그인 회원이 방장인지 확인한다.
3. status가 FINISHED면 중단한다.
4. status를 DELETED로 변경한다. (PARTY_MEMBER·PARTY_CHAT 행은 그대로 둠)
5. 커밋한다.

**Response 200 (data)**
```json
null
```

**오류**

| HTTP | error | message | 발생 조건 |
| --- | --- | --- | --- |
| 403 | NOT_HOST | 방장만 삭제할 수 있습니다. | 방장이 아님 |
| 404 | PARTY_NOT_FOUND | 식사팟을 찾을 수 없습니다. | 없는 partyId |
| 404 | PARTY_DELETED | 삭제된 식사팟입니다. | 이미 DELETED |
| 409 | PARTY_FINISHED | 식사가 완료된 식사팟입니다. | FINISHED |

---

### GET /api/parties/{partyId}/chats — 대화 메시지 조회 (폴링)

**요구사항**: 식사팟 상세 진입 시 afterId 없이 전체 메시지를 받고, 이후 3초마다 마지막으로 받은 chatId를 afterId로 보내 새 메시지만 받는다(폴링). 참여자만 조회할 수 있다. FE는 화면을 떠나면 폴링을 멈추고, readOnly가 true면 입력창을 비활성화한다.

**Request — Path**: partyId (number)

**Request — Query**

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| afterId | number | X | 이 값보다 큰 chatId만 반환. 없으면 전체 |

**시스템 처리**
1. MEAL_PARTY를 조회한다. 없거나 DELETED면 중단한다.
2. 로그인 회원이 참여자인지 확인한다.
3. party_id가 같고 chat_id > afterId인 PARTY_CHAT을 chat_id 오름차순으로 조회한다. (afterId 없으면 전체, 최대 200개)
4. 작성자 닉네임과 본인 메시지 여부(mine)를 함께 반환한다.
5. readOnly = (status가 FINISHED)로 반환한다.

**Response 200 (data)**
```json
{
  "readOnly": false,
  "chats": [
    {
      "chatId": 104,
      "type": "SYSTEM",
      "nickname": null,
      "content": "방장이 일정을 확정했습니다 · 12:00 · 1층 로비",
      "mine": false,
      "createdAt": "2026-11-17T11:10:00"
    },
    {
      "chatId": 105,
      "type": "TEXT",
      "nickname": "슥사인",
      "content": "확정했어요",
      "mine": true,
      "createdAt": "2026-11-17T11:20:03"
    }
  ]
}
```
- 새 메시지가 없으면 chats는 빈 배열
- 다음 날 0시에 대화 기록이 삭제된 뒤에는 빈 배열 + readOnly true

**오류**

| HTTP | error | message | 발생 조건 |
| --- | --- | --- | --- |
| 403 | NOT_JOINED | 참여자만 대화를 볼 수 있습니다. | 참여자가 아님 (나간 경우 포함) |
| 404 | PARTY_NOT_FOUND | 식사팟을 찾을 수 없습니다. | 없는 partyId |
| 404 | PARTY_DELETED | 삭제된 식사팟입니다. | DELETED (폴링 중 팟 삭제 포함, FE는 폴링 중단 후 목록 이동) |

---

### POST /api/parties/{partyId}/chats — 대화 메시지 전송

**요구사항**: 식사팟 참여자가 텍스트 메시지를 보낸다. 식사 완료(FINISHED) 후에는 읽기 전용이라 보낼 수 없다. FE는 전송 성공 후 즉시 조회(폴링 1회)를 실행해 화면에 반영한다.

**Request — Path**: partyId (number)

**Request — Body**
```json
{
  "content": "12시에 1층 로비에서 만나요"
}
```

| 필드 | 타입 | 필수 | 규칙 |
| --- | --- | --- | --- |
| content | string | O | 1~200자, 앞뒤 공백 제거 후 빈 값 불가 |

**시스템 처리**
1. content를 검증한다.
2. MEAL_PARTY를 조회한다. 없거나 DELETED면 중단한다.
3. 로그인 회원이 참여자인지 확인한다.
4. status가 FINISHED면 중단한다.
5. PARTY_CHAT을 저장한다. (msg_type = TEXT)
6. 생성된 chatId를 반환한다.

**Response 200 (data)**
```json
{
  "chatId": 105,
  "createdAt": "2026-11-17T11:20:03"
}
```

**오류**

| HTTP | error | message | 발생 조건 |
| --- | --- | --- | --- |
| 400 | INVALID_REQUEST | 메시지를 입력해 주세요. | 빈 값, 200자 초과 |
| 403 | NOT_JOINED | 참여자만 대화할 수 있습니다. | 참여자가 아님 |
| 404 | PARTY_NOT_FOUND | 식사팟을 찾을 수 없습니다. | 없는 partyId |
| 404 | PARTY_DELETED | 삭제된 식사팟입니다. | DELETED |
| 409 | CHAT_READ_ONLY | 식사가 완료되어 메시지를 보낼 수 없습니다. | FINISHED |

---

### GET /api/parties/{partyId}/invite-link — 식사팟 초대 링크 조회

**요구사항**: 참여 중인 식사팟의 초대 문구와 경로를 반환한다. FE는 '문구 + 사이트 주소 + path'를 클립보드에 복사한다. 링크를 받은 사람은 로그인 후 식사팟 상세로 이동한다(로그인 사용자 조회 API의 redirect 처리).

**Request — Path**: partyId (number)

**시스템 처리**
1. MEAL_PARTY를 조회한다. 없거나 DELETED면 중단한다.
2. 로그인 회원이 참여자인지 확인한다.
3. status가 RECRUITING·SCHEDULED가 아니면 중단한다. (마감·완료된 팟은 초대 불가)
4. 문구 '[닉네임]님이 [식당명] [점심/저녁] 식사에 초대하셨습니다~'와 상세 화면 경로를 만든다. 오늘이 아닌 팟은 식당명 앞에 날짜를 넣는다: '[닉네임]님이 11월 21일 [식당명] 점심 식사에 초대하셨습니다~' (2026-11-20 추가)

**Response 200 (data)**
```json
{
  "text": "슥사인님이 OO국밥 세종대점 점심 식사에 초대하셨습니다~",
  "path": "/party-detail.html?partyId=31"
}
```
- path: FE 화면 경로 규칙이 정해지면 그에 맞춰 수정

**오류**

| HTTP | error | message | 발생 조건 |
| --- | --- | --- | --- |
| 403 | NOT_JOINED | 참여 중인 식사팟만 초대할 수 있습니다. | 참여자가 아님 |
| 404 | PARTY_NOT_FOUND | 식사팟을 찾을 수 없습니다. | 없는 partyId |
| 404 | PARTY_DELETED | 삭제된 식사팟입니다. | DELETED |
| 409 | INVALID_STATUS | 마감되었거나 완료된 식사팟은 초대할 수 없습니다. | FULL, CLOSED, FINISHED |

---

### POST /api/parties/{partyId}/members — 식사팟 참여하기 (1차)

**요구사항**: 수락 절차 없이 식사팟에 참여한다(1차 참여). RECRUITING·SCHEDULED 상태이고 정원 미만일 때만 가능하다. 동시에 여러 명이 마지막 자리에 참여해도 정원을 넘지 않아야 한다. 정원에 도달하면 자동 마감(FULL)한다. 같은 날짜·같은 끼니에는 팟 하나만 참여할 수 있다(참여 확정 전이어도).

> 2026-11-23 변경: 한 끼에 팟 하나 — 같은 날짜·같은 끼니의 다른 팟(DELETED 제외)에 이미 들어가 있으면 참여 불가 (409 `ALREADY_JOINED_SAME_MEAL`). 초대 링크로 들어와도 같다.

**Request — Path**: partyId (number) / Body 없음

**시스템 처리**
1. 트랜잭션을 시작하고 MEAL_PARTY 행을 잠금 조회한다. (SELECT ... FOR UPDATE)
2. 팟이 없거나 DELETED면 중단한다.
3. status가 FINISHED면 중단한다.
4. 이미 PARTY_MEMBER에 있으면 중단한다.
   4-1. 같은 party_date·같은 meal_type의 다른 팟(DELETED 제외, 확정 여부·FINISHED 무관)에 PARTY_MEMBER로 있으면 중단한다.
5. status가 FULL·CLOSED이거나 현재 인원 ≥ capacity면 중단한다.
6. PARTY_MEMBER를 저장한다. (confirmed_yn = N)
7. 참여 후 인원이 capacity와 같으면 status = FULL로 변경한다.
8. PARTY_CHAT에 시스템 메시지 '[닉네임]님이 참여했습니다'를 저장한다.
9. 커밋한다. FE는 식사팟 상세로 이동(또는 상세 재조회)한다.

**Response 200 (data)**
```json
{
  "partyId": 31,
  "memberCount": 5,
  "status": "SCHEDULED"
}
```

**오류**

| HTTP | error | message | 발생 조건 |
| --- | --- | --- | --- |
| 404 | PARTY_NOT_FOUND | 식사팟을 찾을 수 없습니다. | 없는 partyId |
| 404 | PARTY_DELETED | 삭제된 식사팟입니다. | DELETED |
| 409 | ALREADY_JOINED | 이미 참여 중인 식사팟입니다. | 중복 참여 |
| 409 | ALREADY_JOINED_SAME_MEAL | 같은 시간대에 이미 참여 중인 식사팟이 있습니다. | 같은 날짜·끼니의 다른 팟에 이미 참여 중 |
| 409 | PARTY_FULL | 이미 마감된 식사팟입니다. | FULL, CLOSED, 정원 도달 |
| 409 | PARTY_FINISHED | 식사가 완료된 식사팟입니다. | FINISHED |

---

### DELETE /api/parties/{partyId}/members/me — 식사팟 나가기

**요구사항**: 식사팟에서 나간다. 방장이 나가면 식사팟이 삭제(DELETED)된다. 참여자가 나가면 PARTY_MEMBER 행을 삭제한다(대화 기록은 남음). 참여를 확정한 사람은 기준 시간 30분 전까지만 나갈 수 있다. 자동 마감(FULL)됐던 팟은 인원이 줄면 다시 모집 상태로 돌아가고, 방장이 마감(CLOSED)한 팟은 마감을 유지한다. FE는 방장일 때 '식사팟이 삭제됩니다' 확인 창을 띄운다.

**Request — Path**: partyId (number)

**시스템 처리**
1. 트랜잭션을 시작하고 MEAL_PARTY 행을 잠금 조회한다. 없거나 DELETED면 중단한다.
2. status가 FINISHED면 중단한다.
3. 로그인 회원이 참여자가 아니면 중단한다.
4. 방장이면 식사팟 삭제 API와 같은 처리(status = DELETED)를 하고 partyDeleted = true로 응답한다.
5. 참여 확정자이고 현재 시각이 기준 시간 30분 전 이후면 중단한다.
6. PARTY_MEMBER를 삭제한다.
7. status = FULL이고 인원이 capacity 미만이 되면, 일정이 있으면 SCHEDULED, 없으면 RECRUITING으로 변경한다. (CLOSED는 유지)
8. PARTY_CHAT에 시스템 메시지 '[닉네임]님이 나갔습니다'를 저장한다.
9. 커밋한다. FE는 식사팟 목록으로 이동한다.

**Response 200 (data)**
```json
{
  "partyDeleted": false
}
```

**오류**

| HTTP | error | message | 발생 조건 |
| --- | --- | --- | --- |
| 404 | PARTY_NOT_FOUND | 식사팟을 찾을 수 없습니다. | 없는 partyId |
| 404 | PARTY_DELETED | 삭제된 식사팟입니다. | DELETED |
| 409 | NOT_JOINED | 참여하지 않은 식사팟입니다. | 참여자가 아님 |
| 409 | LEAVE_DEADLINE_PASSED | 식사 30분 전부터는 나갈 수 없습니다. | 확정자, 기준 시간 30분 전 이후 |
| 409 | PARTY_FINISHED | 식사가 완료된 식사팟입니다. | FINISHED |

---

### POST /api/parties/{partyId}/members/me/confirm — 참여 확정 (2차)

**요구사항**: 방장이 일정을 확정한 식사팟에서 내 참여를 확정한다(2차 참여). 방장은 일정을 정할 때 자동으로 확정되므로, 방장이 이 API를 쓰는 경우는 확정을 취소했다가 다시 확정할 때뿐이다. 확정자만 식사 완료 후 따봉 요청 대상이 된다.

**Request — Path**: partyId (number) / Body 없음

**시스템 처리**
1. MEAL_PARTY를 조회한다. 없거나 DELETED면 중단한다.
2. status가 FINISHED면 중단한다.
3. 로그인 회원이 참여자인지 확인한다.
4. 일정(confirmed_time)이 없으면 중단한다.
5. 이미 확정했으면 중단한다.
6. PARTY_MEMBER.confirmed_yn을 Y로 변경한다.
7. PARTY_CHAT에 시스템 메시지 '[닉네임]님이 참여를 확정했습니다'를 저장한다.
8. 확정 인원 수를 반환한다.

**Response 200 (data)**
```json
{
  "confirmed": true,
  "confirmedCount": 4
}
```

**오류**

| HTTP | error | message | 발생 조건 |
| --- | --- | --- | --- |
| 404 | PARTY_NOT_FOUND | 식사팟을 찾을 수 없습니다. | 없는 partyId |
| 404 | PARTY_DELETED | 삭제된 식사팟입니다. | DELETED |
| 409 | NOT_JOINED | 참여하지 않은 식사팟입니다. | 참여자가 아님 |
| 409 | SCHEDULE_NOT_SET | 방장이 아직 일정을 확정하지 않았습니다. | 일정 없음 |
| 409 | ALREADY_CONFIRMED | 이미 참여를 확정했습니다. | 중복 확정 |
| 409 | PARTY_FINISHED | 식사가 완료된 식사팟입니다. | FINISHED |

---

### DELETE /api/parties/{partyId}/members/me/confirm — 참여 확정 취소

**요구사항**: 참여 확정을 취소한다. 기준 시간(확정 시간) 30분 전까지만 가능하다. 취소해도 참여자 자격과 대화 참여는 유지된다. (confirmed_yn만 N으로 되돌리며 행은 삭제하지 않는다)

**Request — Path**: partyId (number)

**시스템 처리**
1. MEAL_PARTY를 조회한다. 없거나 DELETED면 중단한다.
2. status가 FINISHED면 중단한다.
3. 로그인 회원이 참여자이고 확정 상태인지 확인한다.
4. 현재 시각이 confirmed_time 30분 전 이후면 중단한다.
5. PARTY_MEMBER.confirmed_yn을 N으로 변경한다.
6. PARTY_CHAT에 시스템 메시지 '[닉네임]님이 참여 확정을 취소했습니다'를 저장한다.
7. 확정 인원 수를 반환한다.

**Response 200 (data)**
```json
{
  "confirmed": false,
  "confirmedCount": 3
}
```

**오류**

| HTTP | error | message | 발생 조건 |
| --- | --- | --- | --- |
| 404 | PARTY_NOT_FOUND | 식사팟을 찾을 수 없습니다. | 없는 partyId |
| 404 | PARTY_DELETED | 삭제된 식사팟입니다. | DELETED |
| 409 | NOT_JOINED | 참여하지 않은 식사팟입니다. | 참여자가 아님 |
| 409 | NOT_CONFIRMED | 참여를 확정하지 않았습니다. | 확정 상태가 아님 |
| 409 | CANCEL_DEADLINE_PASSED | 식사 30분 전부터는 확정을 취소할 수 없습니다. | 기준 시간 30분 전 이후 |
| 409 | PARTY_FINISHED | 식사가 완료된 식사팟입니다. | FINISHED |

---

### PATCH /api/parties/{partyId}/schedule — 일정 확정·수정 (방장)

**요구사항**: 방장이 확정 시간·만날 장소·메모를 설정한다. 처음 설정하면 RECRUITING은 SCHEDULED로 바뀌고(FULL·CLOSED는 유지), 참여자에게 [참여 확정] 버튼이 열린다. 방장은 일정을 정하면 자동으로 참석 확정된다. 이미 설정된 일정을 수정하면 모든 참여자의 확정이 초기화되어 다시 확정을 받고, 방장만 다시 자동 확정된다.

> 2026-11-23 변경: 식사팟 흐름 개편 — ① 방장이 시간·메모로 방을 만듦 → ② 방장이 최종 시간·장소를 정하고 참석 확정 요청 → ③ 방장과 참여자가 참석 확정. 방장 자동 확정(8-1) 추가

**Request — Path**: partyId (number)

**Request — Body**
```json
{
  "confirmedTime": "12:00",
  "meetPlace": "1층 로비",
  "confirmMemo": "늦으면 채팅 남겨 주세요"
}
```

| 필드 | 타입 | 필수 | 규칙 |
| --- | --- | --- | --- |
| confirmedTime | string | O | HH:mm. 팟의 mealType 시간대, 30분 단위, 팟의 식사 날짜 + 시간이 현재 시각 이후 |
| meetPlace | string | O | 1~30자 |
| confirmMemo | string | X | 최대 50자 |

**시스템 처리**
1. 필수값·형식·길이를 검증한다.
2. 트랜잭션을 시작하고 MEAL_PARTY 행을 잠금 조회한다. 없거나 DELETED면 중단한다.
3. 로그인 회원이 방장인지 확인한다.
4. status가 FINISHED면 중단한다.
5. confirmedTime이 mealType 시간대·30분 단위이고, 팟의 party_date + confirmedTime이 현재 시각 이후인지 확인한다.
6. 기존 일정이 있었으면 모든 PARTY_MEMBER의 confirmed_yn을 N으로 초기화한다.
7. confirmed_time, meet_place, confirm_memo를 저장한다.
8. status가 RECRUITING이면 SCHEDULED로 변경한다. (SCHEDULED·FULL·CLOSED는 유지)
8-1. 방장의 PARTY_MEMBER.confirmed_yn을 Y로 변경한다. (방장 자동 참석 확정, 6번 초기화 뒤에 처리)
9. PARTY_CHAT에 시스템 메시지를 저장한다. 최초: '방장이 일정을 확정했습니다 · 12:00 · 1층 로비' / 수정: '방장이 일정을 변경했습니다 · 12:30 · 정문. 참여 확정을 다시 해 주세요'
10. 커밋한다.

**Response 200 (data)**
```json
{
  "status": "SCHEDULED",
  "confirmationsReset": false
}
```
- confirmationsReset: 수정으로 확정이 초기화되었으면 true

**오류**

| HTTP | error | message | 발생 조건 |
| --- | --- | --- | --- |
| 400 | INVALID_REQUEST | 입력값을 확인해 주세요. | 필수값 누락, 길이 초과 |
| 400 | INVALID_MEAL_TIME | 선택할 수 없는 식사 시간입니다. | 시간대·30분 단위 위반, 지난 시간 |
| 403 | NOT_HOST | 방장만 일정을 정할 수 있습니다. | 방장이 아님 |
| 404 | PARTY_NOT_FOUND | 식사팟을 찾을 수 없습니다. | 없는 partyId |
| 404 | PARTY_DELETED | 삭제된 식사팟입니다. | DELETED |
| 409 | PARTY_FINISHED | 식사가 완료된 식사팟입니다. | FINISHED |

---

### PATCH /api/parties/{partyId}/status — 모집 마감·재개 (방장)

**요구사항**: 방장이 모집을 마감하거나 다시 연다. 마감 후에도 대화와 참여 확정은 계속할 수 있다. 마감하면 CLOSED(방장 마감)가 되고, 누가 나가도 마감이 유지된다. 다시 열면 일정이 있으면 SCHEDULED, 없으면 RECRUITING이 된다. 다시 열 때 정원이 가득 차 있으면 FULL(자동 마감)이 된다.

**Request — Path**: partyId (number)

**Request — Body**
```json
{
  "action": "CLOSE"
}
```

| 필드 | 타입 | 필수 | 규칙 |
| --- | --- | --- | --- |
| action | string | O | CLOSE(마감), REOPEN(재개) |

**시스템 처리**
1. action 값을 검증한다.
2. 트랜잭션을 시작하고 MEAL_PARTY 행을 잠금 조회한다. 없거나 DELETED면 중단한다.
3. 로그인 회원이 방장인지 확인한다.
4. status가 FINISHED면 중단한다.
5. CLOSE: status가 RECRUITING·SCHEDULED·FULL이어야 한다. status = CLOSED.
6. REOPEN: status가 CLOSED여야 한다. 인원 = capacity면 FULL, 아니면 일정이 있으면 SCHEDULED, 없으면 RECRUITING.
7. PARTY_CHAT에 시스템 메시지 '방장이 모집을 마감했습니다' 또는 '방장이 모집을 다시 열었습니다'를 저장한다.
8. 커밋한다.

**Response 200 (data)**
```json
{
  "status": "CLOSED"
}
```

**오류**

| HTTP | error | message | 발생 조건 |
| --- | --- | --- | --- |
| 400 | INVALID_REQUEST | 잘못된 요청입니다. | action 누락·잘못된 값 |
| 403 | NOT_HOST | 방장만 변경할 수 있습니다. | 방장이 아님 |
| 404 | PARTY_NOT_FOUND | 식사팟을 찾을 수 없습니다. | 없는 partyId |
| 404 | PARTY_DELETED | 삭제된 식사팟입니다. | DELETED |
| 409 | INVALID_STATUS | 현재 상태에서는 변경할 수 없습니다. | CLOSED인 팟을 CLOSE, CLOSED가 아닌 팟을 REOPEN |
| 409 | PARTY_FINISHED | 식사가 완료된 식사팟입니다. | FINISHED |

---

### POST /api/parties/{partyId}/thumbs — 따봉 등록

**요구사항**: 함께 식사한 식당에 따봉을 남긴다. 식사 완료(FINISHED)된 팟의 참여 확정자만 가능하고, 팟 1건당 1회다. 따봉 대상 식당은 팟의 식당이다. 따봉은 관심과 별개이며 랭킹(최근 30일 따봉 수)에 사용한다.

**Request — Path**: partyId (number) / Body 없음

**시스템 처리**
1. MEAL_PARTY를 조회한다. 없거나 DELETED면 중단한다.
2. status가 FINISHED가 아니면 중단한다.
3. 트랜잭션을 시작하고 로그인 회원의 PARTY_MEMBER 행을 잠금 조회한다. 없거나 confirmed_yn = N이면 중단한다.
4. thumbs_up_yn이 이미 Y면 중단한다. (행 잠금으로 동시 요청 중복 방지)
5. PARTY_MEMBER.thumbs_up_yn을 Y로 변경하고 커밋한다. (따봉 대상 식당 = MEAL_PARTY.restaurant_id)
6. 해당 식당의 최근 30일 따봉 수를 반환한다. (집계 기준: 공통 규칙 '따봉 집계')

**Response 200 (data)**
```json
{
  "restaurantId": 12,
  "thumbsCount30d": 43
}
```

**오류**

| HTTP | error | message | 발생 조건 |
| --- | --- | --- | --- |
| 403 | NOT_CONFIRMED_MEMBER | 참여를 확정한 식사에만 따봉을 남길 수 있습니다. | 참여자가 아니거나 미확정 |
| 404 | PARTY_NOT_FOUND | 식사팟을 찾을 수 없습니다. | 없는 partyId |
| 404 | PARTY_DELETED | 삭제된 식사팟입니다. | DELETED |
| 409 | PARTY_NOT_FINISHED | 식사가 끝난 뒤에 따봉을 남길 수 있습니다. | FINISHED가 아님 |
| 409 | ALREADY_THUMBED | 이미 따봉을 남겼습니다. | 중복 등록 |

---
## 부록. 전체 오류 코드 (ErrorCode enum 작성용)

API별 오류 표를 모은 것이다. message는 API마다 조금씩 다를 수 있으니(예: INVALID_REQUEST), 실제 응답 문구는 각 API 표를 따른다.

| HTTP | error | 대표 message | 사용 API |
| --- | --- | --- | --- |
| 400 | INVALID_REQUEST | 입력값을 확인해 주세요. | 공통 |
| 400 | PASSWORD_MISMATCH | 비밀번호가 일치하지 않습니다. | 회원가입 |
| 400 | INVALID_MAP_URL | 네이버 지도 URL만 입력할 수 있습니다. | 식당 등록 |
| 400 | INVALID_TAG | 선택할 수 없는 태그입니다. | 리뷰 작성 |
| 400 | INVALID_IMAGE | jpg·png 형식, 5MB 이하 사진만 올릴 수 있습니다. | 리뷰 작성 |
| 400 | INVALID_MEAL_TIME | 선택할 수 없는 식사 시간입니다. | 식사팟 생성, 일정 확정 |
| 401 | UNAUTHORIZED | 로그인이 필요합니다. | 공통 |
| 401 | LOGIN_FAILED | 아이디 또는 비밀번호가 일치하지 않습니다. | 로그인 |
| 403 | FORBIDDEN | 본인이 작성한 리뷰만 삭제할 수 있습니다. | 리뷰 삭제 |
| 403 | NOT_HOST | 방장만 ○○할 수 있습니다. | 식사팟 삭제, 일정 확정, 모집 마감·재개 |
| 403 | NOT_JOINED | 참여자만 대화를 볼 수 있습니다. / 참여 중인 식사팟만 초대할 수 있습니다. | 대화 조회·전송, 초대 링크 |
| 403 | NOT_CONFIRMED_MEMBER | 참여를 확정한 식사에만 따봉을 남길 수 있습니다. | 따봉 등록 |
| 404 | NOT_FOUND | 대상 데이터 없음 | 공통 |
| 404 | RESTAURANT_NOT_FOUND | 식당을 찾을 수 없습니다. | 식당 상세·관심·리뷰·수정 제안, 식사팟 생성 |
| 404 | MAP_PLACE_NOT_FOUND | 존재하지 않는 주소입니다. | 식당 등록 |
| 404 | INTEREST_NOT_FOUND | 관심 등록되지 않은 식당입니다. | 관심 해제 |
| 404 | REVIEW_NOT_FOUND | 리뷰를 찾을 수 없습니다. | 리뷰 삭제 |
| 404 | PARTY_NOT_FOUND | 식사팟을 찾을 수 없습니다. | 식사팟 API 전체 |
| 404 | PARTY_DELETED | 삭제된 식사팟입니다. | 식사팟 API 전체 |
| 409 | LOGIN_ID_ALREADY_EXISTS | 이미 사용 중인 아이디입니다. | 회원가입 |
| 409 | NICKNAME_ALREADY_EXISTS | 이미 사용 중인 닉네임입니다. | 회원가입 |
| 409 | RESTAURANT_ALREADY_EXISTS | 이미 등록된 식당입니다. | 식당 등록 |
| 409 | ALREADY_INTERESTED | 이미 관심 등록한 식당입니다. | 관심 등록 |
| 409 | ALREADY_JOINED | 이미 참여 중인 식사팟입니다. | 참여하기 |
| 409 | ALREADY_JOINED_SAME_MEAL | 같은 시간대에 이미 참여 중인 식사팟이 있습니다. | 참여하기, 식사팟 생성 |
| 409 | NOT_JOINED | 참여하지 않은 식사팟입니다. | 나가기, 참여 확정·취소 |
| 409 | PARTY_FULL | 이미 마감된 식사팟입니다. | 참여하기 |
| 409 | PARTY_FINISHED | 식사가 완료된 식사팟입니다. | 식사팟 변경 API 전체 |
| 409 | PARTY_NOT_FINISHED | 식사가 끝난 뒤에 따봉을 남길 수 있습니다. | 따봉 등록 |
| 409 | ALREADY_THUMBED | 이미 따봉을 남겼습니다. | 따봉 등록 |
| 409 | LEAVE_DEADLINE_PASSED | 식사 30분 전부터는 나갈 수 없습니다. | 나가기 |
| 409 | SCHEDULE_NOT_SET | 방장이 아직 일정을 확정하지 않았습니다. | 참여 확정 |
| 409 | ALREADY_CONFIRMED | 이미 참여를 확정했습니다. | 참여 확정 |
| 409 | NOT_CONFIRMED | 참여를 확정하지 않았습니다. | 확정 취소 |
| 409 | CANCEL_DEADLINE_PASSED | 식사 30분 전부터는 확정을 취소할 수 없습니다. | 확정 취소 |
| 409 | CHAT_READ_ONLY | 식사가 완료되어 메시지를 보낼 수 없습니다. | 대화 전송 |
| 409 | INVALID_STATUS | 현재 상태에서는 변경할 수 없습니다. | 모집 마감·재개, 초대 링크 |
| 409 | NOT_ENOUGH_CANDIDATES | 룰렛을 돌릴 식당이 부족합니다. | 룰렛 실행 |
| 500 | INTERNAL_SERVER_ERROR | 서버 오류 | 공통 |
| 502 | MAP_API_ERROR | 지도 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요. | URL 검증, 식당 등록 |

> NOT_JOINED는 API에 따라 403(대화·초대)과 409(나가기·확정)로 HTTP 상태가 다르다. enum을 하나로 만들 경우 팀에서 통일 여부를 정한다.
