-- =====================================================================
-- 06_add_tags.sql : 이미 만들어 둔 DB에 리뷰 태그 14~18을 추가 (2026-11-23)
--   - 새로 DB를 만들 때는 03_init_data.sql에 이미 들어 있으니 실행할 필요 없음
--   - 여러 번 실행해도 결과가 같다 (14번 이후 태그를 지우고 다시 넣음)
--   - FE 상수: static/js/api/review.js TAG_GROUPS
-- =====================================================================

-- 1. 14번 이후 태그 정리 (앞서 받은 14~20 버전을 돌렸어도 깔끔하게 맞춰 준다)
--    새 태그라 실제 리뷰에서 쓰인 적이 없지만, 혹시 붙은 REVIEW_TAG가 있으면 같이 지운다.
DELETE FROM REVIEW_TAG WHERE TAG_ID >= 14;
DELETE FROM TAG WHERE TAG_ID >= 14;

-- 2. 태그 14~18
INSERT INTO TAG (TAG_ID, TAG_NAME, TAG_CATEGORY) VALUES (14, '만원이하', 'PRICE');
INSERT INTO TAG (TAG_ID, TAG_NAME, TAG_CATEGORY) VALUES (15, '양많음', 'PRICE');
INSERT INTO TAG (TAG_ID, TAG_NAME, TAG_CATEGORY) VALUES (16, '빨리나와요', 'WAITING');
INSERT INTO TAG (TAG_ID, TAG_NAME, TAG_CATEGORY) VALUES (17, '예약가능', 'WAITING');
INSERT INTO TAG (TAG_ID, TAG_NAME, TAG_CATEGORY) VALUES (18, '도보15분이상', 'ACCESSIBILITY');

COMMIT;

-- 확인: 18건이 나오면 정상
SELECT TAG_ID, TAG_NAME, TAG_CATEGORY FROM TAG ORDER BY TAG_ID;
