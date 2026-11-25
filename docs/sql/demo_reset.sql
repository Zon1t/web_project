-- =====================================================================
-- demo_reset.sql : 시연 직전 DB 한 번에 초기화 (2026-11-24, A4)
--   테이블을 모두 지우고 00 → 01 → 02 → 03 → 04 → 05 → 08_cafe_data → 08_account_admin → 10_email_login 순서로 다시 만든다.
--   (2026-11-25 T3) 시연 계정은 아이디 또는 회사 이메일로 로그인한다: user01 ~ user05, demo01 ~ demo10 (이메일은 아이디@samsung.com) / test1234!
--   (2026-11-25 T4) 비공개 식사팟 컬럼(11_private_party.sql)은 01_ddl.sql에 이미 들어 있어서 따로 부르지 않는다.
--   날짜는 모두 실행일 기준이라, 발표 당일 아침(또는 리허설 직후)에 실행하면
--     - 오늘 점심·저녁 식사팟 (모집중·일정 확정·정원 마감·모집 마감)
--     - 내일 이후 다가오는 식사팟
--     - 어제 식사를 마친 팟의 따봉 알림 (user01)
--     - 최근 30일 따봉 랭킹 (세종원 16 > 스시온기 13 > 철순이네김치찌개 11 ...)
--   이 모두 "오늘"에 맞춰진다.
--
--   주의: 이 DB의 모든 데이터(화면에서 만든 회원·식당·리뷰·식사팟)가 지워진다. 발표 PC에서만 실행할 것.
--   실행 후 서버를 다시 켜면 로그인 잠금·인증번호(서버 메모리)도 함께 초기화된다.
--
--   실행: SQL Developer에서 이 파일을 열고 F5(스크립트 실행). 같은 폴더의 파일을 @@로 불러온다.
--         (처음에 ORA-00942 "테이블 없음"이 보이는 건 00_drop이 없는 것을 지우려 해서라 괜찮다)
--   구내식당 메뉴가 안 보이면 이어서 09_cafeteria_backup_menu.sql을 실행한다.
-- =====================================================================

SET DEFINE OFF

@@00_drop.sql
@@01_ddl.sql
@@02_sequence.sql
@@03_init_data.sql
@@04_dummy_data.sql
@@05_demo_data.sql
@@08_cafe_data.sql
@@08_account_admin.sql
@@10_email_login.sql

COMMIT;

-- 확인: 아래 숫자가 비슷하면 정상
SELECT (SELECT COUNT(*) FROM MEMBER)                                       AS MEMBERS,       -- 15 (user 5 + demo 10)
       (SELECT COUNT(*) FROM RESTAURANT)                                   AS RESTAURANTS,   -- 66 (구내식당 1 + 식당 57 + 카페 8)
       (SELECT COUNT(*) FROM MEAL_PARTY WHERE PARTY_DATE = TRUNC(SYSDATE)) AS TODAY_PARTIES, -- 5
       (SELECT LOGIN_ID FROM MEMBER WHERE ROLE = 'ADMIN')                  AS ADMIN_ID,      -- user05
       (SELECT COUNT(*) FROM MEMBER WHERE EMAIL IS NOT NULL)               AS WITH_EMAIL     -- 15 (비밀번호 찾기·이메일 로그인 가능)
  FROM DUAL;
