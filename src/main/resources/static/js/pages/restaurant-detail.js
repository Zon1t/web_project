/*
 * restaurant-detail.js — S-05 식당 상세 (B)
 *
 * 주소: /restaurant-detail.html?id=12
 *
 * 이번에 만든 것: 식당 정보(이름·카테고리·주소·태그 TOP 3·따봉·관심·리뷰 수), 네이버 지도 링크,
 *                로딩 / 없는 식당 / 오류 화면
 * 나중에 붙일 것 (자리만 있음)
 *   - [관심] 버튼 동작      → feature/restaurant-interest
 *   - 리뷰 작성·목록        → feature/review
 *   - [식사팟 만들기] 이동  → A의 식사팟 생성 화면 주소 확인 후
 *   - 식당 정보 수정 제안   → feature/restaurant-suggestion
 */
(function () {
  'use strict';

  const CATEGORY_LABELS = restaurantApi.CATEGORY_LABELS;
  const esc = utils.escapeHtml;

  const root = document.getElementById('detail-root');
  const restaurantId = new URLSearchParams(location.search).get('id');

  /* ---------- 시작 ---------- */

  root.addEventListener('click', (event) => {
    if (event.target.closest('[data-action="retry"]')) loadDetail();
  });

  // id가 숫자가 아니면 서버에 묻지 않고 바로 '없는 식당' 화면
  if (!/^[1-9]\d*$/.test(restaurantId || '')) {
    showNotFound();
  } else {
    loadDetail();
  }

  /* ---------- 조회 ---------- */

  async function loadDetail() {
    showLoading();
    try {
      const detail = await restaurantApi.getDetail(restaurantId);
      renderDetail(detail);
    } catch (err) {
      if (err.statusCode === 401) return; // client.js가 로그인 화면으로 이동시킨다
      if (err.error === 'RESTAURANT_NOT_FOUND') {
        showNotFound();
        return;
      }
      showError(err.message);
    }
  }

  /* ---------- 그리기 ---------- */

  function renderDetail(d) {
    document.title = d.name + ' | 슥밥';

    root.innerHTML =
      '<section class="detail-info" aria-labelledby="restaurant-name">' +
      '<div class="detail-info__title-row">' +
      '<h1 id="restaurant-name" class="detail-info__name">' + esc(d.name) + '</h1>' +
      '<span class="badge detail-info__category">' + esc(CATEGORY_LABELS[d.category] || d.category) + '</span>' +
      '</div>' +
      (d.address ? '<p class="detail-info__address">' + esc(d.address) + '</p>' : '') +
      renderTopTags(d.topTags) +
      '<div class="detail-stats">' +
      '<span class="detail-stats__item">' + utils.thumbsUpIcon() +
      '따봉 <strong>' + d.thumbsCount30d + '</strong> <span class="text-muted">(최근 30일)</span></span>' +
      '<span class="detail-stats__item">관심 <strong>' + d.interestCount + '</strong></span>' +
      '<span class="detail-stats__item">리뷰 <strong>' + d.reviewCount + '</strong></span>' +
      '</div>' +
      renderMapLink(d.mapUrl) +
      '<div class="detail-actions">' +
      // TODO(feature/restaurant-interest): 관심 등록·해제 연결
      '<button type="button" class="btn" disabled aria-pressed="' + d.interested + '" title="준비 중">' +
      (d.interested ? '관심 해제' : '관심') +
      '</button>' +
      // TODO(A와 확인): 식사팟 생성 화면 주소 (예: /party-create.html?restaurantId=12)
      '<button type="button" class="btn btn--primary" disabled title="준비 중">이 식당으로 식사팟 만들기</button>' +
      '</div>' +
      '</section>' +

      // TODO(feature/review): 리뷰 작성 폼 + 목록(페이징)
      '<section class="detail-reviews" aria-labelledby="review-title">' +
      '<h2 id="review-title" class="section-title">리뷰<span class="detail-reviews__count">' + d.reviewCount + '</span></h2>' +
      '<div class="state state--empty">' +
      (d.reviewCount === 0
        ? '<p class="state__title">첫 리뷰를 남겨 주세요</p>'
        : '<p class="state__title">리뷰 목록 준비 중</p>') +
      '<p class="state__desc">리뷰 작성·목록은 다음 작업에서 연결됩니다.</p>' +
      '</div>' +
      '</section>';
  }

  function renderTopTags(tags) {
    if (!tags || tags.length === 0) return ''; // 선택된 태그가 없으면 줄 자체를 숨긴다
    return (
      '<div class="detail-tags">' +
      '<span class="detail-tags__label">많이 선택된 태그</span>' +
      tags
        .map((tag) =>
          '<span class="detail-tag">#' + esc(tag.name) +
          '<span class="detail-tag__count">' + tag.count + '</span></span>')
        .join('') +
      '</div>'
    );
  }

  // 네이버 지도 링크는 새 탭으로. http(s) 주소가 아니면 링크를 만들지 않는다.
  function renderMapLink(mapUrl) {
    if (!/^https?:\/\//i.test(mapUrl || '')) return '';
    return (
      '<a class="detail-map-link" href="' + esc(mapUrl) + '" target="_blank" rel="noopener noreferrer">' +
      '네이버 지도에서 보기<span class="sr-only">(새 탭)</span></a>'
    );
  }

  function showLoading() {
    root.innerHTML =
      '<div class="state state--loading">' +
      '<span class="spinner" aria-hidden="true"></span>' +
      '<p class="state__desc">식당 정보를 불러오는 중…</p>' +
      '</div>';
  }

  function showNotFound() {
    document.title = '식당을 찾을 수 없습니다 | 슥밥';
    root.innerHTML =
      '<div class="state state--empty">' +
      '<p class="state__title">식당을 찾을 수 없습니다</p>' +
      '<p class="state__desc">주소가 올바른지 확인해 주세요.</p>' +
      '<a class="btn" href="/restaurant-list.html">식당 목록으로</a>' +
      '</div>';
  }

  function showError(message) {
    root.innerHTML =
      '<div class="state state--error">' +
      '<p class="state__title">식당 정보를 불러오지 못했습니다</p>' +
      '<p class="state__desc">' + esc(message) + '</p>' +
      '<button type="button" class="btn" data-action="retry">다시 시도</button>' +
      '</div>';
  }
})();
