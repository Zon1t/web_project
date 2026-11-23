/*
 * today-meal.js — 메인 맨 위 '내 식사' 슬롯 (2026-11-23 개편, 이전: 지금 끼니 카드 한 장)
 *
 * 하루의 점심·저녁 슬롯 두 칸을 보여 주고, 날짜를 넘겨(오늘 ~ 14일 후) 다른 날 슬롯도 볼 수 있다.
 * 백엔드 추가 없이 기존 API만 쓴다.
 *   - GET  /api/parties/list?date=        (partyApi.getList)         그날 팟 목록 (joined = 내가 참여한 팟, FINISHED 제외)
 *   - GET  /api/parties/my/today          (partyApi.getMyToday)      내가 참여 중인 팟 (가까운 2개, 오늘 끝난 팟 포함)
 *   - GET  /api/parties/{id}              (partyApi.getDetail)       위 2개에 없는 먼 날짜 팟의 장소·확정 정보
 *   - GET  /api/parties/my/thumbs-pending (partyApi.getThumbsPending) 오늘 끝난 팟에 따봉을 남길 수 있는지
 *   - POST /api/parties/{id}/members/me/confirm, /thumbs
 *
 * 슬롯 상태
 *   - 찬 슬롯: 시간·식당·장소·인원 + 진행 막대(참여 → 일정 확정 → 참석 확정 → 식사 → 따봉) + 지금 할 일 버튼
 *       일정 미정 → 방장이 정하는 중 / 일정 확정·내 확정 전 → [참석 확정] / 확정 완료 → 만날 곳
 *       식사 완료 → 따봉 대상이면 [따봉], 아니면 '식사 완료'
 *   - 빈 슬롯: 누르면 식사팟 목록(그 날짜·끼니 탭)으로 이동. 모집 중인 팟 개수를 함께 보여 준다
 *   - 지난 끼니의 빈 슬롯(오늘 점심 14:00 이후 등): 흐리게, 누를 수 없음
 *
 * 처음 날짜: 오늘. 단, 오늘 식사 시간이 다 끝났고(20:00 이후) 오늘 팟이 없으면 내일.
 *
 * HTML:  <section id="today-meal" class="today-meal" aria-labelledby="today-meal-title"></section>
 * 필요:  client.js, utils.js, party.js
 * 이벤트: 따봉을 남기면 document에 'sseukbap:thumbs-added' (detail.source = 'today-meal')를 보낸다.
 *         'sseukbap:thumbs-added'(다른 곳), 'sseukbap:party-joined'(오늘의 식사팟에서 참여)를 받으면 다시 그린다.
 */
(function (global) {
  'use strict';

  const esc = utils.escapeHtml;
  const MEAL_LABELS = partyApi.MEAL_TYPE_LABELS;
  const STATUS_LABELS = partyApi.STATUS_LABELS;
  const MEALS = ['LUNCH', 'DINNER'];
  const JOINABLE = ['RECRUITING', 'SCHEDULED'];
  const CANCEL_BEFORE_MINUTES = 30;

  // 끼니가 끝나는 시각 (마지막 선택 가능 시간 + 30분). 지나면 그 끼니는 '지난 끼니'
  const MEAL_END = { LUNCH: '14:00', DINNER: '20:00' };

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
    prev:
      '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M15 5l-7 7 7 7" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/></svg>',
    next:
      '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M9 5l7 7-7 7" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/></svg>',
  };

  let root = null;
  let slotsEl = null;
  let date = null;          // 지금 보고 있는 날짜 'YYYY-MM-DD'
  let renderSeq = 0;        // 날짜를 빠르게 넘길 때 늦게 온 응답이 덮어쓰지 않게

  async function mount(element) {
    root = element;
    if (!root) return;
    root.innerHTML =
      '<div class="tm-head">' +
      '<h2 id="today-meal-title" class="tm-head__title">내 식사</h2>' +
      // [오늘로]는 화살표 왼쪽에 둔다. 나타나도 화살표 자리는 그대로 (날짜 글자 폭도 고정)
      '<button type="button" class="tm-head__today" data-action="go-today" hidden>오늘로</button>' +
      '<div class="tm-pager" role="group" aria-label="날짜 넘기기">' +
      '<button type="button" class="tm-pager__btn" data-action="prev-date" aria-label="이전 날짜">' + ICONS.prev + '</button>' +
      '<span class="tm-pager__label" aria-live="polite"></span>' +
      '<button type="button" class="tm-pager__btn" data-action="next-date" aria-label="다음 날짜">' + ICONS.next + '</button>' +
      '</div>' +
      '</div>' +
      '<div class="tm-slots"></div>';
    slotsEl = root.querySelector('.tm-slots');
    root.addEventListener('click', onClick);
    document.addEventListener('sseukbap:thumbs-added', (event) => {
      if (!event.detail || event.detail.source !== 'today-meal') render();
    });
    document.addEventListener('sseukbap:party-joined', () => render());

    date = await initialDate();
    render();
  }

  /** 처음 볼 날짜: 오늘 식사 시간이 다 끝났고 오늘 내 팟이 없으면 내일 */
  async function initialDate() {
    const today = partyApi.today();
    if (!isPastMeal(today, 'DINNER')) return today;
    try {
      const mine = await partyApi.getMyToday();
      if (mine.some((p) => !p.partyDate || partyApi.isToday(p.partyDate))) return today;
    } catch (err) {
      return today;
    }
    return partyApi.addDays(today, 1);
  }

  /* ===================== 그리기 ===================== */

  async function render() {
    const seq = ++renderSeq;
    renderPager();
    if (!slotsEl.innerHTML) slotsEl.innerHTML = loadingHtml();
    slotsEl.setAttribute('aria-busy', 'true');

    let slots;
    try {
      slots = await loadSlots(date);
    } catch (err) {
      if (err.statusCode === 401 || seq !== renderSeq) return;
      slotsEl.removeAttribute('aria-busy');
      slotsEl.innerHTML =
        '<div class="tm-error" role="alert"><span>' + esc(err.message || '식사 정보를 불러오지 못했어요.') + '</span>' +
        '<button type="button" class="btn" data-action="retry">다시 시도</button></div>';
      return;
    }
    if (seq !== renderSeq) return;

    slotsEl.removeAttribute('aria-busy');
    slotsEl.innerHTML = MEALS.map((meal) => slotHtml(meal, slots[meal])).join('');
  }

  function renderPager() {
    const today = partyApi.today();
    const last = partyApi.addDays(today, partyApi.MAX_DAYS_AHEAD);
    const label = partyApi.isToday(date) ? '오늘 · ' + partyApi.fullDateLabel(date) : partyApi.relativeDateLabel(date);
    root.querySelector('.tm-pager__label').textContent = label;
    root.querySelector('[data-action="prev-date"]').disabled = date <= today;
    root.querySelector('[data-action="next-date"]').disabled = date >= last;
    root.querySelector('[data-action="go-today"]').hidden = date === today;
  }

  /**
   * 그날 점심·저녁 슬롯 정보.
   * @returns {Promise<{LUNCH: Slot, DINNER: Slot}>}  Slot = { party, pending, recruitingCount }
   *   party: 내가 참여한 팟(없으면 null), pending: 따봉 대상이면 true
   */
  async function loadSlots(day) {
    const isToday = partyApi.isToday(day);
    const [list, mine] = await Promise.all([
      partyApi.getList({ date: day }),
      partyApi.getMyToday().catch(() => []),   // 부가 정보라 실패해도 목록만으로 그린다
    ]);
    const parties = list.parties || [];
    const mineOfDay = mine.filter((p) => (p.partyDate ? p.partyDate === day : isToday));

    const result = {};
    for (const meal of MEALS) {
      const joined = parties.find((p) => p.joined && p.mealType === meal);
      const recruitingCount = parties.filter((p) => p.mealType === meal && !p.joined && JOINABLE.includes(p.status)).length;
      let party = null;
      if (joined) {
        party = mineOfDay.find((p) => Number(p.partyId) === Number(joined.partyId)) || await detailAsSlot(joined, day);
      } else {
        // 목록에는 식사 완료(FINISHED) 팟이 없어서, 오늘 끝난 팟은 my/today에서 찾는다
        party = mineOfDay.find((p) => p.mealType === meal && p.status === 'FINISHED') || null;
      }
      result[meal] = { party, pending: false, recruitingCount };
    }

    // 끝난 팟이 있으면 따봉을 남길 수 있는지 확인 (따봉 요청 대상은 최근 1건)
    const finished = MEALS.map((m) => result[m].party).filter((p) => p && p.status === 'FINISHED');
    if (finished.length) {
      const pending = await partyApi.getThumbsPending().catch(() => null);
      MEALS.forEach((m) => {
        const p = result[m].party;
        if (p && pending && Number(p.partyId) === Number(pending.partyId)) result[m].pending = true;
      });
    }
    return result;
  }

  /** my/today(가까운 2개)에 없는 팟: 상세 조회로 장소·확정 정보를 채운다. 실패하면 목록 정보만 */
  async function detailAsSlot(item, day) {
    try {
      const d = await partyApi.getDetail(item.partyId);
      return {
        partyId: d.partyId,
        restaurantName: d.restaurantName,
        partyDate: d.partyDate,
        mealType: d.mealType,
        time: (d.schedule && d.schedule.confirmedTime) || d.mealTime,
        status: d.status,
        memberCount: d.memberCount,
        confirmedCount: d.confirmedCount,
        myConfirmed: d.myConfirmed,
        meetPlace: d.schedule ? d.schedule.meetPlace : null,
      };
    } catch (err) {
      return Object.assign({ partyDate: day, confirmedCount: 0, myConfirmed: false, meetPlace: null }, item);
    }
  }

  /* ===================== 슬롯 ===================== */

  function slotHtml(meal, slot) {
    const p = slot.party;
    if (!p) return isPastMeal(date, meal) ? pastSlotHtml(meal) : emptySlotHtml(meal, slot.recruitingCount);
    if (p.status === 'FINISHED') return finishedSlotHtml(p, slot.pending);
    if (!p.meetPlace) return waitingSlotHtml(p);
    if (!p.myConfirmed) return needConfirmSlotHtml(p);
    return confirmedSlotHtml(p);
  }

  // 빈 슬롯: 누르면 그 날짜·끼니의 식사팟 목록
  function emptySlotHtml(meal, count) {
    const label = MEAL_LABELS[meal];
    const href = partyApi.LIST_URL + '?date=' + encodeURIComponent(date) + '&mealType=' + meal;
    return (
      '<a class="tm-slot tm-slot--empty" href="' + href + '">' +
      '<span class="tm-slot__meal">' + label + '</span>' +
      '<span class="tm-slot__plus">' + ICONS.plus + '</span>' +
      '<strong class="tm-slot__title">' + label + ' 자리가 비어 있어요</strong>' +
      '<span class="tm-slot__desc">' + (count > 0
        ? '같이 먹을 팟이 ' + count + '개 모집 중이에요'
        : '아직 모집 중인 팟이 없어요. 먼저 열어 보세요') + '</span>' +
      '<span class="tm-slot__cta">' + (count > 0 ? '모집 중인 팟 보기' : '식사팟 보러 가기') + ' <span aria-hidden="true">&gt;</span></span>' +
      '</a>'
    );
  }

  // 지난 끼니의 빈 슬롯: 흐리게, 누를 수 없음
  function pastSlotHtml(meal) {
    const label = MEAL_LABELS[meal];
    return (
      '<div class="tm-slot tm-slot--empty tm-slot--past" aria-disabled="true">' +
      '<span class="tm-slot__meal">' + label + '</span>' +
      '<strong class="tm-slot__title">' + label + ' 시간이 지났어요</strong>' +
      '</div>'
    );
  }

  // 참여 중, 일정 미정
  function waitingSlotHtml(p) {
    return partySlotHtml(p, {
      step: 1,
      stepLabel: '일정 확정 대기',
      timeTone: 'plain',
      timeSub: '예정',
      facts: [factHtml('pin', '장소 정하는 중', true), factHtml('people', '참여 ' + p.memberCount + '명')],
      note: '방장이 시간과 장소를 정하고 있어요',
      actions: detailLink(p, false),
    });
  }

  // 일정 확정, 내 확정 전
  function needConfirmSlotHtml(p) {
    const deadline = minusMinutes(p.time, CANCEL_BEFORE_MINUTES);
    return partySlotHtml(p, {
      step: 2,
      timeTone: 'primary',
      timeSub: timeSub(p),
      facts: [factHtml('pin', esc(p.meetPlace)), factHtml('people', '참석 확정 ' + p.confirmedCount + ' / ' + p.memberCount + '명')],
      note: '갈 수 있으면 참석 확정을 눌러 주세요',
      noteTone: 'warning',
      hint: '확정해야 식사 후 따봉을 남길 수 있어요.' + (deadline ? ' 확정 취소는 ' + deadline + '까지 가능해요.' : ''),
      actions: detailLink(p, false) +
        '<button type="button" class="btn btn--primary" data-action="confirm" data-party-id="' + p.partyId + '">참석 확정</button>',
    });
  }

  // 참석 확정 완료
  function confirmedSlotHtml(p) {
    return partySlotHtml(p, {
      step: 3,
      timeTone: 'primary',
      timeSub: timeSub(p),
      facts: [factHtml('pin', esc(p.meetPlace) + '에서 만나요'), factHtml('people', '참석 확정 ' + p.confirmedCount + ' / ' + p.memberCount + '명')],
      actions: detailLink(p, true),
    });
  }

  // 식사 완료: 따봉 대상이면 [따봉], 아니면 완료 표시만
  function finishedSlotHtml(p, pending) {
    return partySlotHtml(p, {
      step: pending ? 4 : 5,
      timeTone: 'accent',
      timeSub: '식사 완료',
      facts: [factHtml('people', (p.confirmedCount || p.memberCount) + '명이 함께 먹었어요')],
      note: pending ? '식사는 어땠나요?' : '',
      noteTone: 'accent',
      actions: pending
        ? '<button type="button" class="btn tm-btn--accent" data-action="thumbs" data-party-id="' + p.partyId + '">' +
          utils.thumbsUpIcon(16) + '따봉</button>'
        : detailLink(p, false),
    });
  }

  function partySlotHtml(p, o) {
    return (
      '<article class="tm-slot tm-slot--filled">' +
      '<header class="tm-slot__head">' +
      '<span class="tm-slot__meal">' + esc(MEAL_LABELS[p.mealType]) + '</span>' +
      (STATUS_LABELS[p.status]
        ? '<span class="badge badge--' + p.status.toLowerCase() + '">' + esc(STATUS_LABELS[p.status]) + '</span>'
        : '') +
      '</header>' +
      '<div class="tm-body">' +
      '<div class="tm-time tm-time--' + o.timeTone + '">' +
      '<strong class="tm-time__clock">' + esc(p.time) + '</strong>' +
      '<span class="tm-time__sub">' + esc(o.timeSub) + '</span>' +
      '</div>' +
      '<div class="tm-main">' +
      '<a class="tm-main__name" href="' + partyApi.detailUrl(p.partyId) + '">' + esc(p.restaurantName) + '</a>' +
      '<ul class="tm-facts">' + o.facts.join('') + '</ul>' +
      '</div>' +
      '</div>' +
      (o.hint ? '<p class="tm-hint">' + esc(o.hint) + '</p>' : '') +
      '<p class="tm-message" role="alert"></p>' +
      '<div class="tm-cta">' +
      (o.note ? '<p class="tm-cta__note' + (o.noteTone ? ' tm-cta__note--' + o.noteTone : '') + '">' + esc(o.note) + '</p>' : '') +
      '<div class="tm-cta__actions">' + o.actions + '</div>' +
      '</div>' +
      stepsHtml(o.step, o.stepLabel) +
      '</article>'
    );
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

  // 오늘이면 '1시간 20분 후', 다른 날이면 '확정'
  function timeSub(p) {
    return partyApi.isToday(p.partyDate || date) ? untilText(p.time) : '확정';
  }

  function loadingHtml() {
    return '<div class="tm-loading"><span class="spinner" aria-hidden="true"></span>' +
      '<span class="text-muted">식사 정보를 불러오는 중…</span></div>';
  }

  /* ===================== 버튼 ===================== */

  async function onClick(event) {
    const button = event.target.closest('[data-action]');
    if (!button || !root.contains(button)) return;
    const action = button.dataset.action;

    if (action === 'prev-date' || action === 'next-date' || action === 'go-today') {
      date = action === 'go-today' ? partyApi.today() : partyApi.addDays(date, action === 'prev-date' ? -1 : 1);
      render();
      return;
    }
    if (action === 'retry') {
      slotsEl.innerHTML = '';
      render();
      return;
    }

    const partyId = button.dataset.partyId;
    const slot = button.closest('.tm-slot');
    const message = slot && slot.querySelector('.tm-message');
    button.disabled = true;
    if (message) message.textContent = '';

    try {
      if (action === 'confirm') {
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
      const refresh = ['ALREADY_CONFIRMED', 'ALREADY_THUMBED', 'PARTY_FINISHED', 'PARTY_DELETED', 'PARTY_NOT_FOUND'];
      if (refresh.includes(err.error)) {
        render();
        return;
      }
      button.disabled = false;
      if (message) message.textContent = err.message;
    }
  }

  function showThumbsDone(button, result) {
    const slot = button.closest('.tm-slot');
    const note = slot.querySelector('.tm-cta__note');
    if (note) note.textContent = '따봉을 남겼어요';
    button.outerHTML = '<span class="tm-done">' + utils.thumbsUpIcon(16) + '최근 30일 따봉 ' + Number(result.thumbsCount30d) + '개</span>';
    slot.querySelectorAll('.tm-step').forEach((step) => {
      step.className = 'tm-step tm-step--done';
      step.removeAttribute('aria-current');
    });
  }

  /* ===================== 시간 ===================== */

  function nowHHMM() {
    const now = new Date();
    return String(now.getHours()).padStart(2, '0') + ':' + String(now.getMinutes()).padStart(2, '0');
  }

  /** 그 날짜의 그 끼니가 이미 끝났는지 (오늘만 해당, 지난 날짜는 볼 수 없음) */
  function isPastMeal(day, meal) {
    return partyApi.isToday(day) && nowHHMM() >= MEAL_END[meal];
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
