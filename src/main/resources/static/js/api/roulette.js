/*
 * roulette.js — 메뉴 룰렛 API 호출 (C)
 *
 *   const result = await rouletteApi.getCandidates('KOREAN');
 *   // { category, totalCount, candidates: [{ restaurantId, name }], spinnable }
 *   const spin = await rouletteApi.spin([12, 18, 21]);
 *   // { winnerId, winner: { restaurantId, name, category, thumbsCount30d } }
 *
 * 구내식당(CAFETERIA)은 '전체'를 포함해 카테고리 룰렛 후보에서 항상 제외된다. (docs/API_SPEC.md 4장)
 * 내 룰렛은 후보 조회 없이 고른 식당 ID(2~8곳, 구내식당 포함)를 바로 spin에 보낸다. (2026-11-23)
 */
(function (global) {
  'use strict';

  global.rouletteApi = {
    /** 룰렛 화면 주소 (S-11) */
    PAGE_URL: '/roulette.html',

    /** 룰렛 카테고리 (구내식당 없음). null = 전체 */
    CATEGORIES: Object.freeze([null, 'KOREAN', 'CHINESE', 'JAPANESE', 'WESTERN', 'SNACK']),   // '기타' 칩 없음 (11/23, '전체'에는 포함)

    /**
     * 룰렛 후보 조회 — GET /api/restaurants/roulette/candidates
     * @param {string|null} [category] 없으면 전체
     */
    getCandidates(category) {
      return api.get('/restaurants/roulette/candidates', category ? { category } : undefined);
    },

    /**
     * 룰렛 실행 — POST /api/restaurants/roulette/spin
     * @param {number[]} candidateIds 휠에 그려진 후보 ID (2~8개)
     */
    spin(candidateIds) {
      return api.post('/restaurants/roulette/spin', { candidateIds });
    },
  };
})(window);
