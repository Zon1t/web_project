/*
 * cafeteria.js — 구내식당 API 호출 (C)
 *
 *   const today = await cafeteriaApi.getToday();
 *   // { date, cafeteriaName, status: 'OPEN'|'CLOSED'|'UNAVAILABLE', lunch: [], dinner: [] }
 *
 * 수집 실패·미운영일도 200이며 status로 구분한다. (docs/API_SPEC.md 3장)
 */
(function (global) {
  'use strict';

  global.cafeteriaApi = {
    /** 오늘의 구내식당 메뉴 — GET /api/cafeteria/menus/today */
    getToday() {
      return api.get('/cafeteria/menus/today');
    },
  };
})(window);
