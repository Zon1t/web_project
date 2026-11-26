-- =====================================================================
-- 12_official_names.sql : 식당 이름을 네이버 공식 이름으로 (2026-11-26, C)
--   - 05_demo_data.sql 다음에, 맨 마지막에 실행한다.
--     05가 식당을 이름(WHERE NAME = '세종원')으로 찾기 때문에, 04·05는 그대로 두고 여기서 마지막에 바꾼다.
--   - 지도 URL(네이버 장소 ID)로 찾아서 바꾸므로 여러 번 실행해도 결과가 같다. 없는 식당은 건너뛴다.
--   - 2026-11-25부터 새로 등록하는 식당은 서버가 공식 이름으로 저장한다. 이 스크립트는 그 전에 넣은 식당용.
--   - 대상: 지점명만 붙는 30곳. 앞에 다른 말이 붙는 곳(진진면옥, 엽기떡볶이, 수상한 막창 본점, 롬곡)은 그대로 둔다.
--   - 되돌리기: 아래 주석의 '예전 이름'으로 다시 UPDATE 하거나 demo_reset.sql 대신 00~10만 실행
-- =====================================================================

SET DEFINE OFF

UPDATE RESTAURANT SET NAME = '세종원 군자본점' WHERE MAP_URL = 'https://map.naver.com/p/entry/place/34628680' AND NAME <> '세종원 군자본점';   -- 예전: 세종원
UPDATE RESTAURANT SET NAME = '스시온기 화양' WHERE MAP_URL = 'https://map.naver.com/p/entry/place/2074189698' AND NAME <> '스시온기 화양';   -- 예전: 스시온기
UPDATE RESTAURANT SET NAME = '혼다라멘 어린이대공원점' WHERE MAP_URL = 'https://map.naver.com/p/entry/place/1085950217' AND NAME <> '혼다라멘 어린이대공원점';   -- 예전: 혼다라멘
UPDATE RESTAURANT SET NAME = '호파스타 생면파스타 건대본점' WHERE MAP_URL = 'https://map.naver.com/p/entry/place/1134743279' AND NAME <> '호파스타 생면파스타 건대본점';   -- 예전: 호파스타
UPDATE RESTAURANT SET NAME = '빠오즈푸 본점' WHERE MAP_URL = 'https://map.naver.com/p/entry/place/20757891' AND NAME <> '빠오즈푸 본점';   -- 예전: 빠오즈푸
UPDATE RESTAURANT SET NAME = '위클리베이글 어린이대공원역점' WHERE MAP_URL = 'https://map.naver.com/p/entry/place/1499840333' AND NAME <> '위클리베이글 어린이대공원역점';   -- 예전: 위클리베이글
UPDATE RESTAURANT SET NAME = '싸다김밥 어린이대공원역점' WHERE MAP_URL = 'https://map.naver.com/p/entry/place/1981065688' AND NAME <> '싸다김밥 어린이대공원역점';   -- 예전: 싸다김밥
UPDATE RESTAURANT SET NAME = '가츠시 건대점' WHERE MAP_URL = 'https://map.naver.com/p/entry/place/123086605' AND NAME <> '가츠시 건대점';   -- 예전: 가츠시
UPDATE RESTAURANT SET NAME = '서울해장 군자역점' WHERE MAP_URL = 'https://map.naver.com/p/entry/place/2077498919' AND NAME <> '서울해장 군자역점';   -- 예전: 서울해장
UPDATE RESTAURANT SET NAME = '멘츠루 어린이대공원점' WHERE MAP_URL = 'https://map.naver.com/p/entry/place/1993190099' AND NAME <> '멘츠루 어린이대공원점';   -- 예전: 멘츠루
UPDATE RESTAURANT SET NAME = '오늘의샤브 세종대점' WHERE MAP_URL = 'https://map.naver.com/p/entry/place/2072982373' AND NAME <> '오늘의샤브 세종대점';   -- 예전: 오늘의샤브
UPDATE RESTAURANT SET NAME = '춘천골닭갈비 건대본점' WHERE MAP_URL = 'https://map.naver.com/p/entry/place/1840778141' AND NAME <> '춘천골닭갈비 건대본점';   -- 예전: 춘천골 닭갈비
UPDATE RESTAURANT SET NAME = '고미돈까스 건대본점' WHERE MAP_URL = 'https://map.naver.com/p/entry/place/38521395' AND NAME <> '고미돈까스 건대본점';   -- 예전: 고미돈까스
UPDATE RESTAURANT SET NAME = '일품양평해장국 건대스타시티점' WHERE MAP_URL = 'https://map.naver.com/p/entry/place/1002743024' AND NAME <> '일품양평해장국 건대스타시티점';   -- 예전: 일품양평해장국
UPDATE RESTAURANT SET NAME = '피자스쿨 광진구청점' WHERE MAP_URL = 'https://map.naver.com/p/entry/place/20943449' AND NAME <> '피자스쿨 광진구청점';   -- 예전: 피자스쿨
UPDATE RESTAURANT SET NAME = '김밥대학라면학과 건대점' WHERE MAP_URL = 'https://map.naver.com/p/entry/place/38275002' AND NAME <> '김밥대학라면학과 건대점';   -- 예전: 김밥대학라면학과
UPDATE RESTAURANT SET NAME = '써브웨이 건대입구점' WHERE MAP_URL = 'https://map.naver.com/p/entry/place/31237863' AND NAME <> '써브웨이 건대입구점';   -- 예전: 써브웨이
UPDATE RESTAURANT SET NAME = '분식을품다 건대점' WHERE MAP_URL = 'https://map.naver.com/p/entry/place/2062140664' AND NAME <> '분식을품다 건대점';   -- 예전: 분식을품다
UPDATE RESTAURANT SET NAME = '길이식당 건대본점' WHERE MAP_URL = 'https://map.naver.com/p/entry/place/38695722' AND NAME <> '길이식당 건대본점';   -- 예전: 길이식당
UPDATE RESTAURANT SET NAME = '카츠 헤세드 어린이대공원점' WHERE MAP_URL = 'https://map.naver.com/p/entry/place/1093710249' AND NAME <> '카츠 헤세드 어린이대공원점';   -- 예전: 카츠헤세드
UPDATE RESTAURANT SET NAME = '진한방삼계탕군자점' WHERE MAP_URL = 'https://map.naver.com/p/entry/place/11851243' AND NAME <> '진한방삼계탕군자점';   -- 예전: 진한방삼계탕
UPDATE RESTAURANT SET NAME = '본가왕뼈감자탕 어린이대공원점' WHERE MAP_URL = 'https://map.naver.com/p/entry/place/1231259493' AND NAME <> '본가왕뼈감자탕 어린이대공원점';   -- 예전: 본가왕뼈감자
UPDATE RESTAURANT SET NAME = '계탉닭 본점' WHERE MAP_URL = 'https://map.naver.com/p/entry/place/2034482398' AND NAME <> '계탉닭 본점';   -- 예전: 계탉닭
UPDATE RESTAURANT SET NAME = '버거리 건대점' WHERE MAP_URL = 'https://map.naver.com/p/entry/place/1094194038' AND NAME <> '버거리 건대점';   -- 예전: 버거리
UPDATE RESTAURANT SET NAME = '미슌 어린이대공원역점' WHERE MAP_URL = 'https://map.naver.com/p/entry/place/2099905845' AND NAME <> '미슌 어린이대공원역점';   -- 예전: 미슌
UPDATE RESTAURANT SET NAME = '라멘트럭 건대점' WHERE MAP_URL = 'https://map.naver.com/p/entry/place/1271177402' AND NAME <> '라멘트럭 건대점';   -- 예전: 라멘트럭
UPDATE RESTAURANT SET NAME = '아들내미분식 건대후문점' WHERE MAP_URL = 'https://map.naver.com/p/entry/place/2087744631' AND NAME <> '아들내미분식 건대후문점';   -- 예전: 아들내미분식
UPDATE RESTAURANT SET NAME = '투썸플레이스 군자역점' WHERE MAP_URL = 'https://map.naver.com/p/entry/place/33675041' AND NAME <> '투썸플레이스 군자역점';   -- 예전: 투썸플레이스 군자역
UPDATE RESTAURANT SET NAME = '샹츠마라 어린이대공원점' WHERE MAP_URL = 'https://map.naver.com/p/entry/place/1968024933' AND NAME <> '샹츠마라 어린이대공원점';   -- 예전: 샹츠마라 어린이대공원
UPDATE RESTAURANT SET NAME = '맘스터치 어린이대공원역점' WHERE MAP_URL = 'https://map.naver.com/p/entry/place/2064072578' AND NAME <> '맘스터치 어린이대공원역점';   -- 예전: 맘스터치

COMMIT;

-- 확인: demo_reset 직후에는 29 (맘스터치는 화면에서 등록한 식당이라 04에 없음), 지금 개발 DB에서는 30
SELECT COUNT(*) AS OFFICIAL_NAMES FROM RESTAURANT WHERE NAME IN (
    '세종원 군자본점',
    '스시온기 화양',
    '혼다라멘 어린이대공원점',
    '호파스타 생면파스타 건대본점',
    '빠오즈푸 본점',
    '위클리베이글 어린이대공원역점',
    '싸다김밥 어린이대공원역점',
    '가츠시 건대점',
    '서울해장 군자역점',
    '멘츠루 어린이대공원점',
    '오늘의샤브 세종대점',
    '춘천골닭갈비 건대본점',
    '고미돈까스 건대본점',
    '일품양평해장국 건대스타시티점',
    '피자스쿨 광진구청점',
    '김밥대학라면학과 건대점',
    '써브웨이 건대입구점',
    '분식을품다 건대점',
    '길이식당 건대본점',
    '카츠 헤세드 어린이대공원점',
    '진한방삼계탕군자점',
    '본가왕뼈감자탕 어린이대공원점',
    '계탉닭 본점',
    '버거리 건대점',
    '미슌 어린이대공원역점',
    '라멘트럭 건대점',
    '아들내미분식 건대후문점',
    '투썸플레이스 군자역점',
    '샹츠마라 어린이대공원점',
    '맘스터치 어린이대공원역점'
);
