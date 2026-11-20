/*
 * roulette.js — 메뉴 룰렛 화면 (S-11, C · 2026-11-20 회전 연출 개편)
 *
 * - 카테고리(단일 선택, 기본 '전체')를 바꾸면 후보를 다시 받아 휠을 새로 그린다
 * - 후보 2곳 미만이면 '룰렛을 돌릴 식당이 부족해요' + 버튼 비활성
 * - [룰렛 돌리기]
 *     1) 누르는 즉시 최고 속도로 돌기 시작해 약 8초 동안 10바퀴 남짓 돌며 천천히 멈춘다
 *        (일반적인 돌림판처럼 처음엔 빠르고 끝에 길게 느려지는 감속 — ease-out-quart)
 *     2) 서버가 정한 당첨 식당(POST /api/restaurants/roulette/spin)은 돌기 시작한 직후 도착하고,
 *        빠르게 도는 동안 멈출 위치를 그 칸에 맞게 살짝 보정한다 (눈에 띄지 않음)
 *     3) 칸 경계를 지날 때마다 화살표가 '틱' 하고 튕기고, 효과음을 켜 두면 소리가 난다 (기본 꺼짐)
 *     4) 당첨 칸만 강조하고 결과 카드가 나타난다
 *   회전 중에는 돌리기 버튼·카테고리 비활성.
 *   회전 자체가 이 화면의 핵심 기능이라 OS의 '애니메이션 줄이기'(prefers-reduced-motion) 설정과 관계없이 항상 돈다.
 *   (Windows '애니메이션 효과' 끔 상태에서 회전 없이 결과만 나와 동작하지 않는 것처럼 보이던 문제, 2026-11-20 수정)
 * - 결과 카드: [식당 상세 보기] → S-05 / [이 식당으로 식사팟 만들기] → S-08(식당 고정) / [다시 돌리기]
 * - 오류는 결과 카드 자리에 문구 + [다시 시도]로 보여 준다 (alert 없음)
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
  const RIM_RADIUS = CENTER - 7;        // 바깥 테두리(장식 링)의 가운데
  const RADIUS = CENTER - 14;           // 칸 반지름
  const LABEL_RADIUS = RADIUS * 0.6;
  const LABEL_MAX_CHARS = 7;
  const RIM_LIGHTS = 16;
  // 칸 색: 연파랑 · 연주황 번갈아 (칸 수가 홀수면 마지막 칸은 흰색으로 같은 색이 붙지 않게)

  /* 회전 연출 */
  const SPIN_MS = 8000;                 // 전체 회전 시간
  const SPIN_TURNS = 10;                // 대략 몇 바퀴 돌고 멈출지
  const ADJUST_MS = 400;                // 당첨 칸 보정을 나눠 적용하는 시간 (갑자기 튀지 않게)
  const TICK_GAP_MS = 45;               // 틱 애니메이션·소리 최소 간격
  const SOUND_KEY = 'sseukbap.roulette.sound';

  const categoriesEl = document.getElementById('roulette-categories');
  const wheelBoxEl = document.querySelector('.roulette-wheel-box');
  const wheelEl = document.getElementById('roulette-wheel');
  const wheelStateEl = document.getElementById('roulette-wheel-state');
  const pointerEl = document.getElementById('roulette-pointer');
  const spinButton = document.getElementById('roulette-spin');
  const soundButton = document.getElementById('roulette-sound');
  const refillButton = document.getElementById('roulette-refill');
  const resultEl = document.getElementById('roulette-result');

  let category = null;            // null = 전체
  let candidates = [];
  let spinnable = false;
  let spinning = false;
  let rotation = 0;               // 지금 휠 각도 (계속 더해 간다)
  let loadSeq = 0;                // 카테고리를 빠르게 바꿀 때 늦게 온 응답 무시용
  let lastSegment = -1;           // 화살표 아래 칸 (틱 판단용)
  let lastTickAt = 0;
  let soundOn = readSoundSetting();
  let audioCtx = null;
  let refilling = false;
  let lastTotalCount = 0;

  /* ---------- 시작 ---------- */

  renderHubCap();
  renderCategories();
  renderSoundButton();
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
  refillButton.addEventListener('click', refill);

  // 룰렛판을 눌러도 돌아간다 (안내·오류 상자를 누른 경우는 제외)
  wheelBoxEl.addEventListener('click', (event) => {
    if (event.target.closest('.roulette-wheel-state')) return;
    spin();
  });

  soundButton.addEventListener('click', () => {
    soundOn = !soundOn;
    saveSoundSetting(soundOn);
    renderSoundButton();
    if (soundOn) playTick();   // 켜는 순간 한 번 들려준다 (브라우저 소리 허용도 이때 받는다)
  });

  wheelStateEl.addEventListener('click', (event) => {
    if (event.target.closest('[data-action="retry"]')) loadCandidates();
  });

  resultEl.addEventListener('click', (event) => {
    if (event.target.closest('[data-action="respin"]')) spin();
    if (event.target.closest('[data-action="reload"]')) loadCandidates();
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

  /**
   * 후보를 불러와 휠을 그린다.
   * quiet: '다시 채우기'일 때는 로딩 상자를 띄우지 않는다 (휠이 접혔다 펴지는 애니메이션으로 대신)
   * 서버가 무작위로 고른 후보를 주지만, 후보 수가 적으면 같은 식당만 올 수 있어 순서도 한 번 섞는다.
   */
  async function loadCandidates(quiet) {
    const seq = ++loadSeq;
    spinnable = false;
    updateControls();
    if (!quiet) showWheelState('<span class="spinner" aria-hidden="true"></span><p class="state__desc">룰렛을 준비하는 중…</p>');

    try {
      const result = await rouletteApi.getCandidates(category);
      if (seq !== loadSeq) return null;
      candidates = shuffle(result.candidates.slice());
      lastTotalCount = result.totalCount;
      spinnable = result.spinnable && candidates.length >= MIN_CANDIDATES;

      resetRotation();
      drawWheel();
      if (spinnable) {
        hideWheelState();
        renderResultIntro();
      } else {
        showWheelState(
          '<p class="state__title">룰렛을 돌릴 식당이 부족해요</p>' +
          '<p class="state__desc">다른 카테고리를 골라 보세요.</p>');
      }
    } catch (err) {
      if (seq !== loadSeq || err.statusCode === 401) return null;   // 401은 client.js가 로그인 화면으로 이동
      candidates = [];
      drawWheel();
      showWheelState(
        '<p class="state__title">룰렛 후보를 불러오지 못했어요</p>' +
        '<p class="state__desc">' + esc(err.message) + '</p>' +
        '<button class="btn" type="button" data-action="retry">다시 시도</button>', true);
      updateControls();
      return false;
    }
    updateControls();
    return true;
  }

  /** [다시 채우기]: 휠이 접혔다가 새 후보로 펴진다 */
  async function refill() {
    if (spinning || refilling) return;
    refilling = true;
    updateControls();
    wheelEl.classList.remove('is-refill-in');
    wheelEl.classList.add('is-refill-out');
    const [ok] = await Promise.all([loadCandidates(true), wait(260)]);
    wheelEl.classList.remove('is-refill-out');
    void wheelEl.offsetWidth;
    wheelEl.classList.add('is-refill-in');
    refilling = false;
    updateControls();
    if (ok && spinnable) {
      ui.toast(lastTotalCount > candidates.length
        ? '후보 식당을 새로 골랐어요 (' + lastTotalCount + '곳 중 ' + candidates.length + '곳)'
        : '후보 순서를 새로 섞었어요');
    }
  }

  function wait(ms) {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }

  function shuffle(list) {
    for (let i = list.length - 1; i > 0; i -= 1) {
      const j = Math.floor(Math.random() * (i + 1));
      [list[i], list[j]] = [list[j], list[i]];
    }
    return list;
  }

  /** 칸 i의 색: 짝수 연파랑(blue) · 홀수 연주황(orange). 홀수 개면 마지막 칸은 흰색(plain) */
  function toneOf(i, n) {
    if (n % 2 === 1 && i === n - 1) return 'plain';
    return i % 2 === 0 ? 'blue' : 'orange';
  }

  /** 후보 수만큼 칸을 나눈 SVG. 칸 i는 위쪽(12시)에서 시계 방향으로 i번째 */
  function drawWheel() {
    const n = candidates.length;
    let slices;

    if (n < MIN_CANDIDATES) {
      // 후보가 부족해도 빈 휠 모양은 보여 준다
      slices = '<circle class="roulette-seg roulette-seg--empty" cx="' + CENTER + '" cy="' + CENTER + '" r="' + RADIUS + '"></circle>';
    } else {
      const step = 360 / n;
      slices = candidates.map((c, i) => {
        const start = i * step;
        const end = start + step;
        const mid = start + step / 2;
        const [x1, y1] = point(start, RADIUS);
        const [x2, y2] = point(end, RADIUS);
        const [lx, ly] = point(mid, LABEL_RADIUS);
        return (
          '<g class="roulette-slice roulette-slice--' + toneOf(i, n) + '" data-index="' + i + '">' +
          '<title>' + esc(c.name) + '</title>' +
          '<path class="roulette-seg" d="M' + CENTER + ' ' + CENTER +
          ' L' + x1 + ' ' + y1 +
          ' A' + RADIUS + ' ' + RADIUS + ' 0 ' + (step > 180 ? 1 : 0) + ' 1 ' + x2 + ' ' + y2 + ' Z"></path>' +
          // 글자는 반지름 방향(가운데 → 바깥)으로 눕혀서 칸 안에 맞춘다
          '<text class="roulette-label" x="' + lx + '" y="' + ly + '" ' +
          'transform="rotate(' + round(labelAngle(mid)) + ' ' + lx + ' ' + ly + ')" ' +
          'text-anchor="middle" dominant-baseline="middle">' + esc(shorten(c.name)) + '</text>' +
          '</g>'
        );
      }).join('');
    }

    // 바깥 장식 링 + 작은 불빛 (돌 때 반짝인다)
    let lights = '';
    for (let i = 0; i < RIM_LIGHTS; i++) {
      const [x, y] = point((360 / RIM_LIGHTS) * i, RIM_RADIUS);
      lights += '<circle class="roulette-light' + (i % 2 ? ' roulette-light--odd' : '') + '" cx="' + x + '" cy="' + y + '" r="2.6"></circle>';
    }

    wheelEl.innerHTML =
      '<svg viewBox="0 0 ' + SIZE + ' ' + SIZE + '" role="img" focusable="false">' +
      '<circle class="roulette-rim" cx="' + CENTER + '" cy="' + CENTER + '" r="' + RIM_RADIUS + '"></circle>' +
      '<g class="roulette-slices">' + slices + '</g>' +
      lights +
      '</svg>';
    lastSegment = segmentAtPointer();
  }

  /** 글자 회전 각도: 반지름 방향으로 눕히되, 왼쪽 절반은 뒤집혀 보이지 않게 반대로 */
  function labelAngle(mid) {
    return mid > 180 ? mid + 90 : mid - 90;
  }

  /** 가운데 로고 캡 (돌지 않는다) */
  function renderHubCap() {
    if (wheelBoxEl.querySelector('.roulette-hub-cap')) return;
    const cap = document.createElement('span');
    cap.className = 'roulette-hub-cap';
    cap.setAttribute('aria-hidden', 'true');
    cap.innerHTML = window.brand ? window.brand.logoSvg('roulette-hub-cap__logo') : '';
    wheelBoxEl.appendChild(cap);
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
    wheelEl.style.transform = 'rotate(0deg)';
  }

  /* ---------- ④ 돌리기 ---------- */

  async function spin() {
    if (spinning || !spinnable) return;
    spinning = true;
    updateControls();
    clearWinnerHighlight();
    renderResultSpinning();

    // 서버 요청은 바로 보내고, 휠은 응답을 기다리지 않고 돌기 시작한다
    const request = rouletteApi.spin(candidates.map((c) => c.restaurantId))
      .then((result) => ({ result }), (error) => ({ error }));

    try {
      wheelEl.classList.add('is-spinning');
      wheelBoxEl.classList.add('is-spinning');
      const outcome = await spinToStop(request);
      if (outcome.error) throw outcome.error;
      showWinner(outcome.index, outcome.result.winner);
    } catch (err) {
      if (err.statusCode === 401) return;
      // 409(후보 부족)이나 화면과 다른 결과: 그사이 식당이 바뀜 → 후보를 새로 받게 안내
      renderResultError(err.message, err.statusCode === 409 || err.reload);
    } finally {
      wheelEl.classList.remove('is-spinning');
      wheelBoxEl.classList.remove('is-spinning');
      spinning = false;
      updateControls();
    }
  }

  function winnerIndex(result) {
    const index = candidates.findIndex((c) => c.restaurantId === result.winnerId);
    if (index < 0) {
      // 화면 후보와 다른 결과 (일어나지 않아야 함)
      throw Object.assign(new Error('룰렛 후보가 바뀌었어요. 후보를 다시 불러와 주세요.'), { reload: true });
    }
    return index;
  }

  /**
   * 돌림판 회전: 누르는 즉시 최고 속도로 시작해 SPIN_MS 동안 ease-out-quart로 멈춘다.
   * 처음엔 임시 목표(SPIN_TURNS 바퀴)로 돌다가, 서버 결과가 오면 당첨 칸에 멈추도록 남은 거리를 보정한다.
   * 보정은 ADJUST_MS에 걸쳐 나눠 넣어서 휠이 튀지 않는다. (빠르게 도는 초반이라 눈에 띄지 않음)
   * 돌려주는 값: { result, index } 또는 { error }
   */
  function spinToStop(request) {
    return new Promise((resolve) => {
      const from = rotation;
      const baseDistance = SPIN_TURNS * 360 + Math.random() * 360;   // 임시 목표
      let adjust = 0;                 // 당첨 칸에 맞추려고 더하는 각도 (0 ~ 360)
      let adjustStartedAt = null;
      let outcome = null;
      let index = -1;
      const startedAt = performance.now();

      request.then((o) => {
        outcome = o;
        if (o.error) return;
        try {
          index = winnerIndex(o.result);
        } catch (err) {
          outcome = { error: err };
          return;
        }
        // 임시 목표에서 멈췄을 때의 각도 → 당첨 칸 가운데 근처까지 더 돌아야 하는 각도
        const step = 360 / candidates.length;
        const jitter = (Math.random() - 0.5) * step * 0.5;
        const targetMod = mod(-(index * step + step / 2 + jitter), 360);
        adjust = mod(targetMod - mod(from + baseDistance, 360), 360);
        adjustStartedAt = performance.now();
      });

      function frame(now) {
        const p = Math.min(1, (now - startedAt) / SPIN_MS);
        let extra = 0;
        if (adjustStartedAt !== null) {
          const ramp = Math.min(1, (now - adjustStartedAt) / ADJUST_MS);
          extra = adjust * easeInOutSine(ramp);
        }
        setRotation(from + (baseDistance + extra) * easeOutQuart(p));

        if (p < 1) {
          requestAnimationFrame(frame);
          return;
        }
        if (outcome) {
          resolve(outcome.error ? outcome : { result: outcome.result, index });
        } else {
          // 8초가 지나도 서버 응답이 없으면 기다렸다가 오류로 돌려준다
          request.then((o) => resolve(o.error ? o : { error: new Error('룰렛 결과를 받지 못했어요. 다시 시도해 주세요.') }));
        }
      }
      requestAnimationFrame(frame);
    });
  }

  function animate(duration, apply) {
    return new Promise((resolve) => {
      const startedAt = performance.now();
      function frame(now) {
        const p = Math.min(1, (now - startedAt) / duration);
        apply(p);
        if (p < 1) requestAnimationFrame(frame);
        else resolve();
      }
      requestAnimationFrame(frame);
    });
  }

  function easeOutQuart(p) {
    return 1 - Math.pow(1 - p, 4);
  }

  function easeInOutSine(p) {
    return -(Math.cos(Math.PI * p) - 1) / 2;
  }

  function mod(a, n) {
    return ((a % n) + n) % n;
  }

  /** 휠 각도를 바꾸고, 칸 경계를 넘었으면 틱 */
  function setRotation(deg) {
    rotation = deg;
    wheelEl.style.transform = 'rotate(' + deg + 'deg)';
    const segment = segmentAtPointer();
    if (segment !== lastSegment) {
      lastSegment = segment;
      tick();
    }
  }

  /** 12시 화살표 아래에 있는 칸 번호 */
  function segmentAtPointer() {
    const n = candidates.length;
    if (n < MIN_CANDIDATES) return -1;
    return Math.floor(mod(-rotation, 360) / (360 / n));
  }

  /* ---------- 틱: 화살표 튕김 + 효과음 ---------- */

  function tick() {
    const now = performance.now();
    if (now - lastTickAt < TICK_GAP_MS) return;
    lastTickAt = now;
    if (!spinning) return;
    // 애니메이션을 처음부터 다시 재생
    pointerEl.classList.remove('is-tick');
    void pointerEl.offsetWidth;
    pointerEl.classList.add('is-tick');
    playTick();
  }

  function playTick() {
    if (!soundOn) return;
    try {
      if (!audioCtx) audioCtx = new (window.AudioContext || window.webkitAudioContext)();
      if (audioCtx.state === 'suspended') audioCtx.resume();
      const t = audioCtx.currentTime;
      const osc = audioCtx.createOscillator();
      const gain = audioCtx.createGain();
      osc.type = 'triangle';
      osc.frequency.setValueAtTime(1500, t);
      osc.frequency.exponentialRampToValueAtTime(700, t + 0.03);
      gain.gain.setValueAtTime(0.12, t);
      gain.gain.exponentialRampToValueAtTime(0.001, t + 0.04);
      osc.connect(gain).connect(audioCtx.destination);
      osc.start(t);
      osc.stop(t + 0.05);
    } catch (e) {
      // 소리를 못 내는 브라우저면 조용히 넘어간다
    }
  }

  function renderSoundButton() {
    soundButton.setAttribute('aria-pressed', String(soundOn));
    soundButton.innerHTML =
      '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">' +
      '<path d="M4 9h4l5-4v14l-5-4H4z" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/>' +
      (soundOn
        ? '<path d="M16 9a4 4 0 0 1 0 6M18.5 6.5a7.5 7.5 0 0 1 0 11" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>'
        : '<path d="M16.5 9.5l5 5M21.5 9.5l-5 5" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>') +
      '</svg>' +
      (soundOn ? '효과음 켬' : '효과음 끔');
  }

  function readSoundSetting() {
    try {
      return window.localStorage.getItem(SOUND_KEY) === 'on';
    } catch (e) {
      return false;
    }
  }

  function saveSoundSetting(on) {
    try {
      window.localStorage.setItem(SOUND_KEY, on ? 'on' : 'off');
    } catch (e) {
      // 저장 못 해도 이번 화면에서는 적용된다
    }
  }

  /* ---------- 당첨 강조 ---------- */

  function showWinner(index, winner) {
    const svg = wheelEl.querySelector('svg');
    const slice = wheelEl.querySelector('.roulette-slice[data-index="' + index + '"]');
    if (svg) svg.classList.add('has-winner');
    if (slice) slice.classList.add('is-winner');
    renderResult(winner);
    burstConfetti();
    playFanfare();
  }

  /* ---------- 당첨 축하: 컨페티 + '따단~' ---------- */

  // 색은 tokens.css 변수에서 가져온다
  const CONFETTI_TOKENS = ['--color-primary', '--color-accent', '--color-success', '--color-tile-purple',
    '--color-primary-soft', '--color-accent-soft', '--color-warning'];
  const CONFETTI_COUNT = 56;

  /** 결과 카드 위쪽 가운데에서 종이 조각이 '빵' 하고 터져 흩어진다. 1.6초 뒤 지운다 */
  function burstConfetti() {
    const rootStyle = getComputedStyle(document.documentElement);
    const colors = CONFETTI_TOKENS.map((name) => rootStyle.getPropertyValue(name).trim()).filter(Boolean);
    const layer = document.createElement('div');
    layer.className = 'roulette-confetti';
    layer.setAttribute('aria-hidden', 'true');
    for (let i = 0; i < CONFETTI_COUNT; i += 1) {
      const piece = document.createElement('span');
      const angle = (Math.random() * 160 + 190) * Math.PI / 180;   // 위쪽 반원으로 퍼진다
      const distance = 120 + Math.random() * 160;
      piece.style.setProperty('--dx', Math.round(Math.cos(angle) * distance) + 'px');
      piece.style.setProperty('--dy', Math.round(Math.sin(angle) * distance) + 'px');
      piece.style.setProperty('--fall', Math.round(120 + Math.random() * 140) + 'px');
      piece.style.setProperty('--rot', Math.round(Math.random() * 720 - 360) + 'deg');
      piece.style.background = colors[i % colors.length];
      piece.style.animationDelay = Math.round(Math.random() * 80) + 'ms';
      if (i % 3 === 0) piece.classList.add('is-round');
      layer.appendChild(piece);
    }
    resultEl.appendChild(layer);
    setTimeout(() => layer.remove(), 1600);
  }

  /** '따-단~': 짧은 두 음 + 화음. 효과음을 켰을 때만 */
  function playFanfare() {
    if (!soundOn) return;
    try {
      if (!audioCtx) audioCtx = new (window.AudioContext || window.webkitAudioContext)();
      if (audioCtx.state === 'suspended') audioCtx.resume();
      const t = audioCtx.currentTime + 0.05;
      // [시작(초), 길이(초), 주파수들] — 솔(G5) 짧게 → 도·미·솔(C6 화음) 길게
      const notes = [
        [0, 0.14, [784]],
        [0.16, 0.7, [1047, 1319, 1568]],
      ];
      notes.forEach(([start, length, freqs]) => {
        freqs.forEach((freq) => {
          const osc = audioCtx.createOscillator();
          const gain = audioCtx.createGain();
          osc.type = 'triangle';
          osc.frequency.setValueAtTime(freq, t + start);
          gain.gain.setValueAtTime(0.0001, t + start);
          gain.gain.exponentialRampToValueAtTime(0.12 / freqs.length + 0.03, t + start + 0.02);
          gain.gain.exponentialRampToValueAtTime(0.0001, t + start + length);
          osc.connect(gain).connect(audioCtx.destination);
          osc.start(t + start);
          osc.stop(t + start + length + 0.05);
        });
      });
    } catch (e) {
      // 소리를 못 내는 브라우저면 조용히 넘어간다
    }
  }

  function clearWinnerHighlight() {
    const svg = wheelEl.querySelector('svg');
    if (svg) svg.classList.remove('has-winner');
    wheelEl.querySelectorAll('.roulette-slice.is-winner').forEach((el) => el.classList.remove('is-winner'));
  }

  function updateControls() {
    spinButton.disabled = spinning || refilling || !spinnable;
    refillButton.disabled = spinning || refilling || (!spinnable && candidates.length === 0);
    wheelBoxEl.classList.toggle('is-clickable', spinnable && !spinning);
    spinButton.textContent = spinning ? '돌리는 중…' : '룰렛 돌리기';
    renderCategories();
    const respin = resultEl.querySelector('[data-action="respin"]');
    if (respin) respin.disabled = spinning || !spinnable;
  }

  /* ---------- ⑤ 결과 카드 ---------- */

  function setResult(html, reveal) {
    resultEl.classList.remove('is-revealed');
    resultEl.innerHTML = html;
    if (reveal) {
      void resultEl.offsetWidth;   // 등장 애니메이션을 처음부터
      resultEl.classList.add('is-revealed');
    }
  }

  function renderResultIntro() {
    setResult(
      '<p class="roulette-result__eyebrow">오늘의 선택</p>' +
      '<p class="roulette-result__placeholder">룰렛을 돌려 오늘의 식당을 정해 보세요.</p>');
  }

  function renderResultSpinning() {
    setResult(
      '<p class="roulette-result__eyebrow">오늘의 선택</p>' +
      '<p class="roulette-result__placeholder roulette-result__placeholder--spinning">어디로 갈지 고르는 중…</p>');
  }

  function renderResult(winner) {
    const label = CATEGORY_LABELS[winner.category] || winner.category;
    setResult(
      '<p class="roulette-result__eyebrow">오늘의 선택</p>' +
      '<h2 class="roulette-result__name">' + esc(winner.name) + '</h2>' +
      '<p class="roulette-result__meta">' + esc(label) + ' · ' +
      '<span class="roulette-result__thumbs">' + utils.thumbsUpIcon(16) + '최근 30일 따봉 ' + winner.thumbsCount30d + '</span></p>' +
      '<div class="roulette-result__actions">' +
      '<a class="btn btn--primary" href="' + partyApi.createUrl(winner.restaurantId) + '">이 식당으로 식사팟 만들기</a>' +
      '<a class="btn" href="' + RESTAURANT_DETAIL_URL + encodeURIComponent(winner.restaurantId) + '">식당 상세 보기</a>' +
      '<button class="btn btn--text" type="button" data-action="respin">다시 돌리기</button>' +
      '</div>', true);
  }

  function renderResultError(message, needReload) {
    setResult(
      '<p class="roulette-result__eyebrow">오늘의 선택</p>' +
      '<p class="roulette-result__error" role="alert">' + esc(message || '룰렛을 돌리지 못했어요.') + '</p>' +
      '<div class="roulette-result__actions">' +
      (needReload
        ? '<button class="btn btn--primary" type="button" data-action="reload">후보 다시 불러오기</button>'
        : '<button class="btn btn--primary" type="button" data-action="respin">다시 시도</button>') +
      '</div>', true);
  }
})();
