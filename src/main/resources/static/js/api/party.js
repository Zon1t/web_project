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
 */
(function (global) {
  'use strict';

  const enc = encodeURIComponent;

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

    /** 화면 주소 */
    detailUrl(partyId) {
      return '/party-detail.html?partyId=' + enc(partyId);
    },
    createUrl(restaurantId) {
      return '/party-create.html' + (restaurantId ? '?restaurantId=' + enc(restaurantId) : '');
    },
    LIST_URL: '/party-list.html',

    /* ---------- 생성·조회·삭제 ---------- */

    /**
     * 식사팟 생성 — POST /api/parties/create
     * @param {{restaurantId:number, mealType:string, mealTime:string, capacity:number, memo?:string}} body
     * @returns {Promise<{partyId:number}>}
     * 오류: 400 INVALID_REQUEST·INVALID_MEAL_TIME, 404 RESTAURANT_NOT_FOUND
     */
    create(body) {
      return api.post('/parties/create', body);
    },

    /**
     * 식사팟 목록 — GET /api/parties/list
     * @param {{mealType?:string, status?:string, excludeJoined?:boolean, limit?:number}} [condition]
     * @returns {Promise<{lunchCount:number, dinnerCount:number, parties:Array}>}
     */
    getList(condition) {
      return api.get('/parties/list', condition);
    },

    /** 내가 참여 중인 오늘 식사팟 (최대 2개) — GET /api/parties/my/today */
    getMyToday() {
      return api.get('/parties/my/today');
    },

    /** 따봉 요청 대상 (없으면 null) — GET /api/parties/my/thumbs-pending */
    getThumbsPending() {
      return api.get('/parties/my/thumbs-pending');
    },

    /**
     * 식사팟 상세 — GET /api/parties/{partyId}
     * @returns {Promise<Object>} {partyId, restaurantId, restaurantName, category, partyDate, mealType, mealTime,
     *   capacity, memberCount, confirmedCount, status, memo, schedule, cancelDeadline, members, myRole, myConfirmed}
     * 오류: 404 PARTY_NOT_FOUND·PARTY_DELETED
     */
    getDetail(partyId) {
      return api.get('/parties/' + enc(partyId));
    },

    /** 식사팟 삭제 (방장) — DELETE /api/parties/{partyId} */
    remove(partyId) {
      return api.delete('/parties/' + enc(partyId));
    },

    /** 초대 링크 — GET /api/parties/{partyId}/invite-link → {text, path} */
    getInviteLink(partyId) {
      return api.get('/parties/' + enc(partyId) + '/invite-link');
    },

    /* ---------- 참여 ---------- */

    /** 참여하기(1차) — POST /api/parties/{partyId}/members → {partyId, memberCount, status} */
    join(partyId) {
      return api.post('/parties/' + enc(partyId) + '/members');
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
  };
})(window);
