/*
 * today-meal.js — 메인 맨 위 '오늘의 한 끼' 카드 (2026-11-20, 시안 A '진행 단계형')
 *
 * 오늘 내 식사 상황을 한 장의 카드로 보여 준다. 백엔드 추가 없이 기존 API만 쓴다.
 *   - GET  /api/parties/my/today          (partyApi.getMyToday)      내가 참여 중인 팟 (오늘 ~ 14일 후 중 가까운 2개)
 *   - GET  /api/parties/list              (partyApi.getList)         오늘 지금 끼니에 모집 중인 팟
 *   - GET  /api/parties/my/thumbs-pending (partyApi.getThumbsPending) 오늘 팟에 따봉을 남길 수 있는지
 *   - POST /api/parties/{id}/members, /members/me/confirm, /thumbs
 *
 * 카드 구성: [머리: 오늘 점심 · 날짜 · 상태] [본문: 시간 블록 | 식당·장소·인원 | 지금 할 일] [진행 막대]
 * 진행 막대: 참여 → 일정 확정 → 참석 확정 → 식사 → 따봉
 *
 * 상태
 *   ① 오늘 참여한 팟 없음   → 모집 중인 팟 3개 미리 보기([참여]) + [식사팟 만들기] 강조 + 룰렛
 *   ② 참여 중, 일정 미정    → 방장이 일정을 정하는 중
 *   ③ 일정 확정, 내 확정 전 → [참석 확정]
 *   ④ 참석 확정 완료        → 몇 시간 뒤 어디서 만나는지
 *   ⑤ 식사 완료             → 따봉 대상일 때만 [따봉] 카드. 그 외 끝난 팟(일정 미확정·확정 안 함·따봉 완료)은
 *                             없는 것으로 보고 ①처럼 다음 끼니를 안내한다 (2026-11-20, 끝난 팟이 자정까지 남던 문제)
 *   + 오늘이 아닌 다음 식사팟이 있으면 카드 아래에 한 줄
 *
 * HTML:  <section id="today-meal" class="today-meal" aria-labelledby="today-meal-title"></section>
 * 필요:  client.js, utils.js, party.js
 * 이벤트: 따봉을 남기면 document에 'sseukbap:thumbs-added' (detail.source = 'today-meal')를 보낸다.
 *         다른 곳에서 보낸 같은 이벤트를 받으면 카드를 다시 그린다.
 */
(function (global) {
  'use strict';

  const esc = utils.escapeHtml;
  const MEAL_LABELS = partyApi.MEAL_TYPE_LABELS;
  const STATUS_LABELS = partyApi.STATUS_LABELS;
  const JOINABLE = ['RECRUITING', 'SCHEDULED'];
  const PREVIEW_LIMIT = 3;
  const ROULETTE_URL = '/roulette.html';
  const CANCEL_BEFORE_MINUTES = 30;

  // 끼니가 끝나는 시각 (마지막 선택 가능 시간 + 30분). 지나면 다음 끼니 기준
  const LUNCH_END = '14:00';
  const DINNER_END = '20:00';

  const STEPS = ['참여', '일정 확정', '참석 확정', '식사', '따봉'];

  const ICONS = {
    pin:
      '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M12 21s-6.5-6.2-6.5-11a6.5 6.5 0 0 1 13 0c0 4.8-6.5 11-6.5 11z" fill="none" stroke="currentColor" stroke-width="2"/>' +
      '<circle cx="12" cy="10" r="2.3" fill="currentColor"/></svg>',
    people:
      '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><circle cx="9" cy="8" r="3.2" fill="none" stroke="currentColor" stroke-width="2"/>' +
      '<path d="M3 19c.6-3.2 3-5 6-5s5.4 1.8 6 5" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>' +
      '<circle cx="17" cy="9" r="2.4" fill="none" stroke="currentColor" stroke-width="2"/>' +
      '<path d="M16.5 14c2.4.2 4 1.6 4.5 4" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>',
    plus:
      '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M12 5v14M5 12h14" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round"/></svg>',
    roulette:
      '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" stroke-width="2"/>' +
      '<path d="M12 3v9l6.4 6.4M12 12L5.6 18.4M12 12H3" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>',
  };

  let root = null;

  function mount(element) {
    root = element;
    if (!root) return;
    root.addEventListener('click', onClick);
    document.addEventListener('sseukbap:thumbs-added', (event) => {
      if (!event.detail || event.detail.source !== 'today-meal') render();
    });
    render();
  }

  /* ===================== 상태 판단 ===================== */

  async function render() {
    if (!root.innerHTML) root.innerHTML = loadingHtml();

    let myParties = [];
    try {
      myParties = await partyApi.getMyToday();
    } catch (err) {
      if (err.statusCode === 401) return;
      myParties = [];   // 못 불러오면 '참여한 팟 없음' 화면으로 대신 보여 준다
    }

    const todays = myParties.filter((p) => !p.partyDate || partyApi.isToday(p.partyDate));
    const upcoming = myParties.find((p) => p.partyDate && !partyApi.isToday(p.partyDate));
    // 진행 중인 오늘 팟이 먼저. 없으면 끝난 팟은 '내가 따봉을 남길 수 있는 팟'일 때만 보여 준다.
    // (일정 미확정·확정 안 함·따봉 완료로 끝난 팟은 할 일이 없으니, 없는 것으로 보고 다음 끼니를 안내한다)
    let party = todays.find((p) => p.status !== 'FINISHED');
    let pending = null;
    if (!party) {
      pending = await loadThumbsPending();
      party = pending && todays.find((p) => p.status === 'FINISHED' && Number(p.partyId) === Number(pending.partyId));
    }

    let card;
    if (!party) card = await emptyCardHtml();
    else if (party.status === 'FINISHED') card = finishedCardHtml(party);
    else if (!party.meetPlace) card = waitingCardHtml(party);
    else if (!party.myConfirmed) card = needConfirmCardHtml(party);
    else card = confirmedCardHtml(party);

    root.innerHTML =
      '<h2 id="today-meal-title" class="sr-only">오늘의 한 끼</h2>' +
      card +
      (upcoming ? upcomingHtml(upcoming) : '');
  }

  /* ===================== ① 오늘 참여한 팟 없음 ===================== */

  async function emptyCardHtml() {
    const meal = currentMeal();
    const today = partyApi.today();

    if (!meal) {
      // 오늘 식사 시간이 끝났으면 내일 식사팟을 권한다
      const tomorrow = partyApi.addDays(today, 1);
      return cardHtml({
        head: headHtml('오늘 식사', '식사 시간이 끝났어요'),
        body:
          '<div class="tm-empty tm-empty--single">' +
          '<div class="tm-create">' +
          '<p class="tm-create__title">내일 같이 먹을 사람을 미리 모아 보세요</p>' +
          '<p class="tm-create__desc">식사팟은 오늘부터 14일 뒤까지 만들 수 있어요.</p>' +
          '<div class="tm-create__actions">' + createButton(partyApi.createUrl(null, tomorrow), '내일 식사팟 만들기') +
          '<a class="btn" href="' + partyApi.LIST_URL + '">식사팟 보기</a></div>' +
          '</div>' +
          '</div>',
      });
    }

    let parties = [];
    let loadFailed = false;
    try {
      const result = await partyApi.getList({ mealType: meal, excludeJoined: true });
      parties = result.parties
        .filter((p) => JOINABLE.includes(p.status))
        .sort((a, b) => String(a.time).localeCompare(String(b.time)));
    } catch (err) {
      loadFailed = true;
    }

    const preview = parties.slice(0, PREVIEW_LIMIT);
    const mealLabel = MEAL_LABELS[meal];
    let listHtml;
    if (loadFailed) {
      listHtml = '<p class="tm-list__empty">모집 중인 식사팟을 불러오지 못했어요. <a href="' + partyApi.LIST_URL + '">목록에서 보기</a></p>';
    } else if (preview.length === 0) {
      listHtml = '<p class="tm-list__empty">지금 모집 중인 ' + mealLabel + ' 식사팟이 없어요.</p>';
    } else {
      listHtml = '<ul class="tm-list">' + preview.map((p) => previewRowHtml(Object.assign({ mealType: meal }, p))).join('') + '</ul>';
    }

    return cardHtml({
      head: headHtml('오늘 ' + mealLabel, partyApi.fullDateLabel(today) + ' · 아직 정하지 않았어요'),
      body:
        '<div class="tm-empty">' +
        '<div class="tm-empty__col">' +
        '<div class="tm-col-head">' +
        '<p class="tm-col-title">같이 먹기 <small>' + (loadFailed ? '' : '모집 중 ' + parties.length + '개') + '</small></p>' +
        (parties.length > PREVIEW_LIMIT ? '<a class="tm-col-more" href="' + partyApi.LIST_URL + '">전체 보기 &gt;</a>' : '') +
        '</div>' +
        listHtml +
        '<div class="tm-create tm-create--inline">' +
        '<p class="tm-create__title">' + (!loadFailed && parties.length === 0
          ? '먼저 만들면 다른 사람이 참여할 수 있어요'
          : '원하는 시간이나 식당이 없나요?') + '</p>' +
        createButton(partyApi.createUrl(), '식사팟 만들기') +
        '</div>' +
        '</div>' +
        '<div class="tm-empty__col tm-empty__col--roulette">' +
        '<p class="tm-col-title"><span class="tm-col-icon">' + ICONS.roulette + '</span>메뉴가 고민이면</p>' +
        '<p class="tm-col-desc">룰렛으로 식당을 정하고, 그 식당으로 바로 식사팟을 만들 수 있어요.</p>' +
        '<a class="btn tm-btn--accent" href="' + ROULETTE_URL + '">룰렛 돌리기</a>' +
        '</div>' +
        '</div>',
    });
  }

  function previewRowHtml(p) {
    const full = p.capacity && p.memberCount >= p.capacity;
    return (
      '<li class="tm-row">' +
      '<span class="tm-row__time">' + esc(p.time) + '</span>' +
      '<a class="tm-row__name" href="' + partyApi.detailUrl(p.partyId) + '">' + esc(p.restaurantName) + '</a>' +
      '<span class="tm-row__meta">' + p.memberCount + (p.capacity ? ' / ' + p.capacity : '') + '명' +
      (p.status === 'SCHEDULED' ? ' · 일정 확정' : '') + '</span>' +
      '<button type="button" class="btn tm-row__join" data-action="join" data-party-id="' + p.partyId + '"' +
      ' data-party="' + esc(JSON.stringify({ restaurantName: p.restaurantName, category: p.category, time: p.time,
        memberCount: p.memberCount, capacity: p.capacity, memo: p.memo, mealType: p.mealType || null })) + '"' +
      (full ? ' disabled' : '') + '>참여</button>' +
      '</li>'
    );
  }

  function createButton(href, label) {
    return '<a class="btn btn--primary tm-btn--create" href="' + href + '">' + ICONS.plus + label + '</a>';
  }

  /* ===================== ②~⑤ 오늘 참여한 팟 ===================== */

  // ② 참여 중, 일정 미정
  function waitingCardHtml(p) {
    return partyCardHtml(p, {
      step: 1,
      stepLabel: '일정 확정 대기',
      timeTone: 'plain',
      timeSub: '예정',
      facts: [factHtml('pin', '장소 정하는 중', true), factHtml('people', '참여 ' + p.memberCount + '명')],
      ctaNote: '방장이 시간과 장소를 정하고 있어요',
      actions: detailLink(p, false),
    });
  }

  // ③ 일정 확정, 내 확정 전
  function needConfirmCardHtml(p) {
    const deadline = minusMinutes(p.time, CANCEL_BEFORE_MINUTES);
    return partyCardHtml(p, {
      step: 2,
      timeTone: 'primary',
      timeSub: untilText(p.time),
      facts: [factHtml('pin', esc(p.meetPlace)), factHtml('people', '참석 확정 ' + p.confirmedCount + ' / ' + p.memberCount + '명')],
      ctaNote: '갈 수 있으면 참석 확정을 눌러 주세요',
      ctaTone: 'warning',
      hint: '확정해야 식사 후 따봉을 남길 수 있어요.' + (deadline ? ' 확정 취소는 ' + deadline + '까지 가능해요.' : ''),
      actions: detailLink(p, false) +
        '<button type="button" class="btn btn--primary" data-action="confirm" data-party-id="' + p.partyId + '">참석 확정</button>',
    });
  }

  // ④ 참석 확정 완료
  function confirmedCardHtml(p) {
    return partyCardHtml(p, {
      step: 3,
      timeTone: 'primary',
      timeSub: untilText(p.time),
      facts: [factHtml('pin', esc(p.meetPlace) + '에서 만나요'), factHtml('people', '참석 확정 ' + p.confirmedCount + ' / ' + p.memberCount + '명')],
      actions: detailLink(p, true),
    });
  }

  // ⑤ 식사 완료: render()가 따봉 대상인 팟만 넘긴다 (= 내가 참여 확정했고 아직 따봉을 안 남김)
  function finishedCardHtml(p) {
    return partyCardHtml(p, {
      step: 4,
      timeTone: 'accent',
      timeSub: '식사 완료',
      facts: [factHtml('people', p.confirmedCount + '명이 함께 먹었어요')],
      ctaNote: '식사는 어땠나요?',
      ctaTone: 'accent',
      actions:
        '<button type="button" class="btn tm-btn--accent" data-action="thumbs" data-party-id="' + p.partyId + '">' +
        utils.thumbsUpIcon(16) + '따봉</button>',
    });
  }

  /** 따봉 요청 대상 1건. 없거나 못 불러오면 null (끝난 팟 카드 없이 다음 끼니를 안내한다) */
  async function loadThumbsPending() {
    try {
      return await partyApi.getThumbsPending();
    } catch (err) {
      return null;
    }
  }

  /* ===================== 카드 조각 ===================== */

  function cardHtml(o) {
    return '<article class="tm-card">' + o.head + o.body + (o.steps || '') + '</article>';
  }

  function headHtml(title, sub, status) {
    return (
      '<header class="tm-head">' +
      '<span class="tm-head__title">' + esc(title) + '</span>' +
      (sub ? '<span class="tm-head__sub">' + esc(sub) + '</span>' : '') +
      (status && STATUS_LABELS[status]
        ? '<span class="badge badge--' + status.toLowerCase() + ' tm-head__badge">' + esc(STATUS_LABELS[status]) + '</span>'
        : '') +
      '</header>'
    );
  }

  function partyCardHtml(p, o) {
    const body =
      '<div class="tm-body">' +
      '<div class="tm-time tm-time--' + o.timeTone + '">' +
      '<strong class="tm-time__clock">' + esc(p.time) + '</strong>' +
      '<span class="tm-time__sub">' + esc(o.timeSub) + '</span>' +
      '</div>' +
      '<div class="tm-main">' +
      '<a class="tm-main__name" href="' + partyApi.detailUrl(p.partyId) + '">' + esc(p.restaurantName) + '</a>' +
      '<ul class="tm-facts">' + o.facts.join('') + '</ul>' +
      (o.hint ? '<p class="tm-hint">' + esc(o.hint) + '</p>' : '') +
      '<p class="tm-message" role="alert"></p>' +
      '</div>' +
      '<div class="tm-cta">' +
      (o.ctaNote ? '<p class="tm-cta__note' + (o.ctaTone ? ' tm-cta__note--' + o.ctaTone : '') + '">' + esc(o.ctaNote) + '</p>' : '') +
      '<div class="tm-cta__actions">' + o.actions + '</div>' +
      '</div>' +
      '</div>';

    return cardHtml({
      head: headHtml('오늘 ' + MEAL_LABELS[p.mealType], partyApi.fullDateLabel(partyApi.today()), p.status),
      body,
      steps: stepsHtml(o.step, o.stepLabel),
    });
  }

  // current: 지금 단계의 번호(0부터). 그 앞은 완료. current가 5면 모두 완료
  function stepsHtml(current, currentLabel) {
    return (
      '<ol class="tm-steps" aria-label="식사 진행 단계">' +
      STEPS.map((label, i) => {
        const state = i < current ? 'done' : (i === current ? 'now' : 'todo');
        const text = i === current && currentLabel ? currentLabel : label;
        return '<li class="tm-step tm-step--' + state + '"' + (state === 'now' ? ' aria-current="step"' : '') + '>' +
          esc(text) + '</li>';
      }).join('') +
      '</ol>'
    );
  }

  function factHtml(icon, html, muted) {
    return '<li class="tm-fact' + (muted ? ' tm-fact--muted' : '') + '">' + ICONS[icon] + '<span>' + html + '</span></li>';
  }

  function detailLink(p, primary) {
    return '<a class="btn' + (primary ? ' btn--primary' : '') + '" href="' + partyApi.detailUrl(p.partyId) + '">팟 보기</a>';
  }

  function upcomingHtml(p) {
    return (
      '<a class="tm-upcoming" href="' + partyApi.detailUrl(p.partyId) + '">' +
      '<span class="tm-upcoming__label">다음 식사팟</span>' +
      '<span>' + esc(partyApi.relativeDateLabel(p.partyDate)) + ' ' + esc(MEAL_LABELS[p.mealType]) + ' ' +
      esc(p.time) + ' · <b>' + esc(p.restaurantName) + '</b></span>' +
      '<span class="tm-upcoming__more" aria-hidden="true">&gt;</span>' +
      '</a>'
    );
  }

  function loadingHtml() {
    return '<div class="tm-loading"><span class="spinner" aria-hidden="true"></span>' +
      '<span class="text-muted">오늘 식사 정보를 불러오는 중…</span></div>';
  }

  /* ===================== 버튼 ===================== */

  async function onClick(event) {
    const button = event.target.closest('[data-action]');
    if (!button || !root.contains(button)) return;
    const partyId = button.dataset.partyId;
    const action = button.dataset.action;
    const message = button.closest('.tm-card').querySelector('.tm-message');
    if (action === 'join' && button.dataset.party) {
      // 참여 전에 확인 창 (식당·시간·인원·방 설명, 다음 단계 안내)
      const ok = await partyJoin.ask(JSON.parse(button.dataset.party));
      if (!ok) return;
    }
    button.disabled = true;
    if (message) message.textContent = '';

    try {
      if (action === 'join') {
        await partyApi.join(partyId);
        render();
      } else if (action === 'confirm') {
        await partyApi.confirm(partyId);
        render();
      } else if (action === 'thumbs') {
        const result = await partyApi.thumbs(partyId);
        document.dispatchEvent(new CustomEvent('sseukbap:thumbs-added', { detail: { source: 'today-meal' } }));
        showThumbsDone(button, result);
      }
    } catch (err) {
      if (err.statusCode === 401) return;
      // 이미 처리됐거나 상태가 바뀐 경우: 최신 상태로 다시 그린다
      const refresh = ['ALREADY_JOINED', 'ALREADY_CONFIRMED', 'ALREADY_THUMBED', 'PARTY_FINISHED', 'PARTY_DELETED', 'PARTY_NOT_FOUND'];
      if (refresh.includes(err.error)) {
        render();
        return;
      }
      if (action === 'join') {
        // 목록 줄에는 메시지 칸이 없어서 버튼 글자로 알려 준다 (예: 그새 마감)
        button.textContent = err.error === 'PARTY_FULL' ? '마감' : '실패';
        return;
      }
      button.disabled = false;
      if (message) message.textContent = err.message;
    }
  }

  function showThumbsDone(button, result) {
    const card = button.closest('.tm-card');
    const note = card.querySelector('.tm-cta__note');
    if (note) note.textContent = '따봉을 남겼어요';
    button.outerHTML = '<span class="tm-done">' + utils.thumbsUpIcon(16) + '최근 30일 따봉 ' + Number(result.thumbsCount30d) + '개</span>';
    const steps = card.querySelectorAll('.tm-step');
    steps.forEach((step) => {
      step.className = 'tm-step tm-step--done';
      step.removeAttribute('aria-current');
    });
  }

  /* ===================== 시간 ===================== */

  function nowHHMM() {
    const now = new Date();
    return String(now.getHours()).padStart(2, '0') + ':' + String(now.getMinutes()).padStart(2, '0');
  }

  // 14:00 전 점심, 20:00 전 저녁, 그 뒤는 없음
  function currentMeal() {
    const hhmm = nowHHMM();
    if (hhmm < LUNCH_END) return 'LUNCH';
    if (hhmm < DINNER_END) return 'DINNER';
    return null;
  }

  function toMinutes(hhmm) {
    const [h, m] = String(hhmm).split(':').map(Number);
    return h * 60 + m;
  }

  // '12:00' → '1시간 20분 후' / '15분 후' / '곧 시작'
  function untilText(hhmm) {
    const diff = toMinutes(hhmm) - toMinutes(nowHHMM());
    if (isNaN(diff)) return '';
    if (diff <= 0) return '식사 시간';
    if (diff < 5) return '곧 시작';
    const h = Math.floor(diff / 60);
    const m = diff % 60;
    return (h ? h + '시간 ' : '') + (m ? m + '분 ' : '') + '후';
  }

  function minusMinutes(hhmm, minutes) {
    const total = toMinutes(hhmm) - minutes;
    if (isNaN(total) || total < 0) return '';
    return String(Math.floor(total / 60)).padStart(2, '0') + ':' + String(total % 60).padStart(2, '0');
  }

  global.todayMeal = { mount, render };
})(window);
