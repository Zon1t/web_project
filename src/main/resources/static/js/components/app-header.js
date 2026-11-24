/*
 * app-header.js — 공통 헤더 (2026-11-20 개편: 로고·GNB·사용자 메뉴를 이 파일이 그린다)
 *
 * ▼ HTML에는 자리만 둔다
 *   <header id="app-header" class="app-header"></header>                          ← 로그인 후 화면
 *   <header id="app-header" class="app-header" data-variant="simple"></header>    ← 로그인·회원가입 (로고만, 로고를 누르면 /landing.html)
 *   <header id="app-header" class="app-header" data-layout="wide"></header>       ← 지도처럼 화면 전체 폭을 쓰는 화면 (2026-11-23부터 헤더 위치는 다른 화면과 같음)
 *
 * ▼ CSS·JS
 *   <link rel="stylesheet" href="/css/header.css">
 *   <link rel="stylesheet" href="/css/app-header.css">
 *   <script src="/js/api/client.js"></script>
 *   <script src="/js/api/auth.js"></script>          ← simple이면 없어도 됨
 *   <script src="/js/components/brand.js"></script>
 *   <script src="/js/components/app-header.js"></script>
 *
 * - GNB: 홈 · 지도 · 식사팟 · 랭킹 · 마이페이지. 현재 주소를 보고 선택 표시를 한다.
 *   메뉴를 바꾸려면 아래 NAV_ITEMS만 고치면 된다.
 * - 오른쪽: [알림 🔔] [아바타 닉네임 ▾] → 드롭다운(마이페이지, 로그아웃)
 * - 닉네임은 /api/auth/me로 채운다. 로그인하지 않았으면 client.js가 로그인 화면으로 보낸다.
 * - 알림 패널 (2026-11-23 개편: 따봉 1건 → 할 일 목록 + 따봉)
 *   ① 할 일 (GET /api/parties/my/action-pending, A)
 *       SET_SCHEDULE "일정을 정해 주세요"(방장) / CONFIRM "참석을 확정해 주세요"
 *       누르면 식사팟 상세로 이동. 처리하면 서버 목록에서 저절로 빠진다 (읽음 저장 없음)
 *   ② 따봉 (GET /api/parties/my/thumbs-pending): 식사 완료된 팟 중 따봉을 안 남긴 최근 1건
 *       [따봉](POST /api/parties/{id}/thumbs) / [다음에](오늘 하루 배지에서 뺌)
 *       따봉을 남기면 document에 'sseukbap:thumbs-added' (detail.source='header')를 보낸다.
 *   - 종 배지 = 할 일 수 + 따봉(오늘 [다음에]를 안 눌렀으면 1). 0이면 숨김
 *   - 다시 불러오는 때: 화면 열 때, 종을 열 때, 다른 탭에서 돌아왔을 때,
 *     'sseukbap:thumbs-added'(다른 곳에서 보낸 것)·'sseukbap:party-joined' 이벤트를 받았을 때 (주기적 확인은 안 함)
 * - 룰렛 플로팅 버튼(2026-11-20): FAB_PATHS 화면에서만 오른쪽 아래에 띄운다. (룰렛·입력 화면에서는 숨김)
 */
(function (global) {
  'use strict';

  const NAV_ITEMS = [
    { label: '홈', href: '/', paths: ['/', '/index.html'] },
    { label: '지도', href: '/restaurant-list.html', paths: ['/restaurant-list.html', '/restaurant-detail.html', '/restaurant-register.html'] },
    { label: '식사팟', href: '/party-list.html', paths: ['/party-list.html', '/party-detail.html', '/party-create.html'] },
    { label: '랭킹', href: '/ranking.html', paths: ['/ranking.html'] },
    { label: '마이페이지', href: '/mypage.html', paths: ['/mypage.html'] },
  ];

  const LOGIN_PAGE = '/login.html';

  // 룰렛 플로팅 버튼을 띄울 화면 (룰렛 화면, 입력 화면, 로그인 화면에서는 띄우지 않는다)
  const FAB_PATHS = ['/', '/index.html', '/restaurant-list.html', '/restaurant-detail.html', '/party-list.html', '/ranking.html', '/mypage.html'];
  const ROULETTE_URL = '/roulette.html';

  // [다음에]를 누른 날짜 (홈 따봉 배너와 같은 키라서 어느 쪽에서 눌러도 같이 적용)
  const THUMBS_SNOOZE_KEY = 'sseukbap.thumbsBanner.snoozedDate';
  const MEAL_LABELS = { LUNCH: '점심', DINNER: '저녁' };

  const header = document.getElementById('app-header');
  if (!header) return;
  // 지도 화면의 식당 상세 카드 안(iframe, ?embed=1)에서는 헤더·알림·플로팅 버튼을 그리지 않는다
  if (document.documentElement.classList.contains('is-embed')) {
    header.remove();
    return;
  }

  const isSimple = header.dataset.variant === 'simple';
  if (header.dataset.layout === 'wide') header.classList.add('app-header--wide'); // 지도처럼 화면 전체를 쓰는 화면
  const currentPath = location.pathname.toLowerCase();

  header.innerHTML =
    '<div class="app-header__inner">' +
    // 로그인·회원가입 화면(simple)은 로그인 전이라 홈 대신 서비스 소개로 (2026-11-24)
    (isSimple
      ? '<a class="app-header__logo brand-logo" href="/landing.html" aria-label="슥밥 소개">'
      : '<a class="app-header__logo brand-logo" href="/" aria-label="슥밥 홈">') +
    global.brand.wordmarkHtml() + '</a>' +
    (isSimple ? '' : navHtml() + userHtml()) +
    '</div>';

  if (isSimple) return;

  renderFab();

  const userArea = header.querySelector('.app-header__user');
  const userButton = header.querySelector('#header-user');
  const menu = header.querySelector('#header-menu');
  const avatar = header.querySelector('.app-header__avatar');
  const nickname = header.querySelector('.app-header__nickname');

  const noticeButton = header.querySelector('#header-notice-btn');
  const noticePanel = header.querySelector('#header-notice-panel');
  const noticeBadge = header.querySelector('.app-header__badge');
  let pendingThumbs = null;   // 따봉 요청 대상 1건 또는 null
  let actionItems = [];       // 할 일 목록 (일정 정하기 / 참석 확정)
  let loadSeq = 0;            // 늦게 온 이전 응답이 새 응답을 덮지 않게
  let doneMessage = '';       // 따봉을 남긴 직후 패널 위에 잠깐 보여 줄 문구
  let lastPanelHtml = '';     // 같은 내용이면 다시 그리지 않는다 (포커스가 튀지 않게)

  // 할 일 종류별 문구·아이콘 (A의 action-pending 응답 action 값)
  const ACTION_TEXT = {
    SET_SCHEDULE: { title: '일정을 정해 주세요', icon: 'calendar' },
    CONFIRM: { title: '참석을 확정해 주세요', icon: 'check' },
  };

  noticeButton.addEventListener('click', () => (noticePanel.hidden ? openNotice() : closeNotice(false)));
  noticePanel.addEventListener('click', onNoticeClick);
  noticePanel.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') closeNotice(true);
  });
  document.addEventListener('sseukbap:thumbs-added', (event) => {
    if (!event.detail || event.detail.source !== 'header') loadNotices();
  });
  document.addEventListener('sseukbap:party-joined', () => loadNotices());
  // 다른 탭에서 일정·확정을 하고 돌아왔을 때
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible') loadNotices();
  });

  userButton.addEventListener('click', () => (menu.hidden ? openMenu(false) : closeMenu(false)));
  userButton.addEventListener('keydown', (event) => {
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      openMenu(true);
    }
  });
  menu.addEventListener('keydown', onMenuKeydown);
  menu.querySelector('[data-action="logout"]').addEventListener('click', logout);
  document.addEventListener('click', (event) => {
    if (!menu.hidden && !userButton.parentElement.contains(event.target) && !menu.contains(event.target)) closeMenu(false);
    if (!noticePanel.hidden && !noticeButton.parentElement.contains(event.target)) closeNotice(false);
  });

  showUser();
  loadNotices();

  /* ---------- 그리기 ---------- */

  function navHtml() {
    return '<nav class="app-header__nav" aria-label="주 메뉴">' +
      NAV_ITEMS.map((item) => {
        const active = item.paths.includes(currentPath);
        return '<a class="app-header__link' + (active ? ' is-active' : '') + '" href="' + item.href + '"' +
          (active ? ' aria-current="page"' : '') + '>' + item.label + '</a>';
      }).join('') +
      '</nav>';
  }

  function userHtml() {
    return '<div class="app-header__user">' +
      '<div class="app-header__notice">' +
      '<button id="header-notice-btn" class="app-header__icon-btn" type="button" aria-haspopup="true" aria-expanded="false" aria-controls="header-notice-panel" aria-label="알림">' +
      '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M6 16V11a6 6 0 0 1 12 0v5l1.5 2h-15z" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linejoin="round"/>' +
      '<path d="M10 20.5a2.2 2.2 0 0 0 4 0" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/></svg>' +
      '<span class="app-header__badge" aria-hidden="true" hidden></span>' +
      '</button>' +
      '<div id="header-notice-panel" class="app-header__panel" role="dialog" aria-label="알림" hidden></div>' +
      '</div>' +
      '<button id="header-user" class="app-header__user-btn" type="button" aria-haspopup="true" aria-expanded="false" aria-controls="header-menu">' +
      '<span class="app-header__avatar" aria-hidden="true"></span>' +
      '<span class="app-header__nickname"></span>' +
      '<svg class="app-header__chevron" viewBox="0 0 16 16" aria-hidden="true" focusable="false"><path d="M4 6l4 4 4-4" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>' +
      '</button>' +
      '<div id="header-menu" class="app-header__menu" role="menu" aria-label="내 메뉴" hidden>' +
      '<a class="app-header__menu-item" role="menuitem" href="/mypage.html">마이페이지</a>' +
      '<button class="app-header__menu-item" role="menuitem" type="button" data-action="logout">로그아웃</button>' +
      '</div>' +
      '</div>';
  }

  async function showUser() {
    try {
      const me = await global.authApi.me();   // 401이면 client.js가 로그인 화면으로 이동
      nickname.textContent = me.nickname + ' 님';
      avatar.textContent = Array.from(me.nickname || '?')[0];
      userButton.setAttribute('aria-label', me.nickname + ' 님 메뉴');
      userArea.classList.add('is-ready');
    } catch (err) {
      // 401은 이동 처리됨. 그 외 오류면 닉네임 없이 메뉴만 쓸 수 있게 둔다.
      avatar.textContent = '?';
      userButton.setAttribute('aria-label', '내 메뉴');
      userArea.classList.add('is-ready');
    }
  }

  /* ---------- 드롭다운 ---------- */

  function menuItems() {
    return Array.from(menu.querySelectorAll('[role="menuitem"]'));
  }

  function openMenu(focusFirst) {
    closeNotice(false);
    menu.hidden = false;
    userButton.setAttribute('aria-expanded', 'true');
    if (focusFirst) menuItems()[0].focus();
  }

  function closeMenu(returnFocus) {
    menu.hidden = true;
    userButton.setAttribute('aria-expanded', 'false');
    if (returnFocus) userButton.focus();
  }

  function onMenuKeydown(event) {
    const items = menuItems();
    const index = items.indexOf(document.activeElement);
    if (event.key === 'Escape') {
      event.preventDefault();
      closeMenu(true);
    } else if (event.key === 'ArrowDown') {
      event.preventDefault();
      items[(index + 1) % items.length].focus();
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      items[(index - 1 + items.length) % items.length].focus();
    } else if (event.key === 'Tab') {
      closeMenu(false);
    }
  }

  /* ---------- 알림 (할 일 + 따봉) ---------- */

  // 할 일과 따봉을 따로 불러온다. 한쪽이 실패해도 다른 쪽은 보여 준다. (401은 client.js가 로그인 화면으로)
  async function loadNotices() {
    const seq = ++loadSeq;
    const [actions, thumbs] = await Promise.allSettled([
      global.api.get('/parties/my/action-pending'),
      global.api.get('/parties/my/thumbs-pending'),
    ]);
    if (seq !== loadSeq) return;
    actionItems = actions.status === 'fulfilled' && Array.isArray(actions.value) ? actions.value : [];
    pendingThumbs = thumbs.status === 'fulfilled' ? thumbs.value || null : null;
    updateBadge();
    if (!noticePanel.hidden) renderNotice();
  }

  // 종 배지: 할 일 수 + 따봉(오늘 [다음에]를 안 눌렀으면 1)
  function updateBadge() {
    const count = actionItems.length + (pendingThumbs && !isSnoozedToday() ? 1 : 0);
    noticeBadge.hidden = count === 0;
    noticeBadge.textContent = count > 9 ? '9+' : String(count);
    noticeButton.setAttribute('aria-label', count === 0 ? '알림' : '알림 ' + count + '개');
  }

  function renderNotice() {
    const total = actionItems.length + (pendingThumbs ? 1 : 0);
    const both = actionItems.length > 0 && pendingThumbs;
    let body = '';

    if (actionItems.length > 0) {
      body +=
        (both ? '<p class="app-header__panel-section">할 일</p>' : '') +
        '<ul class="app-header__actions">' + actionItems.map(actionItemHtml).join('') + '</ul>';
    }
    if (pendingThumbs) {
      const p = pendingThumbs;
      body +=
        (both ? '<p class="app-header__panel-section">따봉</p>' : '') +
        '<div class="app-header__thumbs' + (both ? ' app-header__thumbs--boxed' : '') + '">' +
        '<p class="app-header__panel-eyebrow">' + esc(whenLabel(p.partyDate) + ' ' + (MEAL_LABELS[p.mealType] || '')) + ' 식사</p>' +
        '<p class="app-header__panel-title"><b>' + esc(p.restaurantName) + '</b>, 식사는 어땠나요?</p>' +
        '<p class="app-header__panel-desc">함께 식사한 식당에 따봉을 남기면 랭킹에 반영돼요.</p>' +
        '<p class="app-header__panel-error" role="alert"></p>' +
        '<div class="app-header__panel-actions">' +
        '<button type="button" class="btn app-header__thumbs-btn" data-action="thumbs">' + thumbsIcon() + '따봉</button>' +
        '<button type="button" class="btn" data-action="snooze">다음에</button>' +
        '</div>' +
        '</div>';
    }
    if (total === 0) {
      body = '<p class="app-header__panel-empty">새 알림이 없어요.</p>';
    }

    const html =
      '<p class="app-header__panel-head"><span>알림</span>' +
      (total > 0 ? '<span class="app-header__panel-count">' + total + '</span>' : '') + '</p>' +
      (doneMessage ? '<p class="app-header__panel-done" role="status">' + esc(doneMessage) + '</p>' : '') +
      '<div class="app-header__panel-body">' + body + '</div>';
    if (html === lastPanelHtml) return;
    lastPanelHtml = html;
    noticePanel.innerHTML = html;
  }

  // 할 일 한 줄: 누르면 식사팟 상세로
  function actionItemHtml(item) {
    const text = ACTION_TEXT[item.action] || { title: '식사팟을 확인해 주세요', icon: 'check' };
    const meta = [
      item.restaurantName,
      actionDateLabel(item.partyDate) + ' ' + (MEAL_LABELS[item.mealType] || '') + ' ' + (item.time || ''),
    ];
    if (item.meetPlace) meta.push(item.meetPlace);
    return '<li>' +
      '<a class="app-header__action" href="/party-detail.html?partyId=' + encodeURIComponent(item.partyId) + '">' +
      '<span class="app-header__action-icon app-header__action-icon--' + text.icon + '" aria-hidden="true">' + actionIcon(text.icon) + '</span>' +
      '<span class="app-header__action-text">' +
      '<span class="app-header__action-title">' + esc(text.title) + '</span>' +
      '<span class="app-header__action-meta">' + esc(meta.join(' · ').replace(/\s+/g, ' ').trim()) + '</span>' +
      '</span>' +
      '<svg class="app-header__action-arrow" viewBox="0 0 16 16" aria-hidden="true" focusable="false"><path d="M6 4l4 4-4 4" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>' +
      '</a></li>';
  }

  function actionIcon(name) {
    const paths = {
      calendar: '<rect x="3.5" y="5" width="17" height="15" rx="2.5"/><path d="M3.5 10h17M8 3v4M16 3v4"/>',
      check: '<path d="M5 12.5l4.5 4.5L19 7.5"/>',
    };
    return '<svg viewBox="0 0 24 24" focusable="false" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">' + paths[name] + '</svg>';
  }

  function openNotice() {
    closeMenu(false);
    doneMessage = '';
    renderNotice();
    noticePanel.hidden = false;
    noticeButton.setAttribute('aria-expanded', 'true');
    const first = noticePanel.querySelector('a, button');
    if (first) first.focus();
    loadNotices();   // 열 때마다 최신으로 (바뀐 게 있으면 다시 그린다)
  }

  function closeNotice(returnFocus) {
    if (noticePanel.hidden) return;
    noticePanel.hidden = true;
    noticeButton.setAttribute('aria-expanded', 'false');
    if (returnFocus) noticeButton.focus();
  }

  async function onNoticeClick(event) {
    const button = event.target.closest('[data-action]');
    if (!button || !pendingThumbs) return;

    if (button.dataset.action === 'snooze') {
      snoozeToday();
      updateBadge();       // 따봉만 배지에서 빠진다 (할 일은 그대로)
      closeNotice(true);
      return;
    }

    button.disabled = true;
    const target = pendingThumbs;
    let doneText = '';
    try {
      const result = await global.api.post('/parties/' + encodeURIComponent(target.partyId) + '/thumbs');
      doneText = target.restaurantName + '에 따봉을 남겼어요. 최근 30일 따봉 ' + result.thumbsCount30d + '개';
      document.dispatchEvent(new CustomEvent('sseukbap:thumbs-added', { detail: { source: 'header' } }));
    } catch (err) {
      if (err.statusCode === 401) return;
      const skippable = ['ALREADY_THUMBED', 'NOT_CONFIRMED_MEMBER', 'PARTY_NOT_FINISHED', 'PARTY_DELETED', 'PARTY_NOT_FOUND'];
      if (!skippable.includes(err.error)) {
        button.disabled = false;
        noticePanel.querySelector('.app-header__panel-error').textContent = err.message;
        return;
      }
    }
    doneMessage = doneText;
    await loadNotices();     // 다음 따봉 대상이 있으면 이어서 보여 준다
    renderNotice();
  }

  function isSnoozedToday() {
    try {
      return global.localStorage.getItem(THUMBS_SNOOZE_KEY) === todayString();
    } catch (e) {
      return false;
    }
  }

  function snoozeToday() {
    try {
      global.localStorage.setItem(THUMBS_SNOOZE_KEY, todayString());
    } catch (e) {
      // 저장 못 해도 이번 화면에서는 숨긴다
    }
  }

  /* ---------- 룰렛 플로팅 버튼 ---------- */

  function renderFab() {
    if (!FAB_PATHS.includes(currentPath) || document.querySelector('.roulette-fab')) return;
    const fab = document.createElement('a');
    fab.className = 'roulette-fab';
    fab.href = ROULETTE_URL;
    fab.setAttribute('aria-label', '메뉴 룰렛 열기');
    fab.innerHTML =
      '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">' +
      '<circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" stroke-width="2"/>' +
      '<path d="M12 3v9l6.4 6.4M12 12L5.6 18.4M12 12H3" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>' +
      '<circle cx="12" cy="12" r="1.8" fill="currentColor"/></svg>' +
      '<span class="roulette-fab__label">오늘 뭐 먹지?</span>';
    document.body.appendChild(fab);
  }

  /* ---------- 작은 도우미 ---------- */

  function esc(value) {
    return String(value == null ? '' : value).replace(/[&<>"']/g, (c) =>
      ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
  }

  function thumbsIcon() {
    return global.utils && global.utils.thumbsUpIcon ? global.utils.thumbsUpIcon(16) : '';
  }

  function todayString() {
    const now = new Date();
    const pad = (n) => String(n).padStart(2, '0');
    return now.getFullYear() + '-' + pad(now.getMonth() + 1) + '-' + pad(now.getDate());
  }

  // 식사 날짜 → '오늘' / '어제' / 'N일 전'
  function whenLabel(partyDate) {
    const [y, m, d] = String(partyDate).split('-').map(Number);
    const meal = new Date(y, m - 1, d);
    const now = new Date();
    const days = Math.round((new Date(now.getFullYear(), now.getMonth(), now.getDate()) - meal) / 86400000);
    if (isNaN(days) || days <= 0) return '오늘';
    if (days === 1) return '어제';
    return days + '일 전';
  }

  // 앞으로의 식사 날짜 → '오늘' / '내일' / '모레' / '11월 26일' (할 일 알림용)
  function actionDateLabel(partyDate) {
    const [y, m, d] = String(partyDate).split('-').map(Number);
    const meal = new Date(y, m - 1, d);
    const now = new Date();
    const days = Math.round((meal - new Date(now.getFullYear(), now.getMonth(), now.getDate())) / 86400000);
    if (isNaN(days)) return '';
    if (days <= 0) return '오늘';
    if (days === 1) return '내일';
    if (days === 2) return '모레';
    return m + '월 ' + d + '일';
  }

  async function logout(event) {
    event.currentTarget.disabled = true;
    try {
      await global.authApi.logout();
    } catch (err) {
      // 이미 세션이 끝났어도 로그인 화면으로 보낸다
    }
    location.href = LOGIN_PAGE;
  }
})(window);
