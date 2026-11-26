/*
 * review.mock.js — 리뷰 API 목업 (B)
 *
 * 응답 구조는 API 명세서 응답 예시와 똑같이 맞춘다.
 * review.js보다 먼저, client.js 다음에 불러온다.
 *
 * 등록된 목업
 *   GET    /restaurants/{restaurantId}/reviews
 *   POST   /restaurants/{restaurantId}/reviews
 *   DELETE /reviews/{reviewId}
 */
(function () {
  'use strict';

  const MY_NICKNAME = '슥사인'; // 목업에서 '내'가 쓴 리뷰의 닉네임
  const TAG_NAMES = {
    1: '점심추천', 2: '저녁추천', 3: '퇴근후식사', 4: '해장추천', 5: '혼밥가능', 6: '단체가능', 7: '소규모모임',
    8: '가성비', 9: '웨이팅많음', 10: '도보5분이내', 11: '주차가능', 12: '포장가능', 13: '단체석',
  };
  const MOCK_RESTAURANT_IDS = ['1', '12', '15', '18', '21', '25']; // restaurant.mock.js와 같은 식당

  let nextReviewId = 100;

  // 목업 리뷰 (restaurantId → 리뷰 배열, 오래된 것부터)
  const reviews = {
    12: [
      { reviewId: 5, nickname: '점심요정', content: '혼자 가기 편해요', tags: ['혼밥가능'], imageUrl: null, createdAt: '2026-11-14T12:10:00' },
      { reviewId: 6, nickname: '국밥러버', content: '가격 대비 양이 많아요', tags: ['점심추천', '가성비'], imageUrl: null, createdAt: '2026-11-15T12:30:00' },
      { reviewId: 7, nickname: MY_NICKNAME, content: '국물이 진해요', tags: ['혼밥가능', '점심추천'], imageUrl: null, createdAt: '2026-11-16T12:40:00' },
    ],
  };

  function toItem(review) {
    return Object.assign({}, review, { mine: review.nickname === MY_NICKNAME });
  }

  // GET /api/restaurants/{restaurantId}/reviews
  api.mock('GET', '/restaurants/{restaurantId}/reviews', ({ params, query }) => {
    const page = query.page === undefined ? 1 : Number(query.page);
    const size = query.size === undefined ? 10 : Number(query.size);
    if (!Number.isInteger(page) || !Number.isInteger(size) || page < 1 || size < 1 || size > 50) {
      throw api.mockError(400, 'INVALID_REQUEST', '페이지 정보를 확인해 주세요.');
    }
    if (!MOCK_RESTAURANT_IDS.includes(params.restaurantId)) {
      throw api.mockError(404, 'RESTAURANT_NOT_FOUND', '식당을 찾을 수 없습니다.');
    }

    const all = (reviews[params.restaurantId] || []).slice().reverse(); // 최신순
    const start = (page - 1) * size;
    return {
      page,
      size,
      totalCount: all.length,
      hasNext: start + size < all.length,
      items: all.slice(start, start + size).map(toItem),
    };
  });

  // POST /api/restaurants/{restaurantId}/reviews (multipart → body는 객체로 바뀌어 들어온다)
  api.mock('POST', '/restaurants/{restaurantId}/reviews', ({ params, body }) => {
    const content = (body.content || '').trim();
    if (!content || content.length > 100) {
      throw api.mockError(400, 'INVALID_REQUEST', '한줄평을 입력해 주세요.');
    }
    if (!MOCK_RESTAURANT_IDS.includes(params.restaurantId)) {
      throw api.mockError(404, 'RESTAURANT_NOT_FOUND', '식당을 찾을 수 없습니다.');
    }
    const tagIds = Array.from(new Set([].concat(body.tagIds || []).map(Number))).sort((a, b) => a - b);
    if (tagIds.some((id) => !TAG_NAMES[id])) {
      throw api.mockError(400, 'INVALID_TAG', '선택할 수 없는 태그입니다.');
    }

    const image = body.image instanceof File && body.image.size > 0 ? body.image : null;
    if (image && (!['image/jpeg', 'image/png'].includes(image.type) || image.size > 5 * 1024 * 1024)) {
      throw api.mockError(400, 'INVALID_IMAGE', 'jpg·png 형식, 5MB 이하 사진만 올릴 수 있습니다.');
    }

    const review = {
      reviewId: nextReviewId++,
      nickname: MY_NICKNAME,
      content,
      tags: tagIds.map((id) => TAG_NAMES[id]),
      // 목업은 파일을 저장하지 않으므로 브라우저 안에서만 보이는 임시 주소를 쓴다 (새로고침하면 사라짐)
      imageUrl: image ? URL.createObjectURL(image) : null,
      createdAt: new Date().toISOString().slice(0, 19),
    };
    (reviews[params.restaurantId] = reviews[params.restaurantId] || []).push(review);
    return { reviewId: review.reviewId };
  });

  // DELETE /api/reviews/{reviewId}
  api.mock('DELETE', '/reviews/{reviewId}', ({ params }) => {
    for (const list of Object.values(reviews)) {
      const index = list.findIndex((r) => String(r.reviewId) === params.reviewId);
      if (index === -1) continue;
      if (list[index].nickname !== MY_NICKNAME) {
        throw api.mockError(403, 'FORBIDDEN', '본인이 작성한 리뷰만 삭제할 수 있습니다.');
      }
      list.splice(index, 1);
      return null;
    }
    throw api.mockError(404, 'REVIEW_NOT_FOUND', '리뷰를 찾을 수 없습니다.');
  });
})();
