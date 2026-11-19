/*
 * review.js — 리뷰 API 호출 + 리뷰 태그 상수 (B)
 *
 * 화면 JS에서는 이렇게 쓴다.
 *   const page = await reviewApi.getList(12, { page: 1, size: 10 });
 *   const { reviewId } = await reviewApi.create(12, { content: '맛있어요', tagIds: [1, 5] });
 *   await reviewApi.delete(7);
 */
(function (global) {
  'use strict';

  /**
   * 리뷰 태그 13개 (FE 상수, 태그 조회 API 없음). 기준: API 명세서 공통 규칙 '리뷰 태그'
   * 화면에서는 이름 앞에 '#'을 붙여 보여 준다.
   */
  const TAG_GROUPS = Object.freeze([
    { category: 'MEAL_TYPE', label: '식사 상황', tags: [[1, '점심추천'], [2, '저녁추천'], [3, '퇴근후식사'], [4, '해장추천']] },
    { category: 'GROUP_TYPE', label: '인원·모임', tags: [[5, '혼밥가능'], [6, '단체가능'], [7, '소규모모임']] },
    { category: 'PRICE', label: '가격', tags: [[8, '가성비']] },
    { category: 'WAITING', label: '시간·대기', tags: [[9, '웨이팅많음']] },
    { category: 'ACCESSIBILITY', label: '접근성·편의', tags: [[10, '도보5분이내'], [11, '주차가능'], [12, '포장가능'], [13, '단체석']] },
  ].map((group) => Object.freeze({
    category: group.category,
    label: group.label,
    tags: group.tags.map(([tagId, name]) => Object.freeze({ tagId, name })),
  })));

  const CONTENT_MAX = 100;

  function reviewsPath(restaurantId) {
    return '/restaurants/' + encodeURIComponent(restaurantId) + '/reviews';
  }

  global.reviewApi = {
    TAG_GROUPS,
    CONTENT_MAX,

    /**
     * 리뷰 목록 (최신순 페이징) — GET /api/restaurants/{restaurantId}/reviews
     * @param {{page?: number, size?: number}} [paging] page는 1부터, size 기본 10 (최대 50)
     * @returns {Promise<{page, size, totalCount, hasNext, items: Array}>}
     *          items: [{reviewId, nickname, content, tags, imageUrl, createdAt, mine}]
     */
    getList(restaurantId, paging) {
      return api.get(reviewsPath(restaurantId), paging);
    },

    /**
     * 리뷰 작성 (multipart) — POST /api/restaurants/{restaurantId}/reviews
     * 오류: 400 INVALID_REQUEST(한줄평), 400 INVALID_TAG, 404 RESTAURANT_NOT_FOUND
     * @param {{content: string, tagIds?: number[]}} review 사진(image)은 다음 작업에서 추가
     * @returns {Promise<{reviewId: number}>}
     */
    create(restaurantId, review) {
      const form = new FormData();
      form.append('content', review.content);
      (review.tagIds || []).forEach((tagId) => form.append('tagIds', tagId)); // tagIds=1&tagIds=4
      return api.postForm(reviewsPath(restaurantId), form);
    },

    /**
     * 리뷰 삭제 (작성자만) — DELETE /api/reviews/{reviewId}
     * 오류: 403 FORBIDDEN, 404 REVIEW_NOT_FOUND
     */
    delete(reviewId) {
      return api.delete('/reviews/' + encodeURIComponent(reviewId));
    },
  };
})(window);
