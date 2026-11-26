/*
 * admin.js — 관리자 화면 (A, 2026-11-24 A3)
 *
 * 탭 4개 (선택한 탭은 주소 #restaurants 등으로 기억 → 새로고침해도 유지)
 *  - 식당: 전체 식당(숨김 포함) + 회사까지 거리. [4km 밖]·[숨김] 필터, 이름 검색, 카테고리 변경, 숨기기/다시 보이기
 *  - 수정 제안: 처리 전/처리 완료/전체, [처리 완료]·[되돌리기]
 *  - 리뷰: 최근 100건(숨김 포함), 검색, 숨기기/다시 보이기
 *  - 구내식당 메뉴: 날짜를 골라 점심·저녁 메뉴를 한 줄에 하나씩 입력 → 저장(그 끼니 교체)
 * 관리자가 아니면 API가 403을 주므로 안내 화면을 보여 준다.
 */
(function () {
  'use strict';

  const esc = utils.escapeHtml;
  const CATEGORY_LABELS = restaurantApi.CATEGORY_LABELS;
  const EDITABLE_CATEGORIES = ['KOREAN', 'CHINESE', 'JAPANESE', 'WESTERN', 'SNACK', 'CAFE', 'ETC'];
  const TABS = ['restaurants', 'suggestions', 'reviews', 'cafeteria'];
  /** 식당 상세 주소. 상세 화면은 ?id=를 읽는다 (restaurant-detail.js, 다른 화면과 같은 형식) */
  const DETAIL_URL = '/restaurant-detail.html?id=';

  const tabsEl = document.getElementById('admin-tabs');
  const panel = document.getElementById('admin-panel');
  const tabs = Array.from(tabsEl.querySelectorAll('.admin-tab'));

  let current = null;
  // 탭별 화면 상태
  const state = {
    restaurants: { items: [], radiusKm: 4, filter: 'all', keyword: '' },
    suggestions: { status: 'OPEN' },
    reviews: { keyword: '' },
    cafeteria: { date: todayString() },
  };

  tabsEl.addEventListener('click', (event) => {
    const tab = event.target.closest('.admin-tab');
    if (tab) select(tab.dataset.tab);
  });
  tabsEl.addEventListener('keydown', (event) => {
    if (event.key !== 'ArrowRight' && event.key !== 'ArrowLeft') return;
    const i = TABS.indexOf(current);
    const next = TABS[(i + (event.key === 'ArrowRight' ? 1 : TABS.length - 1)) % TABS.length];
    select(next);
    tabs[TABS.indexOf(next)].focus();
  });

  const initial = location.hash.slice(1);
  select(TABS.includes(initial) ? initial : 'restaurants');

  function select(name) {
    current = name;
    tabs.forEach((tab) => {
      const on = tab.dataset.tab === name;
      tab.classList.toggle('is-selected', on);
      tab.setAttribute('aria-selected', String(on));
      tab.tabIndex = on ? 0 : -1;
    });
    panel.setAttribute('aria-labelledby', 'tab-' + name);
    history.replaceState(null, '', '#' + name);
    ({ restaurants: loadRestaurants, suggestions: loadSuggestions, reviews: loadReviews, cafeteria: loadCafeteria })[name]();
  }

  /* ===================== 식당 ===================== */

  async function loadRestaurants() {
    panel.innerHTML = ui.skeleton('row', 5);
    try {
      const data = await adminApi.getRestaurants();
      if (current !== 'restaurants') return;
      state.restaurants.items = data.items;
      state.restaurants.radiusKm = data.radiusKm;
      drawRestaurants();
    } catch (err) {
      showError(err, loadRestaurants);
    }
  }

  function drawRestaurants() {
    const s = state.restaurants;
    const outCount = s.items.filter((r) => r.outOfRange).length;
    const hiddenCount = s.items.filter((r) => r.hidden).length;
    panel.innerHTML =
      '<div class="admin-toolbar">' +
      '<div class="admin-chips" role="group" aria-label="식당 필터">' +
      chip('all', '전체 ' + s.items.length, s.filter) +
      chip('out', s.radiusKm + 'km 밖 ' + outCount, s.filter) +
      chip('hidden', '숨김 ' + hiddenCount, s.filter) +
      '</div>' +
      '<input class="input admin-search" type="search" placeholder="식당 이름 검색" aria-label="식당 이름 검색" value="' + esc(s.keyword) + '">' +
      '</div>' +
      '<div id="admin-list"></div>';

    panel.querySelector('.admin-chips').addEventListener('click', (event) => {
      const button = event.target.closest('[data-filter]');
      if (!button) return;
      s.filter = button.dataset.filter;
      drawRestaurants();
    });
    const search = panel.querySelector('.admin-search');
    search.addEventListener('input', () => {
      s.keyword = search.value.trim();
      drawRestaurantRows();
    });
    drawRestaurantRows();
  }

  function drawRestaurantRows() {
    const s = state.restaurants;
    const list = document.getElementById('admin-list');
    const rows = s.items
      .filter((r) => s.filter === 'all' || (s.filter === 'out' ? r.outOfRange : r.hidden))
      .filter((r) => !s.keyword || r.name.includes(s.keyword));
    if (!rows.length) {
      list.innerHTML = ui.empty({ title: '조건에 맞는 식당이 없어요.' });
      return;
    }
    list.innerHTML = '<ul class="admin-list">' + rows.map(restaurantRow).join('') + '</ul>';
    list.querySelector('.admin-list').addEventListener('click', onRestaurantClick);
    list.querySelector('.admin-list').addEventListener('change', onCategoryChange);
  }

  function restaurantRow(r) {
    const distance = r.distanceKm == null ? '거리 정보 없음' : r.distanceKm.toFixed(2) + 'km';
    const options = r.cafeteria
      ? '<option>' + esc(CATEGORY_LABELS.CAFETERIA) + '</option>'
      : EDITABLE_CATEGORIES.map((c) =>
        '<option value="' + c + '"' + (c === r.category ? ' selected' : '') + '>' + esc(CATEGORY_LABELS[c] || c) + '</option>').join('');
    return '<li class="admin-item' + (r.hidden ? ' is-hidden' : '') + '" data-id="' + r.restaurantId + '">' +
      '<div class="admin-item__main">' +
      '<p class="admin-item__title">' +
      (r.hidden ? esc(r.name) : '<a href="' + DETAIL_URL + r.restaurantId + '">' + esc(r.name) + '</a>') +
      (r.hidden ? ' <span class="badge admin-badge admin-badge--hidden">숨김</span>' : '') +
      (r.outOfRange ? ' <span class="badge admin-badge admin-badge--warn">' + state.restaurants.radiusKm + 'km 밖</span>' : '') +
      '</p>' +
      '<p class="admin-item__meta">' + esc(distance) + ' · 리뷰 ' + r.reviewCount +
      ' · 등록 ' + esc(r.registeredBy || '초기 데이터') + '</p>' +
      (r.address ? '<p class="admin-item__meta">' + esc(r.address) + '</p>' : '') +
      '</div>' +
      '<div class="admin-item__actions">' +
      '<select class="select admin-select" data-action="category" aria-label="' + esc(r.name) + ' 카테고리"' +
      (r.cafeteria ? ' disabled' : '') + '>' + options + '</select>' +
      (r.cafeteria ? '' : '<button type="button" class="btn ' + (r.hidden ? '' : 'btn--danger') + '" data-action="toggle-hidden">' +
        (r.hidden ? '다시 보이기' : '숨기기') + '</button>') +
      '</div>' +
      '</li>';
  }

  async function onRestaurantClick(event) {
    const button = event.target.closest('[data-action="toggle-hidden"]');
    if (!button) return;
    const item = findRestaurant(button);
    const hide = !item.hidden;
    if (hide) {
      const ok = await ui.confirm({
        title: '식당 숨기기',
        message: '‘' + item.name + '’을(를) 숨길까요? 목록·지도·랭킹·룰렛에서 보이지 않아요. 진행 중인 식사팟은 그대로 남아요.',
        confirmText: '숨기기',
        danger: true,
      });
      if (!ok) return;
    }
    await updateRestaurant(item, { hidden: hide }, hide ? '식당을 숨겼어요.' : '식당을 다시 보이게 했어요.', button);
  }

  async function onCategoryChange(event) {
    const select = event.target.closest('[data-action="category"]');
    if (!select) return;
    const item = findRestaurant(select);
    await updateRestaurant(item, { category: select.value }, '카테고리를 바꿨어요.', select);
  }

  async function updateRestaurant(item, body, message, control) {
    control.disabled = true;
    try {
      const updated = await adminApi.updateRestaurant(item.restaurantId, body);
      const items = state.restaurants.items;
      items[items.indexOf(item)] = updated;
      ui.toast(message);
      drawRestaurants();
    } catch (err) {
      ui.toast(err.message, { type: 'error' });
      control.disabled = false;
      drawRestaurantRows();
    }
  }

  function findRestaurant(el) {
    const id = Number(el.closest('.admin-item').dataset.id);
    return state.restaurants.items.find((r) => r.restaurantId === id);
  }

  /* ===================== 수정 제안 ===================== */

  async function loadSuggestions() {
    const s = state.suggestions;
    panel.innerHTML =
      '<div class="admin-toolbar">' +
      '<div class="admin-chips" role="group" aria-label="처리 상태">' +
      chip('OPEN', '처리 전', s.status) + chip('DONE', '처리 완료', s.status) + chip('ALL', '전체', s.status) +
      '</div></div><div id="admin-list">' + ui.skeleton('row', 3) + '</div>';
    panel.querySelector('.admin-chips').addEventListener('click', (event) => {
      const button = event.target.closest('[data-filter]');
      if (!button) return;
      s.status = button.dataset.filter;
      loadSuggestions();
    });

    try {
      const items = await adminApi.getSuggestions(s.status);
      if (current !== 'suggestions') return;
      const list = document.getElementById('admin-list');
      if (!items.length) {
        list.innerHTML = ui.empty({ title: s.status === 'OPEN' ? '처리할 수정 제안이 없어요.' : '수정 제안이 없어요.' });
        return;
      }
      list.innerHTML = '<ul class="admin-list">' + items.map(suggestionRow).join('') + '</ul>';
      list.querySelector('.admin-list').addEventListener('click', onSuggestionClick);
    } catch (err) {
      showError(err, loadSuggestions);
    }
  }

  function suggestionRow(item) {
    const done = item.status === 'DONE';
    return '<li class="admin-item' + (done ? ' is-done' : '') + '" data-id="' + item.suggestionId + '">' +
      '<div class="admin-item__main">' +
      '<p class="admin-item__title"><a href="' + DETAIL_URL + item.restaurantId + '">' +
      esc(item.restaurantName) + '</a>' +
      (done ? ' <span class="badge admin-badge admin-badge--done">처리 완료</span>' : '') + '</p>' +
      '<p class="admin-item__content">' + esc(item.content) + '</p>' +
      '<p class="admin-item__meta">' + esc(item.nickname) + ' · ' + esc(formatDateTime(item.createdAt)) + '</p>' +
      '</div>' +
      '<div class="admin-item__actions">' +
      '<button type="button" class="btn ' + (done ? '' : 'btn--primary') + '" data-status="' + (done ? 'OPEN' : 'DONE') + '">' +
      (done ? '되돌리기' : '처리 완료') + '</button>' +
      '</div></li>';
  }

  async function onSuggestionClick(event) {
    const button = event.target.closest('[data-status]');
    if (!button) return;
    const id = Number(button.closest('.admin-item').dataset.id);
    button.disabled = true;
    try {
      await adminApi.updateSuggestion(id, button.dataset.status);
      ui.toast(button.dataset.status === 'DONE' ? '처리 완료로 바꿨어요.' : '처리 전으로 되돌렸어요.');
      loadSuggestions();
    } catch (err) {
      ui.toast(err.message, { type: 'error' });
      button.disabled = false;
    }
  }

  /* ===================== 리뷰 ===================== */

  async function loadReviews() {
    const s = state.reviews;
    panel.innerHTML =
      '<form class="admin-toolbar" role="search">' +
      '<input class="input admin-search" type="search" name="keyword" maxlength="30" placeholder="리뷰 내용·식당·닉네임 검색" aria-label="리뷰 검색" value="' + esc(s.keyword) + '">' +
      '<button type="submit" class="btn">검색</button>' +
      '</form><p class="admin-note">최근 리뷰 100건까지 보여요. 숨긴 리뷰는 식당 화면·리뷰 수·태그 TOP 3에서 빠져요.</p>' +
      '<div id="admin-list">' + ui.skeleton('row', 4) + '</div>';
    panel.querySelector('form').addEventListener('submit', (event) => {
      event.preventDefault();
      s.keyword = event.target.keyword.value.trim();
      loadReviews();
    });

    try {
      const items = await adminApi.getReviews(s.keyword);
      if (current !== 'reviews') return;
      const list = document.getElementById('admin-list');
      if (!items.length) {
        list.innerHTML = ui.empty({ title: s.keyword ? '검색 결과가 없어요.' : '아직 리뷰가 없어요.' });
        return;
      }
      list.innerHTML = '<ul class="admin-list">' + items.map(reviewRow).join('') + '</ul>';
      list.querySelector('.admin-list').addEventListener('click', onReviewClick);
    } catch (err) {
      showError(err, loadReviews);
    }
  }

  function reviewRow(item) {
    return '<li class="admin-item' + (item.hidden ? ' is-hidden' : '') + '" data-id="' + item.reviewId + '" data-hidden="' + item.hidden + '">' +
      '<div class="admin-item__main">' +
      '<p class="admin-item__title">' + esc(item.restaurantName) +
      (item.hidden ? ' <span class="badge admin-badge admin-badge--hidden">숨김</span>' : '') +
      (item.hasImage ? ' <span class="badge admin-badge">사진</span>' : '') + '</p>' +
      '<p class="admin-item__content">' + esc(item.content) + '</p>' +
      '<p class="admin-item__meta">' + esc(item.nickname) + ' · ' + esc(formatDateTime(item.createdAt)) + '</p>' +
      '</div>' +
      '<div class="admin-item__actions">' +
      '<button type="button" class="btn ' + (item.hidden ? '' : 'btn--danger') + '" data-action="toggle-review">' +
      (item.hidden ? '다시 보이기' : '숨기기') + '</button>' +
      '</div></li>';
  }

  async function onReviewClick(event) {
    const button = event.target.closest('[data-action="toggle-review"]');
    if (!button) return;
    const row = button.closest('.admin-item');
    const hide = row.dataset.hidden !== 'true';
    button.disabled = true;
    try {
      const updated = await adminApi.updateReview(Number(row.dataset.id), hide);
      row.outerHTML = reviewRow(updated);
      ui.toast(hide ? '리뷰를 숨겼어요.' : '리뷰를 다시 보이게 했어요.');
    } catch (err) {
      ui.toast(err.message, { type: 'error' });
      button.disabled = false;
    }
  }

  /* ===================== 구내식당 메뉴 ===================== */

  async function loadCafeteria() {
    const s = state.cafeteria;
    panel.innerHTML =
      '<div class="admin-toolbar">' +
      '<label class="admin-date"><span>날짜</span><input class="input" type="date" id="admin-menu-date" value="' + esc(s.date) + '"></label>' +
      '</div>' +
      '<p class="admin-note">크롤링이 안 될 때 직접 입력해요. 한 줄에 메뉴 하나, 저장하면 그 끼니 메뉴가 통째로 바뀌어요. 비우고 저장하면 그 끼니 메뉴가 지워져요.</p>' +
      '<div id="admin-menus">' + ui.skeleton('card', 2) + '</div>';
    panel.querySelector('#admin-menu-date').addEventListener('change', (event) => {
      if (!event.target.value) return;
      s.date = event.target.value;
      loadCafeteria();
    });

    try {
      const data = await adminApi.getCafeteriaMenus(s.date);
      if (current !== 'cafeteria') return;
      const box = document.getElementById('admin-menus');
      box.innerHTML = '<div class="admin-menus">' +
        menuForm('LUNCH', '점심', data.lunch) + menuForm('DINNER', '저녁', data.dinner) + '</div>';
      box.querySelectorAll('form').forEach((form) => form.addEventListener('submit', onMenuSave));
    } catch (err) {
      showError(err, loadCafeteria);
    }
  }

  function menuForm(mealType, label, names) {
    const id = 'admin-menu-' + mealType.toLowerCase();
    return '<form class="admin-menu card" data-meal="' + mealType + '">' +
      '<label class="admin-menu__label" for="' + id + '">' + label + ' <span class="admin-menu__count">' + names.length + '개</span></label>' +
      '<textarea class="textarea admin-menu__input" id="' + id + '" rows="8" placeholder="예)\n현미밥\n김치찌개\n제육볶음">' +
      esc(names.join('\n')) + '</textarea>' +
      '<button type="submit" class="btn btn--primary">' + label + ' 저장</button>' +
      '</form>';
  }

  async function onMenuSave(event) {
    event.preventDefault();
    const form = event.target;
    const button = form.querySelector('button');
    const names = form.querySelector('textarea').value.split('\n').map((v) => v.trim()).filter(Boolean);
    if (names.length > 15) {
      ui.toast('메뉴는 한 끼에 15개까지 넣을 수 있어요.', { type: 'error' });
      return;
    }
    if (names.some((n) => n.length > 50)) {
      ui.toast('메뉴 이름은 50자까지 넣을 수 있어요.', { type: 'error' });
      return;
    }
    button.disabled = true;
    try {
      const saved = await adminApi.saveCafeteriaMenus(state.cafeteria.date, form.dataset.meal, names);
      form.querySelector('textarea').value = saved.menuNames.join('\n');
      form.querySelector('.admin-menu__count').textContent = saved.menuNames.length + '개';
      ui.toast(saved.menuNames.length ? '메뉴를 저장했어요.' : '메뉴를 지웠어요.');
    } catch (err) {
      ui.toast(err.message, { type: 'error' });
    } finally {
      button.disabled = false;
    }
  }

  /* ===================== 공통 ===================== */

  function chip(value, label, selected) {
    return '<button type="button" class="chip' + (value === selected ? ' is-selected' : '') + '" data-filter="' + value + '"' +
      ' aria-pressed="' + (value === selected) + '">' + esc(label) + '</button>';
  }

  function showError(err, retry) {
    if (err.statusCode === 403 || err.error === 'FORBIDDEN') {
      tabsEl.hidden = true;
      panel.innerHTML = ui.empty({
        title: '관리자만 볼 수 있는 화면이에요.',
        desc: '관리자 계정으로 로그인해 주세요.',
        actionHtml: '<a class="btn btn--primary" href="/">홈으로</a>',
      });
      return;
    }
    panel.innerHTML = '<div class="state state--error"><p class="state__title">' + esc(err.message || '불러오지 못했어요.') +
      '</p><button type="button" class="btn" data-retry>다시 시도</button></div>';
    panel.querySelector('[data-retry]').addEventListener('click', retry);
  }

  function formatDateTime(value) {
    if (!value) return '';
    return String(value).slice(0, 16).replace('T', ' ');
  }

  function todayString() {
    const d = new Date();
    return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
  }
})();
