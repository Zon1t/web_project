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
      CAFE: '카페',
      ETC: '기타',
    }),

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
     * 실패도 200: valid=false + reason(INVALID_FORMAT / DUPLICATE / NOT_FOUND) + message
     * 오류: 400 INVALID_REQUEST, 502 MAP_API_ERROR
     * @returns {Promise<{valid, reason, message, place: {name, address, lat, lng} | null}>}
     */
    checkMapUrl(url, name) {
      return api.get('/restaurants/check-map-url', { url, name });
    },

    /**
     * 식당 등록 — POST /api/restaurants/register
     * 오류: 400 INVALID_REQUEST / INVALID_MAP_URL, 404 MAP_PLACE_NOT_FOUND, 409 RESTAURANT_ALREADY_EXISTS, 502 MAP_API_ERROR
     * @param {{mapUrl: string, name: string, category: string}} restaurant category는 CAFETERIA 제외
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
