-- =====================================================================
-- 09_cafeteria_backup_menu.sql : 구내식당 예비 메뉴 (크롤링이 실패했을 때만, 2026-11-24 A4)
--   - 메인 '오늘의 구내식당'은 DB(CAFETERIA_MENU)에 그날 메뉴가 있으면 크롤링 없이 그대로 보여 준다.
--     발표 날 아침 사이트 메뉴가 안 보이거나 메인이 "메뉴를 불러오지 못했어요"이면 이 파일을 실행한다.
--   - 오늘·내일(실행일 기준) 점심·저녁을 넣는다. 11/25에 실행하면 11/25·11/26, 11/26에 실행하면 11/26·11/27.
--   - 그 날짜·끼니에 이미 메뉴가 1개라도 있으면(크롤링 성공) 건드리지 않는다. 여러 번 실행해도 결과가 같다.
--   - 실제 계절밥상 메뉴가 아니라 시연용 예시 메뉴다.
--   - 지우기: 맨 아래 주석의 DELETE 실행
-- =====================================================================

SET DEFINE OFF

-- 날짜·끼니 하나에 메뉴 목록을 넣는 도우미 (그 끼니가 비어 있을 때만)
-- SQL Developer에서는 F5(스크립트 실행)로 파일 전체를 실행한다.
CREATE OR REPLACE PROCEDURE add_meal(p_date DATE, p_meal VARCHAR2, p_items sys.odcivarchar2list) AS
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM CAFETERIA_MENU WHERE MENU_DATE = p_date AND MEAL_TYPE = p_meal;
    IF v_count = 0 THEN
        FOR i IN 1 .. p_items.COUNT LOOP
            INSERT INTO CAFETERIA_MENU (MENU_ID, MENU_DATE, MEAL_TYPE, MENU_NAME, CREATED_AT)
            VALUES (SEQ_CAFETERIA_MENU.NEXTVAL, p_date, p_meal, p_items(i), SYSDATE);
        END LOOP;
    END IF;
END;
/

-- 오늘 점심
BEGIN
    add_meal(TRUNC(SYSDATE) + 0, 'LUNCH', sys.odcivarchar2list('현미밥', '차돌된장찌개', '닭갈비볶음', '계란말이', '시금치나물', '배추김치', '요구르트'));
END;
/
-- 오늘 저녁
BEGIN
    add_meal(TRUNC(SYSDATE) + 0, 'DINNER', sys.odcivarchar2list('흑미밥', '소고기미역국', '고등어구이', '잡채', '콩나물무침', '깍두기'));
END;
/
-- 내일 점심
BEGIN
    add_meal(TRUNC(SYSDATE) + 1, 'LUNCH', sys.odcivarchar2list('기장밥', '김치찌개', '제육볶음', '두부조림', '미역줄기볶음', '배추김치', '오렌지주스'));
END;
/
-- 내일 저녁
BEGIN
    add_meal(TRUNC(SYSDATE) + 1, 'DINNER', sys.odcivarchar2list('잡곡밥', '순두부찌개', '치킨까스', '감자샐러드', '오이무침', '깍두기'));
END;
/

COMMIT;

-- 도우미는 다 썼으니 지운다
DROP PROCEDURE add_meal;

-- 확인: 오늘·내일 점심·저녁이 6~7개씩 보이면 정상
SELECT TO_CHAR(MENU_DATE, 'YYYY-MM-DD') AS MENU_DATE, MEAL_TYPE, COUNT(*) AS CNT
  FROM CAFETERIA_MENU
 WHERE MENU_DATE BETWEEN TRUNC(SYSDATE) AND TRUNC(SYSDATE) + 1
 GROUP BY MENU_DATE, MEAL_TYPE
 ORDER BY MENU_DATE, MEAL_TYPE;

-- (지우기) 예비 메뉴를 지우고 다시 크롤링하게 하려면:
-- DELETE FROM CAFETERIA_MENU WHERE MENU_DATE BETWEEN TRUNC(SYSDATE) AND TRUNC(SYSDATE) + 1;
-- COMMIT;
