/*
 * profile-card.js — 프로필 카드 (C, 2026-11-25)
 *
 * 이름·아바타를 누르면 그 자리 옆에 작은 카드가 뜬다. (메신저 프로필처럼)
 *   닉네임, @아이디, 회사 이메일(메일 쓰기 링크 + 복사), 근무지, 가입일
 *
 * 쓰는 법: 누를 요소에 data-profile-id="회원번호"를 붙이면 끝. (이 파일이 클릭을 알아서 받는다)
 *   <button type="button" class="member-avatar" data-profile-id="3">밥</button>
 *
 * 사람마다 다른 색 (대화방 구분용): profileCard.colorClass(memberId) → 'pc-color-0' ~ 'pc-color-7'
 *
 * 필요: client.js(api), utils.js, ui.js, css/profile-card.css
 * API: GET /api/members/{memberId}/profile → { memberId, nickname, loginId, email, workplace, joinedAt }
 */
(function (global) {
  'use strict';

  const COLORS = 8;
  const esc = (value) => utils.escapeHtml(value == null ? '' : String(value));
  const cache = new Map();   // memberId → 프로필 (페이지에 있는 동안)

  let cardEl = null;
  let anchorEl = null;
  let openSeq = 0;

  /** 같은 회원은 언제나 같은 색 (회원번호가 없으면 닉네임으로) */
  function colorClass(key) {
    const text = String(key == null ? '' : key);
    let hash = 0;
    for (let i = 0; i < text.length; i++) hash = (hash * 31 + text.charCodeAt(i)) >>> 0;
    const n = /^\d+$/.test(text) ? Number(text) : hash;
    return 'pc-color-' + (n % COLORS);
  }

  function initialOf(name) {
    return String(name || '?').trim().charAt(0) || '?';
  }

  async function fetchProfile(memberId) {
    if (cache.has(memberId)) return cache.get(memberId);
    const profile = await api.get('/members/' + encodeURIComponent(memberId) + '/profile');
    cache.set(memberId, profile);
    return profile;
  }

  function ensureCard() {
    if (cardEl) return cardEl;
    cardEl = document.createElement('div');
    cardEl.className = 'pc-card';
    cardEl.setAttribute('role', 'dialog');
    cardEl.setAttribute('aria-label', '프로필');
    cardEl.hidden = true;
    document.body.appendChild(cardEl);
    cardEl.addEventListener('click', onCardClick);
    return cardEl;
  }

  async function open(memberId, anchor) {
    const seq = ++openSeq;
    const card = ensureCard();
    anchorEl = anchor || null;
    card.innerHTML = '<div class="pc-card__loading">불러오는 중…</div>';
    card.hidden = false;
    place();
    try {
      const p = await fetchProfile(memberId);
      if (seq !== openSeq) return;
      card.innerHTML = render(p);
      place();
      const closeButton = card.querySelector('.pc-card__close');
      if (closeButton) closeButton.focus({ preventScroll: true });
    } catch (err) {
      if (seq !== openSeq) return;
      card.innerHTML =
        '<button type="button" class="pc-card__close" data-pc="close" aria-label="닫기">×</button>' +
        '<p class="pc-card__error">' + esc(err.statusCode === 404 ? '탈퇴했거나 없는 회원이에요.' : (err.message || '프로필을 불러오지 못했어요.')) + '</p>';
      place();
    }
  }

  function render(p) {
    const email = p.email
      ? '<a class="pc-card__email" href="mailto:' + esc(p.email) + '">' + esc(p.email) + '</a>' +
        '<button type="button" class="pc-card__copy" data-pc="copy" data-email="' + esc(p.email) + '">복사</button>'
      : '<span class="text-muted">등록한 이메일이 없어요</span>';
    return (
      '<button type="button" class="pc-card__close" data-pc="close" aria-label="닫기">×</button>' +
      '<div class="pc-card__top">' +
      '<span class="pc-card__avatar ' + colorClass(p.memberId) + '" aria-hidden="true">' + esc(initialOf(p.nickname)) + '</span>' +
      '<p class="pc-card__name">' + esc(p.nickname) + '</p>' +
      '<p class="pc-card__id">@' + esc(p.loginId) + '</p>' +
      '</div>' +
      '<dl class="pc-card__info">' +
      '<div><dt>회사 이메일</dt><dd>' + email + '</dd></div>' +
      '<div><dt>근무지</dt><dd>' + esc(p.workplace) + '</dd></div>' +
      (p.joinedAt ? '<div><dt>가입일</dt><dd>' + esc(String(p.joinedAt).replace(/-/g, '.')) + '</dd></div>' : '') +
      '</dl>'
    );
  }

  /** 누른 자리 오른쪽(공간이 없으면 왼쪽) 아래에 띄운다. 화면 밖으로 나가지 않게 */
  function place() {
    if (!cardEl || cardEl.hidden) return;
    const w = cardEl.offsetWidth;
    const h = cardEl.offsetHeight;
    let left = (window.innerWidth - w) / 2;
    let top = (window.innerHeight - h) / 2;
    if (anchorEl && document.body.contains(anchorEl)) {
      const r = anchorEl.getBoundingClientRect();
      left = r.right + 8;
      if (left + w > window.innerWidth - 8) left = r.left - w - 8;
      top = r.top;
    }
    left = Math.min(Math.max(8, left), window.innerWidth - w - 8);
    top = Math.min(Math.max(8, top), window.innerHeight - h - 8);
    cardEl.style.left = left + 'px';
    cardEl.style.top = top + 'px';
  }

  function close() {
    openSeq++;
    if (!cardEl || cardEl.hidden) return;
    cardEl.hidden = true;
    if (anchorEl && document.body.contains(anchorEl)) anchorEl.focus({ preventScroll: true });
    anchorEl = null;
  }

  async function onCardClick(event) {
    const target = event.target.closest('[data-pc]');
    if (!target) return;
    if (target.dataset.pc === 'close') {
      close();
    } else if (target.dataset.pc === 'copy') {
      const ok = await copyText(target.dataset.email);
      ui.toast(ok ? '이메일을 복사했어요.' : '복사하지 못했어요. 직접 선택해서 복사해 주세요.', { type: ok ? 'success' : 'error' });
    }
  }

  /** 클립보드 복사. 막힌 환경이면 숨은 입력칸으로 한 번 더 시도한다. 성공하면 true */
  async function copyText(text) {
    try {
      if (navigator.clipboard && window.isSecureContext) {
        await navigator.clipboard.writeText(text);
        return true;
      }
    } catch (e) {
      // 아래 방법으로 다시 시도
    }
    const area = document.createElement('textarea');
    area.value = text;
    area.setAttribute('readonly', '');
    area.style.position = 'fixed';
    area.style.opacity = '0';
    document.body.appendChild(area);
    area.select();
    let ok = false;
    try {
      ok = document.execCommand('copy');
    } catch (e) {
      ok = false;
    }
    area.remove();
    return ok;
  }

  // data-profile-id 가 붙은 요소를 누르면 카드를 연다 (같은 사람을 다시 누르면 닫는다)
  document.addEventListener('click', (event) => {
    const trigger = event.target.closest('[data-profile-id]');
    if (trigger) {
      event.preventDefault();
      if (cardEl && !cardEl.hidden && anchorEl === trigger) {
        close();
      } else {
        open(trigger.dataset.profileId, trigger);
      }
      return;
    }
    if (cardEl && !cardEl.hidden && !cardEl.contains(event.target)) close();
  });
  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') close();
  });
  window.addEventListener('resize', close);

  global.profileCard = { open, close, colorClass, copyText };
})(window);
