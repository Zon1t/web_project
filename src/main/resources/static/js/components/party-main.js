/*
 * party-main.js — 메인(S-01)에 끼워 넣는 식사팟 부품 3개 (A)
 *
 * 메인 화면(C)은 자리만 만들고 부르면 된다. 조회·버튼·빈 상태·오류는 이 파일이 처리한다.
 *
 *   <link rel="stylesheet" href="/css/party-main.css">
 *   <div id="thumbs-banner"></div>
 *   <div id="my-parties"></div>
 *   <div id="today-parties"></div>
 *
 *   <script src="/js/api/client.js"></script>
 *   <script src="/js/utils.js"></script>
 *   <script src="/js/api/party.js"></script>
 *   <script src="/js/components/party-main.js"></script>
 *   <script>
 *     partyMain.renderThumbsBanner(document.getElementById('thumbs-banner'));
 *     partyMain.renderMyParties(document.getElementById('my-parties'));
 *     partyMain.renderTodayParties(document.getElementById('today-parties'));
 *   </script>
 *
 * 보여 줄 것이 없으면 그 자리(container)에 hidden을 붙여 영역째 숨긴다.
 * (따봉 대상 없음, [다음에]로 오늘 숨김, 참여 중인 팟 없음)
 */
(function (global) {
  'use strict';

  const esc = utils.escapeHtml;
  const STATUS_LABELS = partyApi.STATUS_LABELS;
  const MEAL_TYPE_LABELS = partyApi.MEAL_TYPE_LABELS;

  /** [다음에]를 누른 날짜를 기억하는 브라우저 저장소 키 (값: 'YYYY-MM-DD') */
  const THUMBS_SNOOZE_KEY = 'sseukbap.thumbsBanner.snoozedDate';
  const TODAY_PARTY_LIMIT = 3;

  /* ===================== 따봉 배너 ===================== */

  /**
   * 따봉 요청 배너: 식사 완료된 팟 중 내가 확정했고 따봉을 안 남긴 최근 식사 1건.
   * [따봉] → 등록 후 다음 대상이 있으면 이어서 보여 준다. [다음에] → 오늘 하루 숨김.
   */
  async function renderThumbsBanner(container) {
    if (isSnoozedToday()) {
      hide(container);
      return;
    }
    try {
      const pending = await partyApi.getThumbsPending();
      if (!pending) {
        hide(container);
        return;
      }
      showBanner(container, pending, null);
    } catch (err) {
      // 배너는 부가 기능이라 오류가 나면 조용히 숨긴다 (401은 client.js가 로그인 화면으로 이동)
      hide(container);
    }
  }

  function showBanner(container, pending, notice) {
    container.hidden = false;
    container.innerHTML =
      '<section class="pm-banner" aria-label="따봉 요청">' +
      '<div class="pm-banner__text">' +
      (notice ? '<p class="pm-banner__notice" role="status">' + esc(notice) + '</p>' : '') +
      '<p class="pm-banner__title">' + esc(whenLabel(pending.partyDate)) + ' ' +
      esc(MEAL_TYPE_LABELS[pending.mealType]) + ' <b>' + esc(pending.restaurantName) + '</b> 식사는 어떠셨나요?</p>' +
      '<p class="pm-banner__desc">함께 식사한 식당에 따봉을 남겨 주세요</p>' +
      '</div>' +
      '<div class="pm-banner__actions">' +
      '<button type="button" class="btn btn--primary" data-action="thumbs">' + utils.thumbsUpIcon(16) + '따봉</button>' +
      '<button type="button" class="btn" data-action="snooze">다음에</button>' +
      '</div>' +
      '<p class="pm-banner__error" role="alert"></p>' +
      '</section>';

    container.querySelector('[data-action="thumbs"]').addEventListener('click', (event) => {
      sendThumbs(container, pending, event.currentTarget);
    });
    container.querySelector('[data-action="snooze"]').addEventListener('click', () => {
      snoozeToday();
      hide(container);
    });
  }

  async function sendThumbs(container, pending, button) {
    button.disabled = true;
    let notice;
    try {
      const result = await partyApi.thumbs(pending.partyId);
      notice = '따봉을 남겼어요! ' + pending.restaurantName + ' 최근 30일 따봉 ' + result.thumbsCount30d + '개';
    } catch (err) {
      if (err.statusCode === 401) return;
      // 이미 남겼거나 대상이 아니게 된 경우는 다음 대상으로 넘어간다
      const skippable = ['ALREADY_THUMBED', 'NOT_CONFIRMED_MEMBER', 'PARTY_NOT_FINISHED', 'PARTY_DELETED', 'PARTY_NOT_FOUND'];
      if (!skippable.includes(err.error)) {
        button.disabled = false;
        container.querySelector('.pm-banner__error').textContent = err.message;
        return;
      }
    }

    // 대상이 여러 개면 최근 식사부터 하나씩 (명세)
    try {
      const next = await partyApi.getThumbsPending();
      if (next) {
        showBanner(container, next, notice);
      } else if (notice) {
        showDone(container, notice);
      } else {
        hide(container);
      }
    } catch (err) {
      if (notice) showDone(container, notice); else hide(container);
    }
  }

  /** 마지막 따봉을 남긴 뒤: 감사 문구만 남긴다 */
  function showDone(container, notice) {
    container.innerHTML =
      '<section class="pm-banner pm-banner--done" aria-label="따봉 요청">' +
      '<p class="pm-banner__title" role="status">' + utils.thumbsUpIcon(16) + ' ' + esc(notice) + '</p>' +
      '</section>';
  }

  function isSnoozedToday() {
    try {
      return global.localStorage.getItem(THUMBS_SNOOZE_KEY) === todayString();
    } catch (e) {
      return false; // 저장소를 못 쓰는 브라우저면 그냥 보여 준다
    }
  }

  function snoozeToday() {
    try {
      global.localStorage.setItem(THUMBS_SNOOZE_KEY, todayString());
    } catch (e) {
      // 저장 못 해도 이번 화면에서는 숨긴다
    }
  }

  /* ===================== 참여 중인 식사팟 ===================== */

  /** 내가 참여 중인 오늘 식사팟 카드 최대 2개. 없으면 영역 숨김. 카드를 누르면 상세(S-12) */
  async function renderMyParties(container) {
    container.hidden = false;
    container.innerHTML = section('my-parties-title', '참여 중인 식사팟', '', loading());
    try {
      const parties = await partyApi.getMyToday();
      if (parties.length === 0) {
        hide(container);
        return;
      }
      container.innerHTML = section('my-parties-title', '참여 중인 식사팟', '',
        '<ul class="pm-cards pm-cards--two">' + parties.map(myPartyCard).join('') + '</ul>');
    } catch (err) {
      if (err.statusCode === 401) return;
      container.innerHTML = section('my-parties-title', '참여 중인 식사팟', '', errorBox(err.message));
      bindRetry(container, () => renderMyParties(container));
    }
  }

  function myPartyCard(p) {
    const place = p.meetPlace ? '만날 장소: ' + esc(p.meetPlace) : '방장이 아직 일정을 확정하지 않았어요';
    return (
      '<li><a class="pm-card pm-card--mine" href="' + partyApi.detailUrl(p.partyId) + '">' +
      '<span class="pm-card__top"><b class="pm-card__name">' + esc(p.restaurantName) + '</b>' + statusBadge(p.status) + '</span>' +
      '<span class="pm-card__info">' + esc(MEAL_TYPE_LABELS[p.mealType]) + ' ' + esc(p.time) +
      ' · 확정 ' + p.confirmedCount + ' / ' + p.memberCount + '명' +
      (p.myConfirmed ? ' · <span class="pm-card__mine">내 참여 확정</span>' : '') + '</span>' +
      '<span class="pm-card__sub">' + place + ' · 상세 보기 &gt;</span>' +
      '</a></li>'
    );
  }

  /* ===================== 오늘의 식사팟 ===================== */

  /** 내가 참여하지 않은 모집 중 팟 최대 3개 (식사 시간 빠른 순). [전체 보기] → 목록(S-07) */
  async function renderTodayParties(container) {
    const more = '<a class="pm-section__more" href="' + partyApi.LIST_URL + '">전체 보기 &gt;</a>';
    container.hidden = false;
    container.innerHTML = section('today-parties-title', '오늘의 식사팟', more, loading());
    try {
      const result = await partyApi.getList({ status: 'RECRUITING', excludeJoined: true, limit: TODAY_PARTY_LIMIT });
      const body = result.parties.length === 0
        ? '<div class="pm-empty"><p>지금 모집 중인 식사팟이 없어요.</p>' +
          '<a class="btn btn--primary" href="' + partyApi.createUrl() + '">식사팟 만들기</a></div>'
        : '<ul class="pm-cards pm-cards--three">' + result.parties.map(todayPartyCard).join('') + '</ul>';
      container.innerHTML = section('today-parties-title', '오늘의 식사팟', more, body);
    } catch (err) {
      if (err.statusCode === 401) return;
      container.innerHTML = section('today-parties-title', '오늘의 식사팟', more, errorBox(err.message));
      bindRetry(container, () => renderTodayParties(container));
    }
  }

  function todayPartyCard(p) {
    return (
      '<li><a class="pm-card" href="' + partyApi.detailUrl(p.partyId) + '">' +
      '<span class="pm-card__top"><b class="pm-card__name">' + esc(p.restaurantName) + '</b>' + statusBadge(p.status) + '</span>' +
      '<span class="pm-card__info">' + esc(MEAL_TYPE_LABELS[p.mealType]) + ' ' + esc(p.time) +
      ' · ' + p.memberCount + ' / ' + p.capacity + '명</span>' +
      (p.memo ? '<span class="pm-card__sub">' + esc(p.memo) + '</span>' : '') +
      '</a></li>'
    );
  }

  /* ===================== 공통 ===================== */

  function section(titleId, title, extra, body) {
    return (
      '<section class="pm-section" aria-labelledby="' + titleId + '">' +
      '<div class="pm-section__head"><h2 id="' + titleId + '" class="pm-section__title">' + title + '</h2>' + extra + '</div>' +
      body +
      '</section>'
    );
  }

  function statusBadge(status) {
    return '<span class="badge badge--' + status.toLowerCase() + '">' + esc(STATUS_LABELS[status]) + '</span>';
  }

  function loading() {
    return '<div class="pm-loading"><span class="spinner" aria-hidden="true"></span><span class="text-muted">불러오는 중…</span></div>';
  }

  function errorBox(message) {
    return '<div class="pm-error" role="alert"><span>' + esc(message) + '</span>' +
      '<button type="button" class="btn" data-action="retry">다시 시도</button></div>';
  }

  function bindRetry(container, retry) {
    const button = container.querySelector('[data-action="retry"]');
    if (button) button.addEventListener('click', retry);
  }

  function hide(container) {
    container.hidden = true;
    container.innerHTML = '';
  }

  function todayString() {
    const now = new Date();
    const pad = (n) => String(n).padStart(2, '0');
    return now.getFullYear() + '-' + pad(now.getMonth() + 1) + '-' + pad(now.getDate());
  }

  /** 식사 날짜 → '오늘' / '어제' / 'N일 전' */
  function whenLabel(partyDate) {
    const [y, m, d] = String(partyDate).split('-').map(Number);
    const meal = new Date(y, m - 1, d);
    const today = new Date();
    const days = Math.round((new Date(today.getFullYear(), today.getMonth(), today.getDate()) - meal) / 86400000);
    if (days <= 0) return '오늘';
    if (days === 1) return '어제';
    return days + '일 전';
  }

  global.partyMain = {
    renderThumbsBanner,
    renderMyParties,
    renderTodayParties,
  };
})(window);
