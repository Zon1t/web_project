/*
 * restaurant-detail.js — S-05 식당 상세 (B)
 *
 * 주소: /restaurant-detail.html?id=12
 *
 * 만든 것: 식당 정보(이름·카테고리·주소·태그 TOP 3·따봉·관심·리뷰 수), 네이버 지도 링크,
 *          [관심] 등록·해제, [식사팟 만들기] 이동, 로딩 / 없는 식당 / 오류 화면
 * 리뷰 작성·목록·삭제는 restaurant-detail-reviews.js (이 파일보다 먼저 불러온다)
 * 나중에 붙일 것
 *   - 리뷰 사진             → feature/review-image
 *   - 식당 정보 수정 제안   → feature/restaurant-suggestion
 */
(function () {
  'use strict';

  const CATEGORY_LABELS = restaurantApi.CATEGORY_LABELS;
  const esc = utils.escapeHtml;

  const root = document.getElementById('detail-root');
  const restaurantId = new URLSearchParams(location.search).get('id');

  /* ---------- 시작 ---------- */

  // 관심 상태 (버튼을 누를 때마다 서버 응답으로 갱신)
  const interest = { interested: false, count: 0, pending: false };

  root.addEventListener('click', (event) => {
    if (event.target.closest('[data-action="retry"]')) loadDetail();
    if (event.target.closest('[data-action="toggle-interest"]')) toggleInterest();
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

  /* ---------- 관심 등록·해제 ---------- */

  async function toggleInterest() {
    if (interest.pending) return; // 응답을 기다리는 동안 다시 눌러도 무시
    interest.pending = true;
    setInterestMessage('');
    renderInterest();

    try {
      const result = interest.interested
        ? await restaurantApi.removeInterest(restaurantId)
        : await restaurantApi.addInterest(restaurantId);
      interest.interested = result.interested;
      interest.count = result.interestCount;
    } catch (err) {
      if (err.statusCode === 401) return; // client.js가 로그인 화면으로 이동시킨다
      if (err.error === 'ALREADY_INTERESTED' || err.error === 'INTEREST_NOT_FOUND') {
        // 다른 탭에서 이미 바꾼 경우: 오류 대신 서버의 실제 상태로 다시 맞춘다
        await syncInterest();
      } else {
        setInterestMessage(err.message);
      }
    } finally {
      interest.pending = false;
      renderInterest();
    }
  }

  // 상세를 다시 조회해서 관심 상태·수만 맞춘다
  async function syncInterest() {
    try {
      const detail = await restaurantApi.getDetail(restaurantId);
      interest.interested = detail.interested;
      interest.count = detail.interestCount;
    } catch (err) {
      setInterestMessage(err.message);
    }
  }

  // 버튼 모양과 '관심 N' 숫자를 interest 값에 맞게 바꾼다
  function renderInterest() {
    const button = document.getElementById('interest-button');
    const count = document.getElementById('interest-count');
    if (!button || !count) return;

    button.classList.toggle('is-active', interest.interested);
    button.setAttribute('aria-pressed', String(interest.interested));
    button.setAttribute('aria-busy', String(interest.pending));
    button.disabled = interest.pending;
    button.querySelector('.interest-button__icon').setAttribute('fill', interest.interested ? 'currentColor' : 'none');
    count.textContent = interest.count;
  }

  function setInterestMessage(message) {
    const el = document.getElementById('interest-message');
    if (el) el.textContent = message;
  }

  /* ---------- 그리기 ---------- */

  // 처음 한 번: 식당 정보 + 리뷰 영역
  function renderDetail(d) {
    root.innerHTML =
      '<section id="info-section" class="detail-info" aria-labelledby="restaurant-name"></section>' +
      '<section id="review-section" class="detail-reviews" aria-labelledby="review-title"></section>';

    renderInfo(d);

    // 리뷰 작성·삭제로 리뷰 수·태그 TOP 3가 바뀌면 식당 정보를 다시 불러온다
    restaurantReviews.mount(document.getElementById('review-section'), restaurantId, {
      onChange: refreshInfo,
    });
  }

  // 식당 정보만 다시 조회해서 다시 그린다 (리뷰 영역은 그대로)
  async function refreshInfo() {
    try {
      renderInfo(await restaurantApi.getDetail(restaurantId));
    } catch (err) {
      // 리뷰는 이미 반영됐고 숫자만 못 바꾼 상태라, 새로고침 전까지 그대로 둔다
      console.warn('식당 정보 갱신 실패', err);
    }
  }

  function renderInfo(d) {
    document.title = d.name + ' | 슥밥';
    interest.interested = d.interested;
    interest.count = d.interestCount;

    document.getElementById('info-section').innerHTML =
      '<div class="detail-info__title-row">' +
      '<h1 id="restaurant-name" class="detail-info__name">' + esc(d.name) + '</h1>' +
      '<span class="badge detail-info__category">' + esc(CATEGORY_LABELS[d.category] || d.category) + '</span>' +
      '</div>' +
      (d.address ? '<p class="detail-info__address">' + esc(d.address) + '</p>' : '') +
      renderTopTags(d.topTags) +
      '<div class="detail-stats">' +
      '<span class="detail-stats__item">' + utils.thumbsUpIcon() +
      '따봉 <strong>' + d.thumbsCount30d + '</strong> <span class="text-muted">(최근 30일)</span></span>' +
      '<span class="detail-stats__item">관심 <strong id="interest-count">' + d.interestCount + '</strong></span>' +
      '<span class="detail-stats__item">리뷰 <strong>' + d.reviewCount + '</strong></span>' +
      '</div>' +
      renderMapLink(d.mapUrl) +
      '<div class="detail-actions">' +
      // 관심: 누를 때마다 등록 ↔ 해제. 상태는 aria-pressed와 채워진 하트로 표시
      '<button type="button" id="interest-button" class="btn interest-button" data-action="toggle-interest" aria-pressed="false">' +
      '<svg class="interest-button__icon" width="18" height="18" viewBox="0 0 24 24" fill="none" ' +
      'stroke="currentColor" stroke-width="2" stroke-linejoin="round" aria-hidden="true">' +
      '<path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1-1.1a5.5 5.5 0 0 0-7.8 7.8L12 21l8.8-8.6a5.5 5.5 0 0 0 0-7.8z"></path>' +
      '</svg>관심</button>' +
      // 식사팟 생성(S-08, A 담당): restaurantId를 넘기면 이 식당으로 고정된다
      '<a class="btn btn--primary" href="/party-create.html?restaurantId=' + encodeURIComponent(d.restaurantId) + '">' +
      '이 식당으로 식사팟 만들기</a>' +
      '</div>' +
      '<p id="interest-message" class="field__message field__message--error detail-interest-message" role="alert"></p>';

    renderInterest();
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
