/*
 * restaurant.js — 식당 API 호출 (B)
 *
 * 화면 JS에서는 이렇게 쓴다.
 *   const list = await restaurantApi.getList({ keyword: '국밥', category: 'KOREAN' });
 *   const detail = await restaurantApi.getDetail(12);
 *
 * 다른 도메인도 같은 모양으로 만든다. (예: js/api/party.js → partyApi.join(31))
 * 나머지 식당 API(등록, 관심, 리뷰 등)는 기능을 만들 때 하나씩 추가한다.
 */
(function (global) {
  'use strict';

  global.restaurantApi = {
    /**
     * 카테고리 코드 → 화면 표시 이름 (FE 상수, 순서 = 필터 칩 순서)
     * 사용: restaurantApi.CATEGORY_LABELS.KOREAN → '한식'
     */
    CATEGORY_LABELS: Object.freeze({
      CAFETERIA: '구내식당',
      KOREAN: '한식',
      CHINESE: '중식',
      JAPANESE: '일식',
      WESTERN: '양식',
      SNACK: '분식',
      CAFE: '카페·베이커리',   // 2026-11-24 '카페' → 베이커리도 여기에 포함
      ETC: '기타',
    }),

    /**
     * 카테고리별 음식 예시 (칩에 마우스를 올리거나 골랐을 때 보여 준다, 2026-11-24)
     * 사용: restaurantApi.CATEGORY_EXAMPLES.KOREAN → '국밥·찌개·백반·고기구이·칼국수'
     */
    CATEGORY_EXAMPLES: Object.freeze({
      CAFETERIA: '사옥 구내식당 (계절밥상)',
      KOREAN: '국밥·찌개·백반·고기구이·칼국수',
      CHINESE: '짜장면·짬뽕·마라탕·양꼬치',
      JAPANESE: '초밥·라멘·돈카츠·우동·덮밥',
      WESTERN: '파스타·피자·스테이크·햄버거',
      SNACK: '김밥·떡볶이·라면·순대',
      CAFE: '커피·디저트·빵·샌드위치·브런치',
      ETC: '쌀국수·카레·샐러드·멕시칸',
    }),

    /**
     * 회사 위치 (거리 계산 기준). 서버는 DB의 구내식당 좌표를 쓴다.
     * 화면은 목록에 구내식당이 있으면 그 좌표를, 없으면(카테고리로 거른 경우) 이 값을 쓴다.
     * docs/sql/03_init_data.sql의 구내식당 좌표와 같게 맞춘다.
     */
    OFFICE_LOCATION: Object.freeze({ lat: 37.5503, lng: 127.0731 }),

    /**
     * 식당 목록 조회 (검색·필터·지도) — GET /api/restaurants/list
     * @param {{keyword?: string, category?: string}} [condition] 빈 값이면 전체
     * @returns {Promise<Array>} [{restaurantId, name, category, cafeteria, thumbsCount30d, reviewCount, lat, lng}]
     */
    getList(condition) {
      return api.get('/restaurants/list', condition);
    },

    /**
     * 식당 상세 조회 — GET /api/restaurants/{restaurantId}
     * @param {number} restaurantId
     * @returns {Promise<Object>} {restaurantId, name, category, cafeteria, mapUrl, address,
     *                            topTags, thumbsCount30d, interestCount, reviewCount, interested}
     */
    getDetail(restaurantId) {
      return api.get('/restaurants/' + encodeURIComponent(restaurantId));
    },

    /**
     * 관심 등록 — POST /api/restaurants/{restaurantId}/interest
     * 오류: 404 RESTAURANT_NOT_FOUND, 409 ALREADY_INTERESTED
     * @returns {Promise<{interested: boolean, interestCount: number}>}
     */
    addInterest(restaurantId) {
      return api.post('/restaurants/' + encodeURIComponent(restaurantId) + '/interest');
    },

    /**
     * 관심 해제 — DELETE /api/restaurants/{restaurantId}/interest
     * 오류: 404 INTEREST_NOT_FOUND
     * @returns {Promise<{interested: boolean, interestCount: number}>}
     */
    removeInterest(restaurantId) {
      return api.delete('/restaurants/' + encodeURIComponent(restaurantId) + '/interest');
    },

    /**
     * 식당 이름 자동완성 (등록된 식당, 최대 5개) — GET /api/restaurants/autocomplete
     * @returns {Promise<Array>} [{restaurantId, name, category}]
     */
    autocomplete(keyword) {
      return api.get('/restaurants/autocomplete', { keyword });
    },

    /**
     * 네이버 지도 URL 검증 — GET /api/restaurants/check-map-url
     * 실패도 200: valid=false + reason(INVALID_FORMAT / DUPLICATE / NOT_FOUND / NO_ELIGIBLE_PLACE) + message
     * 오류: 400 INVALID_REQUEST, 502 MAP_API_ERROR
     * 2026-11-24: 장소 1곳(place) → 후보 목록(candidates, 최대 5개). 사용자가 고른다
     * @returns {Promise<{valid, reason, message, radiusKm: number, candidates: Array<{name, address, lat, lng,
     *   naverCategory, distanceKm, suggestedCategory, registrable: boolean, blockReason: 'NOT_A_RESTAURANT'|'OUT_OF_RANGE'|null}>}>}
     */
    checkMapUrl(url, name) {
      return api.get('/restaurants/check-map-url', { url, name });
    },

    /**
     * 식당 등록 — POST /api/restaurants/register
     * 오류: 400 INVALID_REQUEST / INVALID_MAP_URL / NOT_A_RESTAURANT / RESTAURANT_OUT_OF_RANGE,
     *       404 MAP_PLACE_NOT_FOUND, 409 RESTAURANT_ALREADY_EXISTS, 502 MAP_API_ERROR
     * @param {{mapUrl: string, name: string, category: string, placeName: string, placeAddress: string}} restaurant
     *   category는 CAFETERIA 제외, placeName·placeAddress는 check-map-url에서 고른 후보
     * @returns {Promise<{restaurantId: number}>}
     */
    register(restaurant) {
      return api.post('/restaurants/register', restaurant);
    },

    /**
     * 식당 정보 수정 제안 — POST /api/restaurants/{restaurantId}/suggestions
     * 오류: 400 INVALID_REQUEST(내용 없음·500자 초과), 404 RESTAURANT_NOT_FOUND
     * @returns {Promise<{suggestionId: number}>}
     */
    createSuggestion(restaurantId, content) {
      return api.post('/restaurants/' + encodeURIComponent(restaurantId) + '/suggestions', { content });
    },
  };
})(window);
