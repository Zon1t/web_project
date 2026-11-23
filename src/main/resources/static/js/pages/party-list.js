/*
 * party-list.js — S-07 식사팟 목록 (A)
 *
 * 주소: /party-list.html (날짜·탭을 기억하려고 ?date=YYYY-MM-DD&mealType=LUNCH|DINNER 를 붙인다)
 *
 * 만든 것
 *   - 날짜: 달력에서 고르기 + 전날/다음 날 버튼, 오늘 ~ 14일 후 (11/20 추가). 기본은 오늘
 *   - 점심 / 저녁 탭: 오늘은 15시 전 점심·이후 저녁, 다른 날짜는 점심이 기본. 탭에 건수 표시
 *   - 목록: 식사 시간 빠른 순(서버 정렬). 카드에 자리 막대·상태·'곧 마감'/'곧 시작' 뱃지, 방 설명
 *   - 버튼: 참여 중 → [상세 보기] / 모집중·일정 확정 → [참여하기](확인 창 → 참여 후 상세로 이동) / 정원·모집 마감 → [마감] 비활성
 *   - 카드를 누르면 상세(S-12)로 이동
 *   - 로딩 / 빈 상태 / 오류 화면
 */
(function () {
  'use strict';

  const esc = utils.escapeHtml;
  const STATUS_LABELS = partyApi.STATUS_LABELS;
  const MEAL_TYPE_LABELS = partyApi.MEAL_TYPE_LABELS;
  const CATEGORY_LABELS = restaurantApi.CATEGORY_LABELS;
  const LUNCH_TAB_UNTIL_HOUR = 15; // 오늘은 15시 전에 점심 탭이 기본

  const root = document.getElementById('party-list-root');
  const message = document.getElementById('list-message');
  const tabs = Array.from(document.querySelectorAll('.party-tabs__tab'));
  const datePrev = document.getElementById('date-prev');
  const dateNext = document.getElementById('date-next');
  const dateToday = document.getElementById('date-today');
  const listTitle = document.getElementById('list-title');
  const dateLabel = document.getElementById('today-label');
  const createLink = document.getElementById('create-link');

  const params = new URLSearchParams(location.search);
  const state = {
    date: partyApi.isSelectableDate(params.get('date')) ? params.get('date') : partyApi.today(),
    mealType: null,
    joining: false,
    parties: [],
  };
  state.mealType = initialMealType(params.get('mealType'));

  /* ---------- 시작 ---------- */

  const picker = createDatePicker();
  datePrev.addEventListener('click', () => selectDate(partyApi.addDays(state.date, -1)));
  dateNext.addEventListener('click', () => selectDate(partyApi.addDays(state.date, 1)));
  dateToday.addEventListener('click', () => selectDate(partyApi.today()));
  tabs.forEach((tab) => {
    tab.addEventListener('click', () => selectTab(tab.dataset.mealType));
    tab.addEventListener('keydown', onTabKeydown);
  });
  root.addEventListener('click', onRootClick);

  selectTab(state.mealType);

  /** 주소에 ?mealType이 있으면 그 탭. 없으면 오늘은 15시 기준, 다른 날짜는 점심 */
  function initialMealType(fromUrl) {
    if (fromUrl === 'LUNCH' || fromUrl === 'DINNER') return fromUrl;
    if (!partyApi.isToday(state.date)) return 'LUNCH';
    return new Date().getHours() < LUNCH_TAB_UNTIL_HOUR ? 'LUNCH' : 'DINNER';
  }

  /* ---------- 날짜 ---------- */

  function createDatePicker() {
    return datePicker.create(document.getElementById('date-picker'), {
      value: state.date,
      min: partyApi.today(),
      max: partyApi.addDays(partyApi.today(), partyApi.MAX_DAYS_AHEAD),
      formatLabel: (d) => (partyApi.isToday(d) ? '오늘 · ' + partyApi.fullDateLabel(d) : partyApi.relativeDateLabel(d)),
      hint: '오늘부터 2주 후까지 볼 수 있어요.',
      label: '조회 날짜',
      onChange: selectDate,
    });
  }

  /** 날짜를 바꾸면 그 날짜 기본 탭으로 목록을 다시 불러온다 */
  function selectDate(date) {
    if (date === state.date || !partyApi.isSelectableDate(date)) return;
    state.date = date;
    picker.setValue(date);
    selectTab(initialMealType(null));
  }

  /** 제목·달력·전날/다음 날 버튼·[식사팟 만들기] 링크를 지금 고른 날짜에 맞춘다 */
  function syncDateUi() {
    const isToday = partyApi.isToday(state.date);
    // 오늘: '오늘의 식사팟 11월 20일 (금)' / 다른 날짜: '식사팟 내일 · 11월 21일 (토)'
    listTitle.textContent = isToday ? '오늘의 식사팟' : '식사팟';
    dateLabel.textContent = isToday ? partyApi.fullDateLabel(state.date) : partyApi.relativeDateLabel(state.date);
    createLink.href = partyApi.createUrl(null, isToday ? null : state.date);
    datePrev.disabled = !partyApi.isSelectableDate(partyApi.addDays(state.date, -1));
    dateNext.disabled = !partyApi.isSelectableDate(partyApi.addDays(state.date, 1));
    dateToday.hidden = isToday;
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
    syncDateUi();
    // 뒤로 가기로 돌아와도 같은 날짜·탭이 보이도록 주소에 남긴다 (새 기록은 만들지 않음)
    history.replaceState(null, '', '?date=' + state.date + '&mealType=' + mealType);
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
    const requestedMeal = state.mealType;
    const requestedDate = state.date;
    showLoading();
    try {
      const result = await partyApi.getList({ date: requestedDate, mealType: requestedMeal });
      // 그 사이 다른 탭·날짜를 눌렀으면 늦게 온 응답은 무시
      if (requestedMeal !== state.mealType || requestedDate !== state.date) return;
      setCounts(result.lunchCount, result.dinnerCount);
      state.parties = result.parties;
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
    // 한 끼에 팟 하나 (11/23): 이 날짜·끼니에 이미 들어간 팟이 있으면 다른 팟의 [참여하기]를 막는다
    state.joinedThisMeal = parties.some((p) => p.joined);
    root.innerHTML = '<ul class="party-cards">' + parties.map(renderCard).join('') + '</ul>';
  }

  /** 카드: 아바타·식당·시간 / 방 설명 / 자리 막대·뱃지 / 버튼 */
  function renderCard(p) {
    const detailUrl = partyApi.detailUrl(p.partyId);
    const category = CATEGORY_LABELS[p.category] || '';
    const left = p.capacity - p.memberCount;
    const isOpen = p.status === 'RECRUITING' || p.status === 'SCHEDULED';

    return (
      '<li class="party-card' + (isOpen || p.joined ? '' : ' is-closed') + '" data-party-id="' + p.partyId + '" data-href="' + detailUrl + '">' +
      '<div class="party-card__top">' +
      ui.avatar(p.category, 44) +
      '<div class="party-card__title">' +
      '<a class="party-card__link" href="' + detailUrl + '">' + esc(p.restaurantName) + '</a>' +
      '<span class="party-card__category">' + esc(category) + '</span>' +
      '</div>' +
      '<span class="party-card__time">' + ui.icon('clock', 14) + '<span class="sr-only">시간 </span>' + esc(p.time) + '</span>' +
      '</div>' +
      '<p class="party-card__desc">' + (p.memo ? esc(p.memo) : '<span class="text-muted">방 설명이 없어요</span>') + '</p>' +
      '<div class="party-card__people">' +
      seatsHtml(p.memberCount, p.capacity) +
      '<span class="party-card__count">' + p.memberCount + ' / ' + p.capacity + '명</span>' +
      '</div>' +
      '<div class="party-card__foot">' +
      '<span class="party-card__badges">' + statusBadge(p.status) + extraBadges(p, left, isOpen) + '</span>' +
      actionButton(p, detailUrl) +
      '</div>' +
      '</li>'
    );
  }

  /** 자리 막대: 정원만큼 칸, 찬 칸은 색칠 */
  function seatsHtml(count, capacity) {
    const seats = [];
    for (let i = 0; i < capacity; i += 1) {
      seats.push('<span class="party-seat' + (i < count ? ' is-taken' : '') + '"></span>');
    }
    return '<span class="party-seats" aria-hidden="true">' + seats.join('') + '</span>';
  }

  /** '곧 마감'(한 자리 남음), '곧 시작'(오늘·1시간 안) 뱃지 */
  function extraBadges(p, left, isOpen) {
    let html = '';
    if (p.joined) html += '<span class="badge party-badge--joined">참여 중</span>';
    if (isOpen && left === 1) html += '<span class="badge party-badge--hot">곧 마감</span>';
    if (isOpen && partyApi.isToday(state.date) && p.time) {
      const diff = (new Date(state.date + 'T' + p.time + ':00') - new Date()) / 60000;
      if (diff > 0 && diff <= 60) html += '<span class="badge party-badge--soon">곧 시작</span>';
    }
    return html;
  }

  /** 버튼: 참여 중 → 상세 보기 / 같은 끼니 다른 팟 참여 중 → 비활성 / 모집중·일정 확정 → 참여하기 / 마감 → 비활성 */
  function actionButton(p, detailUrl) {
    if (p.joined) {
      return '<a class="btn" href="' + detailUrl + '">상세 보기</a>';
    }
    if (state.joinedThisMeal) {
      // 누르면 이유를 팝업으로 알려 준다 (비활성 버튼은 눌러도 반응이 없고 툴팁은 모바일에서 안 보여서)
      return '<button type="button" class="btn party-card__blocked" data-action="same-meal" aria-haspopup="dialog">참여 불가</button>';
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
    root.innerHTML = ui.skeleton('card', 4);
  }

  function showEmpty() {
    const label = MEAL_TYPE_LABELS[state.mealType];
    const isToday = partyApi.isToday(state.date);
    const when = isToday ? '오늘' : partyApi.relativeDateLabel(state.date);
    root.innerHTML = ui.empty({
      illust: 'tray',
      title: esc(when) + ' 모집 중인 ' + label + ' 식사팟이 없어요',
      desc: '첫 식사팟을 만들어 보세요.' + (isToday ? ' 식사가 끝난 팟은 목록에서 빠져요.' : ''),
      actionHtml: '<a class="btn btn--primary" href="' + partyApi.createUrl(null, isToday ? null : state.date) + '">식사팟 만들기</a>',
    });
  }

  function showError(text) {
    root.innerHTML = ui.empty({
      illust: 'bowl',
      title: '식사팟 목록을 불러오지 못했어요',
      desc: esc(text),
      actionHtml: '<button type="button" class="btn" data-action="retry">다시 시도</button>',
    });
  }

  /* ---------- 클릭 ---------- */

  function onRootClick(event) {
    if (event.target.closest('[data-action="retry"]')) return loadList();

    const joinButton = event.target.closest('[data-action="join"]');
    if (joinButton) return joinParty(joinButton);

    if (event.target.closest('[data-action="same-meal"]')) return showSameMealNotice();

    // 링크·버튼이 아닌 곳을 눌러도 행 전체가 상세로 이동 (키보드 사용자는 식당 이름 링크로 이동)
    if (event.target.closest('a, button')) return;
    const card = event.target.closest('.party-card');
    if (card) location.href = card.dataset.href;
  }

  /** 참여하기 → 확인 창 → 성공하면 상세로 이동. 실패하면 이유를 보여 주고 목록을 새로 불러온다 */
  async function joinParty(button) {
    if (state.joining) return;
    const card = button.closest('.party-card');
    const partyId = card.dataset.partyId;
    const p = state.parties.find((item) => String(item.partyId) === String(partyId));
    if (p) {
      const ok = await partyJoin.ask(Object.assign({ mealType: state.mealType, partyDate: state.date }, p));
      if (!ok) return;
    }
    state.joining = true;
    button.disabled = true;

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
      // 같은 시간대에 이미 다른 팟에 참여 중 (다른 탭에서 참여한 직후 등): 팝업으로 안내하고 목록을 최신으로
      if (partyJoin.isSameMealError(err)) {
        await loadList();
        showSameMealNotice();
        return;
      }
      // 정원 마감(동시에 마지막 자리)·삭제·완료 등: 이유를 보여 주고 목록을 최신으로
      await loadList();
      message.textContent = err.message;
    }
  }

  /** '같은 시간대에는 식사팟 하나만' 팝업. 지금 목록에서 내가 참여한 팟을 함께 보여 준다 */
  function showSameMealNotice() {
    const joined = (state.parties || []).find((p) => p.joined);
    partyJoin.sameMealNotice({
      partyDate: state.date,
      mealType: state.mealType,
      joinedParty: joined ? { partyId: joined.partyId, restaurantName: joined.restaurantName, time: joined.time } : null,
    });
  }
})();
