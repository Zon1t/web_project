-- =====================================================================
-- 11_private_party.sql : 비공개 식사팟 컬럼 추가 (2026-11-25, T4)
--   - 모든 DB에서 실행한다. (새로 만들 때는 컬럼이 01_ddl.sql에 이미 있어서 아무것도 바뀌지 않는다)
--   - 기본값이 있거나 NULL 허용이라 기존 식사팟은 모두 공개(PRIVATE_YN = 'N')로 그대로 동작한다.
--   - 서버를 새 코드(MealParty에 privateParty·inviteCode 필드가 있는 코드)로 띄우기 전에 먼저 실행할 것.
--     안 하면 식사팟 조회에서 ORA-00904(열 이름 오류)가 난다.
--   - 여러 번 실행해도 결과가 같다. 이미 있는 컬럼·제약조건은 건너뛴다.
--   - 되돌리기: 11_private_party_rollback.sql
--
--   | 컬럼        | 설명                                                    |
--   | PRIVATE_YN  | 'Y'면 비공개 식사팟 (초대 코드로만 상세·참여 가능)      |
--   | INVITE_CODE | 비공개 식사팟 초대 코드 22자 (UK). 공개 식사팟은 NULL  |
-- =====================================================================


-- 이미 있는 컬럼(ORA-01430)·제약조건(ORA-02261, ORA-02264)이면 건너뛴다.
BEGIN EXECUTE IMMEDIATE 'ALTER TABLE MEAL_PARTY ADD (PRIVATE_YN CHAR(1) DEFAULT ''N'' NOT NULL)'; EXCEPTION WHEN OTHERS THEN IF SQLCODE NOT IN (-1430, -2261, -2264) THEN RAISE; END IF; END;
/
BEGIN EXECUTE IMMEDIATE 'ALTER TABLE MEAL_PARTY ADD (INVITE_CODE VARCHAR2(32))'; EXCEPTION WHEN OTHERS THEN IF SQLCODE NOT IN (-1430, -2261, -2264) THEN RAISE; END IF; END;
/
BEGIN EXECUTE IMMEDIATE 'ALTER TABLE MEAL_PARTY ADD CONSTRAINT CK_MEAL_PARTY_PRIVATE_YN CHECK (PRIVATE_YN IN (''Y'', ''N''))'; EXCEPTION WHEN OTHERS THEN IF SQLCODE NOT IN (-1430, -2261, -2264) THEN RAISE; END IF; END;
/
BEGIN EXECUTE IMMEDIATE 'ALTER TABLE MEAL_PARTY ADD CONSTRAINT UK_MEAL_PARTY_INVITE_CODE UNIQUE (INVITE_CODE)'; EXCEPTION WHEN OTHERS THEN IF SQLCODE NOT IN (-1430, -2261, -2264) THEN RAISE; END IF; END;
/


-- 확인: 두 컬럼이 보이면 정상
SELECT COLUMN_NAME, DATA_TYPE, DATA_LENGTH, NULLABLE, DATA_DEFAULT
  FROM USER_TAB_COLUMNS
 WHERE TABLE_NAME = 'MEAL_PARTY' AND COLUMN_NAME IN ('PRIVATE_YN', 'INVITE_CODE')
 ORDER BY COLUMN_ID;
