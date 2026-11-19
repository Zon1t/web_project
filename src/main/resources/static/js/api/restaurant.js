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
  };
})(window);
