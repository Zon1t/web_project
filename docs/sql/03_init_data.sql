-- =====================================================================
-- 03_init_data.sql : 서비스에 꼭 필요한 초기 데이터 (운영에도 들어가는 값)
--   1) 리뷰 태그 13건 (TAG_ID 1~13 고정, FE 상수와 같아야 함)
--   2) 구내식당 식당 1건 (등록자 없음)
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 리뷰 태그 13건
-- ---------------------------------------------------------------------
INSERT INTO TAG (TAG_ID, TAG_NAME, TAG_CATEGORY) VALUES (1,  '점심추천',   'MEAL_TYPE');
INSERT INTO TAG (TAG_ID, TAG_NAME, TAG_CATEGORY) VALUES (2,  '저녁추천',   'MEAL_TYPE');
INSERT INTO TAG (TAG_ID, TAG_NAME, TAG_CATEGORY) VALUES (3,  '퇴근후식사', 'MEAL_TYPE');
INSERT INTO TAG (TAG_ID, TAG_NAME, TAG_CATEGORY) VALUES (4,  '해장추천',   'MEAL_TYPE');
INSERT INTO TAG (TAG_ID, TAG_NAME, TAG_CATEGORY) VALUES (5,  '혼밥가능',   'GROUP_TYPE');
INSERT INTO TAG (TAG_ID, TAG_NAME, TAG_CATEGORY) VALUES (6,  '단체가능',   'GROUP_TYPE');
INSERT INTO TAG (TAG_ID, TAG_NAME, TAG_CATEGORY) VALUES (7,  '소규모모임', 'GROUP_TYPE');
INSERT INTO TAG (TAG_ID, TAG_NAME, TAG_CATEGORY) VALUES (8,  '가성비',     'PRICE');
INSERT INTO TAG (TAG_ID, TAG_NAME, TAG_CATEGORY) VALUES (9,  '웨이팅많음', 'WAITING');
INSERT INTO TAG (TAG_ID, TAG_NAME, TAG_CATEGORY) VALUES (10, '도보5분이내', 'ACCESSIBILITY');
INSERT INTO TAG (TAG_ID, TAG_NAME, TAG_CATEGORY) VALUES (11, '주차가능',   'ACCESSIBILITY');
INSERT INTO TAG (TAG_ID, TAG_NAME, TAG_CATEGORY) VALUES (12, '포장가능',   'ACCESSIBILITY');
INSERT INTO TAG (TAG_ID, TAG_NAME, TAG_CATEGORY) VALUES (13, '단체석',     'ACCESSIBILITY');

-- ---------------------------------------------------------------------
-- 2. 구내식당 (세종대학교 계절밥상)
-- TODO(B·C 확인): MAP_URL·주소·좌표는 임시값이다. 실제 네이버 지도 URL과 좌표로 바꿀 것.
--                 MAP_URL은 NOT NULL + UNIQUE라 값이 반드시 있어야 한다.
-- ---------------------------------------------------------------------
INSERT INTO RESTAURANT (RESTAURANT_ID, NAME, CATEGORY, MAP_URL, ADDRESS, LAT, LNG, MEMBER_ID)
VALUES (SEQ_RESTAURANT.NEXTVAL, '계절밥상', 'CAFETERIA',
        'https://map.naver.com/p/entry/place/TODO-CAFETERIA',
        '서울특별시 광진구 능동로 209 세종대학교', 37.5503000, 127.0731000, NULL);

COMMIT;
