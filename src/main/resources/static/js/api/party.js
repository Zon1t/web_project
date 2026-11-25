/*
 * party.js — 식사팟 API 호출 (A)
 *
 * 화면 JS에서는 이렇게 쓴다.
 *   const detail = await partyApi.getDetail(31);
 *   await partyApi.join(31);
 *
 * 모든 함수는 서버 응답의 data만 돌려준다. 오류면 ApiError(err.error, err.message)를 던진다. (client.js)
 * 백엔드가 모두 완성되어 목업 없이 실제 서버를 부른다.
 *
 * 화면 주소 규칙 (B·C와 맞춤)
 *   목록 /party-list.html · 생성 /party-create.html?restaurantId=12 · 상세 /party-detail.html?partyId=31
 *   비공개 식사팟 초대 링크: /party-detail.html?partyId=31&code=초대코드 (2026-11-25 T4)
 */
(function (global) {
  'use strict';

  const enc = encodeURIComponent;
  const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

  /** 'YYYY-MM-DD' → Date (그 날 0시, 브라우저 시간대) */
  function parseIsoDate(isoDate) {
    const [y, m, d] = String(isoDate).split('-').map(Number);
    return new Date(y, m - 1, d);
  }

  /** Date → 'YYYY-MM-DD' */
  function toIsoDate(date) {
    const pad = (n) => String(n).padStart(2, '0');
    return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate());
  }

  /** 오늘과 며칠 차이인지 (내일 = 1, 어제 = -1) */
  function daysFromToday(isoDate) {
    const now = new Date();
    const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    return Math.round((parseIsoDate(isoDate) - today) / 86400000);
  }

  global.partyApi = {
    /** 상태 코드 → 화면 표시 이름 */
    STATUS_LABELS: Object.freeze({
      RECRUITING: '모집중',
      SCHEDULED: '일정 확정',
      FULL: '정원 마감',
      CLOSED: '모집 마감',
      FINISHED: '식사 완료',
    }),

    /** 끼니 코드 → 화면 표시 이름 */
    MEAL_TYPE_LABELS: Object.freeze({
      LUNCH: '점심',
      DINNER: '저녁',
    }),

    /** 끼니별 선택 가능 시간 (30분 단위) — 서버 규칙과 같다 */
    MEAL_TIME_OPTIONS: Object.freeze({
      LUNCH: Object.freeze(['11:00', '11:30', '12:00', '12:30', '13:00', '13:30']),
      DINNER: Object.freeze(['17:00', '17:30', '18:00', '18:30', '19:00', '19:30']),
    }),

    /** 식사팟을 만들고 볼 수 있는 날짜: 오늘 ~ 14일 후 (서버 규칙과 같다, 11/20 변경) */
    MAX_DAYS_AHEAD: 14,

    /** 화면 주소 */
    detailUrl(partyId) {
      return '/party-detail.html?partyId=' + enc(partyId);
    },
    /** 비공개 식사팟 자물쇠 아이콘 (SVG 문자열, 색은 currentColor) */
    lockIcon(size) {
      const s = size || 14;
      return '<svg viewBox="0 0 24 24" width="' + s + '" height="' + s + '" aria-hidden="true" focusable="false"><rect x="5" y="11" width="14" height="10" rx="2" fill="none" stroke="currentColor" stroke-width="2"/><path d="M8 11V8a4 4 0 0 1 8 0v3" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>';
    },
    /** 만들기 화면. restaurantId(식당 고정)·date(날짜 미리 선택)는 둘 다 선택 */
    createUrl(restaurantId, date) {
      const params = [];
      if (restaurantId) params.push('restaurantId=' + enc(restaurantId));
      if (date) params.push('date=' + enc(date));
      return '/party-create.html' + (params.length ? '?' + params.join('&') : '');
    },
    LIST_URL: '/party-list.html',

    /* ---------- 날짜 도우미 (모든 날짜는 'YYYY-MM-DD' 문자열) ---------- */

    /** 오늘 → '2026-11-20' (브라우저 시각 기준) */
    today() {
      return toIsoDate(new Date());
    },

    /** 'YYYY-MM-DD'에서 days일 뒤 */
    addDays(isoDate, days) {
      const d = parseIsoDate(isoDate);
      d.setDate(d.getDate() + days);
      return toIsoDate(d);
    },

    /** 오늘 ~ 14일 후 날짜 목록 (날짜 선택 칩용) */
    selectableDates() {
      const today = this.today();
      const dates = [];
      for (let i = 0; i <= this.MAX_DAYS_AHEAD; i++) dates.push(this.addDays(today, i));
      return dates;
    },

    /** 고를 수 있는 날짜인지 (오늘 ~ 14일 후) */
    isSelectableDate(isoDate) {
      return /^\d{4}-\d{2}-\d{2}$/.test(isoDate || '') && this.selectableDates().includes(isoDate);
    },

    /** 짧은 이름: 오늘 / 내일 / 11.22 (토) — 날짜 칩용 */
    shortDateLabel(isoDate) {
      const diff = daysFromToday(isoDate);
      if (diff === 0) return '오늘';
      if (diff === 1) return '내일';
      const d = parseIsoDate(isoDate);
      return (d.getMonth() + 1) + '.' + d.getDate() + ' (' + WEEKDAYS[d.getDay()] + ')';
    },

    /** 긴 이름: 11월 22일 (토) */
    fullDateLabel(isoDate) {
      const d = parseIsoDate(isoDate);
      return (d.getMonth() + 1) + '월 ' + d.getDate() + '일 (' + WEEKDAYS[d.getDay()] + ')';
    },

    /** 오늘이면 '오늘', 내일이면 '내일 · 11월 21일 (금)', 그 외 '11월 22일 (토)' */
    relativeDateLabel(isoDate) {
      const diff = daysFromToday(isoDate);
      if (diff === 0) return '오늘';
      if (diff === 1) return '내일 · ' + this.fullDateLabel(isoDate);
      return this.fullDateLabel(isoDate);
    },

    /** 오늘 팟인지 */
    isToday(isoDate) {
      return daysFromToday(isoDate) === 0;
    },

    /* ---------- 생성·조회·삭제 ---------- */

    /**
     * 식사팟 생성 — POST /api/parties/create
     * @param {{restaurantId:number, partyDate?:string, mealType:string, mealTime:string, capacity:number, memo?:string}} body
     *        partyDate는 'YYYY-MM-DD', 오늘 ~ 14일 후. 없으면 오늘
     * @returns {Promise<{partyId:number}>}
     * 오류: 400 INVALID_REQUEST·INVALID_MEAL_TIME, 404 RESTAURANT_NOT_FOUND
     */
    create(body) {
      return api.post('/parties/create', body);
    },

    /**
     * 식사팟 목록 — GET /api/parties/list
     * @param {{date?:string, mealType?:string, status?:string, excludeJoined?:boolean, limit?:number}} [condition]
     *        date는 'YYYY-MM-DD', 없으면 오늘
     * @returns {Promise<{lunchCount:number, dinnerCount:number, parties:Array}>}
     */
    getList(condition) {
      return api.get('/parties/list', condition);
    },

    /** 내가 참여 중인 식사팟 (오늘 ~ 14일 후 중 가까운 2개, partyDate 포함) — GET /api/parties/my/today */
    getMyToday() {
      return api.get('/parties/my/today');
    },

    /** 따봉 요청 대상 (없으면 null) — GET /api/parties/my/thumbs-pending */
    getThumbsPending() {
      return api.get('/parties/my/thumbs-pending');
    },

    /**
     * 내가 할 일이 있는 식사팟 (헤더 종 알림, 없으면 []) — GET /api/parties/my/action-pending
     * @returns {Promise<Array<{partyId, action: 'SET_SCHEDULE'|'CONFIRM', restaurantName, partyDate, mealType, time, meetPlace}>>}
     *   SET_SCHEDULE: 방장인데 아직 일정을 안 정함 / CONFIRM: 일정이 정해졌는데 내가 참석 확정을 안 함
     */
    getActionPending() {
      return api.get('/parties/my/action-pending');
    },

    /**
     * 식사팟 상세 — GET /api/parties/{partyId}
     * @returns {Promise<Object>} {partyId, restaurantId, restaurantName, category, partyDate, mealType, mealTime,
     *   capacity, memberCount, confirmedCount, status, memo, schedule, cancelDeadline, members, myRole, myConfirmed}
     * 오류: 404 PARTY_NOT_FOUND·PARTY_DELETED
     */
    getDetail(partyId, inviteCode) {
      // 비공개 식사팟은 초대 링크의 code를 함께 보낸다. 없거나 틀리면 404 PARTY_NOT_FOUND (11/25)
      return api.get('/parties/' + enc(partyId), { code: inviteCode || undefined });
    },

    /** 식사팟 삭제 (방장) — DELETE /api/parties/{partyId} */
    remove(partyId) {
      return api.delete('/parties/' + enc(partyId));
    },

    /** 초대 링크 — GET /api/parties/{partyId}/invite-link → {text, path} */
    getInviteLink(partyId) {
      return api.get('/parties/' + enc(partyId) + '/invite-link');
    },

    /** 비공개 식사팟 초대 코드 새로 만들기 (방장) — POST /api/parties/{partyId}/invite-code → {path} (11/25) */
    regenerateInviteCode(partyId) {
      return api.post('/parties/' + enc(partyId) + '/invite-code');
    },

    /* ---------- 참여 ---------- */

    /** 참여하기(1차) — POST /api/parties/{partyId}/members → {partyId, memberCount, status} */
    join(partyId, inviteCode) {
      // 비공개 식사팟은 초대 코드를 Body로 보낸다 (11/25). 공개 식사팟은 Body 없음
      return inviteCode
        ? api.post('/parties/' + enc(partyId) + '/members', { inviteCode })
        : api.post('/parties/' + enc(partyId) + '/members');
    },

    /** 나가기 — DELETE /api/parties/{partyId}/members/me → {partyDeleted} (방장이면 팟 삭제) */
    leave(partyId) {
      return api.delete('/parties/' + enc(partyId) + '/members/me');
    },

    /** 참여 확정(2차) — POST /api/parties/{partyId}/members/me/confirm → {confirmed, confirmedCount} */
    confirm(partyId) {
      return api.post('/parties/' + enc(partyId) + '/members/me/confirm');
    },

    /** 참여 확정 취소 — DELETE /api/parties/{partyId}/members/me/confirm → {confirmed, confirmedCount} */
    cancelConfirm(partyId) {
      return api.delete('/parties/' + enc(partyId) + '/members/me/confirm');
    },

    /* ---------- 방장 ---------- */

    /**
     * 일정 확정·수정 — PATCH /api/parties/{partyId}/schedule
     * @param {{confirmedTime:string, meetPlace:string, confirmMemo?:string}} body
     * @returns {Promise<{status:string, confirmationsReset:boolean}>}
     */
    updateSchedule(partyId, body) {
      return api.patch('/parties/' + enc(partyId) + '/schedule', body);
    },

    /** 모집 마감 — PATCH /api/parties/{partyId}/status {action: CLOSE} → {status} */
    close(partyId) {
      return api.patch('/parties/' + enc(partyId) + '/status', { action: 'CLOSE' });
    },

    /** 모집 재개 — PATCH /api/parties/{partyId}/status {action: REOPEN} → {status} */
    reopen(partyId) {
      return api.patch('/parties/' + enc(partyId) + '/status', { action: 'REOPEN' });
    },

    /* ---------- 대화 ---------- */

    /**
     * 대화 조회(폴링) — GET /api/parties/{partyId}/chats?afterId=
     * @param {number} [afterId] 이 값보다 새 메시지만. 없으면 전체
     * @returns {Promise<{readOnly:boolean, chats:Array<{chatId, type, nickname, content, mine, createdAt}>}>}
     * 오류: 403 NOT_JOINED, 404 PARTY_DELETED(폴링 중 삭제 → 폴링 중단)
     */
    getChats(partyId, afterId) {
      return api.get('/parties/' + enc(partyId) + '/chats', afterId ? { afterId } : undefined);
    },

    /** 대화 전송 — POST /api/parties/{partyId}/chats {content} → {chatId, createdAt} */
    sendChat(partyId, content) {
      return api.post('/parties/' + enc(partyId) + '/chats', { content });
    },

    /* ---------- 따봉 ---------- */

    /** 따봉 등록 — POST /api/parties/{partyId}/thumbs → {restaurantId, thumbsCount30d} */
    thumbs(partyId) {
      return api.post('/parties/' + enc(partyId) + '/thumbs');
    },

    /* ---------- [식사팟 만들기] 버튼 막기 (2026-11-25) ---------- */

    /**
     * 지금 끼니: 오늘 점심(13:30 전) → 오늘 저녁(19:30 전) → 내일 점심
     * @returns {{date: string, mealType: 'LUNCH'|'DINNER'}}
     */
    currentMealSlot(now) {
      const d = now || new Date();
      const minutes = d.getHours() * 60 + d.getMinutes();
      const today = toIsoDate(d);
      if (minutes < 13 * 60 + 30) return { date: today, mealType: 'LUNCH' };
      if (minutes < 19 * 60 + 30) return { date: today, mealType: 'DINNER' };
      return { date: this.addDays(today, 1), mealType: 'LUNCH' };
    },

    /** 지금 끼니에 내가 참여 중인 식사팟 (없으면 null) */
    async getCurrentMealParty() {
      const slot = this.currentMealSlot();
      const mine = await this.getMyToday();
      return (mine || []).find((p) => p.partyDate === slot.date && p.mealType === slot.mealType) || null;
    },

    /**
     * 만들기 버튼을 막는다: 흐리게 + 누르면 이유 안내 + 마우스를 올리면 툴팁(ui.js data-tip)
     * reason이 없으면 다시 풀어 준다.
     */
    setCreateBlocked(el, reason) {
      if (!el) return;
      el.classList.toggle('is-create-blocked', !!reason);
      if (reason) {
        el.setAttribute('aria-disabled', 'true');
        el.setAttribute('data-tip', reason);
        el.dataset.blockedReason = reason;
      } else {
        el.removeAttribute('aria-disabled');
        el.removeAttribute('data-tip');
        delete el.dataset.blockedReason;
      }
    },

    /**
     * data-create-party 가 붙은 [식사팟 만들기] 버튼을 지금 끼니 기준으로 막는다.
     * 지금 끼니에 이미 참여 중인 식사팟이 있으면 막는다. (같은 끼니에는 팟 하나, 서버도 409로 막음)
     */
    async guardCreateButtons(root) {
      const buttons = Array.from((root || document).querySelectorAll('[data-create-party]'));
      if (buttons.length === 0) return;
      try {
        const party = await this.getCurrentMealParty();
        const reason = party
          ? '이미 ' + this.shortDateLabel(party.partyDate) + ' ' + this.MEAL_TYPE_LABELS[party.mealType] + ' 식사팟(' +
            party.restaurantName + ')에 참여 중이라 새로 만들 수 없어요.'
          : null;
        buttons.forEach((el) => this.setCreateBlocked(el, reason));
      } catch (err) {
        // 확인을 못 하면 그대로 둔다 (만들기 화면과 서버가 다시 막는다)
      }
    },
  };

  // 막힌 [식사팟 만들기]를 누르면 이동하지 않고 이유를 알려 준다
  document.addEventListener('click', (event) => {
    const el = event.target.closest('.is-create-blocked');
    if (!el) return;
    event.preventDefault();
    event.stopPropagation();
    if (global.ui && global.ui.toast) global.ui.toast(el.dataset.blockedReason || '지금은 식사팟을 만들 수 없어요.', { type: 'error' });
  }, true);
})(window);
