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
 *
 * 따봉을 남기면 document에 'sseukbap:thumbs-added' 이벤트를 보낸다. (메인이 따봉 TOP 3를 다시 불러옴)
 */
(function (global) {
  'use strict';

  const esc = utils.escapeHtml;
  const STATUS_LABELS = partyApi.STATUS_LABELS;
  const MEAL_TYPE_LABELS = partyApi.MEAL_TYPE_LABELS;

  /**
   * [다음에]를 누른 날짜를 기억하는 브라우저 저장소 키 (값: 'YYYY-MM-DD')
   * 회원별로 따로 기억한다: 'sseukbap.thumbsBanner.snoozedDate.{memberId}' (2026-11-24, A4)
   * 헤더 종(app-header.js, B)도 같은 키를 읽어야 한다.
   */
  const THUMBS_SNOOZE_KEY_PREFIX = 'sseukbap.thumbsBanner.snoozedDate';
  let snoozeKey = null;   // 로그인 회원을 알기 전에는 null (읽기·저장 안 함)
  const TODAY_PARTY_LIMIT = 4;

  /* ===================== 따봉 배너 ===================== */

  /**
   * 따봉 요청 배너: 식사 완료된 팟 중 내가 확정했고 따봉을 안 남긴 최근 식사 1건.
   * [따봉] → 등록 후 다음 대상이 있으면 이어서 보여 준다. [다음에] → 오늘 하루 숨김.
   */
  async function renderThumbsBanner(container) {
    snoozeKey = await loadSnoozeKey();
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
      // 메인(C)이 이 신호를 받으면 '이달의 따봉 TOP 3'를 다시 불러온다
      document.dispatchEvent(new CustomEvent('sseukbap:thumbs-added'));
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

  /** 로그인 회원 번호를 붙인 키. 회원 정보를 못 가져오면 null */
  async function loadSnoozeKey() {
    try {
      const me = await global.authApi.me();
      return THUMBS_SNOOZE_KEY_PREFIX + '.' + me.memberId;
    } catch (e) {
      return null;
    }
  }

  function isSnoozedToday() {
    if (!snoozeKey) return false;
    try {
      return global.localStorage.getItem(snoozeKey) === todayString();
    } catch (e) {
      return false; // 저장소를 못 쓰는 브라우저면 그냥 보여 준다
    }
  }

  function snoozeToday() {
    if (!snoozeKey) return;   // 회원을 모르면 이번 화면에서만 숨긴다
    try {
      global.localStorage.setItem(snoozeKey, todayString());
    } catch (e) {
      // 저장 못 해도 이번 화면에서는 숨긴다
    }
  }

  /* ===================== 참여 중인 식사팟 ===================== */

  /** 내가 참여 중인 식사팟(오늘 ~ 14일 후) 카드 최대 2개. 없으면 영역 숨김. 카드를 누르면 상세(S-12) */
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

  /**
   * 참여 중인 팟 카드. 서버가 오늘 → 다가오는 날짜 → 시간 순으로 준다. (11/20 변경: 오늘 ~ 14일 후)
   * 오늘 팟은 강조(테두리·배경·'오늘' 표시), 다가오는 팟은 날짜만 붙인 평범한 카드.
   */
  function myPartyCard(p) {
    const place = p.meetPlace ? '만날 장소: ' + esc(p.meetPlace) : '방장이 아직 일정을 정하지 않았어요';
    const isToday = !p.partyDate || partyApi.isToday(p.partyDate);
    const when = isToday
      ? '<span class="pm-card__when pm-card__when--today">오늘</span>'
      : '<span class="pm-card__when">' + esc(partyApi.relativeDateLabel(p.partyDate)) + '</span>';
    return (
      '<li><a class="pm-card pm-card--mine' + (isToday ? ' pm-card--today' : '') + '" href="' + partyApi.detailUrl(p.partyId) + '">' +
      when +
      '<span class="pm-card__top"><b class="pm-card__name">' + esc(p.restaurantName) + '</b>' + statusBadge(p.status) + '</span>' +
      '<span class="pm-card__info">' + esc(MEAL_TYPE_LABELS[p.mealType]) + ' ' + esc(p.time) +
      ' · 참석 확정 ' + p.confirmedCount + ' / ' + p.memberCount + '명' +
      (p.myConfirmed ? ' · <span class="pm-card__mine">내 참석 확정</span>' : '') + '</span>' +
      '<span class="pm-card__sub">' + place + ' · 상세 보기 &gt;</span>' +
      '</a></li>'
    );
  }

  /* ===================== 오늘의 식사팟 ===================== */

  /*
   * 오늘의 식사팟 목록 (2026-11-23 개편, 2026-11-25 T1: 참여 중·마감 식사팟도 보여 준다)
   *   - [점심 | 저녁] 탭, 처음엔 지금 끼니 (14:00 전 점심, 그 뒤 저녁). 탭 숫자 = 그 끼니 식사팟 전체 수
   *   - 목록 API 결과 전체(FINISHED·DELETED는 서버가 이미 뺌)를 세 그룹으로 정렬, 그룹 안에서는 시간 빠른 순
   *       ① 참여 가능 ② 참여 중 ③ 참여 불가 (정원 마감·모집 마감·같은 끼니에 이미 참여 중)
   *   - 줄: 시간 · 식당(상세 링크) · 인원 · 버튼
   *       참여 가능 → [참여] (참여 전 확인 창) / 참여 중 → [식사팟 보기] / 참여 불가 → 흐리게 + 이유 글 + [참여] 비활성
   *   - 최대 4줄. 더 있으면 [전체 보기 (N)] → 식사팟 목록(오늘·그 끼니 탭)
   *   - 아래: '원하는 시간이나 식당이 없나요?' + [식사팟 만들기]
   * 참여하면 document에 'sseukbap:party-joined' (detail.source = 'today-parties')를 보낸다.
   */
  const TODAY_LUNCH_END = '14:00';
  const JOINABLE = ['RECRUITING', 'SCHEDULED'];
  const today = { container: null, meal: null, parties: [], error: null };

  async function renderTodayParties(container) {
    today.container = container;
    if (!today.meal) today.meal = currentMeal();
    if (!container.dataset.pmBound) {
      container.dataset.pmBound = '1';
      container.addEventListener('click', onTodayClick);
      document.addEventListener('sseukbap:party-joined', (event) => {
        if (!event.detail || event.detail.source !== 'today-parties') loadTodayParties();
      });
    }
    container.hidden = false;
    container.innerHTML = todaySection(loading());
    await loadTodayParties();
  }

  async function loadTodayParties() {
    try {
      const result = await partyApi.getList({ date: partyApi.today() });
      today.parties = result.parties || [];
      today.error = null;
    } catch (err) {
      if (err.statusCode === 401) return;
      today.error = err.message;
    }
    drawTodayParties();
  }

  /**
   * @param {string} [message] 목록 아래 오류 문구
   * @param {'next'|'prev'} [dir] 탭을 바꿨을 때 목록이 들어오는 방향 (저녁으로 = 오른쪽에서, 점심으로 = 왼쪽에서)
   */
  function drawTodayParties(message, dir) {
    const container = today.container;
    if (today.error) {
      container.innerHTML = todaySection(errorBox(today.error));
      bindRetry(container, () => renderTodayParties(container));
      return;
    }
    const meal = today.meal;
    const ofMeal = today.parties.filter((p) => p.mealType === meal);
    const joinedThisMeal = ofMeal.some((p) => p.joined);
    const sorted = ofMeal
      .map((p) => ({ p, group: rowGroup(p, joinedThisMeal) }))
      .sort((a, b) => a.group - b.group || String(a.p.time).localeCompare(String(b.p.time)));
    const shown = sorted.slice(0, TODAY_PARTY_LIMIT);
    const joinableCount = sorted.filter((r) => r.group === GROUP.JOINABLE).length;
    const label = MEAL_TYPE_LABELS[meal];

    const list = shown.length === 0
      ? '<p class="pm-list__empty">열린 ' + label + ' 식사팟이 없어요. 직접 만들어 밥친구를 모아 보세요</p>'
      : '<ul class="pm-list">' + shown.map((r) => todayRowHtml(r.p, r.group, joinedThisMeal)).join('') + '</ul>';

    const body =
      '<div class="pm-today' + (dir ? ' pm-today--' + dir : '') + '">' +
      (ofMeal.length
        ? '<p class="pm-today__count"><span>' + label + ' 식사팟 <b>' + ofMeal.length + '</b>개 · 참여 가능 ' + joinableCount + '개</span>' +
          (ofMeal.length > TODAY_PARTY_LIMIT
            ? '<a class="pm-section__more" href="' + listUrl(meal) + '">전체 보기 (' + ofMeal.length + ') &gt;</a>' : '') +
          '</p>'
        : '') +
      list +
      '<p class="pm-today__message" role="alert">' + esc(message || '') + '</p>' +
      '<div class="pm-create">' +
      (ofMeal.length ? '<p class="pm-create__title">원하는 시간이나 식당이 없나요?</p>' : '') +
      '<a class="btn btn--primary pm-create__btn" href="' + partyApi.createUrl(null, partyApi.today()) + '">' +
      '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M12 5v14M5 12h14" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round"/></svg>' +
      '식사팟 만들기</a>' +
      '</div>' +
      '</div>';

    // 탭이 이미 있으면 탭은 그대로 두고(움직이는 표시가 부드럽게 미끄러지도록) 목록만 바꾼다
    const tabsEl = container.querySelector('.pm-tabs');
    const bodyEl = container.querySelector('.pm-today-body');
    if (tabsEl && bodyEl) {
      updateTabs(tabsEl);
      bodyEl.innerHTML = body;
    } else {
      container.innerHTML = todaySection(body);
    }
  }

  function todaySection(body) {
    const tabs =
      '<div class="pm-tabs" role="tablist" aria-label="끼니" data-active="' + today.meal + '">' +
      '<span class="pm-tabs__indicator" aria-hidden="true"></span>' +
      ['LUNCH', 'DINNER'].map((m) =>
        '<button type="button" class="pm-tabs__btn" role="tab" data-meal="' + m + '" aria-selected="' + (m === today.meal) + '">' +
        esc(MEAL_TYPE_LABELS[m]) + (today.error ? '' : ' <span class="pm-tabs__count">' + mealCount(m) + '</span>') +
        '</button>'
      ).join('') +
      '</div>';
    return section('today-parties-title', '오늘의 식사팟', tabs, '<div class="pm-today-body">' + body + '</div>');
  }

  /** 탭 선택 표시·개수만 바꾼다 (다시 그리지 않음) */
  function updateTabs(tabsEl) {
    tabsEl.dataset.active = today.meal;
    tabsEl.querySelectorAll('[data-meal]').forEach((btn) => {
      btn.setAttribute('aria-selected', String(btn.dataset.meal === today.meal));
      const count = btn.querySelector('.pm-tabs__count');
      if (count) count.textContent = mealCount(btn.dataset.meal);
    });
  }

  function mealCount(meal) {
    return today.parties.filter((p) => p.mealType === meal).length;
  }

  /** 정렬 그룹: 참여 가능 → 참여 중 → 참여 불가 */
  const GROUP = { JOINABLE: 0, JOINED: 1, BLOCKED: 2 };

  function rowGroup(p, joinedThisMeal) {
    if (p.joined) return GROUP.JOINED;
    return blockedReason(p, joinedThisMeal) ? GROUP.BLOCKED : GROUP.JOINABLE;
  }

  /** 참여할 수 없는 이유 (없으면 '') */
  function blockedReason(p, joinedThisMeal) {
    if (p.status === 'CLOSED') return '모집 마감';
    if (p.status === 'FULL' || !JOINABLE.includes(p.status) || (p.capacity && p.memberCount >= p.capacity)) return '정원 마감';
    if (joinedThisMeal) return '이미 ' + MEAL_TYPE_LABELS[p.mealType] + ' 식사팟 참여 중';
    return '';
  }

  function todayRowHtml(p, group, joinedThisMeal) {
    const reason = group === GROUP.BLOCKED ? blockedReason(p, joinedThisMeal) : '';
    let action;
    if (group === GROUP.JOINED) {
      action = '<a class="btn btn--primary pm-row__join" href="' + partyApi.detailUrl(p.partyId) + '">식사팟 보기</a>';
    } else {
      action = '<button type="button" class="btn pm-row__join" data-action="join" data-party-id="' + p.partyId + '"' +
        (reason ? ' disabled aria-label="참여 (' + esc(reason) + ')"' : '') + '>참여</button>';
    }
    return (
      '<li class="pm-row' + (group === GROUP.BLOCKED ? ' pm-row--blocked' : '') + (group === GROUP.JOINED ? ' pm-row--joined' : '') + '">' +
      '<span class="pm-row__time">' + esc(p.time) + '</span>' +
      '<a class="pm-row__name" href="' + partyApi.detailUrl(p.partyId) + '">' + esc(p.restaurantName) + '</a>' +
      (p.isPrivate ? '<span class="pm-row__private" title="비공개 식사팟">' + partyApi.lockIcon(13) + '<span class="sr-only">비공개</span></span>' : '') +
      (group === GROUP.JOINED ? '<span class="pm-row__tag">참여 중</span>' : '') +
      (reason ? '<span class="pm-row__reason">' + esc(reason) + '</span>' : '') +
      '<span class="pm-row__meta">' + p.memberCount + ' / ' + p.capacity + '명' +
      (p.status === 'SCHEDULED' ? ' · 일정 확정' : '') + '</span>' +
      action +
      '</li>'
    );
  }

  async function onTodayClick(event) {
    const tab = event.target.closest('[data-meal]');
    if (tab) {
      if (tab.dataset.meal === today.meal) return;
      today.meal = tab.dataset.meal;
      drawTodayParties('', today.meal === 'DINNER' ? 'next' : 'prev');
      return;
    }
    const button = event.target.closest('[data-action="join"]');
    if (button) joinTodayParty(button);
  }

  async function joinTodayParty(button) {
    const partyId = button.dataset.partyId;
    const p = today.parties.find((item) => String(item.partyId) === String(partyId));
    if (!p) return;
    // 참여 전에 확인 창 (식당·시간·인원·방 설명, 다음 단계 안내)
    const ok = await partyJoin.ask(Object.assign({ partyDate: partyApi.today() }, p));
    if (!ok) return;
    button.disabled = true;

    try {
      await partyApi.join(partyId);
      ui.toast(p.restaurantName + ' 식사팟에 참여했어요');
      document.dispatchEvent(new CustomEvent('sseukbap:party-joined', { detail: { source: 'today-parties', partyId } }));
      await loadTodayParties();
    } catch (err) {
      if (err.statusCode === 401) return;
      await loadTodayParties();
      if (partyJoin.isSameMealError(err)) {
        partyJoin.sameMealNotice({ partyDate: partyApi.today(), mealType: p.mealType });
        return;
      }
      if (err.error !== 'ALREADY_JOINED') drawTodayParties(err.message);   // 그새 마감·삭제 등
    }
  }

  function currentMeal() {
    const now = new Date();
    const hhmm = String(now.getHours()).padStart(2, '0') + ':' + String(now.getMinutes()).padStart(2, '0');
    return hhmm < TODAY_LUNCH_END ? 'LUNCH' : 'DINNER';
  }

  function listUrl(meal) {
    return partyApi.LIST_URL + '?date=' + encodeURIComponent(partyApi.today()) + '&mealType=' + meal;
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
