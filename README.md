# 슥밥 (Sseukbap)

식사 메뉴 추천 및 식사팟 모집 서비스 — 삼성 SDS SCSA 팀 프로젝트

## 기술 스택

| 구분 | 버전 |
| --- | --- |
| Java | 17 |
| Spring Boot | 4.1.2 (Hibernate 7.4) |
| 빌드 | Gradle (Groovy DSL) |
| DB | Oracle XE 11g, ojdbc8 19.33.0.0, `OracleLegacyDialect` |
| ORM | Spring Data JPA |
| 기타 | Validation, Lombok, Jsoup, spring-security-crypto |
| 프론트 | HTML / CSS / JS (`src/main/resources/static`) |

## 실행 방법

1. 저장소를 받고 `dev` 브랜치로 이동해요.
   ```bash
   git clone https://github.com/Zon1t/web_project.git
   cd web_project
   git checkout dev
   ```
2. `src/main/resources/application-local.yml.example`을 복사해 `application-local.yml`을 만들고 본인 DB 계정과 네이버 API 키를 입력해요. (이 파일은 Git에 올라가지 않아요)
3. SQL Developer에서 `docs/sql/`의 스크립트를 순서대로 실행해 테이블을 만들어요.
4. IntelliJ에서 프로젝트를 열고 Gradle 동기화 후 `SseukbapApplication`을 실행해요.
5. http://localhost:8081 에 접속해요. (Oracle XE 11g가 8080을 쓰기 때문에 8081 사용)

빌드만 확인할 때는 터미널(PowerShell)에서 `.\gradlew.bat clean build -x test`를 실행해요.

## 패키지 구조

```
com.sseukbap
├─ common/      공통 응답, 예외, 설정, 인터셉터 (C)
├─ auth/        회원가입·로그인 (C)
├─ member/      회원, 마이페이지 (C)
├─ cafeteria/   구내식당 메뉴·크롤링 (C)
├─ restaurant/  식당·관심 (B), 랭킹·룰렛 (C)
├─ review/      리뷰·태그 (B)
└─ party/       식사팟·대화·따봉·스케줄러 (A)

각 도메인: controller / service / repository / dto / entity
```

## 브랜치 전략

- `main`: 발표용. 직접 push 금지
- `dev`: 통합 브랜치. 매일 작업 전 `git pull`
- `feature/도메인-기능`: 기능 개발 (예: `feature/party-join`)

작업 흐름: `dev`에서 `feature/...` 생성 → 작업·커밋 → PR(feature → dev) → 리뷰 짝 1명 승인 → **Squash and merge**

## 커밋 메시지

`타입: 한글 내용` (예: `feat: 식사팟 참여하기 API 구현`)

| 타입 | 용도 |
| --- | --- |
| feat | 새 기능 |
| fix | 버그 수정 |
| refactor | 동작 변화 없는 코드 개선 |
| style | 포맷, 세미콜론 등 |
| docs | 문서 |
| test | 테스트 |
| chore | 설정, 빌드 |

## 문서

프로젝트 규칙과 설계 요약은 `docs/PROJECT_CONTEXT.md`를 보세요. 원본은 노션 문서예요.
