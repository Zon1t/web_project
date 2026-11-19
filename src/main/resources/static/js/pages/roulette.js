/*
 * roulette.js — 메뉴 룰렛 화면 (S-11, C)
 *
 * - 카테고리(단일 선택, 기본 '전체')를 바꾸면 후보를 다시 받아 휠을 새로 그린다
 * - 후보 2곳 미만이면 '룰렛을 돌릴 식당이 부족합니다' + 버튼 비활성
 * - [룰렛 돌리기]: 서버가 당첨 식당을 먼저 정하고, 휠은 그 칸에서 멈추도록 약 3초 회전 연출
 *   회전 중에는 돌리기 버튼·카테고리 비활성. '동작 줄이기' 설정이면 회전 없이 바로 결과
 * - 결과 카드: [식당 상세 보기] → S-05 / [이 식당으로 식사팟 만들기] → S-08(식당 고정) / [다시 돌리기]
 * - 구내식당은 후보에 없다 (서버에서 제외)
 */
(function () {
  'use strict';

  const esc = utils.escapeHtml;
  const CATEGORY_LABELS = restaurantApi.CATEGORY_LABELS;
  const RESTAURANT_DETAIL_URL = '/restaurant-detail.html?id=';
  const MIN_CANDIDATES = 2;

  /* 휠 그리기 값 (SVG 좌표) */
  const SIZE = 360;
  const CENTER = SIZE / 2;
  const RADIUS = CENTER - 4;
  const LABEL_RADIUS = RADIUS * 0.58;
  const LABEL_MAX_CHARS = 7;

  /* 회전 연출 */
  const SPIN_MS = 3200;
  const EXTRA_TURNS = 5;          // 멈추기 전에 더 도는 바퀴 수
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)');

  const categoriesEl = document.getElementById('roulette-categories');
  const wheelEl = document.getElementById('roulette-wheel');
  const wheelStateEl = document.getElementById('roulette-wheel-state');
  const spinButton = document.getElementById('roulette-spin');
  const resultEl = document.getElementById('roulette-result');

  let category = null;            // null = 전체
  let candidates = [];
  let spinnable = false;
  let spinning = false;
  let rotation = 0;               // 지금까지 돈 각도 (계속 더해 간다)
  let loadSeq = 0;                // 카테고리를 빠르게 바꿀 때 늦게 온 응답 무시용

  /* ---------- 시작 ---------- */

  renderCategories();
  renderResultIntro();
  loadCandidates();

  categoriesEl.addEventListener('click', (event) => {
    const chip = event.target.closest('[data-category]');
    if (!chip || spinning) return;
    const next = chip.dataset.category || null;
    if (next === category) return;
    category = next;
    renderCategories();
    renderResultIntro();
    loadCandidates();
  });

  spinButton.addEventListener('click', spin);

  wheelStateEl.addEventListener('click', (event) => {
    if (event.target.closest('[data-action="retry"]')) loadCandidates();
  });

  resultEl.addEventListener('click', (event) => {
    if (event.target.closest('[data-action="respin"]')) spin();
  });

  /* ---------- ② 카테고리 ---------- */

  function renderCategories() {
    categoriesEl.innerHTML = rouletteApi.CATEGORIES.map((value) => {
      const selected = value === category;
      const label = value ? CATEGORY_LABELS[value] || value : '전체';
      return (
        '<button class="chip' + (selected ? ' is-selected' : '') + '" type="button" ' +
        'data-category="' + (value || '') + '" aria-pressed="' + selected + '"' +
        (spinning ? ' disabled' : '') + '>' + esc(label) + '</button>'
      );
    }).join('');
  }

  /* ---------- ③ 후보 + 휠 ---------- */

  async function loadCandidates() {
    const seq = ++loadSeq;
    spinnable = false;
    updateControls();
    showWheelState('<span class="spinner" aria-hidden="true"></span><p class="state__desc">룰렛을 준비하는 중…</p>');

    try {
      const result = await rouletteApi.getCandidates(category);
      if (seq !== loadSeq) return;
      candidates = result.candidates;
      spinnable = result.spinnable && candidates.length >= MIN_CANDIDATES;

      resetRotation();
      drawWheel();
      if (spinnable) {
        hideWheelState();
      } else {
        showWheelState(
          '<p class="state__title">룰렛을 돌릴 식당이 부족합니다</p>' +
          '<p class="state__desc">다른 카테고리를 골라 보세요.</p>');
      }
    } catch (err) {
      if (seq !== loadSeq || err.statusCode === 401) return;   // 401은 client.js가 로그인 화면으로 이동
      candidates = [];
      drawWheel();
      showWheelState(
        '<p class="state__title">룰렛 후보를 불러오지 못했습니다</p>' +
        '<p class="state__desc">' + esc(err.message) + '</p>' +
        '<button class="btn" type="button" data-action="retry">다시 시도</button>', true);
    }
    updateControls();
  }

  /** 후보 수만큼 칸을 나눈 SVG. 칸 i는 위쪽(12시)에서 시계 방향으로 i번째 */
  function drawWheel() {
    const n = candidates.length;
    let body;

    if (n < MIN_CANDIDATES) {
      // 후보가 부족해도 빈 휠 모양은 보여 준다
      body = '<circle class="roulette-seg" cx="' + CENTER + '" cy="' + CENTER + '" r="' + RADIUS + '"></circle>';
    } else {
      const step = 360 / n;
      body = candidates.map((c, i) => {
        const start = i * step;
        const end = start + step;
        const mid = start + step / 2;
        const [x1, y1] = point(start, RADIUS);
        const [x2, y2] = point(end, RADIUS);
        const [lx, ly] = point(mid, LABEL_RADIUS);
        const tone = i % 2 === 0 ? 'roulette-seg--a' : 'roulette-seg--b';
        return (
          '<g class="roulette-slice" data-index="' + i + '">' +
          '<title>' + esc(c.name) + '</title>' +
          '<path class="roulette-seg ' + tone + '" d="M' + CENTER + ' ' + CENTER +
          ' L' + x1 + ' ' + y1 +
          ' A' + RADIUS + ' ' + RADIUS + ' 0 ' + (step > 180 ? 1 : 0) + ' 1 ' + x2 + ' ' + y2 + ' Z"></path>' +
          // 글자는 반지름 방향(가운데 → 바깥)으로 눕혀서 칸 안에 맞춘다
          '<text class="roulette-label" x="' + lx + '" y="' + ly + '" ' +
          'transform="rotate(' + round(mid - 90) + ' ' + lx + ' ' + ly + ')" ' +
          'text-anchor="middle" dominant-baseline="middle">' + esc(shorten(c.name)) + '</text>' +
          '</g>'
        );
      }).join('');
    }

    wheelEl.innerHTML =
      '<svg viewBox="0 0 ' + SIZE + ' ' + SIZE + '" role="img" focusable="false">' +
      body +
      '<circle class="roulette-hub" cx="' + CENTER + '" cy="' + CENTER + '" r="22"></circle>' +
      '</svg>';
  }

  /** 12시 방향 0도, 시계 방향 각도 → SVG 좌표 */
  function point(deg, r) {
    const rad = (deg * Math.PI) / 180;
    return [round(CENTER + r * Math.sin(rad)), round(CENTER - r * Math.cos(rad))];
  }

  function round(v) {
    return Math.round(v * 100) / 100;
  }

  function shorten(name) {
    const chars = Array.from(name || '');
    return chars.length > LABEL_MAX_CHARS ? chars.slice(0, LABEL_MAX_CHARS - 1).join('') + '…' : name;
  }

  function showWheelState(html, isError) {
    wheelStateEl.className = 'roulette-wheel-state state' + (isError ? ' state--error' : '');
    wheelStateEl.innerHTML = html;
    wheelStateEl.hidden = false;
  }

  function hideWheelState() {
    wheelStateEl.hidden = true;
    wheelStateEl.innerHTML = '';
  }

  function resetRotation() {
    rotation = 0;
    wheelEl.style.transition = 'none';
    wheelEl.style.transform = 'rotate(0deg)';
  }

  /* ---------- ④ 돌리기 ---------- */

  async function spin() {
    if (spinning || !spinnable) return;
    spinning = true;
    updateControls();
    clearWinnerHighlight();
    renderResultSpinning();

    try {
      const result = await rouletteApi.spin(candidates.map((c) => c.restaurantId));
      const index = candidates.findIndex((c) => c.restaurantId === result.winnerId);
      if (index < 0) {
        // 화면 후보와 다른 결과 (일어나지 않아야 함) → 후보를 새로 받는다
        throw Object.assign(new Error('룰렛 후보가 바뀌었습니다. 다시 돌려 주세요.'), { reload: true });
      }
      await rotateTo(index);
      highlightWinner(index);
      renderResult(result.winner);
    } catch (err) {
      if (err.statusCode === 401) return;
      renderResultIntro();
      window.alert(err.message);
      // 409(후보 부족): 그사이 식당이 삭제됨 → 후보를 새로 받는다
      if (err.statusCode === 409 || err.reload) {
        spinning = false;
        loadCandidates();
        return;
      }
    } finally {
      spinning = false;
      updateControls();
    }
  }

  /** index 칸의 가운데 근처가 12시 화살표에 오도록 회전 */
  function rotateTo(index) {
    const step = 360 / candidates.length;
    const jitter = (Math.random() - 0.5) * step * 0.6;                  // 칸 안에서 조금씩 다른 위치
    const targetMod = mod(-(index * step + step / 2 + jitter), 360);  // 최종 각도(0~360)
    const delta = mod(targetMod - mod(rotation, 360), 360);
    rotation += EXTRA_TURNS * 360 + delta;

    if (reduceMotion.matches) {
      wheelEl.style.transition = 'none';
      wheelEl.style.transform = 'rotate(' + rotation + 'deg)';
      return Promise.resolve();
    }

    return new Promise((resolve) => {
      let done = false;
      const finish = () => {
        if (done) return;
        done = true;
        wheelEl.removeEventListener('transitionend', finish);
        resolve();
      };
      wheelEl.addEventListener('transitionend', finish);
      setTimeout(finish, SPIN_MS + 300);   // transitionend가 안 와도 멈추도록

      // 이전 transition: none을 확실히 적용한 뒤 회전 시작
      requestAnimationFrame(() => {
        wheelEl.style.transition = 'transform ' + SPIN_MS + 'ms cubic-bezier(0.12, 0.6, 0.12, 1)';
        wheelEl.style.transform = 'rotate(' + rotation + 'deg)';
      });
    });
  }

  function mod(a, n) {
    return ((a % n) + n) % n;
  }

  function highlightWinner(index) {
    const slice = wheelEl.querySelector('.roulette-slice[data-index="' + index + '"]');
    if (slice) slice.classList.add('is-winner');
  }

  function clearWinnerHighlight() {
    wheelEl.querySelectorAll('.roulette-slice.is-winner').forEach((el) => el.classList.remove('is-winner'));
  }

  function updateControls() {
    spinButton.disabled = spinning || !spinnable;
    spinButton.textContent = spinning ? '돌리는 중…' : '룰렛 돌리기';
    renderCategories();
    const respin = resultEl.querySelector('[data-action="respin"]');
    if (respin) respin.disabled = spinning || !spinnable;
  }

  /* ---------- ⑤ 결과 카드 ---------- */

  function renderResultIntro() {
    resultEl.innerHTML =
      '<p class="roulette-result__eyebrow">오늘의 선택</p>' +
      '<p class="roulette-result__placeholder">룰렛을 돌려 오늘의 식당을 정해 보세요.</p>';
  }

  function renderResultSpinning() {
    resultEl.innerHTML =
      '<p class="roulette-result__eyebrow">오늘의 선택</p>' +
      '<p class="roulette-result__placeholder">두구두구… 어디로 갈까요?</p>';
  }

  function renderResult(winner) {
    const label = CATEGORY_LABELS[winner.category] || winner.category;
    resultEl.innerHTML =
      '<p class="roulette-result__eyebrow">오늘의 선택</p>' +
      '<h2 class="roulette-result__name">' + esc(winner.name) + '</h2>' +
      '<p class="roulette-result__meta">' + esc(label) + ' · ' +
      '<span class="roulette-result__thumbs">' + utils.thumbsUpIcon(16) + '따봉 ' + winner.thumbsCount30d + '</span></p>' +
      '<div class="roulette-result__actions">' +
      '<a class="btn" href="' + RESTAURANT_DETAIL_URL + encodeURIComponent(winner.restaurantId) + '">식당 상세 보기</a>' +
      '<a class="btn btn--primary" href="' + partyApi.createUrl(winner.restaurantId) + '">이 식당으로 식사팟 만들기</a>' +
      '<button class="btn" type="button" data-action="respin">다시 돌리기</button>' +
      '</div>';
  }
})();
