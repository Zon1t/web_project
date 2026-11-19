/*
 * mypage.js — 마이페이지 API 호출 (C)
 *
 *   const summary = await mypageApi.getSummary();
 *   // { nickname, loginId, reviewCount, partyCount, restaurantCount, interestCount }
 *
 * 모두 로그인 필요, 목록은 배열(없으면 빈 배열)을 돌려준다. (docs/API_SPEC.md 2장)
 */
(function (global) {
  'use strict';

  global.mypageApi = {
    /** 마이페이지 주소 (S-10). 탭을 바로 열려면 '?tab=parties' 처럼 붙인다 */
    PAGE_URL: '/mypage.html',

    /** 프로필 + 활동 건수 — GET /api/users/me/summary */
    getSummary() {
      return api.get('/users/me/summary');
    },

    /** 내 리뷰 (최신순) — [{ reviewId, restaurantId, restaurantName, content, tags[], imageUrl, createdAt }] */
    getReviews() {
      return api.get('/users/me/reviews');
    },

    /** 참여한 식사팟 — [{ partyId, partyDate, mealType, time, restaurantId, restaurantName, status, host, confirmed }] */
    getParties() {
      return api.get('/users/me/parties');
    },

    /** 등록한 식당 (최신순) — [{ restaurantId, name, category, createdAt }] */
    getRestaurants() {
      return api.get('/users/me/restaurants');
    },

    /** 관심 식당 (최근 등록순) — [{ restaurantId, name, category, thumbsCount30d, visited, interestedAt }] */
    getInterests() {
      return api.get('/users/me/interests');
    },
  };
})(window);
