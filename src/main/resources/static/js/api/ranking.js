/*
 * ranking.js — 따봉 랭킹 API 호출 (C)
 *
 *   const result = await rankingApi.getRankings(3);   // 메인 TOP 3
 *   // { from, to, rankings: [{ rank, restaurantId, name, category, thumbsCount30d, reviewCount }] }
 *
 * 최근 30일 따봉 1건 이상인 식당만, 대상이 없으면 rankings는 빈 배열 (docs/API_SPEC.md 4장)
 */
(function (global) {
  'use strict';

  global.rankingApi = {
    /** 랭킹 화면 주소 (S-09) */
    PAGE_URL: '/ranking.html',

    /**
     * 따봉 랭킹 — GET /api/restaurants/rankings
     * @param {number} [limit] 1~10, 없으면 10
     */
    getRankings(limit) {
      return api.get('/restaurants/rankings', { limit });
    },
  };
})(window);
