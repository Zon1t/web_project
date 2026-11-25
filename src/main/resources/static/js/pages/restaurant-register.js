/*
 * restaurant-register.js — S-06 식당 등록 (B)
 *
 * 순서: 네이버 지도 URL → 식당 이름 → (후보 고르기) → 카테고리
 *   1. 이름을 입력하는 동안 이미 등록된 비슷한 이름을 보여 준다. (자동완성 API, 중복 등록 방지)
 *   2. URL과 이름을 다 입력하면(입력칸을 벗어날 때) 서버에서 확인한다. (check-map-url)
 *      네이버에 URL로 장소를 찾는 공식 API가 없어서 이름으로 검색한다 → 후보(최대 5개)를 보여 주고 사용자가 고른다.
 *      (2026-11-24) 회사에서 4km 밖이거나 음식점·카페가 아닌 후보는 회색으로 막고 이유를 보여 준다.
 *      등록할 수 있는 후보가 1곳뿐이면 미리 골라 둔다.
 *   3. 후보를 고르면 네이버 분류로 추천한 카테고리를 미리 골라 둔다. (사용자가 직접 고른 뒤에는 바꾸지 않음)
 *   4. 카테고리 칩: 마우스를 올리거나 키보드로 옮기면 음식 예시 툴팁, 고른 칩의 예시는 아래 한 줄로 (터치 화면용)
 *   5. 확인 결과가 정상이고, 그 뒤로 URL·이름을 바꾸지 않았고, 후보와 카테고리를 골랐을 때만 [등록하기]가 눌린다.
 *   6. 등록하면 식당 상세로 이동한다.
 *   7. (2026-11-25) URL 형식은 화면에서 바로 확인한다. 붙여 넣거나 칸을 벗어나는 즉시 URL 칸 아래에 안내하고,
 *      형식이 틀리면 서버 확인을 부르지 않는다. (기준은 서버 MapUrlParser와 같다. 최종 판단은 여전히 서버)
 */
(function () {
  'use strict';

  const esc = utils.escapeHtml;
  const NAME_MAX = 30;
  const AUTOCOMPLETE_DELAY = 300; // 글자를 칠 때마다 요청하지 않도록 잠깐 기다린다
  const LABELS = restaurantApi.CATEGORY_LABELS;
  const EXAMPLES = restaurantApi.CATEGORY_EXAMPLES;
  const CATEGORY_HINT_DEFAULT = '후보를 고르면 네이버 분류로 카테고리를 미리 골라 둬요.';

  const els = {
    form: document.getElementById('register-form'),
    url: document.getElementById('map-url'),
    urlError: document.getElementById('map-url-error'),
    name: document.getElementById('restaurant-name'),
    similar: document.getElementById('similar-names'),
    checkResult: document.getElementById('check-result'),
    category: document.getElementById('category'),
    categoryHint: document.getElementById('category-hint'),
    formMessage: document.getElementById('form-message'),
    submit: document.getElementById('submit-button'),
  };

  // 마지막 확인 결과. 확인한 url·name과 지금 입력값이 같아야 유효하다.
  let lastCheck = null; // { url, name, valid, radiusKm, candidates }
  let pickedIndex = null;          // 고른 후보 번호
  let category = '';               // 고른 카테고리
  let categoryByUser = false;      // 사용자가 직접 칩을 눌렀는지 (그러면 추천으로 덮어쓰지 않는다)
  let checkSeq = 0;
  let autocompleteSeq = 0;
  let autocompleteTimer = null;
  let submitting = false;

  renderCategoryChips();
  bindEvents();

  /* ---------- 카테고리 칩 ---------- */

  // 구내식당은 초기 데이터로만 제공 → 선택지에서 뺀다
  function renderCategoryChips() {
    els.category.innerHTML = Object.entries(LABELS)
      .filter(([value]) => value !== 'CAFETERIA')
      .map(([value, label]) => {
        const checked = value === category;
        return (
          '<button type="button" class="chip category-chip' + (checked ? ' is-selected' : '') + '"' +
          ' role="radio" aria-checked="' + checked + '" data-category="' + value + '"' +
          ' data-examples="' + esc(label + ': ' + (EXAMPLES[value] || '')) + '"' +
          ' aria-description="' + esc(EXAMPLES[value] || '') + '">' +
          esc(label) +
          '</button>'
        );
      })
      .join('');
    renderCategoryHint();
  }

  function renderCategoryHint() {
    els.categoryHint.textContent = category
      ? LABELS[category] + ': ' + (EXAMPLES[category] || '') + (categoryByUser ? '' : ' (네이버 분류로 추천)')
      : CATEGORY_HINT_DEFAULT;
  }

  function setCategory(value, byUser) {
    category = value;
    categoryByUser = byUser;
    renderCategoryChips();
    updateSubmitButton();
  }

  /* ---------- 이벤트 ---------- */

  function bindEvents() {
    // 입력이 바뀌면 이전 확인 결과는 무효
    els.url.addEventListener('input', () => {
      onIdentityInput();
      // 치는 도중에는 새 오류를 띄우지 않는다. 이미 오류가 떠 있으면 고치는 즉시 사라지게 다시 확인
      if (!els.urlError.hidden) validateUrl();
    });
    els.name.addEventListener('input', () => {
      onIdentityInput();
      scheduleAutocomplete();
    });

    // 붙여 넣으면 바로 확인 (붙여 넣은 값은 이벤트 직후에 칸에 들어가서 한 박자 뒤에 읽는다)
    els.url.addEventListener('paste', () => {
      setTimeout(() => {
        pickUrlFromText();
        if (validateUrl()) checkIfReady();
      }, 0);
    });

    // 입력칸을 벗어날 때(값이 바뀐 경우) 확인
    els.url.addEventListener('change', () => {
      if (validateUrl()) checkIfReady();
    });
    els.name.addEventListener('change', checkIfReady);

    els.category.addEventListener('click', (event) => {
      const chip = event.target.closest('[data-category]');
      if (chip) setCategory(chip.dataset.category, true);
    });

    // 후보 고르기
    els.checkResult.addEventListener('click', (event) => {
      const card = event.target.closest('[data-candidate]');
      if (!card || card.disabled) return;
      pickCandidate(Number(card.dataset.candidate));
    });

    els.form.addEventListener('submit', (event) => {
      event.preventDefault();
      submit();
    });
  }

  function currentValues() {
    return { url: els.url.value.trim(), name: els.name.value.trim() };
  }

  function onIdentityInput() {
    const { url, name } = currentValues();
    if (lastCheck && (lastCheck.url !== url || lastCheck.name !== name)) {
      lastCheck = null;
      pickedIndex = null;
      els.checkResult.innerHTML = '';
      els.checkResult.className = 'check-result';
    }
    setFormMessage('');
    updateSubmitButton();
  }

  /* ---------- URL 형식 바로 확인 (2026-11-25) ---------- */

  // 서버 MapUrlParser와 같은 기준
  const NAVER_MAP_HOSTS = ['map.naver.com', 'm.map.naver.com'];       // 경로에 /place/숫자 필요
  const NAVER_SHORT_HOST = 'naver.me';                                // 단축 링크 (어디로 가는지는 서버가 확인)
  const NAVER_PLACE_HOSTS = ['place.naver.com', 'm.place.naver.com', 'pcmap.place.naver.com'];
  const MAP_PLACE_PATH = /\/place\/\d{1,20}(\/|$)/;
  const URL_HOW_TO = '네이버 지도에서 식당을 연 뒤 [공유] → [링크 복사]한 주소를 넣어 주세요.';

  /** 형식이 맞으면 null, 틀리면 무엇이 틀렸는지 한 문장 */
  function checkUrlFormat(value) {
    if (!value) return null; // 빈칸은 [등록하기]를 누를 때 안내한다

    let parsed = null;
    try {
      parsed = new URL(value);
    } catch (e) {
      // 주소 형식이 아님
    }
    if (!parsed || (parsed.protocol !== 'https:' && parsed.protocol !== 'http:')) {
      const other = otherMapService(value.toLowerCase()); // https:// 없이 넣은 다른 지도 링크도 알려 준다
      return other ? other + ' 링크예요. 네이버 지도 링크만 넣을 수 있어요.'
        : '링크 주소가 아니에요. https://로 시작하는 주소를 그대로 붙여 넣어 주세요.';
    }

    const host = parsed.hostname.toLowerCase();
    if (host === NAVER_SHORT_HOST) {
      return parsed.pathname.length > 1 ? null : '링크가 끝까지 복사되지 않았어요.';
    }
    if (NAVER_MAP_HOSTS.includes(host)) {
      return MAP_PLACE_PATH.test(parsed.pathname) ? null
        : '식당이 선택된 링크가 아니에요. 지도에서 식당을 눌러 정보를 연 상태에서 복사해 주세요.';
    }
    if (NAVER_PLACE_HOSTS.includes(host)) {
      return '네이버 플레이스 주소예요. 네이버 지도 링크만 넣을 수 있어요.';
    }
    const other = otherMapService(host + parsed.pathname);
    return (other ? other + ' 링크예요. ' : '') + '네이버 지도 링크만 넣을 수 있어요.';
  }

  /** 자주 헷갈리는 다른 지도 서비스 이름 (없으면 null) */
  function otherMapService(text) {
    if (/maps\.app\.goo\.gl|goo\.gl\/maps|maps\.google\.|google\.[a-z.]+\/maps/.test(text)) return 'Google 지도';
    if (/map\.kakao\.com|kko\.to|kakaomap/.test(text)) return '카카오맵';
    return null;
  }

  /** 지금 URL 칸 값을 확인해서 안내를 보이거나 지운다. 형식이 맞으면 true */
  function validateUrl() {
    const message = checkUrlFormat(els.url.value.trim());
    els.url.classList.toggle('input--error', Boolean(message));
    if (message) els.url.setAttribute('aria-invalid', 'true');
    else els.url.removeAttribute('aria-invalid');
    els.urlError.hidden = !message;
    els.urlError.innerHTML = message ? '<strong>' + esc(message) + '</strong>' + esc(URL_HOW_TO) : '';
    return !message;
  }

  // 공유 문구째 붙여 넣은 경우("[네이버 지도] 가게 이름 https://naver.me/…") 주소만 남긴다
  function pickUrlFromText() {
    const value = els.url.value.trim();
    const found = value.match(/https?:\/\/\S+/);
    if (found && found[0] !== value) {
      els.url.value = found[0];
      onIdentityInput();
    }
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
        '<span class="text-muted">' + esc(LABELS[r.category] || r.category) + '</span></li>').join('') +
      '</ul>';
  }

  /* ---------- URL·이름 확인 (check-map-url) ---------- */

  function checkIfReady() {
    const { url, name } = currentValues();
    if (!url || !name) return;                                       // 둘 다 입력해야 확인
    if (!validateUrl()) return;                                      // 형식이 틀리면 서버에 묻지 않는다 (안내는 URL 칸 아래)
    if (lastCheck && lastCheck.url === url && lastCheck.name === name) return; // 이미 확인함
    runCheck();
  }

  async function runCheck() {
    const { url, name } = currentValues();
    const seq = ++checkSeq;
    lastCheck = null;
    pickedIndex = null;
    updateSubmitButton();
    renderCheckLoading();

    try {
      const result = await restaurantApi.checkMapUrl(url, name);
      if (seq !== checkSeq) return;
      lastCheck = { url, name, valid: result.valid, radiusKm: result.radiusKm, candidates: result.candidates || [] };
      renderCheckResult(result);

      // 등록할 수 있는 후보가 1곳뿐이면 미리 고른다
      const registrable = lastCheck.candidates
        .map((c, i) => (c.registrable ? i : -1))
        .filter((i) => i >= 0);
      if (registrable.length === 1) pickCandidate(registrable[0]);
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
    const candidates = result.candidates || [];

    // 형식·중복·검색 실패: 후보 없음
    if (!result.valid && candidates.length === 0) {
      els.checkResult.className = 'check-result check-result--fail';
      els.checkResult.innerHTML =
        '<p class="check-result__title">' + esc(result.message) + '</p>' +
        '<p class="check-result__hint">' + esc(failHint(result.reason)) + '</p>';
      return;
    }

    const title = result.valid
      ? (candidates.length > 1 ? '어느 장소인가요? 맞는 곳을 골라 주세요.' : '이 장소가 맞나요?')
      : result.message;
    const hint = result.valid
      ? '찾는 곳이 없으면 식당 이름에 지점명까지 넣어 주세요. (예: OO국밥 세종대점)'
      : '등록할 수 없는 장소만 찾았어요. 지점명까지 넣거나 네이버 지도에 나오는 이름 그대로 입력해 주세요.';

    els.checkResult.className = 'check-result ' + (result.valid ? 'check-result--pick' : 'check-result--fail');
    els.checkResult.innerHTML =
      '<p class="check-result__title" id="candidate-title">' + esc(title) + '</p>' +
      '<div class="candidate-list" role="radiogroup" aria-labelledby="candidate-title">' +
      candidates.map((c, i) => candidateHtml(c, i, result.radiusKm)).join('') +
      '</div>' +
      '<p class="check-result__hint">' + esc(hint) + '</p>';
  }

  function candidateHtml(c, index, radiusKm) {
    const picked = index === pickedIndex;
    const distance = c.distanceKm == null ? '' : '회사에서 ' + utils.formatDistance(c.distanceKm);
    const block = blockText(c.blockReason, radiusKm);
    return (
      '<button type="button" class="candidate' + (picked ? ' is-picked' : '') + (c.registrable ? '' : ' is-blocked') + '"' +
      ' role="radio" aria-checked="' + picked + '" data-candidate="' + index + '"' + (c.registrable ? '' : ' disabled') + '>' +
      '<span class="candidate__radio" aria-hidden="true"></span>' +
      '<span class="candidate__body">' +
      '<span class="candidate__name">' + esc(c.name) + '</span>' +
      '<span class="candidate__address">' + esc(c.address || '주소 정보 없음') + '</span>' +
      '<span class="candidate__meta">' +
      (c.naverCategory ? '<span>' + esc(c.naverCategory.replace(/>/g, ' › ')) + '</span>' : '') +
      (distance ? '<span>' + esc(distance) + '</span>' : '') +
      '</span>' +
      (block ? '<span class="candidate__block">' + esc(block) + '</span>' : '') +
      '</span>' +
      '</button>'
    );
  }

  function blockText(reason, radiusKm) {
    if (reason === 'OUT_OF_RANGE') return '회사에서 ' + radiusKm + 'km 밖이라 등록할 수 없어요';
    if (reason === 'NOT_A_RESTAURANT') return '음식점·카페가 아니라서 등록할 수 없어요';
    return '';
  }

  function pickCandidate(index) {
    if (!lastCheck || !lastCheck.candidates[index] || !lastCheck.candidates[index].registrable) return;
    pickedIndex = index;
    els.checkResult.querySelectorAll('[data-candidate]').forEach((card) => {
      const picked = Number(card.dataset.candidate) === index;
      card.classList.toggle('is-picked', picked);
      card.setAttribute('aria-checked', String(picked));
    });
    // 직접 고른 카테고리가 없으면 추천 카테고리를 미리 골라 둔다
    const suggested = lastCheck.candidates[index].suggestedCategory;
    if (!categoryByUser && suggested && suggested !== 'CAFETERIA') setCategory(suggested, false);
    updateSubmitButton();
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

  function pickedCandidate() {
    const { url, name } = currentValues();
    if (!lastCheck || !lastCheck.valid || lastCheck.url !== url || lastCheck.name !== name) return null;
    return pickedIndex == null ? null : lastCheck.candidates[pickedIndex] || null;
  }

  function updateSubmitButton() {
    els.submit.disabled = submitting || !pickedCandidate() || !category;
  }

  async function submit() {
    if (submitting) return;
    const { url, name } = currentValues();
    if (!url || !name) {
      setFormMessage('네이버 지도 URL과 식당 이름을 입력해 주세요.');
      return;
    }
    if (!validateUrl()) {
      els.url.focus();
      return;
    }
    if (!lastCheck || lastCheck.url !== url || lastCheck.name !== name) {
      runCheck(); // 아직 확인하지 않았으면 먼저 확인
      return;
    }
    const place = pickedCandidate();
    if (!place) {
      setFormMessage('찾은 장소 중 등록할 곳을 골라 주세요.');
      return;
    }
    if (!category) {
      setFormMessage('카테고리를 골라 주세요.');
      return;
    }

    submitting = true;
    updateSubmitButton();
    els.submit.textContent = '등록 중…';
    setFormMessage('');

    try {
      const { restaurantId } = await restaurantApi.register({
        mapUrl: url, name, category, placeName: place.name, placeAddress: place.address,
      });
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
