/*
 * main.js — 메인 화면 (S-01, C)
 *
 * C가 그리는 영역: ③ 오늘의 구내식당 ⑥ 이달의 따봉 TOP 3 ⑦ 룰렛 배너(P1)
 *   (① 헤더의 닉네임·로그아웃은 js/components/app-header.js)
 * A가 그리는 영역: ② 따봉 배너 ④ 참여 중인 식사팟 ⑤ 오늘의 식사팟
 *
 * A 부품 (js/components/party-main.js, css/party-main.css) — 사용법은 그 파일 맨 위 주석
 *   - 자리: <div id="thumbs-banner">, <div id="my-parties">, <div id="today-parties">
 *   - 부르기: partyMain.renderThumbsBanner / renderMyParties / renderTodayParties (자리 요소를 넘김)
 *   - 조회·버튼·빈 상태·오류·숨김(hidden)은 부품이 처리한다. 자리 div에 display를 직접 주지 않는다.
 *   - 따봉을 남기면 'sseukbap:thumbs-added' 이벤트로 TOP 3를 다시 불러온다. (A 부품이 이벤트를 보내야 동작)
 *   - party-main.js가 없거나 한 부품이 실패해도 나머지 영역은 그대로 보인다.
 *
 * 각 영역은 따로 불러온다. (하나가 느리거나 실패해도 다른 영역에 영향 없음)
 * 로그인하지 않았으면 client.js가 로그인 화면으로 보낸다. (redirect=/ → 로그인 후 메인으로 복귀)
 */
(function () {
  'use strict';

  const esc = utils.escapeHtml;
  const CATEGORY_LABELS = restaurantApi.CATEGORY_LABELS;
  const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];
  const TOP_LIMIT = 3;
  const RESTAURANT_DETAIL_URL = '/restaurant-detail.html?id=';
  const ROULETTE_READY = true;    // 룰렛 화면(S-11) 완성 → 배너 표시

  const CAFETERIA_MESSAGES = {
    CLOSED: '오늘은 운영하지 않습니다.',
    UNAVAILABLE: '메뉴 정보를 불러올 수 없습니다.',
  };

  /* ---------- 시작 ---------- */

  loadCafeteria();
  loadTop3();
  mountPartyParts();
  document.getElementById('main-roulette').hidden = !ROULETTE_READY;

  document.addEventListener('sseukbap:thumbs-added', loadTop3);   // A의 따봉 배너에서 따봉 성공 시

  /* ---------- ③ 오늘의 구내식당 ---------- */

  async function loadCafeteria() {
    const body = document.getElementById('cafeteria-body');
    const meta = document.getElementById('cafeteria-meta');
    body.innerHTML = loadingHtml('메뉴를 불러오는 중…');

    try {
      const today = await cafeteriaApi.getToday();
      meta.textContent = formatDate(today.date) + ' · ' + today.cafeteriaName;

      if (today.status !== 'OPEN') {
        body.innerHTML = noticeHtml(CAFETERIA_MESSAGES[today.status] || CAFETERIA_MESSAGES.UNAVAILABLE);
        return;
      }
      body.innerHTML =
        '<div class="cafeteria-meals">' +
        mealHtml('점심', today.lunch) +
        mealHtml('저녁', today.dinner) +
        '</div>';
    } catch (err) {
      if (err.statusCode === 401) return;
      body.innerHTML = errorHtml('메뉴를 불러오지 못했습니다', err.message, 'cafeteria');
    }
  }

  function mealHtml(label, menus) {
    const text = menus && menus.length
        ? menus.map(esc).join(' · ')
        : '<span class="text-muted">메뉴 정보가 없어요</span>';
    return (
      '<div class="cafeteria-meal">' +
      '<h3 class="cafeteria-meal__title">' + label + '</h3>' +
      '<p class="cafeteria-meal__menus">' + text + '</p>' +
      '</div>'
    );
  }

  /* ---------- ⑥ 이달의 따봉 TOP 3 ---------- */

  async function loadTop3() {
    const body = document.getElementById('top3-body');
    body.innerHTML = loadingHtml('랭킹을 불러오는 중…');

    try {
      const result = await rankingApi.getRankings(TOP_LIMIT);
      if (!result.rankings.length) {
        body.innerHTML = noticeHtml('최근 30일간 따봉을 받은 식당이 없습니다.');
        return;
      }
      body.innerHTML = '<ol class="top3-list">' + result.rankings.map(rankingItemHtml).join('') + '</ol>';
    } catch (err) {
      if (err.statusCode === 401) return;
      body.innerHTML = errorHtml('랭킹을 불러오지 못했습니다', err.message, 'top3');
    }
  }

  function rankingItemHtml(item) {
    const category = CATEGORY_LABELS[item.category] || item.category;
    return (
      '<li class="top3-item">' +
      '<span class="top3-item__rank">' + item.rank + '</span>' +
      '<a class="top3-item__name" href="' + RESTAURANT_DETAIL_URL + encodeURIComponent(item.restaurantId) + '">' +
      esc(item.name) + '</a>' +
      '<span class="top3-item__category">· ' + esc(category) + '</span>' +
      '<span class="top3-item__thumbs" aria-label="최근 30일 따봉 ' + item.thumbsCount30d + '개">' +
      utils.thumbsUpIcon(16) + item.thumbsCount30d + '</span>' +
      '</li>'
    );
  }

  /* ---------- ②④⑤ A 부품 ---------- */

  function mountPartyParts() {
    const parts = window.partyMain;

    mount(parts && parts.renderThumbsBanner, 'thumbs-banner');
    mount(parts && parts.renderMyParties, 'my-parties');
    mount(parts && parts.renderTodayParties, 'today-parties');
  }

  function mount(fn, containerId) {
    const container = document.getElementById(containerId);
    if (typeof fn !== 'function') {
      container.hidden = true;   // A 부품을 못 불러오면 자리를 숨긴다
      return;
    }
    Promise.resolve()
      .then(() => fn(container))
      .catch((err) => {
        console.error('[main] 부품 표시 실패:', containerId, err);
        container.hidden = true;
      });
  }

  /* ---------- 다시 시도 버튼 ---------- */

  document.querySelector('.main-page').addEventListener('click', (event) => {
    const button = event.target.closest('[data-retry]');
    if (!button) return;
    if (button.dataset.retry === 'cafeteria') loadCafeteria();
    if (button.dataset.retry === 'top3') loadTop3();
  });

  /* ---------- 공통 ---------- */

  /** '2026-11-19' → '2026-11-19 (목)' */
  function formatDate(value) {
    const [y, m, d] = String(value).split('-').map(Number);
    const date = new Date(y, m - 1, d);
    return isNaN(date) ? String(value) : value + ' (' + WEEKDAYS[date.getDay()] + ')';
  }

  function loadingHtml(text) {
    return (
      '<div class="state state--loading main-state">' +
      '<span class="spinner" aria-hidden="true"></span>' +
      '<p class="state__desc">' + esc(text) + '</p>' +
      '</div>'
    );
  }

  function noticeHtml(text) {
    return '<div class="state main-state"><p class="state__desc">' + esc(text) + '</p></div>';
  }

  function errorHtml(title, message, retryKey) {
    return (
      '<div class="state state--error main-state">' +
      '<p class="state__title">' + esc(title) + '</p>' +
      '<p class="state__desc">' + esc(message) + '</p>' +
      '<button class="btn" type="button" data-retry="' + retryKey + '">다시 시도</button>' +
      '</div>'
    );
  }
})();
