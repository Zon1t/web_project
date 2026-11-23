-- =====================================================================
-- 07_add_cafe_category.sql : 식당 카테고리에 카페(CAFE) 추가 (2026-11-23)
--   - 이미 만들어 둔 DB에서 한 번 실행한다. (새로 만들 때는 01_ddl.sql에 반영됨)
--   - 서버를 새 코드로 띄우기 전에 먼저 실행할 것. 안 하면 카페 등록 시 ORA-02290
--   - CHECK 제약은 수정이 안 돼서 지우고 다시 만든다.
--   - 규칙: 룰렛 '전체'·랭킹 '전체'에서는 제외, '카페' 칩으로만 보임. 식사팟은 카페도 선택 가능
-- =====================================================================

ALTER TABLE RESTAURANT DROP CONSTRAINT CK_RESTAURANT_CATEGORY;

ALTER TABLE RESTAURANT ADD CONSTRAINT CK_RESTAURANT_CATEGORY CHECK (CATEGORY IN
    ('CAFETERIA', 'KOREAN', 'CHINESE', 'JAPANESE', 'WESTERN', 'SNACK', 'ETC', 'CAFE'));

-- (선택) '기타'로 등록해 둔 카페가 있으면 옮긴다. 이름은 직접 확인해서 바꿀 것
-- UPDATE RESTAURANT SET CATEGORY = 'CAFE' WHERE CATEGORY = 'ETC' AND NAME IN ('OO카페', '△△커피');
-- COMMIT;

-- 확인: SEARCH_CONDITION에 'CAFE'가 보이면 정상
SELECT CONSTRAINT_NAME, SEARCH_CONDITION FROM USER_CONSTRAINTS WHERE CONSTRAINT_NAME = 'CK_RESTAURANT_CATEGORY';
