/*
 * party-list.js — S-07 식사팟 목록 (A)
 *
 * 주소: /party-list.html (탭을 기억하려고 ?mealType=LUNCH|DINNER 를 붙인다)
 *
 * 만든 것
 *   - 점심 / 저녁 탭: 기본은 15시 전 점심, 이후 저녁. 탭에 건수 표시
 *   - 목록: 식사 시간 빠른 순(서버 정렬). 인원 막대·상태, 한마디
 *   - 버튼: 참여 중 → [상세 보기] / 모집중·일정 확정 → [참여하기](참여 후 상세로 이동) / 정원·모집 마감 → [마감] 비활성
 *   - 행을 누르면 상세(S-12)로 이동
 *   - 로딩 / 빈 상태 / 오류 화면
 */
(function () {
  'use strict';

  const esc = utils.escapeHtml;
  const STATUS_LABELS = partyApi.STATUS_LABELS;
  const MEAL_TYPE_LABELS = partyApi.MEAL_TYPE_LABELS;
  const CATEGORY_LABELS = restaurantApi.CATEGORY_LABELS;
  const LUNCH_TAB_UNTIL_HOUR = 15; // 15시 전에는 점심 탭이 기본
  const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

  const root = document.getElementById('party-list-root');
  const message = document.getElementById('list-message');
  const tabs = Array.from(document.querySelectorAll('.party-tabs__tab'));

  const state = {
    mealType: initialMealType(),
    joining: false,
  };

  /* ---------- 시작 ---------- */

  document.getElementById('today-label').textContent = formatToday();

  tabs.forEach((tab) => {
    tab.addEventListener('click', () => selectTab(tab.dataset.mealType));
    tab.addEventListener('keydown', onTabKeydown);
  });
  root.addEventListener('click', onRootClick);

  selectTab(state.mealType);

  /** 주소에 ?mealType이 있으면 그 탭, 없으면 현재 시각 기준 (15시 전 점심) */
  function initialMealType() {
    const fromUrl = new URLSearchParams(location.search).get('mealType');
    if (fromUrl === 'LUNCH' || fromUrl === 'DINNER') return fromUrl;
    return new Date().getHours() < LUNCH_TAB_UNTIL_HOUR ? 'LUNCH' : 'DINNER';
  }

  /* ---------- 탭 ---------- */

  function selectTab(mealType) {
    state.mealType = mealType;
    tabs.forEach((tab) => {
      const isSelected = tab.dataset.mealType === mealType;
      tab.setAttribute('aria-selected', String(isSelected));
      tab.tabIndex = isSelected ? 0 : -1;
    });
    root.setAttribute('aria-labelledby', 'tab-' + mealType);
    // 뒤로 가기로 돌아와도 같은 탭이 보이도록 주소에 남긴다 (새 기록은 만들지 않음)
    history.replaceState(null, '', '?mealType=' + mealType);
    loadList();
  }

  /** 탭 키보드 이동: ← → 로 탭 전환 */
  function onTabKeydown(event) {
    if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return;
    const next = state.mealType === 'LUNCH' ? 'DINNER' : 'LUNCH';
    selectTab(next);
    document.getElementById('tab-' + next).focus();
  }

  function setCounts(lunchCount, dinnerCount) {
    document.querySelector('#tab-LUNCH .party-tabs__count').textContent = lunchCount;
    document.querySelector('#tab-DINNER .party-tabs__count').textContent = dinnerCount;
  }

  /* ---------- 조회 ---------- */

  async function loadList() {
    const requested = state.mealType;
    showLoading();
    try {
      const result = await partyApi.getList({ mealType: requested });
      if (requested !== state.mealType) return; // 그 사이 다른 탭을 눌렀으면 무시
      setCounts(result.lunchCount, result.dinnerCount);
      if (result.parties.length === 0) {
        showEmpty();
      } else {
        renderList(result.parties);
      }
    } catch (err) {
      if (err.statusCode === 401) return; // client.js가 로그인 화면으로 이동시킨다
      showError(err.message);
    }
  }

  /* ---------- 그리기 ---------- */

  function renderList(parties) {
    root.innerHTML =
      '<div class="party-rows__head" aria-hidden="true">' +
      '<span>식당</span><span>시간</span><span>인원 · 상태</span><span>한마디</span><span></span>' +
      '</div>' +
      '<ul class="party-rows">' + parties.map(renderRow).join('') + '</ul>';
  }

  function renderRow(p) {
    const detailUrl = partyApi.detailUrl(p.partyId);
    const percent = Math.min(100, Math.round((p.memberCount / p.capacity) * 100));
    const category = CATEGORY_LABELS[p.category] || '';

    return (
      '<li class="party-row" data-party-id="' + p.partyId + '" data-href="' + detailUrl + '">' +
      '<div class="party-row__name">' +
      '<a class="party-row__link" href="' + detailUrl + '">' + esc(p.restaurantName) + '</a>' +
      '<span class="party-row__category">' + esc(category) + '</span>' +
      '</div>' +
      '<div class="party-row__time"><span class="sr-only">시간 </span>' + esc(p.time) + '</div>' +
      '<div class="party-row__people">' +
      '<span class="party-row__count">' + p.memberCount + ' / ' + p.capacity + '명 ' + statusBadge(p.status) + '</span>' +
      '<div class="party-row__bar" aria-hidden="true"><div class="party-row__bar-fill" style="width: ' + percent + '%"></div></div>' +
      '</div>' +
      '<div class="party-row__memo">' + (p.memo ? esc(p.memo) : '<span class="text-muted">-</span>') + '</div>' +
      '<div class="party-row__action">' + actionButton(p, detailUrl) + '</div>' +
      '</li>'
    );
  }

  /** 버튼: 참여 중 → 상세 보기 / 모집중·일정 확정 → 참여하기 / 마감 → 비활성 */
  function actionButton(p, detailUrl) {
    if (p.joined) {
      return '<a class="btn" href="' + detailUrl + '">상세 보기</a>';
    }
    if (p.status === 'RECRUITING' || p.status === 'SCHEDULED') {
      return '<button type="button" class="btn btn--primary" data-action="join">참여하기</button>';
    }
    return '<button type="button" class="btn" disabled>마감</button>';
  }

  function statusBadge(status) {
    return '<span class="badge badge--' + status.toLowerCase() + '">' + esc(STATUS_LABELS[status]) + '</span>';
  }

  /* ---------- 상태 화면 ---------- */

  function showLoading() {
    message.textContent = '';
    root.innerHTML =
      '<div class="state state--loading">' +
      '<span class="spinner" aria-hidden="true"></span>' +
      '<p class="state__desc">식사팟을 불러오는 중…</p>' +
      '</div>';
  }

  function showEmpty() {
    const label = MEAL_TYPE_LABELS[state.mealType];
    root.innerHTML =
      '<div class="state state--empty party-empty">' +
      '<p class="state__title">오늘 모집 중인 ' + label + ' 식사팟이 없습니다</p>' +
      '<p class="state__desc">첫 식사팟을 만들어 보세요. 식사가 끝난 팟은 목록에서 빠집니다.</p>' +
      '<a class="btn btn--primary" href="/party-create.html">식사팟 만들기</a>' +
      '</div>';
  }

  function showError(text) {
    root.innerHTML =
      '<div class="state state--error">' +
      '<p class="state__title">식사팟 목록을 불러오지 못했습니다</p>' +
      '<p class="state__desc">' + esc(text) + '</p>' +
      '<button type="button" class="btn" data-action="retry">다시 시도</button>' +
      '</div>';
  }

  /* ---------- 클릭 ---------- */

  function onRootClick(event) {
    if (event.target.closest('[data-action="retry"]')) return loadList();

    const joinButton = event.target.closest('[data-action="join"]');
    if (joinButton) return joinParty(joinButton);

    // 링크·버튼이 아닌 곳을 눌러도 행 전체가 상세로 이동 (키보드 사용자는 식당 이름 링크로 이동)
    if (event.target.closest('a, button')) return;
    const row = event.target.closest('.party-row');
    if (row) location.href = row.dataset.href;
  }

  /** 참여하기(1차) → 성공하면 상세로 이동. 실패하면 그 행에 이유를 보여 주고 목록을 새로 불러온다 */
  async function joinParty(button) {
    if (state.joining) return;
    state.joining = true;
    button.disabled = true;
    const row = button.closest('.party-row');
    const partyId = row.dataset.partyId;

    try {
      await partyApi.join(partyId);
      location.href = partyApi.detailUrl(partyId);
    } catch (err) {
      state.joining = false;
      if (err.statusCode === 401) return;
      // 이미 참여 중이면 그냥 상세로 보낸다
      if (err.error === 'ALREADY_JOINED') {
        location.href = partyApi.detailUrl(partyId);
        return;
      }
      // 정원 마감(동시에 마지막 자리)·삭제·완료 등: 이유를 보여 주고 목록을 최신으로
      await loadList();
      message.textContent = err.message;
    }
  }

  /* ---------- 날짜 ---------- */

  /** 오늘 → '2026-11-19 (목)' */
  function formatToday() {
    const now = new Date();
    const pad = (n) => String(n).padStart(2, '0');
    return now.getFullYear() + '-' + pad(now.getMonth() + 1) + '-' + pad(now.getDate()) +
      ' (' + WEEKDAYS[now.getDay()] + ')';
  }
})();
