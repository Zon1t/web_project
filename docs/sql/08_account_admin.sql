-- =====================================================================
-- 08_account_admin.sql : 회원 탈퇴·계정 찾기·관리자 기능용 컬럼 추가 (2026-11-24)
--   - 모든 DB에서 실행한다. (새로 만들 때는 컬럼이 01_ddl.sql에 이미 있고, 관리자 지정만 반영된다)
--   - 모든 컬럼에 기본값이 있거나 NULL 허용이라, 기존 행과 기존 코드는 그대로 동작한다.
--   - 서버를 새 코드(엔티티에 새 필드가 있는 코드)로 띄우기 전에 먼저 실행할 것.
--     안 하면 회원·식당·리뷰·수정 제안 조회에서 ORA-00904(열 이름 오류)가 난다.
--   - 여러 번 실행해도 결과가 같다. 이미 있는 컬럼·제약조건은 건너뛴다.
--     그래서 00~01로 새로 만든 DB에서도 그냥 실행하면 된다(관리자 지정만 반영됨).
--   - 되돌리기: 08_account_admin_rollback.sql
--
--   | 테이블     | 컬럼                   | 쓰는 작업      |
--   | MEMBER     | STATUS, WITHDRAWN_AT   | C2 회원 탈퇴   |
--   | MEMBER     | EMAIL, RESET_TOKEN_*   | C3 계정 찾기   |
--   | MEMBER     | ROLE                   | A3 관리자      |
--   | RESTAURANT | HIDDEN_YN              | A3 식당 숨김   |
--   | REVIEW     | HIDDEN_YN              | A3 리뷰 숨김   |
--   | SUGGESTION | STATUS                 | A3 제안 처리   |
-- =====================================================================


-- 이미 있는 컬럼(ORA-01430)·제약조건(ORA-02261, ORA-02264)이면 건너뛰는 방식으로 한 줄씩 실행한다.

-- ---------------------------------------------------------------------
-- 1. MEMBER
--   STATUS                 ACTIVE / WITHDRAWN (탈퇴)
--   WITHDRAWN_AT           탈퇴 일시
--   EMAIL                  계정 찾기용 (선택, NULL 여러 개 허용)
--   RESET_TOKEN_HASH       비밀번호 재설정 토큰의 SHA-256 16진수 (원문 저장 금지)
--   RESET_TOKEN_EXPIRES_AT 재설정 토큰 만료 일시
--   ROLE                   USER / ADMIN
-- ---------------------------------------------------------------------
BEGIN EXECUTE IMMEDIATE 'ALTER TABLE MEMBER ADD (STATUS VARCHAR2(10) DEFAULT ''ACTIVE'' NOT NULL)'; EXCEPTION WHEN OTHERS THEN IF SQLCODE NOT IN (-1430, -2261, -2264) THEN RAISE; END IF; END;
/
BEGIN EXECUTE IMMEDIATE 'ALTER TABLE MEMBER ADD (WITHDRAWN_AT DATE)'; EXCEPTION WHEN OTHERS THEN IF SQLCODE NOT IN (-1430, -2261, -2264) THEN RAISE; END IF; END;
/
BEGIN EXECUTE IMMEDIATE 'ALTER TABLE MEMBER ADD (EMAIL VARCHAR2(100))'; EXCEPTION WHEN OTHERS THEN IF SQLCODE NOT IN (-1430, -2261, -2264) THEN RAISE; END IF; END;
/
BEGIN EXECUTE IMMEDIATE 'ALTER TABLE MEMBER ADD (RESET_TOKEN_HASH VARCHAR2(64))'; EXCEPTION WHEN OTHERS THEN IF SQLCODE NOT IN (-1430, -2261, -2264) THEN RAISE; END IF; END;
/
BEGIN EXECUTE IMMEDIATE 'ALTER TABLE MEMBER ADD (RESET_TOKEN_EXPIRES_AT DATE)'; EXCEPTION WHEN OTHERS THEN IF SQLCODE NOT IN (-1430, -2261, -2264) THEN RAISE; END IF; END;
/
BEGIN EXECUTE IMMEDIATE 'ALTER TABLE MEMBER ADD (ROLE VARCHAR2(10) DEFAULT ''USER'' NOT NULL)'; EXCEPTION WHEN OTHERS THEN IF SQLCODE NOT IN (-1430, -2261, -2264) THEN RAISE; END IF; END;
/
BEGIN EXECUTE IMMEDIATE 'ALTER TABLE MEMBER ADD CONSTRAINT CK_MEMBER_STATUS CHECK (STATUS IN (''ACTIVE'', ''WITHDRAWN''))'; EXCEPTION WHEN OTHERS THEN IF SQLCODE NOT IN (-1430, -2261, -2264) THEN RAISE; END IF; END;
/
BEGIN EXECUTE IMMEDIATE 'ALTER TABLE MEMBER ADD CONSTRAINT CK_MEMBER_ROLE CHECK (ROLE IN (''USER'', ''ADMIN''))'; EXCEPTION WHEN OTHERS THEN IF SQLCODE NOT IN (-1430, -2261, -2264) THEN RAISE; END IF; END;
/
BEGIN EXECUTE IMMEDIATE 'ALTER TABLE MEMBER ADD CONSTRAINT UK_MEMBER_EMAIL UNIQUE (EMAIL)'; EXCEPTION WHEN OTHERS THEN IF SQLCODE NOT IN (-1430, -2261, -2264) THEN RAISE; END IF; END;
/


-- ---------------------------------------------------------------------
-- 2. RESTAURANT : 관리자 숨김 (Y면 목록·랭킹·룰렛 등에서 빠짐)
-- ---------------------------------------------------------------------
BEGIN EXECUTE IMMEDIATE 'ALTER TABLE RESTAURANT ADD (HIDDEN_YN CHAR(1) DEFAULT ''N'' NOT NULL)'; EXCEPTION WHEN OTHERS THEN IF SQLCODE NOT IN (-1430, -2261, -2264) THEN RAISE; END IF; END;
/
BEGIN EXECUTE IMMEDIATE 'ALTER TABLE RESTAURANT ADD CONSTRAINT CK_RESTAURANT_HIDDEN_YN CHECK (HIDDEN_YN IN (''Y'', ''N''))'; EXCEPTION WHEN OTHERS THEN IF SQLCODE NOT IN (-1430, -2261, -2264) THEN RAISE; END IF; END;
/


-- ---------------------------------------------------------------------
-- 3. REVIEW : 관리자 숨김
-- ---------------------------------------------------------------------
BEGIN EXECUTE IMMEDIATE 'ALTER TABLE REVIEW ADD (HIDDEN_YN CHAR(1) DEFAULT ''N'' NOT NULL)'; EXCEPTION WHEN OTHERS THEN IF SQLCODE NOT IN (-1430, -2261, -2264) THEN RAISE; END IF; END;
/
BEGIN EXECUTE IMMEDIATE 'ALTER TABLE REVIEW ADD CONSTRAINT CK_REVIEW_HIDDEN_YN CHECK (HIDDEN_YN IN (''Y'', ''N''))'; EXCEPTION WHEN OTHERS THEN IF SQLCODE NOT IN (-1430, -2261, -2264) THEN RAISE; END IF; END;
/


-- ---------------------------------------------------------------------
-- 4. SUGGESTION : 처리 상태 OPEN / DONE
-- ---------------------------------------------------------------------
BEGIN EXECUTE IMMEDIATE 'ALTER TABLE SUGGESTION ADD (STATUS VARCHAR2(10) DEFAULT ''OPEN'' NOT NULL)'; EXCEPTION WHEN OTHERS THEN IF SQLCODE NOT IN (-1430, -2261, -2264) THEN RAISE; END IF; END;
/
BEGIN EXECUTE IMMEDIATE 'ALTER TABLE SUGGESTION ADD CONSTRAINT CK_SUGGESTION_STATUS CHECK (STATUS IN (''OPEN'', ''DONE''))'; EXCEPTION WHEN OTHERS THEN IF SQLCODE NOT IN (-1430, -2261, -2264) THEN RAISE; END IF; END;
/


-- ---------------------------------------------------------------------
-- 5. 관리자 1명 지정 (04_dummy_data.sql의 user05)
-- ---------------------------------------------------------------------
UPDATE MEMBER SET ROLE = 'ADMIN' WHERE LOGIN_ID = 'user05';
COMMIT;


-- 확인: 컬럼 9개가 보이고, user05만 ADMIN이면 정상
SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, DATA_DEFAULT
  FROM USER_TAB_COLUMNS
 WHERE (TABLE_NAME = 'MEMBER'     AND COLUMN_NAME IN ('STATUS', 'WITHDRAWN_AT', 'EMAIL', 'RESET_TOKEN_HASH', 'RESET_TOKEN_EXPIRES_AT', 'ROLE'))
    OR (TABLE_NAME IN ('RESTAURANT', 'REVIEW') AND COLUMN_NAME = 'HIDDEN_YN')
    OR (TABLE_NAME = 'SUGGESTION' AND COLUMN_NAME = 'STATUS')
 ORDER BY TABLE_NAME, COLUMN_ID;

SELECT LOGIN_ID, ROLE FROM MEMBER WHERE ROLE = 'ADMIN';
