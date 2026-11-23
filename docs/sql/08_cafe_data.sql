-- =====================================================================
-- 08_cafe_data.sql : 카페 8곳 (2026-11-23)
--   선행: 07_add_cafe_category.sql (안 돌리면 ORA-02290), 04_dummy_data.sql (등록자 user01)
--   - 주소·좌표·지도 URL은 식당 등록 API(네이버 지역 검색)로 실제 등록해서 얻은 값
--   - 같은 지도 URL이 이미 있으면 건너뛰므로 여러 번 실행해도 괜찮다
--   - 룰렛·랭킹 '전체'에는 안 나오고 '카페' 칩에서만 보인다
-- =====================================================================

INSERT INTO RESTAURANT (RESTAURANT_ID, NAME, CATEGORY, MAP_URL, ADDRESS, LAT, LNG, MEMBER_ID)
SELECT SEQ_RESTAURANT.NEXTVAL, '릴리베이커리', 'CAFE', 'https://map.naver.com/p/entry/place/1126127714',
       '서울특별시 광진구 능동로13길 21 1층 릴리베이커리(김밥천국바로옆)-지하X', 37.5430274, 127.0707631,
       (SELECT MEMBER_ID FROM MEMBER WHERE LOGIN_ID = 'user01')
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM RESTAURANT WHERE MAP_URL = 'https://map.naver.com/p/entry/place/1126127714');

INSERT INTO RESTAURANT (RESTAURANT_ID, NAME, CATEGORY, MAP_URL, ADDRESS, LAT, LNG, MEMBER_ID)
SELECT SEQ_RESTAURANT.NEXTVAL, '모서리에커피집', 'CAFE', 'https://map.naver.com/p/entry/place/34052643',
       '서울특별시 광진구 능동로17길 5 1층 모서리에 커피집', 37.5460799, 127.0730438,
       (SELECT MEMBER_ID FROM MEMBER WHERE LOGIN_ID = 'user01')
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM RESTAURANT WHERE MAP_URL = 'https://map.naver.com/p/entry/place/34052643');

INSERT INTO RESTAURANT (RESTAURANT_ID, NAME, CATEGORY, MAP_URL, ADDRESS, LAT, LNG, MEMBER_ID)
SELECT SEQ_RESTAURANT.NEXTVAL, '보난자커피 군자', 'CAFE', 'https://map.naver.com/p/entry/place/1689645997',
       '서울특별시 광진구 능동로 239-1 B동 1층 보난자커피', 37.5516342, 127.0763031,
       (SELECT MEMBER_ID FROM MEMBER WHERE LOGIN_ID = 'user01')
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM RESTAURANT WHERE MAP_URL = 'https://map.naver.com/p/entry/place/1689645997');

INSERT INTO RESTAURANT (RESTAURANT_ID, NAME, CATEGORY, MAP_URL, ADDRESS, LAT, LNG, MEMBER_ID)
SELECT SEQ_RESTAURANT.NEXTVAL, '소림사 세종대점', 'CAFE', 'https://map.naver.com/p/entry/place/2036212514',
       '서울특별시 광진구 광나루로 386 104호', 37.5474781, 127.0723173,
       (SELECT MEMBER_ID FROM MEMBER WHERE LOGIN_ID = 'user01')
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM RESTAURANT WHERE MAP_URL = 'https://map.naver.com/p/entry/place/2036212514');

INSERT INTO RESTAURANT (RESTAURANT_ID, NAME, CATEGORY, MAP_URL, ADDRESS, LAT, LNG, MEMBER_ID)
SELECT SEQ_RESTAURANT.NEXTVAL, '애드앤드', 'CAFE', 'https://map.naver.com/p/entry/place/1506430135',
       '서울특별시 광진구 광나루로26길 28 1층', 37.5452996, 127.0774377,
       (SELECT MEMBER_ID FROM MEMBER WHERE LOGIN_ID = 'user01')
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM RESTAURANT WHERE MAP_URL = 'https://map.naver.com/p/entry/place/1506430135');

INSERT INTO RESTAURANT (RESTAURANT_ID, NAME, CATEGORY, MAP_URL, ADDRESS, LAT, LNG, MEMBER_ID)
SELECT SEQ_RESTAURANT.NEXTVAL, '오소리 베이커리 어린이대공원 본점', 'CAFE', 'https://map.naver.com/p/entry/place/1790543616',
       '서울특별시 광진구 능동로 177 1~3층', 37.5465110, 127.0735098,
       (SELECT MEMBER_ID FROM MEMBER WHERE LOGIN_ID = 'user01')
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM RESTAURANT WHERE MAP_URL = 'https://map.naver.com/p/entry/place/1790543616');

INSERT INTO RESTAURANT (RESTAURANT_ID, NAME, CATEGORY, MAP_URL, ADDRESS, LAT, LNG, MEMBER_ID)
SELECT SEQ_RESTAURANT.NEXTVAL, '트레비커피로스터스', 'CAFE', 'https://map.naver.com/p/entry/place/1198884294',
       '서울특별시 광진구 군자로 63', 37.5490092, 127.0705780,
       (SELECT MEMBER_ID FROM MEMBER WHERE LOGIN_ID = 'user01')
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM RESTAURANT WHERE MAP_URL = 'https://map.naver.com/p/entry/place/1198884294');

INSERT INTO RESTAURANT (RESTAURANT_ID, NAME, CATEGORY, MAP_URL, ADDRESS, LAT, LNG, MEMBER_ID)
SELECT SEQ_RESTAURANT.NEXTVAL, '페프리', 'CAFE', 'https://map.naver.com/p/entry/place/1021376543',
       '서울특별시 광진구 광나루로 428 1층', 37.5458760, 127.0768728,
       (SELECT MEMBER_ID FROM MEMBER WHERE LOGIN_ID = 'user01')
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM RESTAURANT WHERE MAP_URL = 'https://map.naver.com/p/entry/place/1021376543');

COMMIT;

-- 확인: 8건이 나오면 정상
SELECT RESTAURANT_ID, NAME, ADDRESS FROM RESTAURANT WHERE CATEGORY = 'CAFE' ORDER BY RESTAURANT_ID;
