/*
 * admin.js — 관리자 API 호출 (A, 2026-11-24 A3). 기준: docs/API_SPEC.md 7장
 * 관리자가 아니면 서버가 403 FORBIDDEN을 준다.
 *
 *   const { radiusKm, items } = await adminApi.getRestaurants({ outOfRange: true });
 *   await adminApi.updateRestaurant(12, { hidden: true });
 *   await adminApi.updateRestaurant(12, { category: 'KOREAN' });
 *   const list = await adminApi.getSuggestions('OPEN');        // OPEN | DONE | ALL
 *   await adminApi.updateSuggestion(3, 'DONE');
 *   const reviews = await adminApi.getReviews('세종원');
 *   await adminApi.updateReview(31, true);
 *   const { lunch, dinner } = await adminApi.getCafeteriaMenus('2026-11-26');
 *   await adminApi.saveCafeteriaMenus('2026-11-26', 'LUNCH', ['현미밥', '김치찌개']);
 */
(function (global) {
  'use strict';

  global.adminApi = {
    /** 전체 식당 (숨김 포함, 회사까지 거리) — GET /api/admin/restaurants → { radiusKm, items } */
    getRestaurants(options) {
      const outOfRange = options && options.outOfRange ? 'true' : undefined;
      return api.get('/admin/restaurants', { outOfRange });
    },

    /** 식당 숨김·카테고리 변경 — PATCH /api/admin/restaurants/{id} → item */
    updateRestaurant(restaurantId, body) {
      return api.patch('/admin/restaurants/' + restaurantId, body);
    },

    /** 수정 제안 목록 — GET /api/admin/suggestions?status= → [item] */
    getSuggestions(status) {
      return api.get('/admin/suggestions', { status });
    },

    /** 수정 제안 처리 상태 — PATCH /api/admin/suggestions/{id} → item */
    updateSuggestion(suggestionId, status) {
      return api.patch('/admin/suggestions/' + suggestionId, { status });
    },

    /** 최근 리뷰 (숨김 포함, 최대 100건) — GET /api/admin/reviews?keyword= → [item] */
    getReviews(keyword) {
      return api.get('/admin/reviews', { keyword: keyword || undefined });
    },

    /** 리뷰 숨김·숨김 해제 — PATCH /api/admin/reviews/{id} → item */
    updateReview(reviewId, hidden) {
      return api.patch('/admin/reviews/' + reviewId, { hidden });
    },

    /** 날짜별 구내식당 메뉴 — GET /api/admin/cafeteria/menus?date= → { menuDate, lunch, dinner } */
    getCafeteriaMenus(date) {
      return api.get('/admin/cafeteria/menus', { date });
    },

    /** 날짜·끼니 메뉴 교체 — POST /api/admin/cafeteria/menus → { menuDate, mealType, menuNames } */
    saveCafeteriaMenus(menuDate, mealType, menuNames) {
      return api.post('/admin/cafeteria/menus', { menuDate, mealType, menuNames });
    },
  };
})(window);
