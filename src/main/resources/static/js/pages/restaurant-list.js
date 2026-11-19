/*
 * restaurant-list.js — S-04 식당 목록 (B)
 *
 * 하는 일
 *   1. 검색어(Enter 또는 [검색])와 카테고리 칩(누르는 즉시)으로 목록을 다시 불러온다.
 *   2. 조건은 주소(?keyword=&category=)에도 남긴다. 상세에서 [뒤로]로 돌아와도 조건이 유지된다.
 *   3. 로딩 / 결과 없음 / 오류 화면을 보여 준다.
 *
 * 지도(F-17)는 나중에 이 파일에서 renderList와 같은 데이터로 핀을 그린다.
 */
(function () {
  'use strict';

  const CATEGORY_LABELS = restaurantApi.CATEGORY_LABELS;
  const KEYWORD_MAX = 30;

  const els = {
    form: document.getElementById('search-form'),
    keyword: document.getElementById('search-keyword'),
    chips: document.getElementById('category-chips'),
    summary: document.getElementById('result-summary'),
    result: document.getElementById('restaurant-result'),
  };

  // 현재 조회 조건
  const condition = { keyword: '', category: '' };

  // 빠르게 칩을 여러 번 누르면 응답 순서가 뒤바뀔 수 있다. 마지막 요청의 응답만 화면에 그린다.
  let requestSeq = 0;

  /* ---------- 시작 ---------- */

  readConditionFromUrl();
  renderChips();
  bindEvents();
  loadList();

  /* ---------- 조건 ---------- */

  function readConditionFromUrl() {
    const params = new URLSearchParams(location.search);
    const keyword = (params.get('keyword') || '').trim().slice(0, KEYWORD_MAX);
    const category = params.get('category') || '';

    condition.keyword = keyword;
    condition.category = Object.prototype.hasOwnProperty.call(CATEGORY_LABELS, category) ? category : '';
    els.keyword.value = condition.keyword;
  }

  function writeConditionToUrl() {
    const params = new URLSearchParams();
    if (condition.keyword) params.set('keyword', condition.keyword);
    if (condition.category) params.set('category', condition.category);
    const query = params.toString();
    history.replaceState(null, '', location.pathname + (query ? '?' + query : ''));
  }

  /* ---------- 이벤트 ---------- */

  function bindEvents() {
    // Enter 또는 [검색]
    els.form.addEventListener('submit', (event) => {
      event.preventDefault();
      condition.keyword = els.keyword.value.trim();
      loadList();
    });

    // 카테고리 칩: 하나만 선택, 누르는 즉시 조회
    els.chips.addEventListener('click', (event) => {
      const chip = event.target.closest('.chip');
      if (!chip) return;
      const category = chip.dataset.category;
      if (category === condition.category) return;
      condition.category = category;
      renderChips();
      loadList();
    });

    // 오류 화면의 [다시 시도]
    els.result.addEventListener('click', (event) => {
      if (event.target.closest('[data-action="retry"]')) loadList();
    });
  }

  /* ---------- 조회 ---------- */

  async function loadList() {
    const seq = ++requestSeq;
    writeConditionToUrl();
    showLoading();

    try {
      const list = await restaurantApi.getList({
        keyword: condition.keyword,
        category: condition.category,
      });
      if (seq !== requestSeq) return;
      renderList(list);
    } catch (err) {
      if (seq !== requestSeq) return;
      if (err.statusCode === 401) return; // client.js가 로그인 화면으로 이동시킨다
      showError(err.message);
    }
  }

  /* ---------- 그리기 ---------- */

  function renderChips() {
    const items = [['', '전체']].concat(Object.entries(CATEGORY_LABELS));
    els.chips.innerHTML = items
      .map(([value, label]) => {
        const selected = value === condition.category;
        return (
          '<button type="button" class="chip' + (selected ? ' is-selected' : '') + '"' +
          ' data-category="' + value + '" aria-pressed="' + selected + '">' +
          label +
          '</button>'
        );
      })
      .join('');
  }

  function renderList(list) {
    els.summary.textContent = '총 ' + list.length + '곳 · 따봉순';

    if (list.length === 0) {
      els.result.innerHTML =
        '<div class="state state--empty">' +
        '<p class="state__title">조건에 맞는 식당이 없습니다</p>' +
        '<p class="state__desc">검색어나 카테고리를 바꿔 보세요.</p>' +
        '</div>';
      return;
    }

    els.result.innerHTML =
      '<ul class="restaurant-list">' + list.map(renderItem).join('') + '</ul>';
  }

  function renderItem(r) {
    const esc = utils.escapeHtml;
    const categoryText = CATEGORY_LABELS[r.category] || r.category;
    const meta = r.cafeteria ? categoryText + ' · 기본 등록' : categoryText;

    return (
      '<li>' +
      '<a class="restaurant-item" href="/restaurant-detail.html?id=' + encodeURIComponent(r.restaurantId) + '">' +
      '<span class="restaurant-item__info">' +
      '<span class="restaurant-item__name">' + esc(r.name) + '</span>' +
      '<span class="restaurant-item__meta">' + esc(meta) + '</span>' +
      '</span>' +
      '<span class="restaurant-item__stats">' +
      utils.thumbsUpIcon() +
      '<span class="sr-only">최근 30일 따봉</span>' + r.thumbsCount30d +
      ' · 리뷰 ' + r.reviewCount +
      '</span>' +
      '</a>' +
      '</li>'
    );
  }

  function showLoading() {
    els.summary.textContent = '';
    els.result.innerHTML =
      '<div class="state state--loading">' +
      '<span class="spinner" aria-hidden="true"></span>' +
      '<p class="state__desc">식당을 불러오는 중…</p>' +
      '</div>';
  }

  function showError(message) {
    els.summary.textContent = '';
    els.result.innerHTML =
      '<div class="state state--error">' +
      '<p class="state__title">식당 목록을 불러오지 못했습니다</p>' +
      '<p class="state__desc">' + utils.escapeHtml(message) + '</p>' +
      '<button type="button" class="btn" data-action="retry">다시 시도</button>' +
      '</div>';
  }
})();
