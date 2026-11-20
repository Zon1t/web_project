/*
 * party-detail.js — S-12 식사팟 상세 (A)
 *
 * 주소: /party-detail.html?partyId=31
 *
 * 만든 것
 *   - 제목·상태 4단계, 일정 정보, 참여자 목록
 *   - 내 버튼: 참여하기 / 참석 확정 / 확정 취소 / 나가기 / 초대 링크 복사 (내 역할·상태에 따라)
 *   - 내 진행: ① 팟 참여 → ② 일정 확정(방장) → ③ 내 참석 확정, 할 일이 있으면 안내 상자
 *   - 방장 메뉴: 일정 확정·수정(창), 모집 마감·재개, 팟 삭제
 *   - 대화: 참여자만. 처음엔 전체, 이후 3초마다 새 메시지만(afterId 폴링). 식사 완료면 읽기 전용
 *   - 로딩 / 없는 팟 / 삭제된 팟 / 오류 화면
 *
 * 버튼을 누른 뒤에는 상세 API를 다시 불러 화면 전체를 새로 그린다. (명세: "버튼 처리 후 이 API를 다시 호출")
 * 대화에 시스템 메시지(누가 참여·확정·일정 변경 등)가 새로 오면 상세도 다시 불러 참여자·상태를 맞춘다.
 */
(function () {
  'use strict';

  const esc = utils.escapeHtml;
  const STATUS_LABELS = partyApi.STATUS_LABELS;
  const MEAL_TYPE_LABELS = partyApi.MEAL_TYPE_LABELS;
  const CATEGORY_LABELS = restaurantApi.CATEGORY_LABELS;
  const POLL_INTERVAL_MS = 3000;

  const partyId = new URLSearchParams(location.search).get('partyId');

  const root = document.getElementById('party-root');
  const chatRoot = document.getElementById('chat-root');
  const chatBody = document.getElementById('chat-body');
  const chatForm = document.getElementById('chat-form');
  const chatInput = document.getElementById('chat-input');
  const chatSubmit = chatForm.querySelector('button[type="submit"]');
  const chatMessage = document.getElementById('chat-message');

  const dialog = document.getElementById('schedule-dialog');
  const scheduleForm = document.getElementById('schedule-form');
  const scheduleTitle = document.getElementById('schedule-title');
  const scheduleNotice = document.getElementById('schedule-notice');
  const scheduleTime = document.getElementById('schedule-time');
  const schedulePlace = document.getElementById('schedule-place');
  const scheduleMemo = document.getElementById('schedule-memo');
  const scheduleMessage = document.getElementById('schedule-message');
  const scheduleSubmit = document.getElementById('schedule-submit');

  // 화면 상태
  const state = {
    detail: null,     // 마지막으로 받은 상세 응답
    busy: false,      // 버튼 처리 중이면 다른 버튼 무시
    feedback: null,   // 버튼 아래 안내 문구 {type: 'success'|'error', text}
  };

  // 대화 상태
  const chat = {
    active: false,    // 폴링 중인지 (참여자일 때만)
    lastId: 0,        // 마지막으로 받은 chatId → 다음 요청의 afterId
    timer: null,
    loading: false,
    again: false,     // 요청 중에 또 요청이 들어오면 끝난 뒤 한 번 더
    readOnly: false,
  };

  /* ---------- 시작 ---------- */

  root.addEventListener('click', onRootClick);
  scheduleForm.addEventListener('submit', onScheduleSubmit);
  dialog.addEventListener('click', (event) => {
    if (event.target.closest('[data-action="close-dialog"]')) dialog.close();
  });
  chatForm.addEventListener('submit', onChatSubmit);
  document.addEventListener('visibilitychange', () => {
    if (!document.hidden && chat.active) pollChats(); // 탭으로 돌아오면 바로 새 메시지 확인
  });
  window.addEventListener('pagehide', stopChat); // 화면을 떠나면 폴링 중단

  // partyId가 숫자가 아니면 서버에 묻지 않고 바로 '없는 식사팟' 화면
  if (!/^[1-9]\d*$/.test(partyId || '')) {
    showNotFound();
  } else {
    loadDetail(true);
  }

  /* ---------- 상세 조회 ---------- */

  /** @param {boolean} withSpinner 처음 열 때만 로딩 화면을 보여 준다 (버튼 처리 후 다시 부를 땐 화면 유지) */
  async function loadDetail(withSpinner) {
    if (withSpinner) showLoading();
    try {
      state.detail = await partyApi.getDetail(partyId);
      render();
      syncChat();
    } catch (err) {
      handleLoadError(err, withSpinner);
    }
  }

  function handleLoadError(err, withSpinner) {
    if (err.statusCode === 401) return; // client.js가 로그인 화면으로 이동시킨다
    if (err.error === 'PARTY_NOT_FOUND') return showNotFound();
    if (err.error === 'PARTY_DELETED') return showDeleted();
    if (withSpinner || !state.detail) return showError(err.message);
    setFeedback('error', err.message); // 이미 화면이 있으면 그대로 두고 문구만
  }

  /* ---------- 그리기 ---------- */

  function render() {
    const d = state.detail;
    document.title = d.restaurantName + ' ' + MEAL_TYPE_LABELS[d.mealType] + '팟 | 슥밥';
    root.innerHTML =
      renderHead(d) +
      renderInfo(d) +
      renderActions(d) +
      renderMembers(d) +
      renderHostMenu(d);
  }

  function renderHead(d) {
    const restaurantUrl = '/restaurant-detail.html?id=' + encodeURIComponent(d.restaurantId);
    return (
      '<section class="party-head" aria-labelledby="party-title">' +
      '<div class="party-head__top">' +
      '<h1 id="party-title" class="party-head__title">' +
      '<a href="' + restaurantUrl + '">' + esc(d.restaurantName) + '</a> ' +
      esc(MEAL_TYPE_LABELS[d.mealType]) + '팟' +
      // 오늘이 아닌 팟은 제목 옆에 날짜를 붙여 오늘 팟과 구분한다 (11/20)
      (partyApi.isToday(d.partyDate) ? '' : ' <span class="party-head__when">' + esc(partyApi.shortDateLabel(d.partyDate)) + '</span>') +
      '</h1>' +
      '<span class="text-muted">' + esc(CATEGORY_LABELS[d.category] || '') + '</span>' +
      '</div>' +
      (d.memo ? '<p class="party-head__desc">' + esc(d.memo) + '</p>' : '') +
      renderSteps(d.status) +
      '</section>'
    );
  }

  /** 상태 4단계: 모집중 › 일정 확정 › (정원/모집) 마감 › 식사 완료, 현재 단계 강조 */
  function renderSteps(status) {
    const current = { RECRUITING: 0, SCHEDULED: 1, FULL: 2, CLOSED: 2, FINISHED: 3 }[status];
    const labels = ['모집중', '일정 확정', status === 'FULL' ? '정원 마감' : '모집 마감', '식사 완료'];
    const items = labels.map((label, i) => {
      const cls = i === current ? ' is-current' : i < current ? ' is-done' : '';
      const aria = i === current ? ' aria-current="step"' : '';
      return '<li class="party-steps__item' + cls + '"' + aria + '>' + label + '</li>';
    });
    return (
      '<ol class="party-steps" aria-label="진행 상태: ' + esc(STATUS_LABELS[status]) + '">' +
      items.join('<li class="party-steps__sep" aria-hidden="true">›</li>') +
      '</ol>'
    );
  }

  function renderInfo(d) {
    const schedule = d.schedule;
    const rows = [
      ['날짜', esc(partyApi.isToday(d.partyDate)
        ? '오늘 · ' + partyApi.fullDateLabel(d.partyDate)
        : partyApi.relativeDateLabel(d.partyDate))],
      ['예정 시간', esc(d.mealTime)],
      [
        '확정 일정',
        schedule
          ? '<span class="party-info__schedule">' + esc(schedule.confirmedTime) + ' · ' + esc(schedule.meetPlace) + '</span>'
          : '<span class="text-muted">방장이 아직 일정을 정하지 않았어요</span>',
      ],
    ];
    if (schedule && schedule.confirmMemo) rows.push(['일정 메모', esc(schedule.confirmMemo)]);

    return (
      '<dl class="party-info">' +
      rows.map(([dt, dd]) => '<dt>' + dt + '</dt><dd>' + dd + '</dd>').join('') +
      '</dl>'
    );
  }

  /** 내 버튼: 내 역할(myRole)·상태(status)·확정 여부(myConfirmed)에 따라 다르다 (명세의 버튼 표) */
  function renderActions(d) {
    const isMember = d.myRole !== 'NONE';
    const isJoinable = d.status === 'RECRUITING' || d.status === 'SCHEDULED';
    let body;

    if (d.status === 'FINISHED') {
      body = '<p class="party-actions__done">식사가 끝난 식사팟이에요. 대화는 읽기만 할 수 있어요.</p>';
    } else if (!isMember) {
      body = isJoinable
        ? row(button('join', '참여하기', 'btn--primary')) +
          hint('참여하면 대화방에 들어가요. 방장이 일정을 정하면 참석 확정을 한 번 더 해 주세요.')
        : row(button('', '마감', 'btn--primary', true)) +
          hint(d.status === 'FULL' ? '정원이 가득 찼어요.' : '방장이 모집을 마감했어요.');
    } else {
      const deadlinePassed = new Date() > new Date(d.cancelDeadline);
      const deadlineTime = esc(String(d.cancelDeadline).substring(11, 16));
      const buttons = [];
      const hints = [];

      if (d.schedule && !d.myConfirmed) {
        // 할 일 상자 안에 [참석 확정] 버튼이 있으므로 여기서는 생략
      } else if (d.myConfirmed) {
        buttons.push(button('cancel-confirm', '확정 취소', '', deadlinePassed));
      }

      // 확정자는 기준 시간 30분 전까지만 나갈 수 있다. 방장이 나가면 팟이 삭제된다.
      const leaveLocked = d.myConfirmed && deadlinePassed && d.myRole !== 'HOST';
      buttons.push(button('leave', d.myRole === 'HOST' ? '나가기 (팟 삭제)' : '나가기', '', leaveLocked));

      if (d.myConfirmed) {
        hints.push(deadlinePassed
          ? '식사 30분 전이 지나 확정 취소·나가기를 할 수 없어요.'
          : '확정 취소·나가기는 ' + deadlineTime + '까지 할 수 있어요.');
      }
      if (isJoinable) {
        buttons.push(button('invite', '초대 링크 복사'));
      }
      body = renderMyProgress(d) + renderTodo(d) + row(buttons.join('')) + hints.map(hint).join('');
    }

    return (
      '<section class="party-actions" aria-label="내 참여">' +
      body +
      renderFeedback() +
      '</section>'
    );
  }

  /** 내 진행 3단계: ① 팟 참여 → ② 일정 확정(방장) → ③ 내 참석 확정 */
  function renderMyProgress(d) {
    const done = [true, !!d.schedule, !!d.myConfirmed];
    const labels = ['팟 참여', '일정 확정 <small>방장</small>', '내 참석 확정'];
    const current = done.indexOf(false);
    const items = labels.map((label, i) => {
      const cls = done[i] ? ' is-done' : i === current ? ' is-current' : '';
      const mark = done[i] ? ui.icon('check', 14) : String(i + 1);
      return '<li class="my-progress__item' + cls + '"' + (i === current ? ' aria-current="step"' : '') + '>' +
        '<span class="my-progress__dot">' + mark + '</span>' +
        '<span class="my-progress__label">' + label + '</span></li>';
    });
    return '<ol class="my-progress" aria-label="내 진행 상황">' + items.join('') + '</ol>';
  }

  /** 지금 내가 할 일이 있으면 눈에 띄는 상자로 */
  function renderTodo(d) {
    if (!d.schedule) {
      return '<div class="party-todo party-todo--wait">' + ui.icon('clock', 18) +
        '<p><b>방장이 일정을 정하는 중이에요.</b><br>일정이 정해지면 여기서 참석 확정을 할 수 있어요.</p></div>';
    }
    if (!d.myConfirmed) {
      return '<div class="party-todo">' + ui.icon('calendar', 18) +
        '<p><b>' + esc(d.schedule.confirmedTime) + ' · ' + esc(d.schedule.meetPlace) + '</b>(으)로 정해졌어요.<br>' +
        '갈 수 있으면 참석 확정을 눌러 주세요.</p>' +
        button('confirm', '참석 확정', 'btn--primary') + '</div>';
    }
    return '';
  }

  function renderFeedback() {
    const f = state.feedback;
    const cls = f ? ' party-feedback--' + f.type : '';
    return '<p id="party-feedback" class="party-feedback' + cls + '" role="status">' + (f ? esc(f.text) : '') + '</p>';
  }

  function renderMembers(d) {
    const items = d.members.map((m) => {
      const name = esc(m.nickname) + (m.host ? ' <span class="badge">방장</span>' : '');
      const stateText = m.confirmed
        ? '<span class="party-members__state is-confirmed">' + ui.icon('check', 12) + ' 참석 확정</span>'
        : '<span class="party-members__state">확인 전</span>';
      return '<li class="party-members__item">' +
        '<span class="member-avatar' + (m.confirmed ? ' is-confirmed' : '') + '" aria-hidden="true">' + esc(initialOf(m.nickname)) + '</span>' +
        '<span class="party-members__name">' + name + '</span>' + stateText + '</li>';
    });
    return (
      '<section aria-labelledby="members-title">' +
      '<div class="party-members__head">' +
      '<h2 id="members-title"><b>참여자 ' + d.memberCount + ' / ' + d.capacity + '명</b></h2>' +
      '<span class="text-muted">참석 확정 ' + d.confirmedCount + '명</span>' +
      '</div>' +
      '<ul>' + items.join('') + '</ul>' +
      '</section>'
    );
  }

  function initialOf(name) {
    return String(name || '?').trim().charAt(0) || '?';
  }

  /** 방장 메뉴: 방장에게만, 식사 완료 전까지 */
  function renderHostMenu(d) {
    if (d.myRole !== 'HOST' || d.status === 'FINISHED') return '';
    const statusButton = d.status === 'CLOSED'
      ? button('reopen', '모집 재개')
      : button('close', '모집 마감');
    return (
      '<section class="party-host" aria-labelledby="host-title">' +
      '<h2 id="host-title" class="text-muted">방장 메뉴</h2>' +
      '<div class="party-host__buttons">' +
      button('open-schedule', d.schedule ? '일정 수정' : '일정 정하기', d.schedule ? '' : 'btn--primary') +
      statusButton +
      button('delete', '팟 삭제', 'btn--danger') +
      '</div>' +
      '</section>'
    );
  }

  function button(action, label, extraClass, disabled) {
    return (
      '<button type="button" class="btn ' + (extraClass || '') + '"' +
      (action ? ' data-action="' + action + '"' : '') +
      (disabled ? ' disabled' : '') + '>' + label + '</button>'
    );
  }

  function row(html) {
    return '<div class="party-actions__row">' + html + '</div>';
  }

  function hint(text) {
    return '<p class="party-actions__hint">' + esc(text) + '</p>';
  }

  /* ---------- 상태 화면 ---------- */

  function showLoading() {
    root.innerHTML = ui.skeleton('card', 3);
  }

  function showNotFound() {
    stopChat();
    root.innerHTML =
      '<div class="state state--empty">' +
      '<p class="state__title">식사팟을 찾을 수 없어요</p>' +
      '<p class="state__desc">주소가 맞는지 확인해 주세요.</p>' +
      '<a class="btn" href="' + partyApi.LIST_URL + '">식사팟 목록으로</a>' +
      '</div>';
  }

  function showDeleted() {
    stopChat();
    if (dialog.open) dialog.close();
    root.innerHTML =
      '<div class="state state--empty">' +
      '<p class="state__title">삭제된 식사팟이에요</p>' +
      '<p class="state__desc">방장이 식사팟을 삭제했어요. 다른 식사팟을 찾아보세요.</p>' +
      '<a class="btn btn--primary" href="' + partyApi.LIST_URL + '">식사팟 목록으로</a>' +
      '</div>';
  }

  function showError(message) {
    root.innerHTML =
      '<div class="state state--error">' +
      '<p class="state__title">식사팟을 불러오지 못했어요</p>' +
      '<p class="state__desc">' + esc(message) + '</p>' +
      '<button type="button" class="btn" data-action="retry">다시 시도</button>' +
      '</div>';
  }

  function setFeedback(type, text) {
    state.feedback = text ? { type, text } : null;
    const el = document.getElementById('party-feedback');
    if (!el) return;
    el.className = 'party-feedback' + (text ? ' party-feedback--' + type : '');
    el.textContent = text || '';
  }

  /* ---------- 버튼 ---------- */

  function onRootClick(event) {
    const target = event.target.closest('[data-action]');
    if (!target || target.disabled) return;

    switch (target.dataset.action) {
      case 'retry':
        loadDetail(true);
        break;
      case 'join':
        joinParty();
        break;
      case 'confirm':
        runAction(() => partyApi.confirm(partyId), '참석 확정했어요. 맛있게 드세요!');
        break;
      case 'cancel-confirm':
        cancelConfirm();
        break;
      case 'leave':
        leaveParty();
        break;
      case 'invite':
        copyInviteLink();
        break;
      case 'open-schedule':
        openScheduleDialog();
        break;
      case 'close':
        runAction(() => partyApi.close(partyId), '모집을 마감했어요.');
        break;
      case 'reopen':
        runAction(() => partyApi.reopen(partyId), '모집을 다시 열었어요.');
        break;
      case 'delete':
        deleteParty();
        break;
      default:
    }
  }

  /**
   * 버튼 하나의 공통 처리: 중복 클릭 막기 → API → 성공 문구 → 상세 다시 불러오기
   * 실패하면 서버 문구를 보여 주고, 그 사이 상태가 바뀌었을 수 있으니 상세를 다시 불러온다.
   */
  async function runAction(call, successText) {
    if (state.busy) return;
    state.busy = true;
    setButtonsDisabled(true);
    try {
      await call();
      state.feedback = null;
      ui.toast(successText, { type: 'success' });
    } catch (err) {
      if (err.statusCode === 401) return;
      if (err.error === 'PARTY_DELETED') return showDeleted();
      state.feedback = { type: 'error', text: err.message };
    } finally {
      state.busy = false;
    }
    await loadDetail(false);
  }

  function setButtonsDisabled(disabled) {
    root.querySelectorAll('button[data-action]').forEach((btn) => {
      if (disabled) btn.disabled = true;
    });
    if (!disabled) render();
  }

  async function joinParty() {
    const d = state.detail;
    const ok = await partyJoin.ask({
      restaurantName: d.restaurantName, category: d.category, mealType: d.mealType, time: d.mealTime,
      partyDate: d.partyDate, memberCount: d.memberCount, capacity: d.capacity, memo: d.memo,
    });
    if (!ok) return;
    runAction(() => partyApi.join(partyId), '참여했어요. 대화방에서 인사해 보세요!');
  }

  async function cancelConfirm() {
    const ok = await ui.confirm({
      title: '참석 확정을 취소할까요?',
      message: '팟에는 그대로 남아 있어요. 마음이 바뀌면 다시 확정할 수 있어요.',
      confirmText: '확정 취소',
    });
    if (!ok) return;
    runAction(() => partyApi.cancelConfirm(partyId), '참석 확정을 취소했어요.');
  }

  async function leaveParty() {
    const isHost = state.detail.myRole === 'HOST';
    const ok = await ui.confirm({
      title: isHost ? '나가면 식사팟이 삭제돼요' : '식사팟에서 나갈까요?',
      message: isHost
        ? '방장이 나가면 팟이 삭제되고 되돌릴 수 없어요. 그래도 나갈까요?'
        : '나가면 대화방에서도 빠져요.',
      confirmText: '나가기',
      danger: true,
    });
    if (!ok) return;

    if (state.busy) return;
    state.busy = true;
    try {
      await partyApi.leave(partyId);
      stopChat();
      location.href = partyApi.LIST_URL; // 나간 뒤에는 목록으로
    } catch (err) {
      state.busy = false;
      if (err.statusCode === 401) return;
      if (err.error === 'PARTY_DELETED') return showDeleted();
      state.feedback = { type: 'error', text: err.message };
      await loadDetail(false);
    }
  }

  async function deleteParty() {
    const ok = await ui.confirm({
      title: '식사팟을 삭제할까요?',
      message: '삭제하면 되돌릴 수 없어요. 참여자들에게도 사라져요.',
      confirmText: '삭제',
      danger: true,
    });
    if (!ok) return;

    if (state.busy) return;
    state.busy = true;
    try {
      await partyApi.remove(partyId);
      stopChat();
      location.href = partyApi.LIST_URL;
    } catch (err) {
      state.busy = false;
      if (err.statusCode === 401) return;
      if (err.error === 'PARTY_DELETED') return showDeleted();
      state.feedback = { type: 'error', text: err.message };
      await loadDetail(false);
    }
  }

  /** 초대 링크: '문구 + 사이트 주소 + 경로'를 클립보드에 복사한다 */
  async function copyInviteLink() {
    try {
      const link = await partyApi.getInviteLink(partyId);
      const text = link.text + '\n' + location.origin + link.path;
      try {
        await navigator.clipboard.writeText(text);
        ui.toast('초대 링크를 복사했어요. 메신저에 붙여 넣어 보내 주세요.', { type: 'success' });
      } catch (copyErr) {
        // 클립보드를 쓸 수 없는 브라우저: 직접 복사할 수 있게 보여 준다
        window.prompt('아래 내용을 복사해 주세요.', text);
      }
    } catch (err) {
      if (err.statusCode === 401) return;
      setFeedback('error', err.message);
    }
  }

  /* ---------- 방장: 일정 확정·수정 ---------- */

  function openScheduleDialog() {
    const d = state.detail;
    const isUpdate = !!d.schedule;

    // 끼니 시간대의 30분 단위 시간 중, 오늘 팟이면 지금 이후만 (서버도 같은 규칙으로 검사)
    const now = new Date();
    const options = partyApi.MEAL_TIME_OPTIONS[d.mealType].filter((time) => {
      return new Date(d.partyDate + 'T' + time + ':00') > now;
    });
    if (options.length === 0) {
      setFeedback('error', '지금 고를 수 있는 시간이 없어요. 끼니 시간이 이미 지났어요.');
      return;
    }

    const selected = isUpdate ? d.schedule.confirmedTime : d.mealTime;
    scheduleTime.innerHTML = options
      .map((time) => '<option value="' + time + '"' + (time === selected ? ' selected' : '') + '>' + time + '</option>')
      .join('');
    schedulePlace.value = isUpdate ? d.schedule.meetPlace : '';
    scheduleMemo.value = isUpdate && d.schedule.confirmMemo ? d.schedule.confirmMemo : '';
    scheduleTitle.textContent = isUpdate ? '일정 수정' : '일정 정하기';
    scheduleNotice.hidden = !isUpdate;
    scheduleMessage.textContent = '';
    schedulePlace.classList.remove('input--error');
    dialog.showModal();
    schedulePlace.focus();
  }

  async function onScheduleSubmit(event) {
    event.preventDefault();
    const meetPlace = schedulePlace.value.trim();
    const confirmMemo = scheduleMemo.value.trim();

    if (!meetPlace) {
      scheduleMessage.textContent = '만날 장소를 입력해 주세요.';
      schedulePlace.classList.add('input--error');
      schedulePlace.focus();
      return;
    }

    scheduleSubmit.disabled = true;
    try {
      const result = await partyApi.updateSchedule(partyId, {
        confirmedTime: scheduleTime.value,
        meetPlace,
        confirmMemo: confirmMemo || null,
      });
      dialog.close();
      state.feedback = null;
      ui.toast(result.confirmationsReset
        ? '일정을 바꿨어요. 모두의 참석 확정이 초기화되어 다시 받아야 해요.'
        : '일정을 정했어요. 이제 참여자들이 참석 확정을 할 수 있어요.', { type: 'success', duration: 4000 });
      await loadDetail(false);
    } catch (err) {
      if (err.statusCode === 401) return;
      if (err.error === 'PARTY_DELETED') return showDeleted();
      scheduleMessage.textContent = err.message;
    } finally {
      scheduleSubmit.disabled = false;
    }
  }

  /* ---------- 대화 (참여자만, 3초 폴링) ---------- */

  /** 상세를 새로 받을 때마다: 참여자면 대화 시작, 아니면 대화 숨김. 식사 완료면 읽기 전용 */
  function syncChat() {
    const isMember = state.detail.myRole !== 'NONE';
    if (isMember && !chat.active) startChat();
    if (!isMember && chat.active) stopChat();
    if (state.detail.status === 'FINISHED') setReadOnly(true);
  }

  function startChat() {
    chat.active = true;
    chat.lastId = 0;
    chatRoot.hidden = false;
    chatMessage.textContent = '';
    chatBody.innerHTML =
      '<div class="state state--loading"><span class="spinner" aria-hidden="true"></span>' +
      '<p class="state__desc">대화를 불러오는 중…</p></div>';
    pollChats();
    chat.timer = setInterval(pollChats, POLL_INTERVAL_MS);
  }

  function stopChat() {
    chat.active = false;
    clearInterval(chat.timer);
    chat.timer = null;
    chatRoot.hidden = true;
  }

  async function pollChats() {
    if (!chat.active || document.hidden) return; // 다른 탭을 보고 있으면 쉬었다가 돌아오면 바로 조회
    if (chat.loading) {
      chat.again = true; // 지금 요청이 끝나면 한 번 더 (전송 직후 조회가 묻히지 않게)
      return;
    }
    chat.loading = true;
    const isFirst = chat.lastId === 0;
    try {
      const result = await partyApi.getChats(partyId, chat.lastId);
      if (!chat.active) return;
      if (isFirst) chatBody.innerHTML = '';
      appendChats(result.chats, isFirst);
      setReadOnly(result.readOnly);
      chatMessage.textContent = '';

      // 누가 참여·나가기·확정·일정 변경을 했다는 시스템 메시지가 오면 상세도 새로 불러온다
      if (!isFirst && result.chats.some((c) => c.type === 'SYSTEM') && !state.busy) {
        loadDetail(false);
      }
    } catch (err) {
      if (err.statusCode === 401) return stopChat();
      if (err.error === 'PARTY_DELETED') return showDeleted();     // 폴링 중 방장이 삭제
      if (err.error === 'NOT_JOINED') {                            // 참여자가 아니게 됨
        stopChat();
        loadDetail(false);
        return;
      }
      chatMessage.textContent = '대화를 불러오지 못했어요. 잠시 후 다시 시도할게요.';
    } finally {
      chat.loading = false;
      if (chat.again) {
        chat.again = false;
        pollChats();
      }
    }
  }

  function appendChats(chats, isFirst) {
    if (chats.length === 0) {
      if (isFirst) chatBody.innerHTML = '<p class="state__desc">아직 대화가 없어요. 먼저 인사해 보세요!</p>';
      return;
    }
    // 맨 아래를 보고 있었으면 새 메시지도 따라 내려간다 (위로 올려 읽는 중이면 그대로)
    const nearBottom = chatBody.scrollHeight - chatBody.scrollTop - chatBody.clientHeight < 80;
    const emptyNote = chatBody.querySelector('.state__desc');
    if (emptyNote) emptyNote.remove();

    chatBody.insertAdjacentHTML('beforeend', chats.map(renderChat).join(''));
    chat.lastId = Math.max(chat.lastId, ...chats.map((c) => c.chatId));

    if (isFirst || nearBottom || chats.some((c) => c.mine)) {
      chatBody.scrollTop = chatBody.scrollHeight;
    }
  }

  function renderChat(c) {
    if (c.type === 'SYSTEM') {
      return '<p class="chat-system">' + esc(c.content) + '</p>';
    }
    const time = esc(String(c.createdAt).substring(11, 16));
    return (
      '<div class="chat-msg' + (c.mine ? ' chat-msg--mine' : '') + '">' +
      (c.mine ? '' : '<span class="member-avatar member-avatar--sm chat-msg__avatar" aria-hidden="true">' + esc(initialOf(c.nickname)) + '</span>') +
      '<div class="chat-msg__main">' +
      (c.mine ? '' : '<span class="chat-msg__name">' + esc(c.nickname) + '</span>') +
      '<div class="chat-msg__line"><p class="chat-msg__bubble">' + esc(c.content) + '</p>' +
      '<span class="chat-msg__time">' + time + '</span></div>' +
      '</div>' +
      '</div>'
    );
  }

  function setReadOnly(readOnly) {
    if (chat.readOnly === readOnly) return;
    chat.readOnly = readOnly;
    chatInput.disabled = readOnly;
    chatSubmit.disabled = readOnly;
    chatInput.placeholder = readOnly ? '식사가 끝나서 메시지를 보낼 수 없어요' : '메시지를 입력해 주세요';
  }

  async function onChatSubmit(event) {
    event.preventDefault();
    const content = chatInput.value.trim();
    if (!content) {
      chatMessage.textContent = '메시지를 입력해 주세요.';
      chatInput.focus();
      return;
    }

    chatSubmit.disabled = true;
    try {
      await partyApi.sendChat(partyId, content);
      chatInput.value = '';
      chatMessage.textContent = '';
      pollChats(); // 명세: 전송 후 바로 한 번 조회해서 화면에 반영
    } catch (err) {
      if (err.statusCode === 401) return;
      if (err.error === 'PARTY_DELETED') return showDeleted();
      if (err.error === 'CHAT_READ_ONLY') setReadOnly(true);
      chatMessage.textContent = err.message;
    } finally {
      if (!chat.readOnly) chatSubmit.disabled = false;
      chatInput.focus();
    }
  }

})();
