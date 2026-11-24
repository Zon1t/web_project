/*
 * mypage.js — 마이페이지 화면 (S-10, C)
 *
 * - 프로필: 닉네임, 아이디 + '나의 슥밥 기록' 카드 4개 (카드를 누르면 해당 탭으로 이동)
 *   [계정 설정] 버튼 → account.html (닉네임·비밀번호 변경, 2026-11-24)
 * - 탭: 내 리뷰(기본) / 참여한 식사팟 / 등록한 식당 / 관심 식당
 *   · 탭은 처음 열 때 한 번만 불러오고 이후에는 저장해 둔 목록을 쓴다
 *   · 주소에 ?tab=parties 처럼 붙이면 그 탭으로 바로 연다
 * - 내 리뷰 [삭제] → reviewApi.delete (B), 관심 식당 [관심 해제] → restaurantApi.removeInterest (B)
 *   성공하면 목록에서 빼고 프로필 건수도 1 줄인다
 * - 참여한 식사팟을 누르면 식사팟 상세(S-12), 식당을 누르면 식당 상세(S-05)
 */
(function () {
  'use strict';

  const esc = utils.escapeHtml;
  const CATEGORY_LABELS = restaurantApi.CATEGORY_LABELS;
  const STATUS_LABELS = partyApi.STATUS_LABELS;
  const MEAL_TYPE_LABELS = partyApi.MEAL_TYPE_LABELS;
  const RESTAURANT_DETAIL_URL = '/restaurant-detail.html?id=';

  /** 탭 설정: 불러오기 함수, 한 줄 그리기, 빈 상태 문구 */
  const TABS = {
    reviews: {
      load: () => mypageApi.getReviews(),
      itemHtml: reviewHtml,
      emptyTitle: '아직 작성한 리뷰가 없어요',
      emptyDesc: '다녀온 식당에 한줄평을 남겨 보세요.',
      illust: 'chat',
      emptyAction: '<a class="btn" href="/restaurant-list.html">식당 둘러보기</a>',
      countKey: 'reviewCount',
    },
    parties: {
      load: () => mypageApi.getParties(),
      itemHtml: partyHtml,
      emptyTitle: '참여한 식사팟이 없어요',
      emptyDesc: '식사팟에 참여해서 함께 밥 먹어요.',
      illust: 'tray',
      emptyAction: '<a class="btn btn--primary" href="/party-list.html">식사팟 찾기</a>',
      countKey: 'partyCount',
    },
    restaurants: {
      load: () => mypageApi.getRestaurants(),
      itemHtml: restaurantHtml,
      emptyTitle: '등록한 식당이 없어요',
      emptyDesc: '알고 있는 맛집을 등록해 보세요.',
      illust: 'map',
      emptyAction: '<a class="btn" href="/restaurant-register.html">식당 등록하기</a>',
      countKey: 'restaurantCount',
    },
    interests: {
      load: () => mypageApi.getInterests(),
      itemHtml: interestHtml,
      emptyTitle: '관심 식당이 없어요',
      emptyDesc: '식당 상세에서 관심 식당으로 등록할 수 있어요.',
      illust: 'thumbs',
      countKey: 'interestCount',
    },
  };
  const DEFAULT_TAB = 'reviews';

  // 근무지: 회원가입에서 '멀티캠퍼스 세종대'만 고를 수 있어 서버에 저장하지 않고 고정 표시 (2026-11-23)
  const WORKPLACE = '멀티캠퍼스 세종대';

  const profileEl = document.getElementById('mypage-profile');
  const panelEl = document.getElementById('mypage-panel');
  const tabButtons = Array.from(document.querySelectorAll('.mypage-tab'));

  let summary = null;            // 프로필 응답 (건수를 바꾸면 다시 그린다)
  let currentTab = null;
  const lists = {};              // 탭별로 불러온 목록 (캐시)

  /* ---------- 이벤트 ---------- */

  tabButtons.forEach((button) => {
    button.addEventListener('click', () => openTab(button.dataset.tab));
  });

  // 좌우 화살표로 탭 이동 (접근성)
  document.querySelector('.mypage-tabs').addEventListener('keydown', (event) => {
    if (event.key !== 'ArrowRight' && event.key !== 'ArrowLeft') return;
    const index = tabButtons.findIndex((b) => b.dataset.tab === currentTab);
    const step = event.key === 'ArrowRight' ? 1 : -1;
    const next = tabButtons[(index + step + tabButtons.length) % tabButtons.length];
    next.focus();
    openTab(next.dataset.tab);
  });

  profileEl.addEventListener('click', (event) => {
    const stat = event.target.closest('[data-tab]');
    if (stat) openTab(stat.dataset.tab);
    if (event.target.closest('[data-action="retry-summary"]')) loadSummary();
  });

  panelEl.addEventListener('click', (event) => {
    const target = event.target.closest('[data-action]');
    if (!target) return;
    const action = target.dataset.action;
    if (action === 'retry') loadTab(currentTab, true);
    if (action === 'delete-review') deleteReview(target);
    if (action === 'remove-interest') removeInterest(target);
  });

  /* ---------- 시작 ---------- */

  loadSummary();
  openTab(tabFromUrl());

  function tabFromUrl() {
    const tab = new URLSearchParams(location.search).get('tab');
    return TABS[tab] ? tab : DEFAULT_TAB;
  }

  /* ---------- 프로필 ---------- */

  async function loadSummary() {
    profileEl.innerHTML = ui.skeleton('card', 1);
    try {
      summary = await mypageApi.getSummary();
      renderSummary();
    } catch (err) {
      if (err.statusCode === 401) return;   // client.js가 로그인 화면으로 이동
      profileEl.innerHTML =
        '<div class="state state--error"><p class="state__title">내 정보를 불러오지 못했어요</p>' +
        '<p class="state__desc">' + esc(err.message) + '</p>' +
        '<button class="btn" type="button" data-action="retry-summary">다시 시도</button></div>';
    }
  }

  function renderSummary() {
    // [탭, 이름, 건수, 단위, 아이콘, 색 톤]
    const stats = [
      ['reviews', '남긴 리뷰', summary.reviewCount, '개', 'chat', 'blue'],      // 별점 기능이 없어 별 대신 말풍선 (2026-11-24)
      ['parties', '참여한 식사팟', summary.partyCount, '개', 'people', 'orange'],   // 예정 팟도 포함이라 '함께한' 대신 (2026-11-23)
      ['restaurants', '등록한 식당', summary.restaurantCount, '곳', 'pin', 'green'],
      ['interests', '관심 식당', summary.interestCount, '곳', 'star', 'purple'],  // 따봉(엄지)과 헷갈리지 않게 별 (2026-11-24)
    ];
    const nickname = String(summary.nickname || '');
    profileEl.innerHTML =
      '<div class="mypage-profile__who">' +
      '<span class="mypage-profile__avatar" aria-hidden="true">' + esc(nickname.charAt(0) || '?') + '</span>' +
      '<div>' +
      '<p class="mypage-profile__nickname">' + esc(nickname) + ' 님</p>' +
      '<p class="mypage-profile__login-id">' + esc(summary.loginId) + '<span aria-hidden="true"> · </span>' + WORKPLACE + '</p>' +
      '</div>' +
      '<a class="btn mypage-profile__settings" href="/account.html">계정 설정</a>' +
      '</div>' +
      '<h2 class="mypage-record__title">나의 슥밥 기록</h2>' +
      '<ul class="mypage-stats">' +
      stats.map(([tab, label, count, unit, icon, tone]) =>
        '<li><button class="mypage-stat mypage-stat--' + tone + '" type="button" data-tab="' + tab + '">' +
        '<span class="mypage-stat__icon">' + ui.icon(icon, 20) + '</span>' +
        '<span class="mypage-stat__label">' + label + '</span>' +
        '<span class="mypage-stat__count">' + count + '<small>' + unit + '</small></span>' +
        '</button></li>').join('') +
      '</ul>';
  }

  /** 삭제·해제 후 건수를 줄이고 프로필을 다시 그린다 */
  function decreaseCount(tab) {
    if (!summary) return;
    const key = TABS[tab].countKey;
    summary[key] = Math.max(0, summary[key] - 1);
    renderSummary();
  }

  /* ---------- 탭 ---------- */

  function openTab(tab) {
    if (!TABS[tab] || tab === currentTab) return;
    currentTab = tab;

    tabButtons.forEach((button) => {
      const selected = button.dataset.tab === tab;
      button.classList.toggle('is-selected', selected);
      button.setAttribute('aria-selected', String(selected));
      button.tabIndex = selected ? 0 : -1;
    });
    panelEl.setAttribute('aria-labelledby', 'tab-' + tab);

    // 새로고침해도 같은 탭이 열리도록 주소만 바꾼다 (기록은 남기지 않음)
    const url = tab === DEFAULT_TAB ? location.pathname : location.pathname + '?tab=' + tab;
    history.replaceState(null, '', url);

    loadTab(tab, false);
  }

  async function loadTab(tab, force) {
    if (lists[tab] && !force) {
      renderList(tab);
      return;
    }
    panelEl.innerHTML = ui.skeleton('row', 3);
    try {
      const items = await TABS[tab].load();
      lists[tab] = items;
      if (currentTab === tab) renderList(tab);   // 불러오는 사이 다른 탭으로 바꿨으면 그리지 않는다
    } catch (err) {
      if (err.statusCode === 401) return;
      if (currentTab !== tab) return;
      panelEl.innerHTML =
        ui.empty({ illust: 'bowl', title: '목록을 불러오지 못했어요', desc: esc(err.message),
          actionHtml: '<button class="btn" type="button" data-action="retry">다시 시도</button>' });
    }
  }

  function renderList(tab) {
    const config = TABS[tab];
    const items = lists[tab];
    if (!items.length) {
      panelEl.innerHTML = ui.empty({ illust: config.illust, title: config.emptyTitle, desc: config.emptyDesc,
        actionHtml: config.emptyAction || '' });
      return;
    }
    panelEl.innerHTML = '<ul class="mypage-list">' + items.map(config.itemHtml).join('') + '</ul>';
  }

  /* ---------- 한 줄 그리기 ---------- */

  function reviewHtml(review) {
    const tags = review.tags.length
      ? '<p class="mypage-item__tags">' + review.tags.map((t) => '<span>#' + esc(t) + '</span>').join('') + '</p>'
      : '';
    const image = review.imageUrl
      ? '<img class="mypage-item__thumb" src="' + esc(review.imageUrl) + '" alt="리뷰 사진" loading="lazy">'
      : '';
    return (
      '<li class="mypage-item">' +
      '<div class="mypage-item__body">' +
      '<a class="mypage-item__title" href="' + restaurantUrl(review.restaurantId) + '">' +
      esc(review.restaurantName) + '</a>' +
      '<p class="mypage-item__content">' + esc(review.content) + '</p>' +
      tags +
      '<p class="mypage-item__meta">' + formatDate(review.createdAt) + '</p>' +
      '</div>' +
      image +
      '<div class="mypage-item__actions">' +
      '<button class="btn btn--text mypage-item__danger" type="button" data-action="delete-review" ' +
      'data-id="' + review.reviewId + '">삭제</button>' +
      '</div>' +
      '</li>'
    );
  }

  function partyHtml(party) {
    const status = STATUS_LABELS[party.status] || party.status;
    const mealType = MEAL_TYPE_LABELS[party.mealType] || party.mealType;
    const badges =
      '<span class="badge badge--' + esc(party.status.toLowerCase()) + '">' + esc(status) + '</span>' +
      (party.confirmed
        ? '<span class="badge mypage-badge--confirmed">참석 확정</span>'
        : '<span class="badge">참석 확정 전</span>') +
      (party.host ? '<span class="badge mypage-badge--host">방장</span>' : '');
    return (
      '<li class="mypage-item mypage-item--link">' +
      '<a class="mypage-item__cover" href="' + partyApi.detailUrl(party.partyId) + '">' +
      '<span class="sr-only">' + esc(party.restaurantName) + ' 식사팟 상세 보기</span></a>' +
      '<div class="mypage-item__body">' +
      '<p class="mypage-item__meta">' + formatDate(party.partyDate) + ' · ' + esc(mealType) + ' ' +
      esc(party.time) + '</p>' +
      '<p class="mypage-item__title">' + esc(party.restaurantName) + '</p>' +
      '<p class="mypage-item__badges">' + badges + '</p>' +
      '</div>' +
      '</li>'
    );
  }

  function restaurantHtml(restaurant) {
    return (
      '<li class="mypage-item mypage-item--link">' +
      '<a class="mypage-item__cover" href="' + restaurantUrl(restaurant.restaurantId) + '">' +
      '<span class="sr-only">' + esc(restaurant.name) + ' 상세 보기</span></a>' +
      '<div class="mypage-item__body">' +
      '<p class="mypage-item__title">' + esc(restaurant.name) + '</p>' +
      '<p class="mypage-item__meta">' + esc(categoryLabel(restaurant.category)) + ' · ' +
      formatDate(restaurant.createdAt) + ' 등록</p>' +
      '</div>' +
      '</li>'
    );
  }

  function interestHtml(interest) {
    const visited = interest.visited
      ? '<span class="mypage-visited mypage-visited--yes">방문 기록 있음</span>'
      : '<span class="mypage-visited">아직 방문 전</span>';
    return (
      '<li class="mypage-item">' +
      '<div class="mypage-item__body">' +
      '<a class="mypage-item__title" href="' + restaurantUrl(interest.restaurantId) + '">' +
      esc(interest.name) + '</a>' +
      '<p class="mypage-item__meta">' +
      esc(categoryLabel(interest.category)) + ' · ' +
      '<span class="mypage-thumbs">' + utils.thumbsUpIcon(14) + '따봉 ' + interest.thumbsCount30d + '</span>' +
      ' · ' + visited +
      '</p>' +
      '</div>' +
      '<div class="mypage-item__actions">' +
      '<button class="btn" type="button" data-action="remove-interest" ' +
      'data-id="' + interest.restaurantId + '">관심 해제</button>' +
      '</div>' +
      '</li>'
    );
  }

  /* ---------- 삭제 / 해제 ---------- */

  async function deleteReview(button) {
    const reviewId = Number(button.dataset.id);
    const ok = await ui.confirm({ title: '이 리뷰를 삭제할까요?', message: '삭제하면 되돌릴 수 없어요.', confirmText: '삭제', danger: true });
    if (!ok) return;

    button.disabled = true;
    try {
      await reviewApi.delete(reviewId);
      lists.reviews = lists.reviews.filter((r) => r.reviewId !== reviewId);
      decreaseCount('reviews');
      if (currentTab === 'reviews') renderList('reviews');
      ui.toast('리뷰를 삭제했어요.');
    } catch (err) {
      if (err.statusCode === 401) return;
      button.disabled = false;
      ui.toast(err.message, { type: 'error' });
    }
  }

  async function removeInterest(button) {
    const restaurantId = Number(button.dataset.id);

    button.disabled = true;
    try {
      await restaurantApi.removeInterest(restaurantId);
      lists.interests = lists.interests.filter((i) => i.restaurantId !== restaurantId);
      decreaseCount('interests');
      if (currentTab === 'interests') renderList('interests');
      ui.toast('관심 식당에서 뺐어요.');
    } catch (err) {
      if (err.statusCode === 401) return;
      button.disabled = false;
      ui.toast(err.message, { type: 'error' });
    }
  }

  /* ---------- 도우미 ---------- */

  function restaurantUrl(restaurantId) {
    return RESTAURANT_DETAIL_URL + encodeURIComponent(restaurantId);
  }

  function categoryLabel(category) {
    return CATEGORY_LABELS[category] || category;
  }

  /** '2026-11-19' 또는 '2026-11-19T12:40:00' → '2026.11.19' */
  function formatDate(value) {
    if (!value) return '';
    return esc(String(value).slice(0, 10).replace(/-/g, '.'));
  }
})();
