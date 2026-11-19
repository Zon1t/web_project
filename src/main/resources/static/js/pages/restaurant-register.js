/*
 * restaurant-register.js — S-06 식당 등록 (B)
 *
 * 순서: 네이버 지도 URL → 식당 이름 → 카테고리
 *   1. 이름을 입력하는 동안 이미 등록된 비슷한 이름을 보여 준다. (자동완성 API, 중복 등록 방지)
 *   2. URL과 이름을 다 입력하면(입력칸을 벗어날 때) 서버에서 확인한다. (check-map-url)
 *      네이버에 URL로 장소를 찾는 공식 API가 없어서 이름으로 검색한다 → 찾은 주소를 보여 주고 사용자가 확인한다.
 *   3. 확인 결과가 정상이고 그 뒤로 URL·이름을 바꾸지 않았고 카테고리를 골랐을 때만 [등록하기]가 눌린다.
 *   4. 등록하면 식당 상세로 이동한다.
 */
(function () {
  'use strict';

  const esc = utils.escapeHtml;
  const NAME_MAX = 30;
  const AUTOCOMPLETE_DELAY = 300; // 글자를 칠 때마다 요청하지 않도록 잠깐 기다린다

  const els = {
    form: document.getElementById('register-form'),
    url: document.getElementById('map-url'),
    name: document.getElementById('restaurant-name'),
    similar: document.getElementById('similar-names'),
    checkResult: document.getElementById('check-result'),
    category: document.getElementById('category'),
    formMessage: document.getElementById('form-message'),
    submit: document.getElementById('submit-button'),
  };

  // 마지막 확인 결과. 확인한 url·name과 지금 입력값이 같아야 유효하다.
  let lastCheck = null; // { url, name, valid }
  let checkSeq = 0;
  let autocompleteSeq = 0;
  let autocompleteTimer = null;
  let submitting = false;

  renderCategoryOptions();
  bindEvents();

  /* ---------- 준비 ---------- */

  // 카테고리: 구내식당은 초기 데이터로만 제공 → 선택지에서 뺀다
  function renderCategoryOptions() {
    Object.entries(restaurantApi.CATEGORY_LABELS)
      .filter(([value]) => value !== 'CAFETERIA')
      .forEach(([value, label]) => els.category.add(new Option(label, value)));
  }

  function bindEvents() {
    // 입력이 바뀌면 이전 확인 결과는 무효
    els.url.addEventListener('input', onIdentityInput);
    els.name.addEventListener('input', () => {
      onIdentityInput();
      scheduleAutocomplete();
    });

    // 입력칸을 벗어날 때(값이 바뀐 경우) 확인
    els.url.addEventListener('change', checkIfReady);
    els.name.addEventListener('change', checkIfReady);

    els.category.addEventListener('change', updateSubmitButton);

    els.form.addEventListener('submit', (event) => {
      event.preventDefault();
      submit();
    });
  }

  function currentValues() {
    return { url: els.url.value.trim(), name: els.name.value.trim(), category: els.category.value };
  }

  function onIdentityInput() {
    const { url, name } = currentValues();
    if (lastCheck && (lastCheck.url !== url || lastCheck.name !== name)) {
      lastCheck = null;
      els.checkResult.innerHTML = '';
    }
    setFormMessage('');
    updateSubmitButton();
  }

  /* ---------- 이미 등록된 비슷한 이름 (자동완성) ---------- */

  function scheduleAutocomplete() {
    clearTimeout(autocompleteTimer);
    autocompleteTimer = setTimeout(loadSimilarNames, AUTOCOMPLETE_DELAY);
  }

  async function loadSimilarNames() {
    const keyword = els.name.value.trim();
    const seq = ++autocompleteSeq;
    if (!keyword || keyword.length > NAME_MAX) {
      els.similar.innerHTML = '';
      return;
    }
    try {
      const list = await restaurantApi.autocomplete(keyword);
      if (seq !== autocompleteSeq) return;
      renderSimilarNames(list);
    } catch (err) {
      if (seq === autocompleteSeq) els.similar.innerHTML = ''; // 보조 기능이라 실패해도 조용히 숨긴다
    }
  }

  function renderSimilarNames(list) {
    if (list.length === 0) {
      els.similar.innerHTML = '';
      return;
    }
    els.similar.innerHTML =
      '<p class="similar-names__title">이미 등록된 비슷한 이름이 있어요. 같은 식당인지 확인해 주세요.</p>' +
      '<ul class="similar-names__list">' +
      list.map((r) =>
        '<li><a href="/restaurant-detail.html?id=' + encodeURIComponent(r.restaurantId) + '" target="_blank" rel="noopener">' +
        esc(r.name) + '<span class="sr-only">(새 탭)</span></a> ' +
        '<span class="text-muted">' + esc(restaurantApi.CATEGORY_LABELS[r.category] || r.category) + '</span></li>').join('') +
      '</ul>';
  }

  /* ---------- URL·이름 확인 (check-map-url) ---------- */

  function checkIfReady() {
    const { url, name } = currentValues();
    if (!url || !name) return;                                       // 둘 다 입력해야 확인
    if (lastCheck && lastCheck.url === url && lastCheck.name === name) return; // 이미 확인함
    runCheck();
  }

  async function runCheck() {
    const { url, name } = currentValues();
    const seq = ++checkSeq;
    lastCheck = null;
    updateSubmitButton();
    renderCheckLoading();

    try {
      const result = await restaurantApi.checkMapUrl(url, name);
      if (seq !== checkSeq) return;
      lastCheck = { url, name, valid: result.valid };
      renderCheckResult(result);
    } catch (err) {
      if (seq !== checkSeq || err.statusCode === 401) return;
      renderCheckError(err.message);
    } finally {
      if (seq === checkSeq) updateSubmitButton();
    }
  }

  function renderCheckLoading() {
    els.checkResult.className = 'check-result check-result--loading';
    els.checkResult.innerHTML =
      '<span class="spinner" aria-hidden="true"></span><span>네이버 지도에서 장소를 찾는 중…</span>';
  }

  function renderCheckResult(result) {
    if (result.valid) {
      els.checkResult.className = 'check-result check-result--ok';
      els.checkResult.innerHTML =
        '<p class="check-result__title">이 장소가 맞나요?</p>' +
        '<p class="check-result__place">' + esc(result.place.name) + '</p>' +
        '<p>' + esc(result.place.address || '주소 정보 없음') + '</p>' +
        '<p class="check-result__hint">다른 지점이 나왔다면 식당 이름에 지점명까지 넣어 주세요. (예: OO국밥 세종대점)</p>';
      return;
    }
    els.checkResult.className = 'check-result check-result--fail';
    els.checkResult.innerHTML =
      '<p class="check-result__title">' + esc(result.message) + '</p>' +
      '<p class="check-result__hint">' + esc(failHint(result.reason)) + '</p>';
  }

  function failHint(reason) {
    if (reason === 'INVALID_FORMAT') return '네이버 지도에서 식당을 연 뒤 [공유] → [링크 복사]한 주소를 넣어 주세요.';
    if (reason === 'DUPLICATE') return '이 식당은 이미 등록되어 있어요. 식당 목록에서 찾아보세요.';
    if (reason === 'NOT_FOUND') return '식당 이름으로 장소를 찾지 못했어요. 네이버 지도에 나오는 이름 그대로 입력해 주세요.';
    return '';
  }

  function renderCheckError(message) {
    els.checkResult.className = 'check-result check-result--fail';
    els.checkResult.innerHTML =
      '<p class="check-result__title">' + esc(message) + '</p>' +
      '<p class="check-result__hint">입력칸을 다시 고치거나 잠시 후 다시 시도해 주세요.</p>';
  }

  /* ---------- 등록 ---------- */

  function isCheckValid() {
    const { url, name } = currentValues();
    return Boolean(lastCheck && lastCheck.valid && lastCheck.url === url && lastCheck.name === name);
  }

  function updateSubmitButton() {
    els.submit.disabled = submitting || !isCheckValid() || !els.category.value;
  }

  async function submit() {
    if (submitting) return;
    const { url, name, category } = currentValues();
    if (!url || !name || !category) {
      setFormMessage('네이버 지도 URL, 식당 이름, 카테고리를 모두 입력해 주세요.');
      return;
    }
    if (!isCheckValid()) {
      runCheck(); // 아직 확인하지 않았으면 먼저 확인
      return;
    }

    submitting = true;
    updateSubmitButton();
    els.submit.textContent = '등록 중…';
    setFormMessage('');

    try {
      const { restaurantId } = await restaurantApi.register({ mapUrl: url, name, category });
      location.href = '/restaurant-detail.html?id=' + encodeURIComponent(restaurantId);
    } catch (err) {
      if (err.statusCode === 401) return;
      setFormMessage(err.message);
      submitting = false;
      els.submit.textContent = '등록하기';
      updateSubmitButton();
    }
  }

  function setFormMessage(message) {
    els.formMessage.textContent = message;
  }
})();
