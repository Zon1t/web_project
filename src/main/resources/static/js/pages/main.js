/*
 * main.js — 메인 화면 (S-01, C)
 *
 * C가 그리는 영역: ③ 오늘의 구내식당 ⑥ 이달의 따봉 TOP 3 ⑦ 룰렛 배너(P1)
 *   (① 헤더의 닉네임·로그아웃은 js/components/app-header.js)
 * A가 그리는 영역: ⑤ 오늘의 식사팟
 * 따봉 요청: 헤더 종 알림(app-header.js)과 '오늘의 한 끼' 카드가 맡는다. (2026-11-20, 홈 따봉 배너 제거)
 * 오늘의 한 끼 카드: js/components/today-meal.js (2026-11-20, 기존 '참여 중인 식사팟' 영역을 대신함)
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
    CLOSED: '오늘은 운영하지 않아요.',
    UNAVAILABLE: '메뉴 정보를 불러올 수 없어요.',
  };
  const LUNCH_UNTIL_HOUR = 14;   // 14시 전에는 점심 메뉴를, 이후에는 저녁 메뉴를 크게 보여 준다
  const MAIN_MENU_INDEX = 1;     // 구내식당 메뉴 중 크게 보여 줄 메인 요리 위치 (0부터, 2번째)

  /*
   * 시간대별 인사 문구. 화면을 열 때마다 하나를 무작위로 고른다.
   * {who}는 '○○님, '(닉네임을 못 받으면 빈 칸)으로 바뀐다. 금요일·월요일은 전용 문구도 섞는다.
   */
  const GREETINGS = {
    morning: {
      illust: 'bowl',
      items: [
        ['{who}좋은 아침이에요', '오늘 점심은 누구랑, 어디서 먹을지 미리 정해 볼까요?'],
        ['{who}출근하느라 수고했어요', '점심 식사팟을 먼저 만들어 두면 같이 먹을 사람이 금방 모여요.'],
        ['{who}오늘 점심, 벌써 기대되죠?', '구내식당 메뉴부터 확인해 보세요.'],
        ['{who}커피 한 잔 하셨어요?', '점심 메뉴 고민은 룰렛에 맡겨도 돼요.'],
      ],
    },
    lunch: {
      illust: 'bowl',
      items: [
        ['{who}점심 뭐 드실래요?', '같이 먹을 사람을 찾거나, 룰렛으로 식당을 정해 보세요.'],
        ['{who}배꼽시계가 울릴 시간이에요', '지금 모집 중인 점심 식사팟을 둘러보세요.'],
        ['{who}오늘은 어디로 갈까요?', '따봉 랭킹에서 요즘 인기 식당을 확인해 보세요.'],
        ['{who}혼밥도 좋지만 같이 먹으면 더 맛있어요', '시간 맞는 식사팟에 슥 참여해 보세요.'],
      ],
    },
    afternoon: {
      illust: 'tray',
      items: [
        ['{who}오후도 힘내요', '저녁 식사팟이 하나둘 열리고 있어요.'],
        ['{who}점심은 맛있게 드셨어요?', '다녀온 식당에 따봉과 리뷰를 남겨 주세요.'],
        ['{who}나른한 오후네요', '저녁은 누구랑 먹을지 미리 정해 둘까요?'],
        ['{who}조금만 더 버티면 저녁이에요', '오늘 저녁 구내식당 메뉴를 확인해 보세요.'],
      ],
    },
    evening: {
      illust: 'tray',
      items: [
        ['{who}저녁은 누구랑 먹을까요?', '퇴근 전에 저녁 식사팟을 확인해 보세요.'],
        ['{who}오늘 하루도 고생 많았어요', '든든한 저녁으로 마무리해요.'],
        ['{who}저녁 메뉴가 고민이라면', '룰렛을 한 번 돌려 보세요.'],
      ],
    },
    night: {
      illust: 'thumbs',
      items: [
        ['{who}오늘도 수고했어요', '내일 점심 식사팟을 미리 만들어 둘 수도 있어요.'],
        ['{who}푹 쉬고 내일 봬요', '오늘 다녀온 식당이 있다면 따봉을 남겨 주세요.'],
        ['{who}늦게까지 고생 많아요', '내일은 맛있는 점심이 기다리고 있어요.'],
      ],
    },
  };
  const WEEKDAY_GREETINGS = {
    1: ['{who}월요일도 힘내요', '한 주의 시작은 든든한 한 끼로!'],
    5: ['{who}드디어 금요일이에요', '이번 주 마지막 식사팟, 같이 먹을 사람을 찾아볼까요?'],
  };


  /* ---------- 시작 ---------- */

  renderGreeting();
  loadCafeteria();
  loadTop3();
  mountPartyParts();
  document.getElementById('main-roulette').hidden = !ROULETTE_READY;
  document.getElementById('main-roulette-illust').innerHTML = ui.illust('roulette');

  document.addEventListener('sseukbap:thumbs-added', loadTop3);   // 헤더 알림·오늘의 한 끼에서 따봉 성공 시

  /* ---------- 시간대 인사 ---------- */

  /** 지금 시간에 맞는 인사 + 오늘 날짜. 닉네임은 /auth/me로 채운다 (실패해도 인사는 보인다) */
  async function renderGreeting() {
    const el = document.getElementById('main-greeting');
    const seed = Math.random();   // 닉네임을 받아 다시 그려도 같은 문구가 나오도록
    const draw = (nickname) => {
      const g = greetingOf(new Date().getHours(), nickname ? esc(nickname) + '님' : '', seed);
      el.innerHTML =
        '<div class="main-greeting__text">' +
        '<p class="main-greeting__date">' + esc(formatToday()) + '</p>' +
        '<p class="main-greeting__title">' + g.title + '</p>' +
        '<p class="main-greeting__desc">' + esc(g.desc) + '</p>' +
        '</div>' +
        '<span class="main-greeting__illust" aria-hidden="true">' + ui.illust(g.illust) + '</span>';
    };
    draw('');
    try {
      const me = await authApi.me();
      draw(me && me.nickname);
    } catch (err) {
      // 401이면 client.js가 로그인 화면으로 보낸다. 그 외에는 닉네임 없이 둔다
    }
  }

  /** name은 이미 이스케이프된 '○○님' 또는 '' */
  function greetingOf(hour, name, seed) {
    const slot = hour < 10 ? 'morning' : hour < 14 ? 'lunch' : hour < 17 ? 'afternoon' : hour < 21 ? 'evening' : 'night';
    const group = GREETINGS[slot];
    const candidates = group.items.slice();
    const weekday = WEEKDAY_GREETINGS[new Date().getDay()];
    if (weekday && hour < 21) candidates.push(weekday);
    const [title, desc] = candidates[Math.floor(seed * candidates.length)];
    return { title: title.replace('{who}', name ? name + ', ' : ''), desc, illust: group.illust };
  }

  /** 오늘 → '11월 20일 금요일' */
  function formatToday() {
    const now = new Date();
    return (now.getMonth() + 1) + '월 ' + now.getDate() + '일 ' + WEEKDAYS[now.getDay()] + '요일';
  }

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
      const isLunchNow = new Date().getHours() < LUNCH_UNTIL_HOUR;
      body.innerHTML =
        '<div class="cafeteria-meals">' +
        mealHtml('점심', today.lunch, isLunchNow) +
        mealHtml('저녁', today.dinner, !isLunchNow) +
        '</div>';
    } catch (err) {
      if (err.statusCode === 401) return;
      body.innerHTML = errorHtml('메뉴를 불러오지 못했어요', err.message, 'cafeteria');
    }
  }

  /**
   * 끼니 카드: 2번째 메뉴(메인 요리)를 크게, 나머지는 칩으로. 지금 끼니는 강조
   * 구내식당 메뉴는 보통 '밥 → 메인 요리 → 국 → 반찬' 순서라 2번째를 메인으로 본다. (메뉴가 1개면 그것, MAIN_MENU_INDEX)
   */
  function mealHtml(label, menus, isNow) {
    const list = (menus || []).filter(Boolean);
    let body;
    if (!list.length) {
      body = '<p class="cafeteria-meal__empty">메뉴 정보가 없어요</p>';
    } else {
      const mainIndex = list.length > MAIN_MENU_INDEX ? MAIN_MENU_INDEX : 0;
      const rest = list.filter((m, i) => i !== mainIndex);
      body =
        '<p class="cafeteria-meal__main">' + esc(list[mainIndex]) + '</p>' +
        (rest.length
          ? '<ul class="cafeteria-meal__chips">' + rest.map((m) => '<li>' + esc(m) + '</li>').join('') + '</ul>'
          : '');
    }
    return (
      '<div class="cafeteria-meal' + (isNow ? ' is-now' : '') + '">' +
      '<div class="cafeteria-meal__head">' +
      ui.avatar('CAFETERIA', 36) +
      '<h3 class="cafeteria-meal__title">' + label + '</h3>' +
      (isNow ? '<span class="cafeteria-meal__now">지금 메뉴</span>' : '') +
      '</div>' +
      body +
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
        body.innerHTML = noticeHtml('최근 30일 동안 따봉을 받은 식당이 아직 없어요.');
        return;
      }
      body.innerHTML = '<ol class="top3-list">' + result.rankings.map(rankingItemHtml).join('') + '</ol>';
    } catch (err) {
      if (err.statusCode === 401) return;
      body.innerHTML = errorHtml('랭킹을 불러오지 못했어요', err.message, 'top3');
    }
  }

  function rankingItemHtml(item) {
    const category = CATEGORY_LABELS[item.category] || item.category;
    return (
      '<li class="top3-item">' +
      '<span class="top3-item__rank top3-item__rank--' + item.rank + '">' + item.rank + '</span>' +
      ui.avatar(item.category, 32) +
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

    mount(parts && parts.renderTodayParties, 'today-parties');

    if (window.todayMeal) window.todayMeal.mount(document.getElementById('today-meal'));
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

  /** 로딩: 회색 뼈대 (text는 화면 읽기 프로그램용) */
  function loadingHtml(text) {
    return '<div aria-label="' + esc(text) + '">' + ui.skeleton('row', 2) + '</div>';
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
