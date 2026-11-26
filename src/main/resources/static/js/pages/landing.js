/*
 * landing.js — 서비스 소개 랜딩 (landing.html, B, 2026-11-24)
 *
 * 로그인 없이 열리는 화면이라 API를 부르지 않는다. 화면 움직임만 담당한다.
 *   ② 첫 화면   '슥' 글자 애니메이션(처음 + 마우스를 올릴 때), 마우스를 따라 그림 층이 움직임
 *   ③ 공감      스크롤하면 시계가 11:50 → 12:00
 *   ④ 밥 지도   스크롤 진행률로 길 → 도보 5분 원 → 식당 핀 → 태그 → "우리 회사 사람들만의 밥 지도"
 *   ⑤ 기능      터치 화면은 마우스 올리기가 없으니, 카드가 보이면 그 모습을 자동으로 보여 준다
 *   ⑥ 식사팟    스크롤 진행률로 5단계 카드 상태를 바꾼다. 단계 이름을 누르면 그 단계로 스크롤
 *   ⑦ 랭킹      보이면 막대가 자라고 따봉 수가 올라간다 (예시 데이터)
 *   [data-lp-reveal] 요소는 화면에 들어오면 떠오른다.
 *
 * - OS '애니메이션 줄이기'가 켜져 있으면 html에 .lp-rm → 모든 장면을 완성된 상태로 보여 준다.
 * - 식당 이름·따봉 수는 설명용 예시다. (실제 데이터 아님)
 */
(function (global) {
  'use strict';

  const ui = global.ui;
  const root = document.documentElement;
  const reduceMotion = global.matchMedia('(prefers-reduced-motion: reduce)').matches;
  const isTouch = global.matchMedia('(hover: none)').matches;
  root.classList.add('lp-js');
  root.classList.toggle('lp-rm', reduceMotion);

  const clamp = (v) => Math.min(1, Math.max(0, v));

  /* ---------- 그림 채우기: 로고, 아이콘, 지도 핀 ---------- */

  document.getElementById('lp-logo').innerHTML = global.brand.wordmarkHtml();
  document.getElementById('lp-office-mark').innerHTML = global.brand.LOGO_PATHS;
  document.getElementById('lp-caption-mark').innerHTML = global.brand.logoSvg('');
  document.getElementById('lp-bowl').innerHTML = global.brand.logoSvg('');

  document.querySelectorAll('[data-lp-icon]').forEach((el) => {
    el.insertAdjacentHTML('afterbegin', ui.icon(el.dataset.lpIcon, 18));
  });

  // 지도 핀: 모양·색은 map-kit.css(.map-pin), 음식 그림은 ui.avatarGlyph (실제 지도 핀과 같다)
  function pinHtml(category) {
    return '<span class="map-pin map-pin--' + category.toLowerCase() + '">' +
      '<svg class="map-pin__shape" viewBox="0 0 40 52" aria-hidden="true" focusable="false">' +
      '<path class="map-pin__body" d="M20 51 C20 51 3.5 32.5 3.5 19.5 A16.5 16.5 0 1 1 36.5 19.5 C36.5 32.5 20 51 20 51 Z"/>' +
      '<circle cx="20" cy="19.5" r="12.5" fill="#fff"/>' +
      '<svg class="map-pin__glyph" x="9.5" y="9" width="21" height="21" viewBox="0 0 40 40">' + ui.avatarGlyph(category) + '</svg>' +
      '</svg></span>';
  }

  document.querySelectorAll('[data-lp-pin]').forEach((el) => {
    el.innerHTML = pinHtml(el.dataset.lpPin);
  });

  /* ---------- ① 상단 바 ---------- */

  const header = document.getElementById('lp-header');

  /* ---------- ② 첫 화면: '슥' ---------- */

  const hero = document.getElementById('lp-hero');
  const sseuk = document.getElementById('lp-sseuk');
  let lastPlay = 0;

  function playSseuk() {
    if (reduceMotion || Date.now() - lastPlay < 900) return; // 너무 자주 반복되지 않게
    lastPlay = Date.now();
    hero.classList.remove('is-playing');
    void hero.offsetWidth; // 애니메이션을 처음부터 다시
    hero.classList.add('is-playing');
  }

  playSseuk();
  sseuk.addEventListener('mouseenter', playSseuk);

  // 마우스를 따라 그림 층이 조금씩 다르게 움직인다 (깊이감)
  const visual = document.getElementById('lp-hero-visual');
  const layers = visual.querySelectorAll('[data-depth]');
  if (!reduceMotion && !isTouch) {
    hero.addEventListener('pointermove', (event) => {
      const rect = visual.getBoundingClientRect();
      const x = (event.clientX - (rect.left + rect.width / 2)) / rect.width;
      const y = (event.clientY - (rect.top + rect.height / 2)) / rect.height;
      layers.forEach((layer) => {
        const depth = Number(layer.dataset.depth);
        layer.style.transform = 'translate(' + (-x * depth).toFixed(1) + 'px,' + (-y * depth).toFixed(1) + 'px)';
      });
    });
    hero.addEventListener('pointerleave', () => layers.forEach((layer) => { layer.style.transform = ''; }));
  }

  /* ---------- ③ 시계: 스크롤하면 11:50 → 12:00 ---------- */

  const worry = document.getElementById('lp-worry');
  const ticks = document.getElementById('lp-clock-ticks');
  const hourHand = document.getElementById('lp-clock-hour');
  const minHand = document.getElementById('lp-clock-min');
  const clockTime = document.getElementById('lp-clock-time');
  const clockLeft = document.getElementById('lp-clock-left');

  let tickHtml = '';
  for (let i = 0; i < 12; i++) {
    const a = (i * 30 * Math.PI) / 180;
    tickHtml += '<line class="lp-clock__tick" x1="' + (60 + 46 * Math.sin(a)).toFixed(1) + '" y1="' + (60 - 46 * Math.cos(a)).toFixed(1) +
      '" x2="' + (60 + 51 * Math.sin(a)).toFixed(1) + '" y2="' + (60 - 51 * Math.cos(a)).toFixed(1) + '"/>';
  }
  ticks.innerHTML = tickHtml;

  function renderClock() {
    const rect = worry.getBoundingClientRect();
    const p = reduceMotion ? 0 : clamp((global.innerHeight * 0.75 - rect.top) / (rect.height * 0.9));
    const minutes = 50 + p * 10;
    minHand.style.transform = 'rotate(' + minutes * 6 + 'deg)';
    hourHand.style.transform = 'rotate(' + (330 + minutes * 0.5) + 'deg)';
    const m = Math.floor(minutes);
    clockTime.textContent = m >= 60 ? '12:00' : '11:' + m;
    clockLeft.textContent = String(Math.max(0, 60 - m));
  }

  /* ---------- 스크롤 장면 공통 ---------- */

  // 장면(높이가 긴 section)을 얼마나 지나왔는지 0~1
  function sceneProgress(el) {
    const rect = el.getBoundingClientRect();
    const total = rect.height - global.innerHeight;
    return total > 0 ? clamp(-rect.top / total) : 1;
  }

  // 단계 목록: 지금 단계 is-active, 지난 단계 is-done, 왼쪽 막대(--local)는 단계 안 진행률
  function renderSteps(items, ranges, p) {
    let active = ranges.findIndex(([, end]) => p < end);
    if (active === -1) active = ranges.length - 1;
    items.forEach((el, i) => {
      const [start, end] = ranges[i];
      el.classList.toggle('is-active', i === active);
      el.classList.toggle('is-done', i < active);
      el.style.setProperty('--local', clamp((p - start) / (end - start)).toFixed(3));
    });
    const [start, end] = ranges[active];
    return { active, local: clamp((p - start) / (end - start)) };
  }

  /* ---------- ④ 밥 지도 ---------- */

  const why = document.getElementById('lp-why');
  const map = document.getElementById('lp-map');
  const streets = map.querySelectorAll('.lp-map__street');
  const whySteps = why.querySelectorAll('.lp-step');

  // 예시 식당 (x, y: 지도 안 % 위치)
  const MAP_PINS = [
    { category: 'CAFETERIA', name: '계절밥상 · 구내식당', x: 57, y: 47 },
    { category: 'KOREAN', name: '을지 칼국수 · 한식', x: 24, y: 30 },
    { category: 'CHINESE', name: '홍반점 · 중식', x: 77, y: 24 },
    { category: 'CAFE', name: '모퉁이 커피 · 카페', x: 13, y: 62 },
    { category: 'WESTERN', name: '호파스타 · 양식', x: 38, y: 77 },
    { category: 'JAPANESE', name: '스시 하루 · 일식', x: 82, y: 60 },
    { category: 'SNACK', name: '싸다김밥 · 분식', x: 61, y: 86 },
  ];
  const MAP_TAGS = [
    { text: '#혼밥가능', x: 33, y: 20 },
    { text: '#도보5분이내', x: 49, y: 67 },
    { text: '#단체석', x: 88, y: 49 },
  ];

  // 핀은 키보드로도 이름을 볼 수 있게 button (누르면 아무 일 없음, 마우스·포커스 때 이름 표시)
  const pinEls = MAP_PINS.map((pin) => {
    const el = document.createElement('button');
    el.type = 'button';
    el.className = 'lp-pin' + (pin.category === 'CAFETERIA' ? ' lp-pin--cafeteria' : '');
    el.style.left = pin.x + '%';
    el.style.top = pin.y + '%';
    el.setAttribute('aria-label', pin.name + ' (예시)');
    el.innerHTML = pinHtml(pin.category) + '<span class="lp-pin__tip" aria-hidden="true">' + pin.name + '</span>';
    map.appendChild(el);
    return el;
  });

  const tagEls = MAP_TAGS.map((tag) => {
    const el = document.createElement('span');
    el.className = 'lp-map__tag';
    el.setAttribute('aria-hidden', 'true');
    el.style.left = tag.x + '%';
    el.style.top = tag.y + '%';
    el.innerHTML = '<span class="lp-chip lp-chip--tag">' + tag.text + '</span>';
    map.appendChild(el);
    return el;
  });

  // 이유 1 = 길·도보 5분 / 이유 2 = 동료가 남긴 핀 / 이유 3 = 태그 + 완성
  const WHY_RANGES = [[0, 0.3], [0.3, 0.64], [0.64, 1]];

  function renderWhy(p) {
    const streetP = clamp(p / 0.14);
    streets.forEach((s) => { s.style.strokeDashoffset = String(1 - streetP); });
    map.classList.toggle('is-ring', p > 0.14);
    pinEls.forEach((el, i) => el.classList.toggle('is-in', p > 0.32 + i * 0.045));
    tagEls.forEach((el, i) => el.classList.toggle('is-in', p > 0.67 + i * 0.06));
    why.classList.toggle('is-complete', p > 0.86);
    renderSteps(whySteps, WHY_RANGES, p);
  }

  /* ---------- ⑥ 식사팟 5단계 ---------- */

  const partyScroll = document.getElementById('lp-party-scroll');
  const partySteps = partyScroll.querySelectorAll('.lp-step');
  const pcard = document.getElementById('lp-pcard');
  const pcardStep = document.getElementById('lp-pcard-step');
  const PARTY_RANGES = [[0, 0.17], [0.17, 0.36], [0.36, 0.55], [0.55, 0.78], [0.78, 1]];

  function renderParty(p) {
    const { active, local } = renderSteps(partySteps, PARTY_RANGES, p);
    for (let i = 1; i <= 4; i++) pcard.classList.toggle('is-step' + i, active >= i);
    // 4단계: 앞 절반은 종 알림이 울리고, 뒤 절반은 모두 확정
    const confirmed = active > 3 || (active === 3 && local > 0.45);
    pcard.classList.toggle('is-confirmed', confirmed);
    pcard.classList.toggle('is-asking', active === 3 && !confirmed);
    pcard.classList.toggle('is-thumbed', active === 4 && local > 0.35);
    pcardStep.textContent = (active + 1) + ' / 5';
  }

  // 단계 이름을 누르면 그 단계가 보이는 위치로 스크롤
  partySteps.forEach((button, i) => {
    button.addEventListener('click', () => {
      if (reduceMotion) return; // 스크롤 장면이 없다 (모든 단계가 펼쳐져 있음)
      const rect = partyScroll.getBoundingClientRect();
      const total = rect.height - global.innerHeight;
      const [start, end] = PARTY_RANGES[i];
      const at = start + (end - start) * (i === 3 ? 0.7 : 0.5); // 4단계는 확정된 모습이 보이게
      global.scrollTo({ top: global.scrollY + rect.top + total * at, behavior: 'smooth' });
    });
  });

  /* ---------- ⑦ 랭킹 (예시 데이터) ---------- */

  const RANKING = [
    { category: 'WESTERN', name: '호파스타', label: '양식', thumbs: 24 },
    { category: 'KOREAN', name: '을지 칼국수', label: '한식', thumbs: 19 },
    { category: 'JAPANESE', name: '스시 하루', label: '일식', thumbs: 15 },
    { category: 'CHINESE', name: '홍반점', label: '중식', thumbs: 11 },
    { category: 'SNACK', name: '싸다김밥', label: '분식', thumbs: 8 },
  ];

  document.getElementById('lp-rank-list').innerHTML = RANKING.map((r, i) =>
    '<li class="lp-rank__row">' +
    '<span class="lp-rank__no">' + (i + 1) + '</span>' +
    ui.avatar(r.category, 40) +
    '<span><span class="lp-rank__name">' + r.name + '</span><span class="lp-rank__cat">' + r.label + '</span>' +
    '<span class="lp-rank__bar"><i style="--w:' + Math.round((r.thumbs / RANKING[0].thumbs) * 100) + '%;--d:' + i * 0.1 + 's"></i></span></span>' +
    '<span class="lp-rank__thumbs">' + ui.icon('thumbs', 14) + '<span data-count="' + r.thumbs + '">' + (reduceMotion ? r.thumbs : 0) + '</span></span>' +
    '</li>').join('');

  function countUp(el) {
    const target = Number(el.dataset.count);
    if (reduceMotion) {
      el.textContent = target;
      return;
    }
    const start = performance.now();
    const step = (now) => {
      const t = clamp((now - start) / 1000);
      el.textContent = Math.round(target * (1 - Math.pow(1 - t, 3)));
      if (t < 1) requestAnimationFrame(step);
    };
    requestAnimationFrame(step);
  }

  /* ---------- 화면에 들어오면 나타나기 ---------- */

  const cta = document.getElementById('lp-cta');

  function renderReveal() {
    document.querySelectorAll('[data-lp-reveal]:not(.is-in)').forEach((el) => {
      if (!reduceMotion && el.getBoundingClientRect().top > global.innerHeight * 0.86) return;
      el.classList.add('is-in');
      el.querySelectorAll('[data-count]').forEach(countUp);
      // 터치 화면: 마우스를 올린 모습을 잠깐 뒤 자동으로
      if (isTouch && el.hasAttribute('data-lp-play')) setTimeout(() => el.classList.add('is-play'), 500);
    });
    if (!cta.classList.contains('is-in') && (reduceMotion || cta.getBoundingClientRect().top < global.innerHeight * 0.75)) {
      cta.classList.add('is-in'); // '밥 지도' 밑줄 그리기
    }
  }

  /* ---------- 스크롤할 때마다 (한 프레임에 한 번) ---------- */

  let ticking = false;

  function render() {
    ticking = false;
    header.classList.toggle('is-scrolled', global.scrollY > 4);
    renderClock();
    renderWhy(reduceMotion ? 1 : sceneProgress(why));
    renderParty(reduceMotion ? 1 : sceneProgress(partyScroll));
    renderReveal();
  }

  function onScroll() {
    if (ticking) return;
    ticking = true;
    requestAnimationFrame(render);
  }

  global.addEventListener('scroll', onScroll, { passive: true });
  global.addEventListener('resize', onScroll);
  render();
})(window);
