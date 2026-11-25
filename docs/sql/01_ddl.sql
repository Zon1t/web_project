-- =====================================================================
-- 01_ddl.sql : 슥밥 테이블 11개 + 제약조건 + 인덱스 (Oracle XE 11g)
-- 기준: docs/PROJECT_CONTEXT.md 8장 ERD (원본은 노션 ERD)
-- 규칙
--   - PK: 숫자 대리키 단일 PK, 시퀀스는 02_sequence.sql
--   - 제약조건 이름: PK_테이블, FK_자식_부모, UK_테이블_컬럼, CK_테이블_컬럼
--   - 문자열 VARCHAR2(byte), 한글 1자 = 3byte
--   - Y/N: CHAR(1) + CHECK, 일시: DATE DEFAULT SYSDATE, 식사 시간: VARCHAR2(5) 'HH:mm'
--   - 11g는 객체 이름이 30자 이하여야 한다.
-- 실행 순서: (00_drop.sql) → 01_ddl.sql → 02_sequence.sql → 03_init_data.sql → 04_dummy_data.sql
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. MEMBER (회원)
-- ---------------------------------------------------------------------
CREATE TABLE MEMBER (
    MEMBER_ID   NUMBER                      NOT NULL,
    LOGIN_ID    VARCHAR2(20)                NOT NULL,   -- 내부 아이디: 영문·숫자 4~20자 (2026-11-25 T3: 가입 시 이메일 앞부분으로 자동 생성)
    PASSWORD    VARCHAR2(60)                NOT NULL,   -- BCrypt 해시
    NICKNAME    VARCHAR2(36 CHAR)           NOT NULL,   -- 2~12자 (2026-11-25 T3: 30바이트 → 36 CHAR, 기존 DB는 10_email_login.sql)
    CREATED_AT  DATE         DEFAULT SYSDATE NOT NULL,
    -- 2026-11-24 추가 (기존 DB는 08_account_admin.sql)
    STATUS                  VARCHAR2(10)  DEFAULT 'ACTIVE' NOT NULL,   -- ACTIVE / WITHDRAWN
    WITHDRAWN_AT            DATE,                                      -- 탈퇴 일시
    EMAIL                   VARCHAR2(100),                             -- 회사 이메일 = 로그인 아이디 (2026-11-25 T3, 기존 회원 때문에 아직 NULL 허용)
    RESET_TOKEN_HASH        VARCHAR2(64),                              -- 재설정 토큰 SHA-256 16진수
    RESET_TOKEN_EXPIRES_AT  DATE,                                      -- 재설정 토큰 만료
    ROLE                    VARCHAR2(10)  DEFAULT 'USER' NOT NULL,     -- USER / ADMIN
    CONSTRAINT PK_MEMBER          PRIMARY KEY (MEMBER_ID),
    CONSTRAINT UK_MEMBER_LOGIN_ID UNIQUE (LOGIN_ID),
    CONSTRAINT UK_MEMBER_NICKNAME UNIQUE (NICKNAME),
    CONSTRAINT UK_MEMBER_EMAIL    UNIQUE (EMAIL),
    CONSTRAINT CK_MEMBER_STATUS   CHECK (STATUS IN ('ACTIVE', 'WITHDRAWN')),
    CONSTRAINT CK_MEMBER_ROLE     CHECK (ROLE IN ('USER', 'ADMIN'))
);


-- ---------------------------------------------------------------------
-- 2. RESTAURANT (식당)
-- ---------------------------------------------------------------------
CREATE TABLE RESTAURANT (
    RESTAURANT_ID  NUMBER                       NOT NULL,
    NAME           VARCHAR2(90)                 NOT NULL,   -- 30자
    CATEGORY       VARCHAR2(20)                 NOT NULL,
    MAP_URL        VARCHAR2(500)                NOT NULL,   -- 네이버 지도 URL
    ADDRESS        VARCHAR2(300),                           -- 네이버 API
    LAT            NUMBER(10, 7),                           -- 위도
    LNG            NUMBER(10, 7),                           -- 경도
    MEMBER_ID      NUMBER,                                  -- 등록자 (구내식당 초기 데이터는 NULL)
    CREATED_AT     DATE          DEFAULT SYSDATE NOT NULL,
    HIDDEN_YN      CHAR(1)       DEFAULT 'N'     NOT NULL,   -- 관리자 숨김 (2026-11-24)
    CONSTRAINT PK_RESTAURANT         PRIMARY KEY (RESTAURANT_ID),
    CONSTRAINT CK_RESTAURANT_HIDDEN_YN CHECK (HIDDEN_YN IN ('Y', 'N')),
    CONSTRAINT UK_RESTAURANT_MAP_URL UNIQUE (MAP_URL),
    CONSTRAINT CK_RESTAURANT_CATEGORY CHECK (CATEGORY IN
        ('CAFETERIA', 'KOREAN', 'CHINESE', 'JAPANESE', 'WESTERN', 'SNACK', 'ETC', 'CAFE')),   -- CAFE: 2026-11-23 추가
    CONSTRAINT FK_RESTAURANT_MEMBER  FOREIGN KEY (MEMBER_ID) REFERENCES MEMBER (MEMBER_ID)
);


-- ---------------------------------------------------------------------
-- 3. REVIEW (리뷰)
-- ---------------------------------------------------------------------
CREATE TABLE REVIEW (
    REVIEW_ID      NUMBER                       NOT NULL,
    RESTAURANT_ID  NUMBER                       NOT NULL,
    MEMBER_ID      NUMBER                       NOT NULL,   -- 작성자
    CONTENT        VARCHAR2(300)                NOT NULL,   -- 100자
    IMAGE_PATH     VARCHAR2(300),                           -- 사진 경로
    CREATED_AT     DATE          DEFAULT SYSDATE NOT NULL,
    HIDDEN_YN      CHAR(1)       DEFAULT 'N'     NOT NULL,   -- 관리자 숨김 (2026-11-24)
    CONSTRAINT PK_REVIEW            PRIMARY KEY (REVIEW_ID),
    CONSTRAINT CK_REVIEW_HIDDEN_YN  CHECK (HIDDEN_YN IN ('Y', 'N')),
    CONSTRAINT FK_REVIEW_RESTAURANT FOREIGN KEY (RESTAURANT_ID) REFERENCES RESTAURANT (RESTAURANT_ID),
    CONSTRAINT FK_REVIEW_MEMBER     FOREIGN KEY (MEMBER_ID)     REFERENCES MEMBER (MEMBER_ID)
);


-- ---------------------------------------------------------------------
-- 4. TAG (리뷰 태그, 1~13 고정값이라 시퀀스 없음)
-- ---------------------------------------------------------------------
CREATE TABLE TAG (
    TAG_ID        NUMBER        NOT NULL,
    TAG_NAME      VARCHAR2(30)  NOT NULL,   -- '#' 없이 저장
    TAG_CATEGORY  VARCHAR2(20)  NOT NULL,
    CONSTRAINT PK_TAG              PRIMARY KEY (TAG_ID),
    CONSTRAINT UK_TAG_TAG_NAME     UNIQUE (TAG_NAME),
    CONSTRAINT CK_TAG_TAG_CATEGORY CHECK (TAG_CATEGORY IN
        ('MEAL_TYPE', 'GROUP_TYPE', 'PRICE', 'WAITING', 'ACCESSIBILITY'))
);


-- ---------------------------------------------------------------------
-- 5. REVIEW_TAG (리뷰-태그 매핑)
-- ---------------------------------------------------------------------
CREATE TABLE REVIEW_TAG (
    REVIEW_TAG_ID  NUMBER  NOT NULL,
    REVIEW_ID      NUMBER  NOT NULL,
    TAG_ID         NUMBER  NOT NULL,
    CONSTRAINT PK_REVIEW_TAG        PRIMARY KEY (REVIEW_TAG_ID),
    CONSTRAINT UK_REVIEW_TAG        UNIQUE (REVIEW_ID, TAG_ID),
    CONSTRAINT FK_REVIEW_TAG_REVIEW FOREIGN KEY (REVIEW_ID) REFERENCES REVIEW (REVIEW_ID) ON DELETE CASCADE,
    CONSTRAINT FK_REVIEW_TAG_TAG    FOREIGN KEY (TAG_ID)    REFERENCES TAG (TAG_ID)
);


-- ---------------------------------------------------------------------
-- 6. INTEREST (관심) - 해제 시 행 삭제
-- ---------------------------------------------------------------------
CREATE TABLE INTEREST (
    INTEREST_ID    NUMBER                      NOT NULL,
    RESTAURANT_ID  NUMBER                      NOT NULL,
    MEMBER_ID      NUMBER                      NOT NULL,
    CREATED_AT     DATE         DEFAULT SYSDATE NOT NULL,
    CONSTRAINT PK_INTEREST            PRIMARY KEY (INTEREST_ID),
    CONSTRAINT UK_INTEREST            UNIQUE (MEMBER_ID, RESTAURANT_ID),
    CONSTRAINT FK_INTEREST_RESTAURANT FOREIGN KEY (RESTAURANT_ID) REFERENCES RESTAURANT (RESTAURANT_ID),
    CONSTRAINT FK_INTEREST_MEMBER     FOREIGN KEY (MEMBER_ID)     REFERENCES MEMBER (MEMBER_ID)
);


-- ---------------------------------------------------------------------
-- 7. MEAL_PARTY (식사팟) - 소프트 삭제: 모든 조회·집계에 STATUS <> 'DELETED'
-- ---------------------------------------------------------------------
CREATE TABLE MEAL_PARTY (
    PARTY_ID        NUMBER                               NOT NULL,
    RESTAURANT_ID   NUMBER                               NOT NULL,
    HOST_ID         NUMBER                               NOT NULL,   -- 방장 → MEMBER
    PARTY_DATE      DATE                                 NOT NULL,   -- 식사 날짜(오늘~14일 후), 시간 없이 TRUNC 값
    MEAL_TYPE       VARCHAR2(10)                         NOT NULL,   -- LUNCH / DINNER
    MEAL_TIME       VARCHAR2(5)                          NOT NULL,   -- 예정 시간 'HH:mm'
    CAPACITY        NUMBER(2)     DEFAULT 4              NOT NULL,   -- 방장 포함 2~12
    STATUS          VARCHAR2(20)  DEFAULT 'RECRUITING'   NOT NULL,
    MEMO            VARCHAR2(150),                                   -- 한마디 50자
    CONFIRMED_TIME  VARCHAR2(5),                                     -- 방장 확정 시간 'HH:mm'
    MEET_PLACE      VARCHAR2(90),                                    -- 30자
    CONFIRM_MEMO    VARCHAR2(150),                                   -- 50자
    PRIVATE_YN      CHAR(1)       DEFAULT 'N'            NOT NULL,   -- 비공개 식사팟 (11/25, 11_private_party.sql)
    INVITE_CODE     VARCHAR2(32),                                    -- 비공개 초대 코드 22자, 공개는 NULL
    CREATED_AT      DATE          DEFAULT SYSDATE        NOT NULL,
    CONSTRAINT PK_MEAL_PARTY            PRIMARY KEY (PARTY_ID),
    CONSTRAINT CK_MEAL_PARTY_MEAL_TYPE  CHECK (MEAL_TYPE IN ('LUNCH', 'DINNER')),
    CONSTRAINT CK_MEAL_PARTY_CAPACITY   CHECK (CAPACITY BETWEEN 2 AND 12),
    CONSTRAINT CK_MEAL_PARTY_STATUS     CHECK (STATUS IN
        ('RECRUITING', 'SCHEDULED', 'FULL', 'CLOSED', 'FINISHED', 'DELETED')),
    CONSTRAINT CK_MEAL_PARTY_PRIVATE_YN CHECK (PRIVATE_YN IN ('Y', 'N')),
    CONSTRAINT UK_MEAL_PARTY_INVITE_CODE UNIQUE (INVITE_CODE),
    CONSTRAINT FK_MEAL_PARTY_RESTAURANT FOREIGN KEY (RESTAURANT_ID) REFERENCES RESTAURANT (RESTAURANT_ID),
    CONSTRAINT FK_MEAL_PARTY_MEMBER     FOREIGN KEY (HOST_ID)       REFERENCES MEMBER (MEMBER_ID)
);


-- ---------------------------------------------------------------------
-- 8. PARTY_MEMBER (식사팟 참여자, 따봉 포함) - 나가기 시 행 삭제
--    방장 여부는 MEAL_PARTY.HOST_ID로 판단
-- ---------------------------------------------------------------------
CREATE TABLE PARTY_MEMBER (
    PARTY_MEMBER_ID  NUMBER                      NOT NULL,
    PARTY_ID         NUMBER                      NOT NULL,
    MEMBER_ID        NUMBER                      NOT NULL,
    CONFIRMED_YN     CHAR(1)      DEFAULT 'N'     NOT NULL,   -- 참여 확정(2차)
    THUMBS_UP_YN     CHAR(1)      DEFAULT 'N'     NOT NULL,   -- 따봉 완료
    JOINED_AT        DATE         DEFAULT SYSDATE NOT NULL,   -- 참여하기(1차) 일시
    CONSTRAINT PK_PARTY_MEMBER              PRIMARY KEY (PARTY_MEMBER_ID),
    CONSTRAINT UK_PARTY_MEMBER              UNIQUE (PARTY_ID, MEMBER_ID),
    CONSTRAINT CK_PARTY_MEMBER_CONFIRMED_YN CHECK (CONFIRMED_YN IN ('Y', 'N')),
    CONSTRAINT CK_PARTY_MEMBER_THUMBS_UP_YN CHECK (THUMBS_UP_YN IN ('Y', 'N')),
    CONSTRAINT FK_PARTY_MEMBER_MEAL_PARTY   FOREIGN KEY (PARTY_ID)  REFERENCES MEAL_PARTY (PARTY_ID),
    CONSTRAINT FK_PARTY_MEMBER_MEMBER       FOREIGN KEY (MEMBER_ID) REFERENCES MEMBER (MEMBER_ID)
);


-- ---------------------------------------------------------------------
-- 9. PARTY_CHAT (식사팟 대화)
-- ---------------------------------------------------------------------
CREATE TABLE PARTY_CHAT (
    CHAT_ID     NUMBER                       NOT NULL,   -- 폴링 afterId 기준
    PARTY_ID    NUMBER                       NOT NULL,
    MEMBER_ID   NUMBER,                                  -- 시스템 메시지는 NULL
    CONTENT     VARCHAR2(600)                NOT NULL,   -- 200자
    MSG_TYPE    VARCHAR2(10)  DEFAULT 'TEXT'  NOT NULL,   -- TEXT / SYSTEM
    CREATED_AT  DATE          DEFAULT SYSDATE NOT NULL,
    CONSTRAINT PK_PARTY_CHAT            PRIMARY KEY (CHAT_ID),
    CONSTRAINT CK_PARTY_CHAT_MSG_TYPE   CHECK (MSG_TYPE IN ('TEXT', 'SYSTEM')),
    CONSTRAINT FK_PARTY_CHAT_MEAL_PARTY FOREIGN KEY (PARTY_ID)  REFERENCES MEAL_PARTY (PARTY_ID),
    CONSTRAINT FK_PARTY_CHAT_MEMBER     FOREIGN KEY (MEMBER_ID) REFERENCES MEMBER (MEMBER_ID)
);


-- ---------------------------------------------------------------------
-- 10. CAFETERIA_MENU (구내식당 메뉴, 메뉴 1개 = 1행)
-- ---------------------------------------------------------------------
CREATE TABLE CAFETERIA_MENU (
    MENU_ID     NUMBER                       NOT NULL,
    MENU_DATE   DATE                         NOT NULL,
    MEAL_TYPE   VARCHAR2(10)                 NOT NULL,   -- LUNCH / DINNER
    MENU_NAME   VARCHAR2(150)                NOT NULL,
    CREATED_AT  DATE          DEFAULT SYSDATE NOT NULL,   -- 수집일시
    CONSTRAINT PK_CAFETERIA_MENU           PRIMARY KEY (MENU_ID),
    CONSTRAINT UK_CAFETERIA_MENU           UNIQUE (MENU_DATE, MEAL_TYPE, MENU_NAME),
    CONSTRAINT CK_CAFETERIA_MENU_MEAL_TYPE CHECK (MEAL_TYPE IN ('LUNCH', 'DINNER'))
);


-- ---------------------------------------------------------------------
-- 11. SUGGESTION (식당 정보 수정 제안)
-- ---------------------------------------------------------------------
CREATE TABLE SUGGESTION (
    SUGGESTION_ID  NUMBER                        NOT NULL,
    RESTAURANT_ID  NUMBER                        NOT NULL,
    MEMBER_ID      NUMBER                        NOT NULL,
    CONTENT        VARCHAR2(1500)                NOT NULL,   -- 500자
    CREATED_AT     DATE           DEFAULT SYSDATE NOT NULL,
    STATUS         VARCHAR2(10)   DEFAULT 'OPEN'  NOT NULL,   -- OPEN / DONE (2026-11-24)
    CONSTRAINT PK_SUGGESTION            PRIMARY KEY (SUGGESTION_ID),
    CONSTRAINT CK_SUGGESTION_STATUS     CHECK (STATUS IN ('OPEN', 'DONE')),
    CONSTRAINT FK_SUGGESTION_RESTAURANT FOREIGN KEY (RESTAURANT_ID) REFERENCES RESTAURANT (RESTAURANT_ID),
    CONSTRAINT FK_SUGGESTION_MEMBER     FOREIGN KEY (MEMBER_ID)     REFERENCES MEMBER (MEMBER_ID)
);


-- ---------------------------------------------------------------------
-- 권장 인덱스 (PK·UK는 Oracle이 인덱스를 자동으로 만든다)
-- ---------------------------------------------------------------------
CREATE INDEX IX_REVIEW_RESTAURANT     ON REVIEW (RESTAURANT_ID, CREATED_AT);   -- 식당별 리뷰 페이징
CREATE INDEX IX_MEAL_PARTY_RESTAURANT ON MEAL_PARTY (RESTAURANT_ID, PARTY_DATE); -- 30일 따봉 집계
CREATE INDEX IX_MEAL_PARTY_DATE       ON MEAL_PARTY (PARTY_DATE, MEAL_TYPE);    -- 날짜별 식사팟 목록
CREATE INDEX IX_PARTY_CHAT_PARTY      ON PARTY_CHAT (PARTY_ID, CHAT_ID);        -- 대화 폴링
CREATE INDEX IX_PARTY_MEMBER_MEMBER   ON PARTY_MEMBER (MEMBER_ID);              -- 내가 참여한 식사팟
