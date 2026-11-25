-- =====================================================================
-- 10_email_login.sql : 아이디 = 회사 이메일 (2026-11-25, T3)
--   - 08_account_admin.sql 다음에 실행한다. (EMAIL 컬럼이 있어야 함)
--   - 여러 번 실행해도 결과가 같다.
--
--   1. NICKNAME VARCHAR2(30) → VARCHAR2(36 CHAR)
--      30은 바이트라 한글 10자가 한계였다. 닉네임 2~12자(한글 12자)를 저장하려면 글자(CHAR) 기준으로 바꾼다.
--   2. 더미·시연 계정(user01~05, demo01~10)에 '아이디@samsung.com' 이메일을 채운다.
--      - 이미 이메일이 있는 계정, 탈퇴한 계정은 건드리지 않는다.
--      - 같은 이메일을 다른 회원이 쓰고 있으면 건너뛴다. (UK_MEMBER_EMAIL)
--   EMAIL은 이메일이 없는 기존 회원 때문에 아직 NOT NULL로 바꾸지 않는다. (전환이 끝나면 별도 스크립트)
--
--   실행 후 시연 계정 로그인: user01@samsung.com / test1234! (예전 아이디 user01로는 로그인되지 않음)
-- =====================================================================

-- 1. 닉네임 12자 (이미 36 CHAR면 같은 정의로 다시 바꾸는 것이라 오류 없이 지나간다)
BEGIN
  EXECUTE IMMEDIATE 'ALTER TABLE MEMBER MODIFY (NICKNAME VARCHAR2(36 CHAR))';
EXCEPTION WHEN OTHERS THEN
  IF SQLCODE NOT IN (-1442) THEN RAISE; END IF;
END;
/

-- 2. 더미·시연 계정 이메일 채우기
UPDATE MEMBER m
   SET m.EMAIL = LOWER(m.LOGIN_ID) || '@samsung.com'
 WHERE m.EMAIL IS NULL
   AND m.STATUS <> 'WITHDRAWN'
   AND (REGEXP_LIKE(m.LOGIN_ID, '^user0[1-5]$') OR REGEXP_LIKE(m.LOGIN_ID, '^demo(0[1-9]|10)$'))
   AND NOT EXISTS (SELECT 1 FROM MEMBER o WHERE o.EMAIL = LOWER(m.LOGIN_ID) || '@samsung.com');

COMMIT;

-- 확인: 15명 정도가 나오면 정상
SELECT LOGIN_ID, EMAIL, NICKNAME FROM MEMBER
 WHERE REGEXP_LIKE(LOGIN_ID, '^(user0[1-5]|demo(0[1-9]|10))$')
 ORDER BY LOGIN_ID;
