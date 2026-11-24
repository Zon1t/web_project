/*
 * restaurant.mock.js — 식당 API 목업 (B)
 *
 * 응답 구조는 docs/API_SPEC.md(노션 API 명세서) 응답 예시와 똑같이 맞춘다.
 * 백엔드 API가 완성되면 HTML에서 이 파일의 <script>를 빼면 실제 서버를 호출한다.
 *
 * 등록된 목업
 *   GET /restaurants/list
 *   GET /restaurants/{restaurantId}
 *   POST, DELETE /restaurants/{restaurantId}/interest
 *   GET /restaurants/autocomplete, GET /restaurants/check-map-url, POST /restaurants/register
 *   POST /restaurants/{restaurantId}/suggestions
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

  // ⚠️ '/restaurants/autocomplete'처럼 고정 주소 목업은 '/restaurants/{restaurantId}'보다 먼저 등록해야 한다.
  //    (목업은 등록한 순서대로 찾기 때문에, 뒤에 두면 {restaurantId}가 'autocomplete'를 가로챈다)

  // GET /api/restaurants/autocomplete
  api.mock('GET', '/restaurants/autocomplete', ({ query }) => {
    const keyword = (query.keyword || '').trim();
    if (!keyword || keyword.length > 30) {
      throw api.mockError(400, 'INVALID_REQUEST', '검색어를 입력해 주세요.');
    }
    return restaurants
      .filter((r) => r.name.includes(keyword))
      .sort((a, b) =>
        Number(!a.name.startsWith(keyword)) - Number(!b.name.startsWith(keyword)) ||
        a.name.localeCompare(b.name, 'ko'))
      .slice(0, 5)
      .map((r) => ({ restaurantId: r.restaurantId, name: r.name, category: r.category }));
  });

  // 목업용 장소 ID 찾기: map.naver.com/.../place/{숫자} 또는 naver.me/{아무 글자}
  function mockPlaceKey(url) {
    const place = /^https?:\/\/(m\.)?map\.naver\.com\/.*\/place\/(\d+)/i.exec(url);
    if (place) return 'place:' + place[2];
    const short = /^https?:\/\/naver\.me\/(\w+)/i.exec(url);
    return short ? 'short:' + short[1] : null;
  }

  // GET /api/restaurants/check-map-url
  // 목업 규칙: 이름에 '없는'이 들어가면 NOT_FOUND, 'naver.me/xxxx'(12번 식당 링크)는 DUPLICATE
  api.mock('GET', '/restaurants/check-map-url', ({ query }) => {
    const url = (query.url || '').trim();
    const name = (query.name || '').trim();
    if (!url || !name || name.length > 30) {
      throw api.mockError(400, 'INVALID_REQUEST', 'URL과 식당 이름을 입력해 주세요.');
    }
    const key = mockPlaceKey(url);
    const fail = (reason, message) => ({ valid: false, reason, message, radiusKm: 4, candidates: [] });
    if (!key) return fail('INVALID_FORMAT', '네이버 지도 URL만 입력할 수 있습니다.');
    if (restaurants.some((r) => mockPlaceKey(r.mapUrl) === key)) return fail('DUPLICATE', '이미 등록된 식당입니다.');
    if (name.includes('없는')) return fail('NOT_FOUND', '존재하지 않는 주소입니다.');
    // 2026-11-24: 후보 목록 (가까운 지점 / 먼 지점 / 음식점 아님)
    return {
      valid: true,
      reason: null,
      message: null,
      radiusKm: 4,
      candidates: [
        { name: name + ' 세종대점', address: '서울 광진구 능동로 ...', lat: 37.5502, lng: 127.0731, naverCategory: '음식점>한식',
          distanceKm: 0.01, suggestedCategory: 'KOREAN', registrable: true, blockReason: null },
        { name: name + ' 강남점', address: '서울 강남구 ...', lat: 37.4979, lng: 127.0276, naverCategory: '음식점>한식',
          distanceKm: 7.03, suggestedCategory: 'KOREAN', registrable: false, blockReason: 'OUT_OF_RANGE' },
        { name: name + ' 편의점', address: '서울 광진구 ...', lat: 37.5495, lng: 127.0735, naverCategory: '생활,편의>편의점',
          distanceKm: 0.1, suggestedCategory: null, registrable: false, blockReason: 'NOT_A_RESTAURANT' },
      ],
    };
  });

  // POST /api/restaurants/register
  api.mock('POST', '/restaurants/register', ({ body }) => {
    const name = (body.name || '').trim();
    const category = body.category;
    if (!body.mapUrl || !name || name.length > 30 || !CATEGORIES.includes(category) || category === 'CAFETERIA' ||
        !body.placeName || !body.placeAddress) {
      throw api.mockError(400, 'INVALID_REQUEST', '입력값을 확인해 주세요.');
    }
    const key = mockPlaceKey(body.mapUrl.trim());
    if (!key) {
      throw api.mockError(400, 'INVALID_MAP_URL', '네이버 지도 URL만 입력할 수 있습니다.');
    }
    if (restaurants.some((r) => mockPlaceKey(r.mapUrl) === key)) {
      throw api.mockError(409, 'RESTAURANT_ALREADY_EXISTS', '이미 등록된 식당입니다.');
    }
    const restaurantId = Math.max(...restaurants.map((r) => r.restaurantId)) + 1;
    restaurants.push({
      restaurantId, name, category, mapUrl: body.mapUrl.trim(), address: '서울 광진구 능동로 ...',
      lat: 37.5502, lng: 127.0731, thumbsCount30d: 0, reviewCount: 0, interestCount: 0, interested: false, topTags: [],
    });
    return { restaurantId };
  });

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
      lat: r.lat,
      lng: r.lng,
      topTags: r.topTags,
      thumbsCount30d: r.thumbsCount30d,
      interestCount: r.interestCount,
      reviewCount: r.reviewCount,
      interested: r.interested,
    };
  });

  // POST /api/restaurants/{restaurantId}/interest
  api.mock('POST', '/restaurants/{restaurantId}/interest', ({ params }) => {
    const r = restaurants.find((item) => String(item.restaurantId) === params.restaurantId);
    if (!r) {
      throw api.mockError(404, 'RESTAURANT_NOT_FOUND', '식당을 찾을 수 없습니다.');
    }
    if (r.interested) {
      throw api.mockError(409, 'ALREADY_INTERESTED', '이미 관심 등록한 식당입니다.');
    }
    r.interested = true;
    r.interestCount += 1;
    return { interested: true, interestCount: r.interestCount };
  });

  // DELETE /api/restaurants/{restaurantId}/interest
  api.mock('DELETE', '/restaurants/{restaurantId}/interest', ({ params }) => {
    const r = restaurants.find((item) => String(item.restaurantId) === params.restaurantId);
    if (!r || !r.interested) {
      throw api.mockError(404, 'INTEREST_NOT_FOUND', '관심 등록되지 않은 식당입니다.');
    }
    r.interested = false;
    r.interestCount -= 1;
    return { interested: false, interestCount: r.interestCount };
  });

  // POST /api/restaurants/{restaurantId}/suggestions
  let nextSuggestionId = 1;
  api.mock('POST', '/restaurants/{restaurantId}/suggestions', ({ params, body }) => {
    const content = ((body && body.content) || '').trim();
    if (!content || content.length > 500) {
      throw api.mockError(400, 'INVALID_REQUEST', '제안 내용을 입력해 주세요.');
    }
    if (!restaurants.some((item) => String(item.restaurantId) === params.restaurantId)) {
      throw api.mockError(404, 'RESTAURANT_NOT_FOUND', '식당을 찾을 수 없습니다.');
    }
    return { suggestionId: nextSuggestionId++ };
  });
})();
