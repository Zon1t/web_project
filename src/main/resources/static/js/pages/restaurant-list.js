/*
 * restaurant-list.js — S-04 식당 목록 (B)
 *
 * 하는 일
 *   1. 검색어(Enter 또는 [검색])와 카테고리 칩(누르는 즉시)으로 목록을 다시 불러온다.
 *   2. 조건은 주소(?keyword=&category=)에도 남긴다. 상세에서 [뒤로]로 돌아와도 조건이 유지된다.
 *   3. 로딩 / 결과 없음 / 오류 화면을 보여 준다.
 *
 *   4. 같은 목록으로 오른쪽 지도 핀을 그리고, 목록 항목에 마우스를 올리면 핀을 강조한다. (restaurant-map.js)
 *   5. (2026-11-24) 정렬: [가까운 순](기본) / [따봉 순]. 가까운 순은 회사(구내식당 좌표)에서의 거리를 화면에서 계산한다.
 *      서버는 따봉 순으로 주므로 따봉 순은 받은 순서 그대로. 고른 정렬은 브라우저에 기억한다.
 *      식당마다 회사까지 거리를 보여 준다. 좌표가 없는 식당은 맨 아래.
 *   6. (2026-11-24) 카테고리 칩에 마우스를 올리면 음식 예시 툴팁, 고른 카테고리의 예시는 칩 아래 한 줄로.
 *   7. (2026-11-25) 좁은 화면(900px 이하)에서는 목록과 지도 중 하나만 크게 보여 주고 [지도 보기]/[목록 보기]로 바꾼다.
 *      JS는 <main data-view>만 바꾸고, 실제로 숨기는 건 CSS(@media)가 한다 → 넓은 화면에서는 값과 상관없이 둘 다 보인다.
 *      고른 보기는 이번 방문 동안만 기억한다(sessionStorage). 상세 카드를 보고 돌아와도 지도 화면이 유지된다.
 */
(function () {
  'use strict';

  const CATEGORY_LABELS = restaurantApi.CATEGORY_LABELS;
  const CATEGORY_EXAMPLES = restaurantApi.CATEGORY_EXAMPLES;
  const KEYWORD_MAX = 30;
  const SORT_KEY = 'sseukbap.restaurantList.sort';
  const VIEW_KEY = 'sseukbap.restaurantList.view';
  const VIEW_LABELS = {
    list: { next: 'map', icon: 'map', text: '지도 보기' },    // 지금 목록 → 버튼은 지도로
    map: { next: 'list', icon: 'list', text: '목록 보기' },   // 지금 지도 → 버튼은 목록으로
  };

  const els = {
    form: document.getElementById('search-form'),
    keyword: document.getElementById('search-keyword'),
    chips: document.getElementById('category-chips'),
    categoryHint: document.getElementById('category-hint'),
    summary: document.getElementById('result-summary'),
    sortToggle: document.getElementById('sort-toggle'),
    result: document.getElementById('restaurant-result'),
    page: document.getElementById('restaurant-page'),
    viewToggle: document.getElementById('view-toggle'),
  };

  // 현재 조회 조건
  const condition = { keyword: '', category: '' };

  // 정렬 ('distance' | 'thumbs')와 마지막으로 받은 목록 (정렬만 바꿀 때 다시 불러오지 않는다)
  let sort = readSort();
  let lastList = null;
  // 회사 위치: 목록에 구내식당이 있으면 그 좌표로 바꾼다 (카테고리로 거르면 구내식당이 빠지므로 기억해 둔다)
  let office = restaurantApi.OFFICE_LOCATION;

  // 빠르게 칩을 여러 번 누르면 응답 순서가 뒤바뀔 수 있다. 마지막 요청의 응답만 화면에 그린다.
  let requestSeq = 0;

  // 식당 상세 카드 (아래 '식당 상세 카드' 참고)
  let detailModal = null;
  let detailPushed = false;   // 카드를 열면서 주소 기록을 하나 추가했는지 (닫을 때 뒤로가기로 되돌림)

  /* ---------- 시작 ---------- */

  readConditionFromUrl();
  renderChips();
  renderSortToggle();
  bindEvents();
  restaurantMap.init(document.getElementById('restaurant-map')); // 지도는 목록과 따로 불러온다
  restaurantMap.onHover(highlightFromMap);                       // 지도 핀에 마우스 → 목록 항목 강조
  restaurantMap.onSelect((id) => openDetail(id));                // 지도 핀 클릭 → 식당 상세 카드
  setView(readView(), false);
  els.viewToggle.addEventListener('click', () => setView(VIEW_LABELS[els.page.dataset.view].next, true));
  loadList();
  openDetailFromUrl();

  /* ---------- 좁은 화면: 목록 / 지도 보기 전환 (2026-11-25) ---------- */

  function readView() {
    try {
      return sessionStorage.getItem(VIEW_KEY) === 'map' ? 'map' : 'list';
    } catch (e) {
      return 'list';
    }
  }

  /** view: 'list' | 'map'. byUser면 화면 맨 위로 올리고 고른 값을 기억한다 */
  function setView(view, byUser) {
    const label = VIEW_LABELS[view];
    els.page.dataset.view = view;
    els.viewToggle.innerHTML = ui.icon(label.icon, 18) + '<span>' + label.text + '</span>';
    if (view === 'map') restaurantMap.refresh(); // 숨겨져 있던 지도는 칸 크기를 다시 재야 제대로 그려진다
    if (!byUser) return;
    window.scrollTo(0, 0);
    try {
      sessionStorage.setItem(VIEW_KEY, view);
    } catch (e) {
      // 기억하지 못해도 전환은 된다
    }
  }

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
    // 상세 카드가 열려 있으면 주소의 restaurantId는 남겨 둔다
    const openId = new URLSearchParams(location.search).get('restaurantId');
    if (openId) params.set('restaurantId', openId);
    const query = params.toString();
    history.replaceState(history.state, '', location.pathname + (query ? '?' + query : ''));
  }

  /* ---------- 식당 상세 카드 (2026-11-20) ----------
   * 목록 항목이나 지도 핀을 누르면 지도 위에 식당 상세 카드가 뜬다. 뒤 지도는 어둡고 흐려진다.
   * 카드 안은 기존 식당 상세 화면(restaurant-detail.html?embed=1)을 헤더·푸터 없이 그대로 쓴다.
   * 주소에 ?restaurantId=가 붙어서 링크 공유·뒤로가기로 닫기가 된다. [X]·Esc·바깥 클릭으로 닫으면 지도로 돌아온다.
   */
  function openDetail(restaurantId, fromHistory) {
    const id = String(restaurantId);
    if (detailModal) detailModal.close('replace');

    detailModal = ui.modal({
      size: 'xl',
      flush: true,
      html:
        '<div class="rd-frame">' +
        '<div class="rd-frame__loading">' + ui.skeleton('row', 5) + '</div>' +
        '<iframe class="rd-frame__iframe" title="식당 상세" src="/restaurant-detail.html?id=' + encodeURIComponent(id) + '&embed=1"></iframe>' +
        '</div>',
      onClose: (reason) => {
        detailModal = null;
        if (reason === 'replace' || reason === 'popstate') return;
        if (detailPushed) {
          detailPushed = false;
          history.back();
        } else {
          setDetailInUrl(null, false);
        }
      },
    });
    const frame = detailModal.body.querySelector('.rd-frame');
    frame.querySelector('iframe').addEventListener('load', () => frame.classList.add('is-loaded'));

    if (!fromHistory) {
      setDetailInUrl(id, true);
      detailPushed = true;
    }
  }

  function openDetailFromUrl() {
    const id = new URLSearchParams(location.search).get('restaurantId');
    if (id) openDetail(id, true);
  }

  function setDetailInUrl(id, push) {
    const params = new URLSearchParams(location.search);
    if (id) params.set('restaurantId', id); else params.delete('restaurantId');
    const query = params.toString();
    const url = location.pathname + (query ? '?' + query : '');
    if (push) history.pushState({ restaurantId: id }, '', url);
    else history.replaceState(null, '', url);
  }

  // 브라우저 뒤로가기·앞으로가기
  window.addEventListener('popstate', () => {
    const id = new URLSearchParams(location.search).get('restaurantId');
    if (id && !detailModal) {
      openDetail(id, true);
    } else if (!id && detailModal) {
      detailPushed = false;
      detailModal.close('popstate');
    }
  });

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

    // 정렬: 다시 불러오지 않고 받은 목록을 다시 그린다
    els.sortToggle.addEventListener('click', (event) => {
      const button = event.target.closest('[data-sort]');
      if (!button || button.dataset.sort === sort) return;
      sort = button.dataset.sort;
      saveSort(sort);
      renderSortToggle();
      if (lastList) renderList(lastList);
    });

    // 오류 화면의 [다시 시도] / 목록 항목 → 식당 상세 카드 (Ctrl·Shift 클릭은 새 탭 등 기본 동작)
    els.result.addEventListener('click', (event) => {
      if (event.target.closest('[data-action="retry"]')) loadList();
      const item = event.target.closest('.restaurant-item');
      if (item && !event.ctrlKey && !event.metaKey && !event.shiftKey && event.button === 0) {
        event.preventDefault();
        openDetail(decodeURIComponent(item.dataset.restaurantId));
      }
    });

    // 목록 항목에 마우스를 올리거나 키보드로 포커스하면 지도 핀 강조
    els.result.addEventListener('mouseover', (event) => highlightItem(event.target.closest('.restaurant-item')));
    els.result.addEventListener('focusin', (event) => highlightItem(event.target.closest('.restaurant-item')));
    els.result.addEventListener('mouseleave', () => highlightItem(null));
    els.result.addEventListener('focusout', (event) => {
      if (!els.result.contains(event.relatedTarget)) highlightItem(null);
    });
  }

  function highlightItem(item) {
    els.result.querySelectorAll('.restaurant-item.is-highlighted').forEach((el) => {
      if (el !== item) el.classList.remove('is-highlighted');
    });
    if (item) item.classList.add('is-highlighted');
    restaurantMap.highlight(item ? item.dataset.restaurantId : null);
  }

  // 지도 핀에 마우스를 올리면 목록 항목도 강조하고, 목록 밖에 있으면 보이게 스크롤
  function highlightFromMap(restaurantId) {
    const item = restaurantId == null ? null
      : els.result.querySelector('.restaurant-item[data-restaurant-id="' + restaurantId + '"]');
    els.result.querySelectorAll('.restaurant-item.is-highlighted').forEach((el) => {
      if (el !== item) el.classList.remove('is-highlighted');
    });
    if (item) {
      item.classList.add('is-highlighted');
      item.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
    }
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
        const examples = CATEGORY_EXAMPLES[value] || '';
        return (
          '<button type="button" class="chip' + (selected ? ' is-selected' : '') + '"' +
          ' data-category="' + value + '" aria-pressed="' + selected + '"' +
          (examples ? ' data-examples="' + utils.escapeHtml(label + ': ' + examples) + '"' : '') + '>' +
          label +
          '</button>'
        );
      })
      .join('');
    const hint = condition.category ? CATEGORY_EXAMPLES[condition.category] : '';
    els.categoryHint.textContent = hint ? CATEGORY_LABELS[condition.category] + ': ' + hint : '';
  }

  function renderSortToggle() {
    els.sortToggle.querySelectorAll('[data-sort]').forEach((button) => {
      button.setAttribute('aria-pressed', String(button.dataset.sort === sort));
    });
  }

  function readSort() {
    try {
      return localStorage.getItem(SORT_KEY) === 'thumbs' ? 'thumbs' : 'distance';
    } catch (e) {
      return 'distance';
    }
  }

  function saveSort(value) {
    try {
      localStorage.setItem(SORT_KEY, value);
    } catch (e) {
      // 저장소를 못 쓰는 브라우저면 기억만 안 한다
    }
  }

  // 식당마다 회사까지 거리(km)를 붙인다. 좌표가 없으면 null
  function withDistance(list) {
    const cafeteria = list.find((r) => r.cafeteria && typeof r.lat === 'number' && typeof r.lng === 'number');
    if (cafeteria) office = { lat: cafeteria.lat, lng: cafeteria.lng };
    return list.map((r, index) => ({
      ...r,
      order: index, // 서버 순서 (따봉 순)
      distanceKm: utils.distanceKm(office.lat, office.lng, r.lat, r.lng),
    }));
  }

  function sortList(list) {
    if (sort !== 'distance') return list;
    return list.slice().sort((a, b) => {
      if (a.distanceKm === null && b.distanceKm === null) return a.order - b.order;
      if (a.distanceKm === null) return 1;   // 좌표 없는 식당은 맨 아래
      if (b.distanceKm === null) return -1;
      return a.distanceKm - b.distanceKm || a.order - b.order;
    });
  }

  function renderList(rawList) {
    lastList = rawList;
    const list = sortList(withDistance(rawList));
    els.summary.textContent = '총 ' + list.length + '곳 · ' + (sort === 'distance' ? '회사에서 가까운 순' : '최근 30일 따봉 순');
    restaurantMap.highlight(null);
    restaurantMap.setRestaurants(list); // 지도 핀도 같은 목록으로

    if (list.length === 0) {
      els.result.innerHTML = ui.empty({
        illust: 'map',
        title: '조건에 맞는 식당이 없어요',
        desc: '검색어나 카테고리를 바꾸거나, 새 식당을 등록해 보세요.',
        actionHtml: '<a class="btn btn--primary" href="/restaurant-register.html">식당 등록</a>',
      });
      return;
    }

    els.result.innerHTML =
      '<ul class="restaurant-list">' + list.map(renderItem).join('') + '</ul>';
  }

  function renderItem(r) {
    const esc = utils.escapeHtml;
    const categoryText = CATEGORY_LABELS[r.category] || r.category;
    const distanceText = r.cafeteria ? '회사 안' : (r.distanceKm === null ? '거리 정보 없음' : utils.formatDistance(r.distanceKm));
    const meta = (r.cafeteria ? categoryText + ' · 기본 등록' : categoryText) + ' · ' + distanceText;

    return (
      '<li>' +
      '<a class="restaurant-item" href="/restaurant-detail.html?id=' + encodeURIComponent(r.restaurantId) + '"' +
      ' data-restaurant-id="' + encodeURIComponent(r.restaurantId) + '">' +
      ui.avatar(r.cafeteria ? 'CAFETERIA' : r.category, 40) +
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
    els.result.innerHTML = ui.skeleton('row', 5);
  }

  function showError(message) {
    els.summary.textContent = '';
    els.result.innerHTML =
      '<div class="state state--error">' +
      '<p class="state__title">식당 목록을 불러오지 못했어요</p>' +
      '<p class="state__desc">' + utils.escapeHtml(message) + '</p>' +
      '<button type="button" class="btn" data-action="retry">다시 시도</button>' +
      '</div>';
  }
})();
