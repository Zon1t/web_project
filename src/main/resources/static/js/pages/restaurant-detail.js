/*
 * restaurant-detail.js — S-05 식당 상세 (B)
 *
 * 주소: /restaurant-detail.html?id=12
 *
 * 만든 것: 식당 정보(이름·카테고리·주소·태그 TOP 3·따봉·관심·리뷰 수), 작은 지도(map-kit.js)·네이버 지도 링크,
 *          [관심] 등록·해제, [식사팟 만들기] 이동, 로딩 / 없는 식당 / 오류 화면
 * 리뷰 작성·목록·삭제는 restaurant-detail-reviews.js, 수정 제안 입력 창은 restaurant-detail-suggestion.js
 * (둘 다 이 파일보다 먼저 불러온다)
 * 나중에 붙일 것
 *   - 리뷰 사진             → feature/review-image
 */
(function () {
  'use strict';

  const CATEGORY_LABELS = restaurantApi.CATEGORY_LABELS;
  const esc = utils.escapeHtml;

  const root = document.getElementById('detail-root');
  const restaurantId = new URLSearchParams(location.search).get('id');
  // 지도 화면 안의 상세 카드(iframe, ?embed=1)에서는 옆에 큰 지도가 있으니 작은 지도를 그리지 않는다
  const isEmbed = document.documentElement.classList.contains('is-embed');

  /* ---------- 시작 ---------- */

  // 관심 상태 (버튼을 누를 때마다 서버 응답으로 갱신)
  const interest = { interested: false, count: 0, pending: false };

  root.addEventListener('click', (event) => {
    if (event.target.closest('[data-action="retry"]')) loadDetail();
    if (event.target.closest('[data-action="toggle-interest"]')) toggleInterest();
    if (event.target.closest('[data-action="open-suggestion"]')) restaurantSuggestion.open(restaurantId);
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
      showInterestFeedback(result.interested);
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

  // 관심을 바꾼 뒤 짧은 피드백: 담으면 하트가 숑숑 + 버튼 통통, 빼면 토스트만 (C, 2026-11-24)
  function showInterestFeedback(interested) {
    const button = document.getElementById('interest-button');
    if (interested) {
      ui.burst(button, { icon: 'heart' });
      ui.pop(button);
      ui.toast('관심 식당에 담았어요');
    } else {
      ui.toast('관심 식당에서 뺐어요');
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
      '<section id="review-section" class="detail-reviews" aria-labelledby="review-title"></section>' +
      // 식당 정보 수정 제안: 누르면 입력 창 (restaurant-detail-suggestion.js)
      '<p class="detail-suggestion">' +
      '<button type="button" class="detail-suggestion__link" data-action="open-suggestion">식당 정보 수정 제안</button>' +
      '</p>';

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

    // 작은 지도는 리뷰가 바뀌어 다시 그릴 때마다 새로 만들지 않도록, 이미 있으면 떼어 두었다가 다시 붙인다
    const keptMap = document.getElementById('detail-mini-map');
    const showMap = !isEmbed && typeof d.lat === 'number' && typeof d.lng === 'number';

    document.getElementById('info-section').innerHTML =
      // 머리: 식당 아바타 + 이름 + 카테고리·주소 (2026-11-20 개편)
      '<div class="detail-hero">' +
      ui.avatar(d.cafeteria ? 'CAFETERIA' : d.category, 64) +
      '<div class="detail-hero__text">' +
      '<div class="detail-info__title-row">' +
      '<h1 id="restaurant-name" class="detail-info__name">' + esc(d.name) + '</h1>' +
      '<span class="badge detail-info__category">' + esc(CATEGORY_LABELS[d.category] || d.category) + '</span>' +
      '</div>' +
      (d.address ? '<p class="detail-info__address">' + ui.icon('pin', 16) + esc(d.address) + '</p>' : '') +
      '</div>' +
      '</div>' +
      // 작은 지도 (2026-11-23, js/components/map-kit.js). [네이버 지도에서 보기]는 지도 오른쪽 아래에 들어간다
      (showMap ? '<div id="detail-mini-map-slot"></div>' : '') +
      // 숫자 타일
      '<div class="detail-stats">' +
      // 카페·베이커리는 따봉 칸을 빼고 관심·리뷰만 (2026-11-25)
      (d.category === 'CAFE' ? '' :
        '<div class="detail-stat detail-stat--thumbs"><span class="detail-stat__label">' + ui.icon('thumbs', 16) + '따봉 (최근 30일)</span><strong>' + d.thumbsCount30d + '</strong></div>') +
      '<div class="detail-stat"><span class="detail-stat__label">관심</span><strong id="interest-count">' + d.interestCount + '</strong></div>' +
      '<div class="detail-stat"><span class="detail-stat__label">리뷰</span><strong>' + d.reviewCount + '</strong></div>' +
      '</div>' +
      renderTopTags(d.topTags) +
      (showMap ? '' : renderMapLink(d.mapUrl)) +   // 지도가 없을 때만 따로 링크
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

    if (showMap) renderMiniMap(d, keptMap);
    renderInterest();
  }

  /** 많이 선택된 태그 TOP 3: 막대그래프 (가장 많은 태그 = 100%) */
  function renderTopTags(tags) {
    if (!tags || tags.length === 0) return ''; // 선택된 태그가 없으면 줄 자체를 숨긴다
    const max = Math.max.apply(null, tags.map((t) => t.count)) || 1;
    return (
      '<div class="detail-tags">' +
      '<p class="detail-tags__label">이 식당을 이렇게 말해요</p>' +
      '<ul class="detail-tagbars">' +
      tags.map((tag) =>
        '<li class="detail-tagbar">' +
        '<span class="detail-tagbar__name">#' + esc(tag.name) + '</span>' +
        '<span class="detail-tagbar__track"><span style="width:' + Math.round((tag.count / max) * 100) + '%"></span></span>' +
        '<span class="detail-tagbar__count">' + tag.count + '</span>' +
        '</li>').join('') +
      '</ul>' +
      '</div>'
    );
  }

  // 작은 지도를 자리(#detail-mini-map-slot)에 넣는다. 이미 그린 지도가 있으면 그것을 다시 붙인다.
  function renderMiniMap(d, keptMap) {
    const slot = document.getElementById('detail-mini-map-slot');
    if (keptMap) {
      slot.replaceWith(keptMap);
      return;
    }
    const el = document.createElement('div');
    el.id = 'detail-mini-map';
    el.className = 'detail-mini-map';
    if (window.mapKit && window.mapKit.mini(el, d)) slot.replaceWith(el);
    else slot.replaceWith(htmlToNode(renderMapLink(d.mapUrl)));   // 혹시 못 그리면 링크만
  }

  function htmlToNode(html) {
    const template = document.createElement('template');
    template.innerHTML = html;
    return template.content;
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
    root.innerHTML = ui.skeleton('card', 2);
  }

  function showNotFound() {
    document.title = '식당을 찾을 수 없어요 | 슥밥';
    root.innerHTML =
      '<div class="state state--empty">' +
      '<p class="state__title">식당을 찾을 수 없어요</p>' +
      '<p class="state__desc">주소가 올바른지 확인해 주세요.</p>' +
      '<a class="btn" href="/restaurant-list.html">식당 목록으로</a>' +
      '</div>';
  }

  function showError(message) {
    root.innerHTML =
      '<div class="state state--error">' +
      '<p class="state__title">식당 정보를 불러오지 못했어요</p>' +
      '<p class="state__desc">' + esc(message) + '</p>' +
      '<button type="button" class="btn" data-action="retry">다시 시도</button>' +
      '</div>';
  }
})();
