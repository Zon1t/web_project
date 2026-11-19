/*
 * restaurant.mock.js — 식당 API 목업 (B)
 *
 * 응답 구조는 docs/API_SPEC.md(노션 API 명세서) 응답 예시와 똑같이 맞춘다.
 * 백엔드 API가 완성되면 HTML에서 이 파일의 <script>를 빼면 실제 서버를 호출한다.
 *
 * 등록된 목업
 *   GET /restaurants/list
 *   GET /restaurants/{restaurantId}
 */
(function () {
  'use strict';

  const CATEGORIES = ['CAFETERIA', 'KOREAN', 'CHINESE', 'JAPANESE', 'WESTERN', 'SNACK', 'ETC'];

  // 목업 식당 데이터 (이름·주소는 가짜 값)
  const restaurants = [
    {
      restaurantId: 1,
      name: '세종대 계절밥상',
      category: 'CAFETERIA',
      mapUrl: 'https://naver.me/mock0001',
      address: '서울 광진구 능동로 ...',
      lat: 37.5509,
      lng: 127.0738,
      thumbsCount30d: 0,
      reviewCount: 0,
      interestCount: 2,
      interested: false,
      topTags: [],
    },
    {
      restaurantId: 12,
      name: 'OO국밥 세종대점',
      category: 'KOREAN',
      mapUrl: 'https://naver.me/xxxx',
      address: '서울 광진구 능동로 ...',
      lat: 37.5502,
      lng: 127.0731,
      thumbsCount30d: 42,
      reviewCount: 3,
      interestCount: 15,
      interested: false,
      topTags: [
        { tagId: 5, name: '혼밥가능', category: 'GROUP_TYPE', count: 18 },
        { tagId: 1, name: '점심추천', category: 'MEAL_TYPE', count: 11 },
        { tagId: 8, name: '가성비', category: 'PRICE', count: 9 },
      ],
    },
    {
      restaurantId: 15,
      name: 'OO국수',
      category: 'KOREAN',
      mapUrl: 'https://naver.me/mock0015',
      address: '서울 광진구 군자로 ...',
      lat: 37.5521,
      lng: 127.0749,
      thumbsCount30d: 7,
      reviewCount: 5,
      interestCount: 4,
      interested: true,
      topTags: [{ tagId: 10, name: '도보5분이내', category: 'ACCESSIBILITY', count: 3 }],
    },
    {
      restaurantId: 18,
      name: '△△분식',
      category: 'SNACK',
      mapUrl: 'https://naver.me/mock0018',
      address: '서울 광진구 능동로 ...',
      lat: 37.5493,
      lng: 127.0752,
      thumbsCount30d: 7,
      reviewCount: 2,
      interestCount: 1,
      interested: false,
      topTags: [],
    },
    {
      restaurantId: 21,
      name: '□□반점',
      category: 'CHINESE',
      mapUrl: 'https://naver.me/mock0021',
      address: '서울 광진구 아차산로 ...',
      lat: 37.5488,
      lng: 127.0719,
      thumbsCount30d: 0,
      reviewCount: 1,
      interestCount: 0,
      interested: false,
      topTags: [{ tagId: 6, name: '단체가능', category: 'GROUP_TYPE', count: 1 }],
    },
    {
      restaurantId: 25,
      name: '◇◇스시',
      category: 'JAPANESE',
      mapUrl: 'https://naver.me/mock0025',
      address: '서울 광진구 능동로 ...',
      lat: 37.5515,
      lng: 127.0709,
      thumbsCount30d: 0,
      reviewCount: 0,
      interestCount: 0,
      interested: false,
      topTags: [],
    },
  ];

  // GET /api/restaurants/list
  // 정렬: 따봉 수 내림차순 → 리뷰 수 내림차순 → 등록 순(restaurantId 오름차순)
  api.mock('GET', '/restaurants/list', ({ query }) => {
    const keyword = (query.keyword || '').trim();
    const category = query.category || '';

    if ((category && !CATEGORIES.includes(category)) || keyword.length > 30) {
      throw api.mockError(400, 'INVALID_REQUEST', '검색 조건을 확인해 주세요.');
    }

    return restaurants
      .filter((r) => !keyword || r.name.includes(keyword))
      .filter((r) => !category || r.category === category)
      .sort((a, b) =>
        b.thumbsCount30d - a.thumbsCount30d ||
        b.reviewCount - a.reviewCount ||
        a.restaurantId - b.restaurantId)
      .map((r) => ({
        restaurantId: r.restaurantId,
        name: r.name,
        category: r.category,
        cafeteria: r.category === 'CAFETERIA',
        thumbsCount30d: r.thumbsCount30d,
        reviewCount: r.reviewCount,
        lat: r.lat,
        lng: r.lng,
      }));
  });

  // GET /api/restaurants/{restaurantId}
  api.mock('GET', '/restaurants/{restaurantId}', ({ params }) => {
    const r = restaurants.find((item) => String(item.restaurantId) === params.restaurantId);
    if (!r) {
      throw api.mockError(404, 'RESTAURANT_NOT_FOUND', '식당을 찾을 수 없습니다.');
    }
    return {
      restaurantId: r.restaurantId,
      name: r.name,
      category: r.category,
      cafeteria: r.category === 'CAFETERIA',
      mapUrl: r.mapUrl,
      address: r.address,
      topTags: r.topTags,
      thumbsCount30d: r.thumbsCount30d,
      interestCount: r.interestCount,
      reviewCount: r.reviewCount,
      interested: r.interested,
    };
  });
})();
