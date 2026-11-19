-- =====================================================================
-- 00_drop.sql : 슥밥 테이블·시퀀스 전체 삭제 (처음부터 다시 만들 때만 사용)
-- 주의: 모든 데이터가 지워진다. 실행 전 접속 계정을 꼭 확인할 것.
-- 없는 테이블·시퀀스는 오류 없이 건너뛴다. (Oracle 11g에는 IF EXISTS가 없음)
-- =====================================================================

BEGIN
    FOR t IN (
        SELECT 'PARTY_CHAT'     AS name FROM DUAL UNION ALL
        SELECT 'PARTY_MEMBER'           FROM DUAL UNION ALL
        SELECT 'MEAL_PARTY'             FROM DUAL UNION ALL
        SELECT 'REVIEW_TAG'             FROM DUAL UNION ALL
        SELECT 'REVIEW'                 FROM DUAL UNION ALL
        SELECT 'TAG'                    FROM DUAL UNION ALL
        SELECT 'INTEREST'               FROM DUAL UNION ALL
        SELECT 'SUGGESTION'             FROM DUAL UNION ALL
        SELECT 'CAFETERIA_MENU'         FROM DUAL UNION ALL
        SELECT 'RESTAURANT'             FROM DUAL UNION ALL
        SELECT 'MEMBER'                 FROM DUAL
    ) LOOP
        BEGIN
            EXECUTE IMMEDIATE 'DROP TABLE ' || t.name || ' CASCADE CONSTRAINTS PURGE';
        EXCEPTION
            WHEN OTHERS THEN
                IF SQLCODE != -942 THEN RAISE; END IF;   -- -942: 테이블 없음
        END;
    END LOOP;

    FOR s IN (
        SELECT 'SEQ_MEMBER'         AS name FROM DUAL UNION ALL
        SELECT 'SEQ_RESTAURANT'             FROM DUAL UNION ALL
        SELECT 'SEQ_REVIEW'                 FROM DUAL UNION ALL
        SELECT 'SEQ_REVIEW_TAG'             FROM DUAL UNION ALL
        SELECT 'SEQ_INTEREST'               FROM DUAL UNION ALL
        SELECT 'SEQ_MEAL_PARTY'             FROM DUAL UNION ALL
        SELECT 'SEQ_PARTY_MEMBER'           FROM DUAL UNION ALL
        SELECT 'SEQ_PARTY_CHAT'             FROM DUAL UNION ALL
        SELECT 'SEQ_CAFETERIA_MENU'         FROM DUAL UNION ALL
        SELECT 'SEQ_SUGGESTION'             FROM DUAL
    ) LOOP
        BEGIN
            EXECUTE IMMEDIATE 'DROP SEQUENCE ' || s.name;
        EXCEPTION
            WHEN OTHERS THEN
                IF SQLCODE != -2289 THEN RAISE; END IF;  -- -2289: 시퀀스 없음
        END;
    END LOOP;
END;
/
