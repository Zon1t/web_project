/*
 * party-create.js — S-08 식사팟 만들기 (A)
 *
 * 주소: /party-create.html               → 식당 이름 검색으로 선택
 *       /party-create.html?restaurantId=12 → 그 식당으로 고정 (식당 상세·룰렛에서 진입)
 *       ?date=2026-11-21 을 붙이면 그 날짜를 미리 고른다 (식사팟 목록에서 진입)
 *       ?restaurantId=12&restore=1 → 식당 등록을 마치고 돌아온 경우. 등록 전에 입력한 값을 되살린다 (T2)
 *
 * 만든 것
 *   - 식당: 고정 또는 이름 검색(입력이 멈추고 300ms 뒤 검색, ↑↓ Enter 선택)
 *   - 날짜 달력: 오늘 ~ 14일 후 (11/20 추가). 오늘 남은 시간이 없으면 오늘은 고를 수 없고 내일이 기본
 *   - 끼니 토글: 오늘이면 15시 전 점심이 기본, 미래 날짜는 점심이 기본. 남은 시간이 없는 끼니는 고를 수 없다
 *   - 식사 시간: 오늘이면 지난 시간은 빼고 가장 가까운 다음 시간이 기본, 미래 날짜는 끼니 첫 시간이 기본
 *   - 정원 −/+: 2~12명, 기본 4명
 *   - 방 설명(memo): 최대 50자, 글자 수 표시
 *   - 공개 범위: [비공개: 초대받은 사람만] 체크 (2026-11-25 T4). 체크하면 isPrivate: true로 만든다
 *   - [만들기]: 식당·시간이 있으면 활성. 성공하면 상세(S-12)로 이동
 *   - 검색 결과가 없으면 [식당 등록하고 이어서 만들기] (2026-11-25 T2)
 *       입력값을 sessionStorage['sseukbap.partyDraft']에 저장 → 식당 등록(?name=검색어&returnTo=party-create)
 *       → 등록 화면이 ?restaurantId={새 ID}&restore=1 로 돌려보내면 저장값을 되살리고 바로 지운다 (30분 지난 값은 버림)
 */
(function () {
  'use strict';

  const esc = utils.escapeHtml;
  const CATEGORY_LABELS = restaurantApi.CATEGORY_LABELS;
  const TIME_OPTIONS = partyApi.MEAL_TIME_OPTIONS;
  const LUNCH_UNTIL_HOUR = 15;
  const CAPACITY_MIN = 2;
  const CAPACITY_MAX = 12;
  const SEARCH_DELAY_MS = 300;
  const SEARCH_LIMIT = 5;
  const DRAFT_KEY = 'sseukbap.partyDraft';
  const DRAFT_TTL_MS = 30 * 60 * 1000;   // 30분 지난 임시 저장값은 버린다
  const REGISTER_URL = '/restaurant-register.html';

  const form = document.getElementById('party-form');
  const restaurantSelected = document.getElementById('restaurant-selected');
  const restaurantName = document.getElementById('restaurant-name');
  const restaurantFixedBadge = document.getElementById('restaurant-fixed-badge');
  const restaurantChange = document.getElementById('restaurant-change');
  const restaurantSearch = document.getElementById('restaurant-search');
  const restaurantInput = document.getElementById('restaurant-input');
  const restaurantOptions = document.getElementById('restaurant-options');
  const restaurantMessage = document.getElementById('restaurant-message');
  const registerHint = document.getElementById('register-hint');
  const datePickerEl = document.getElementById('date-picker');
  const dateMessage = document.getElementById('date-message');
  const mealButtons = Array.from(document.querySelectorAll('.party-form__toggle-btn'));
  const timeSelect = document.getElementById('meal-time');
  const timeMessage = document.getElementById('time-message');
  const capacityValue = document.getElementById('capacity-value');
  const stepButtons = Array.from(document.querySelectorAll('.party-form__step'));
  const memoInput = document.getElementById('memo');
  const memoCount = document.getElementById('memo-count');
  const privateToggle = document.getElementById('private-toggle');
  const formMessage = document.getElementById('form-message');
  const submitButton = document.getElementById('submit-button');

  const RESTAURANT_HINT = restaurantMessage.textContent;
  const TIME_HINT = timeMessage.textContent;
  const DATE_HINT = dateMessage.textContent;

  const state = {
    restaurant: null,      // {restaurantId, name, category}
    isFixed: false,        // 식당 상세에서 들어와 고정됐는지
    partyDate: null,       // 'YYYY-MM-DD'
    mealType: null,
    capacity: 4,
    searchTimer: null,
    searchSeq: 0,          // 늦게 온 검색 결과를 버리기 위한 번호
    options: [],           // 현재 검색 결과
    activeIndex: -1,       // 키보드로 고른 검색 결과 위치
    submitting: false,
    picker: null,          // 날짜 달력 (date-picker.js)
    mealTaken: false,      // 고른 날짜·끼니에 이미 참여 중인 팟이 있는지 (2026-11-25)
    takenSeq: 0,
  };

  /* ---------- 시작 ---------- */

  mealButtons.forEach((btn) => btn.addEventListener('click', () => selectMealType(btn.dataset.mealType)));
  stepButtons.forEach((btn) => btn.addEventListener('click', () => changeCapacity(Number(btn.dataset.step))));
  memoInput.addEventListener('input', () => { memoCount.textContent = memoInput.value.length; });
  restaurantInput.addEventListener('input', onSearchInput);
  restaurantInput.addEventListener('keydown', onSearchKeydown);
  restaurantInput.addEventListener('blur', (event) => {
    // Tab으로 [식당 등록하고 이어서 만들기] 버튼에 가는 경우는 목록을 닫지 않는다
    if (event.relatedTarget && restaurantOptions.contains(event.relatedTarget)) return;
    setTimeout(closeOptions, 150); // 목록 클릭이 먼저 처리되도록
  });
  restaurantOptions.addEventListener('mousedown', (event) => event.preventDefault()); // 클릭 중 입력창 포커스 유지
  restaurantOptions.addEventListener('click', onOptionClick);
  restaurantChange.addEventListener('click', clearRestaurant);
  document.getElementById('register-link').addEventListener('click', goRegister);
  timeSelect.addEventListener('change', updateSubmit);
  form.addEventListener('submit', onSubmit);

  renderCapacity();
  initDate();
  initRestaurant();
  restoreDraft();

  /* ---------- 식당 ---------- */

  /** ?restaurantId가 있으면 그 식당으로 고정, 없으면 검색 */
  async function initRestaurant() {
    const restaurantId = new URLSearchParams(location.search).get('restaurantId');
    if (!restaurantId) {
      showSearch();
      return;
    }
    if (!/^[1-9]\d*$/.test(restaurantId)) {
      showSearch('식당 정보가 올바르지 않아요. 식당을 직접 검색해 주세요.');
      return;
    }

    restaurantName.textContent = '식당 정보를 불러오는 중…';
    restaurantSelected.hidden = false;
    restaurantSearch.hidden = true;
    try {
      const detail = await restaurantApi.getDetail(restaurantId);
      state.isFixed = true;
      setRestaurant({ restaurantId: detail.restaurantId, name: detail.name, category: detail.category });
      document.getElementById('cancel-link').href = '/restaurant-detail.html?id=' + encodeURIComponent(restaurantId);
    } catch (err) {
      if (err.statusCode === 401) return; // client.js가 로그인 화면으로 이동시킨다
      showSearch(err.error === 'RESTAURANT_NOT_FOUND'
        ? '식당을 찾을 수 없어요. 식당을 직접 검색해 주세요.'
        : err.message);
    }
  }

  function setRestaurant(restaurant) {
    state.restaurant = restaurant;
    const category = CATEGORY_LABELS[restaurant.category];
    restaurantName.textContent = restaurant.name + (category ? ' · ' + category : '');
    restaurantFixedBadge.hidden = !state.isFixed;
    restaurantChange.hidden = state.isFixed; // 식당 상세에서 온 경우는 바꿀 수 없다
    restaurantSelected.hidden = false;
    restaurantSearch.hidden = true;
    registerHint.hidden = true;
    setMessage(restaurantMessage, '', RESTAURANT_HINT);
    closeOptions();
    updateSubmit();
  }

  function clearRestaurant() {
    state.restaurant = null;
    showSearch();
    restaurantInput.value = '';
    restaurantInput.focus();
    updateSubmit();
  }

  function showSearch(errorText) {
    state.isFixed = false;
    restaurantSelected.hidden = true;
    restaurantSearch.hidden = false;
    registerHint.hidden = false;
    setMessage(restaurantMessage, errorText, RESTAURANT_HINT);
  }

  /** 입력이 멈추고 300ms 뒤에 검색 (명세: 디바운스) */
  function onSearchInput() {
    clearTimeout(state.searchTimer);
    const keyword = restaurantInput.value.trim();
    if (!keyword) {
      closeOptions();
      return;
    }
    state.searchTimer = setTimeout(() => searchRestaurants(keyword), SEARCH_DELAY_MS);
  }

  async function searchRestaurants(keyword) {
    const seq = ++state.searchSeq;
    try {
      const results = await findRestaurants(keyword);
      if (seq !== state.searchSeq) return; // 그 사이 더 최근 검색이 있었으면 버린다
      state.options = results.slice(0, SEARCH_LIMIT);
      state.activeIndex = -1;
      renderOptions();
    } catch (err) {
      if (err.statusCode === 401) return;
      setMessage(restaurantMessage, err.message, RESTAURANT_HINT);
    }
  }

  /**
   * 식당 이름 검색.
   * 자동완성 API(GET /api/restaurants/autocomplete)를 부르는 함수는 B의 restaurant.js에 추가 요청해 두었다.
   * 추가되면 그것을 쓰고, 그 전에는 식당 목록 API의 이름 검색(keyword)을 쓴다.
   */
  function findRestaurants(keyword) {
    if (typeof restaurantApi.autocomplete === 'function') {
      return restaurantApi.autocomplete(keyword);
    }
    return restaurantApi.getList({ keyword });
  }

  function renderOptions() {
    if (state.options.length === 0) {
      const keyword = restaurantInput.value.trim();
      restaurantOptions.innerHTML =
        '<li class="party-form__option-empty" role="option" aria-disabled="true">' +
        '<span>\'' + esc(keyword) + '\'은(는) 아직 등록되지 않았어요</span>' +
        '<button type="button" class="btn btn--primary party-form__register" data-action="register">식당 등록하고 이어서 만들기</button>' +
        '</li>';
    } else {
      restaurantOptions.innerHTML = state.options.map((r, i) => (
        '<li id="restaurant-option-' + i + '" class="party-form__option' + (i === state.activeIndex ? ' is-active' : '') +
        '" role="option" aria-selected="' + (i === state.activeIndex) + '" data-index="' + i + '">' +
        '<span>' + esc(r.name) + '</span>' +
        '<span class="text-muted">' + esc(CATEGORY_LABELS[r.category] || '') + '</span>' +
        '</li>'
      )).join('');
    }
    restaurantOptions.hidden = false;
    restaurantInput.setAttribute('aria-expanded', 'true');
    if (state.activeIndex >= 0) {
      restaurantInput.setAttribute('aria-activedescendant', 'restaurant-option-' + state.activeIndex);
    } else {
      restaurantInput.removeAttribute('aria-activedescendant');
    }
  }

  function closeOptions() {
    restaurantOptions.hidden = true;
    restaurantInput.setAttribute('aria-expanded', 'false');
    restaurantInput.removeAttribute('aria-activedescendant');
  }

  function onOptionClick(event) {
    if (event.target.closest('[data-action="register"]')) {
      goRegister();
      return;
    }
    const item = event.target.closest('[data-index]');
    if (item) setRestaurant(state.options[Number(item.dataset.index)]);
  }

  /* ---------- 없는 식당: 등록하고 돌아오기 (T2) ---------- */

  /** 지금 입력값을 임시 저장하고 식당 등록 화면으로 */
  function goRegister() {
    const draft = {
      date: state.partyDate,
      mealType: state.mealType,
      time: timeSelect.value || null,
      capacity: state.capacity,
      memo: memoInput.value,
      isPrivate: privateToggle.checked,
      savedAt: Date.now(),
    };
    try {
      sessionStorage.setItem(DRAFT_KEY, JSON.stringify(draft));
    } catch (e) {
      // 저장을 못 하면 입력값만 잃고 등록은 그대로 진행한다
    }
    const name = restaurantInput.value.trim();
    location.href = REGISTER_URL + '?name=' + encodeURIComponent(name) + '&returnTo=party-create';
  }

  /** ?restore=1 이면 임시 저장값을 되살리고 지운다. 식당은 ?restaurantId로 고정된다(기존 기능) */
  function restoreDraft() {
    if (new URLSearchParams(location.search).get('restore') !== '1') return;
    let draft = null;
    try {
      draft = JSON.parse(sessionStorage.getItem(DRAFT_KEY) || 'null');
      sessionStorage.removeItem(DRAFT_KEY);
    } catch (e) {
      return;
    }
    if (!draft || typeof draft.savedAt !== 'number' || Date.now() - draft.savedAt > DRAFT_TTL_MS) return;

    // 날짜: 지금도 고를 수 있는 날짜일 때만
    if (partyApi.isSelectableDate(draft.date) && hasAnyTime(draft.date) && draft.date !== state.partyDate) {
      state.picker.setValue(draft.date);
      selectDate(draft.date);
    }
    // 끼니·시간: 그 끼니·시간이 아직 남아 있을 때만
    if ((draft.mealType === 'LUNCH' || draft.mealType === 'DINNER') && availableTimes(draft.mealType).length > 0) {
      selectMealType(draft.mealType);
      if (draft.time && availableTimes(draft.mealType).includes(draft.time)) {
        timeSelect.value = draft.time;
      }
    }
    const capacity = Number(draft.capacity);
    if (Number.isInteger(capacity) && capacity >= CAPACITY_MIN && capacity <= CAPACITY_MAX) {
      state.capacity = capacity;
      renderCapacity();
    }
    if (draft.isPrivate === true) privateToggle.checked = true;
    if (typeof draft.memo === 'string') {
      memoInput.value = draft.memo.slice(0, Number(memoInput.maxLength) > 0 ? Number(memoInput.maxLength) : 50);
      memoCount.textContent = memoInput.value.length;
    }
    updateSubmit();
    ui.toast('식당을 등록했어요. 입력하던 내용을 이어서 만들어 주세요');
  }

  /** ↑↓로 고르고 Enter로 선택, Esc로 닫기 */
  function onSearchKeydown(event) {
    if (restaurantOptions.hidden || state.options.length === 0) {
      if (event.key === 'Enter') event.preventDefault(); // 검색 중 Enter로 폼이 제출되지 않게
      return;
    }
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      const step = event.key === 'ArrowDown' ? 1 : -1;
      state.activeIndex = (state.activeIndex + step + state.options.length) % state.options.length;
      renderOptions();
    } else if (event.key === 'Enter') {
      event.preventDefault();
      setRestaurant(state.options[Math.max(state.activeIndex, 0)]);
    } else if (event.key === 'Escape') {
      closeOptions();
    }
  }

  /* ---------- 날짜 ---------- */

  /**
   * 날짜 달력을 만들고 기본 날짜를 고른다.
   * ?date가 고를 수 있는 날짜면 그 날짜, 아니면 오늘. 오늘 남은 시간이 없으면 내일.
   * 화면을 열어 둔 사이 날짜가 바뀌면 다시 불러 범위를 새로 맞춘다.
   */
  function initDate() {
    const fromUrl = new URLSearchParams(location.search).get('date');
    let date = partyApi.isSelectableDate(fromUrl) ? fromUrl : partyApi.today();
    if (!hasAnyTime(date)) date = partyApi.addDays(partyApi.today(), 1);

    const min = partyApi.today();
    const max = partyApi.addDays(min, partyApi.MAX_DAYS_AHEAD);
    if (state.picker) {
      state.picker.setRange(min, max);
      state.picker.setValue(date);
    } else {
      state.picker = datePicker.create(datePickerEl, {
        value: date,
        min,
        max,
        // 오늘인데 남은 식사 시간이 없으면 고를 수 없다
        isDisabled: (d) => partyApi.isToday(d) && !hasAnyTime(d),
        formatLabel: (d) => partyApi.relativeDateLabel(d),
        hint: '오늘부터 2주 후까지 고를 수 있어요.',
        label: '식사 날짜',
        onChange: selectDate,
      });
    }
    selectDate(date);
  }

  /** 날짜를 바꾸면 끼니·시간 목록을 그 날짜에 맞게 다시 만든다 */
  function selectDate(date) {
    state.partyDate = date;
    setMessage(dateMessage, '', partyApi.isToday(date) ? DATE_HINT : partyApi.relativeDateLabel(date) + ' 식사팟을 만들어요.');
    initMealType();
  }

  /** 그 날짜에 고를 수 있는 시간이 하나라도 있는지 */
  function hasAnyTime(date) {
    return availableTimes('LUNCH', date).length > 0 || availableTimes('DINNER', date).length > 0;
  }

  /* ---------- 끼니·시간 ---------- */

  /**
   * 기본 끼니: 오늘이면 15시 전 점심, 미래 날짜는 지금 고른 끼니를 유지(처음이면 점심).
   * 그 끼니의 남은 시간이 없으면 다른 끼니.
   */
  function initMealType() {
    const lunchLeft = availableTimes('LUNCH').length > 0;
    const dinnerLeft = availableTimes('DINNER').length > 0;
    mealButtons.forEach((btn) => {
      btn.disabled = availableTimes(btn.dataset.mealType).length === 0;
    });

    if (!lunchLeft && !dinnerLeft) {
      // 오늘 19:30이 지나면 오늘은 만들 수 있는 식사팟이 없다 (보통은 내일이 자동 선택되어 여기 오지 않음)
      timeSelect.innerHTML = '';
      timeSelect.disabled = true;
      setMessage(timeMessage, '이 날짜에는 더 만들 수 있는 식사 시간이 없어요. 다른 날짜를 골라 주세요.', TIME_HINT);
      updateSubmit();
      return;
    }
    let preferred;
    if (partyApi.isToday(state.partyDate)) {
      preferred = new Date().getHours() < LUNCH_UNTIL_HOUR ? 'LUNCH' : 'DINNER';
    } else {
      preferred = state.mealType || 'LUNCH';
    }
    const usable = preferred === 'LUNCH' ? lunchLeft : dinnerLeft;
    selectMealType(usable ? preferred : (lunchLeft ? 'LUNCH' : 'DINNER'));
  }

  /** 끼니를 바꾸면 그 끼니의 시간 목록으로 바꾸고 가장 가까운 다음 시간을 고른다 */
  function selectMealType(mealType) {
    state.mealType = mealType;
    mealButtons.forEach((btn) => btn.setAttribute('aria-pressed', String(btn.dataset.mealType === mealType)));

    const times = availableTimes(mealType);
    timeSelect.innerHTML = times.map((t) => '<option value="' + t + '">' + t + '</option>').join('');
    timeSelect.disabled = times.length === 0;
    setMessage(timeMessage, '', TIME_HINT);
    updateSubmit();
    checkMealTaken();
  }

  /**
   * 고른 날짜·끼니에 이미 참여 중인 식사팟이 있으면 [만들기]를 막는다 (2026-11-25)
   * 서버도 같은 경우 409 ALREADY_JOINED_SAME_MEAL로 막지만, 누르기 전에 알려 준다.
   */
  async function checkMealTaken() {
    const seq = ++state.takenSeq;
    const date = state.partyDate;
    const mealType = state.mealType;
    state.mealTaken = false;
    try {
      const result = await partyApi.getList({ date, mealType });
      if (seq !== state.takenSeq) return;
      const mine = result.parties.find((p) => p.joined);
      state.mealTaken = !!mine;
      if (mine) {
        setMessage(timeMessage,
          '이미 ' + partyApi.shortDateLabel(date) + ' ' + partyApi.MEAL_TYPE_LABELS[mealType] + ' 식사팟(' + mine.restaurantName +
          ')에 참여 중이에요. 다른 날짜나 끼니를 골라 주세요.', TIME_HINT);
      }
    } catch (err) {
      // 확인을 못 해도 만들기는 서버가 다시 검사한다
    }
    if (seq === state.takenSeq) updateSubmit();
  }

  /**
   * 끼니의 30분 단위 시간 중 고를 수 있는 것 (서버도 같은 규칙으로 검사한다)
   * 오늘이면 지금 이후만, 미래 날짜면 전부
   */
  function availableTimes(mealType, date) {
    const target = date || state.partyDate;
    if (!partyApi.isToday(target)) {
      return TIME_OPTIONS[mealType].slice();
    }
    const now = new Date();
    const nowMinutes = now.getHours() * 60 + now.getMinutes();
    return TIME_OPTIONS[mealType].filter((time) => {
      const [h, m] = time.split(':').map(Number);
      return h * 60 + m > nowMinutes;
    });
  }

  /* ---------- 정원 ---------- */

  function changeCapacity(step) {
    state.capacity = Math.min(CAPACITY_MAX, Math.max(CAPACITY_MIN, state.capacity + step));
    renderCapacity();
  }

  function renderCapacity() {
    capacityValue.textContent = state.capacity + '명';
    stepButtons[0].disabled = state.capacity <= CAPACITY_MIN;
    stepButtons[1].disabled = state.capacity >= CAPACITY_MAX;
  }

  /* ---------- 만들기 ---------- */

  /** 식당과 시간이 있어야 [만들기] 활성 */
  function updateSubmit() {
    submitButton.disabled = state.submitting || state.mealTaken || !state.restaurant || !timeSelect.value;
  }

  async function onSubmit(event) {
    event.preventDefault();
    if (submitButton.disabled) return;

    formMessage.textContent = '';
    state.submitting = true;
    updateSubmit();
    try {
      const result = await partyApi.create({
        restaurantId: state.restaurant.restaurantId,
        partyDate: state.partyDate,
        mealType: state.mealType,
        mealTime: timeSelect.value,
        capacity: state.capacity,
        memo: memoInput.value.trim() || null,
        isPrivate: privateToggle.checked,
      });
      location.href = partyApi.detailUrl(result.partyId); // 방장이자 첫 참여자로 상세 화면
    } catch (err) {
      state.submitting = false;
      updateSubmit();
      if (err.statusCode === 401) return;
      if (err.error === 'INVALID_MEAL_TIME') {
        // 화면을 열어 둔 사이 시간이 지났을 수 있다 → 시간 목록을 새로 만든다
        initMealType();
        setMessage(timeMessage, err.message + ' 시간을 다시 골라 주세요.', TIME_HINT);
        timeSelect.focus();
      } else if (err.error === 'INVALID_REQUEST' && !partyApi.isSelectableDate(state.partyDate)) {
        // 화면을 열어 둔 사이 날짜가 바뀌어 범위를 벗어난 경우 → 달력 범위를 새로 맞춘다
        initDate();
        setMessage(dateMessage, '날짜가 바뀌었어요. 날짜를 다시 골라 주세요.', DATE_HINT);
      } else if (partyJoin.isSameMealError(err)) {
        // 같은 날짜·끼니에 이미 참여 중인 팟이 있음: 팝업으로 안내 (닫으면 날짜·끼니를 다시 고를 수 있게 그대로 둔다)
        partyJoin.sameMealNotice({
          partyDate: state.partyDate,
          mealType: state.mealType,
          cancelText: '다른 날짜·끼니 고르기',
        });
      } else if (err.error === 'RESTAURANT_NOT_FOUND') {
        clearRestaurant();
        setMessage(restaurantMessage, err.message, RESTAURANT_HINT);
      } else {
        formMessage.textContent = err.message;
      }
    }
  }

  /** 입력칸 아래 문구: 오류면 빨간 글씨, 없으면 원래 안내 문구로 */
  function setMessage(el, errorText, hintText) {
    el.textContent = errorText || hintText;
    el.classList.toggle('field__message--error', !!errorText);
  }
})();
