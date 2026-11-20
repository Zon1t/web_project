/*
 * ui.js — 화면 공통 부품 (B, 2026-11-20)
 *
 * 모든 화면에서 brand.js 다음에 불러온다. (css/ui.css와 함께)
 *   <script src="/js/components/brand.js"></script>
 *   <script src="/js/components/ui.js"></script>
 *
 * 쓰는 법
 *   ui.icon('clock', 18)                  선 아이콘 SVG 문자열 (색은 currentColor)
 *   ui.avatar('KOREAN', 44)               식당 아바타 (카테고리별 색 + 음식 아이콘)
 *   ui.illust('bowl')                     파스텔 일러스트 SVG (빈 상태·배너용)
 *   ui.empty({ illust, title, desc, actionHtml })   빈 상태 화면 HTML (title·desc는 HTML 그대로 — 사용자 입력·서버 문구는 이스케이프해서 넘길 것)
 *   ui.toast('따봉을 남겼어요')            아래에서 올라왔다 사라지는 알림 (type: 'success' | 'error')
 *   await ui.confirm({ title, message, html, confirmText, danger })   확인 모달 → true/false
 *   const m = ui.modal({ title, html, size, onClose })          모달 열기 → m.close(), m.body
 *   ui.skeleton('row', 3)                 회색 틀 로딩 HTML ('row' | 'card' | 'text')
 */
(function (global) {
  'use strict';

  /* ===================== 아이콘 (24px 격자, 선 굵기 2) ===================== */

  const ICON_PATHS = {
    clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
    pin: '<path d="M12 21s-6.5-6.2-6.5-11a6.5 6.5 0 0 1 13 0c0 4.8-6.5 11-6.5 11z"/><circle cx="12" cy="10" r="2.3"/>',
    people: '<circle cx="9" cy="8" r="3.2"/><path d="M3 19c.6-3.2 3-5 6-5s5.4 1.8 6 5"/><circle cx="17" cy="9" r="2.4"/><path d="M16.5 14c2.4.2 4 1.6 4.5 4"/>',
    thumbs: '<path d="M7 10v11H3V10h4zm0 0l4-8a3 3 0 0 1 3 3v4h5.5a2 2 0 0 1 2 2.3l-1.3 8A2 2 0 0 1 18.2 21H7"/>',
    bell: '<path d="M6 16V11a6 6 0 0 1 12 0v5l1.5 2h-15z"/><path d="M10 20.5a2.2 2.2 0 0 0 4 0"/>',
    plus: '<path d="M12 5v14M5 12h14"/>',
    close: '<path d="M6 6l12 12M18 6L6 18"/>',
    check: '<path d="M5 12.5l4.5 4.5L19 7.5"/>',
    chevronRight: '<path d="M9 6l6 6-6 6"/>',
    search: '<circle cx="11" cy="11" r="7"/><path d="M20 20l-3.5-3.5"/>',
    roulette: '<circle cx="12" cy="12" r="9"/><path d="M12 3v9l6.4 6.4M12 12L5.6 18.4M12 12H3"/>',
    refresh: '<path d="M20 11a8 8 0 1 0-2.3 5.7"/><path d="M20 5v6h-6"/>',
    chat: '<path d="M4 5h16v11H9l-5 4z"/>',
    star: '<path d="M12 3.5l2.6 5.3 5.9.9-4.3 4.1 1 5.8L12 16.9l-5.2 2.7 1-5.8-4.3-4.1 5.9-.9z"/>',
    trophy: '<path d="M8 4h8v5a4 4 0 0 1-8 0z"/><path d="M8 6H5a3 3 0 0 0 3 4M16 6h3a3 3 0 0 1-3 4M12 13v4M8 20h8M9.5 17h5"/>',
    map: '<path d="M3 6l6-2 6 2 6-2v14l-6 2-6-2-6 2z"/><path d="M9 4v14M15 6v14"/>',
    info: '<circle cx="12" cy="12" r="9"/><path d="M12 11v5M12 8h.01"/>',
    calendar: '<rect x="4" y="5" width="16" height="15" rx="2"/><path d="M4 10h16M9 3v4M15 3v4"/>',
  };

  function icon(name, size) {
    const px = size || 20;
    return '<svg class="ui-icon" width="' + px + '" height="' + px + '" viewBox="0 0 24 24" fill="none" stroke="currentColor" ' +
      'stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">' +
      (ICON_PATHS[name] || '') + '</svg>';
  }

  /* ===================== 식당 아바타 ===================== */

  // 카테고리별 그림 (40px 격자, 채움)
  const AVATAR_GLYPHS = {
    KOREAN: // 밥그릇 + 젓가락
      '<path d="M8 20h24a12 11 0 0 1-24 0z" fill="currentColor"/><path d="M11 20c1-4.5 4.5-6.5 9-6.5s8 2 9 6.5z" fill="currentColor" opacity=".35"/>' +
      '<rect x="16" y="31" width="8" height="2.6" rx="1.3" fill="currentColor"/><path d="M24 15L33 6M27 16l8-8" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"/>',
    CHINESE: // 면 그릇
      '<path d="M7 19h26a13 11 0 0 1-26 0z" fill="currentColor"/><path d="M12 19c0-4 2-6 2-9M18 19c0-4 2-6 2-10M24 19c0-4 2-6 2-9" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" fill="none" opacity=".55"/>' +
      '<rect x="15" y="30" width="10" height="2.6" rx="1.3" fill="currentColor"/>',
    JAPANESE: // 초밥
      '<rect x="7" y="20" width="26" height="10" rx="5" fill="currentColor" opacity=".35"/><path d="M8 21c3-6 9-8 14-8 5 0 9 2 11 5-2 3-23 5-25 3z" fill="currentColor"/>' +
      '<rect x="18" y="17" width="4" height="13" rx="1" fill="currentColor" opacity=".7"/>',
    WESTERN: // 포크 + 나이프
      '<path d="M13 7v9a3 3 0 0 0 3 3v14M13 7v7M16 7v7M19 7v9a3 3 0 0 1-3 3" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" fill="none"/>' +
      '<path d="M27 33V7c3 2 4.5 6 4.5 11h-4.5" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" fill="currentColor"/>',
    SNACK: // 떡꼬치
      '<path d="M10 32L31 9" stroke="currentColor" stroke-width="2.4" stroke-linecap="round"/>' +
      '<rect x="12" y="20" width="8" height="6" rx="3" transform="rotate(-48 16 23)" fill="currentColor"/>' +
      '<rect x="17" y="14.5" width="8" height="6" rx="3" transform="rotate(-48 21 17.5)" fill="currentColor"/>' +
      '<rect x="22" y="9" width="8" height="6" rx="3" transform="rotate(-48 26 12)" fill="currentColor"/>',
    ETC: // 숟가락 + 포크
      '<ellipse cx="15" cy="12" rx="4" ry="5.5" fill="currentColor"/><path d="M15 17v16" stroke="currentColor" stroke-width="2.6" stroke-linecap="round"/>' +
      '<path d="M24 7v7a3 3 0 0 0 3 3 3 3 0 0 0 3-3V7M27 17v16" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" fill="none"/>',
    CAFETERIA: // 식판
      '<rect x="6" y="11" width="28" height="20" rx="4" fill="currentColor" opacity=".35"/><rect x="9" y="14" width="10" height="7" rx="2" fill="currentColor"/>' +
      '<rect x="21" y="14" width="10" height="7" rx="2" fill="currentColor"/><rect x="9" y="23" width="22" height="5" rx="2" fill="currentColor"/>',
  };

  function avatar(category, size) {
    const key = AVATAR_GLYPHS[category] ? category : 'ETC';
    const px = size || 44;
    return '<span class="ui-avatar ui-avatar--' + key.toLowerCase() + '" style="width:' + px + 'px;height:' + px + 'px" aria-hidden="true">' +
      '<svg viewBox="0 0 40 40" focusable="false">' + AVATAR_GLYPHS[key] + '</svg></span>';
  }

  /* ===================== 일러스트 (파스텔, 은은한 입체감) ===================== */

  // 각 일러스트는 160×120 상자. 그림자 타원 + 오브젝트. 그라데이션은 오브젝트 안의 명암에만 쓴다.
  const ILLUSTS = {
    bowl:
      '<defs><linearGradient id="ib-b" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#5b8ff0"/><stop offset="1" stop-color="#2d6cdf"/></linearGradient>' +
      '<linearGradient id="ib-r" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#ffffff"/><stop offset="1" stop-color="#e9eef6"/></linearGradient></defs>' +
      '<ellipse cx="80" cy="108" rx="46" ry="6" fill="#1b2430" opacity=".08"/>' +
      '<path d="M52 30c-4-8 4-12 0-20M80 26c-4-8 4-12 0-20M108 30c-4-8 4-12 0-20" stroke="#c9d1dc" stroke-width="4" stroke-linecap="round" fill="none"/>' +
      '<path d="M40 58c4-16 22-24 40-24s36 8 40 24z" fill="url(#ib-r)"/>' +
      '<path d="M32 58h96c0 26-21 42-48 42S32 84 32 58z" fill="url(#ib-b)"/>' +
      '<path d="M40 64h80" stroke="#ffffff" stroke-opacity=".35" stroke-width="4" stroke-linecap="round"/>' +
      '<rect x="64" y="98" width="32" height="7" rx="3.5" fill="#2459bd"/>' +
      '<g transform="rotate(32 120 32)"><ellipse cx="120" cy="22" rx="8" ry="11" fill="#e8772e"/><rect x="117.5" y="31" width="5" height="26" rx="2.5" fill="#e8772e"/></g>',
    trophy:
      '<defs><linearGradient id="it-g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#ffd27a"/><stop offset="1" stop-color="#f0a33a"/></linearGradient></defs>' +
      '<ellipse cx="80" cy="110" rx="40" ry="6" fill="#1b2430" opacity=".08"/>' +
      '<path d="M54 26h52v22a26 26 0 0 1-52 0z" fill="url(#it-g)"/>' +
      '<path d="M54 32H40a14 14 0 0 0 16 18M106 32h14a14 14 0 0 1-16 18" stroke="#f0a33a" stroke-width="6" fill="none" stroke-linecap="round"/>' +
      '<rect x="74" y="72" width="12" height="16" fill="#f0a33a"/><rect x="58" y="88" width="44" height="16" rx="5" fill="#4a5563"/>' +
      '<path d="M80 34l4 8 9 1.3-6.5 6.2 1.6 8.8L80 54l-8.1 4.3 1.6-8.8-6.5-6.2 9-1.3z" fill="#ffffff" opacity=".9"/>' +
      '<rect x="66" y="94" width="28" height="4" rx="2" fill="#ffffff" opacity=".35"/>',
    roulette:
      '<ellipse cx="80" cy="110" rx="40" ry="6" fill="#1b2430" opacity=".08"/>' +
      '<circle cx="80" cy="58" r="44" fill="#4a5563"/>' +
      '<path d="M80 58V18a40 40 0 0 1 34.6 20z" fill="#e3eeff"/><path d="M80 58l34.6-20a40 40 0 0 1 0 40z" fill="#fdeee3"/>' +
      '<path d="M80 58l34.6 20A40 40 0 0 1 80 98z" fill="#e3eeff"/><path d="M80 58V98a40 40 0 0 1-34.6-20z" fill="#fdeee3"/>' +
      '<path d="M80 58L45.4 78a40 40 0 0 1 0-40z" fill="#e3eeff"/><path d="M80 58L45.4 38A40 40 0 0 1 80 18z" fill="#fdeee3"/>' +
      '<circle cx="80" cy="58" r="10" fill="#ffffff" stroke="#4a5563" stroke-width="3"/>' +
      '<path d="M80 6l-8 14h16z" fill="#e8772e" stroke="#ffffff" stroke-width="2" stroke-linejoin="round"/>',
    map:
      '<ellipse cx="80" cy="110" rx="48" ry="6" fill="#1b2430" opacity=".08"/>' +
      '<path d="M28 40l34-10 36 10 34-10v62l-34 10-36-10-34 10z" fill="#e3eeff"/>' +
      '<path d="M62 30v62l36 10V40z" fill="#cfe0ff"/>' +
      '<path d="M36 74c14-4 22 6 34 2s22-14 40-6" stroke="#ffffff" stroke-width="5" fill="none" stroke-linecap="round"/>' +
      '<path d="M98 16c-12 0-20 9-20 19 0 14 20 32 20 32s20-18 20-32c0-10-8-19-20-19z" fill="#e8772e"/><circle cx="98" cy="35" r="7" fill="#ffffff"/>',
    chat:
      '<ellipse cx="80" cy="110" rx="46" ry="6" fill="#1b2430" opacity=".08"/>' +
      '<rect x="22" y="22" width="76" height="50" rx="16" fill="#2d6cdf"/><path d="M40 72l-6 16 20-16z" fill="#2d6cdf"/>' +
      '<circle cx="44" cy="47" r="5" fill="#ffffff"/><circle cx="60" cy="47" r="5" fill="#ffffff"/><circle cx="76" cy="47" r="5" fill="#ffffff"/>' +
      '<rect x="66" y="52" width="72" height="46" rx="16" fill="#fdeee3"/><path d="M120 98l6 12-18-12z" fill="#fdeee3"/>' +
      '<path d="M84 76l6 6 12-12" stroke="#e8772e" stroke-width="5" fill="none" stroke-linecap="round" stroke-linejoin="round"/>',
    tray:
      '<ellipse cx="80" cy="110" rx="50" ry="6" fill="#1b2430" opacity=".08"/>' +
      '<rect x="24" y="34" width="112" height="68" rx="14" fill="#c9d1dc"/><rect x="28" y="30" width="104" height="66" rx="12" fill="#eef2f7"/>' +
      '<rect x="36" y="38" width="40" height="24" rx="6" fill="#fdeee3"/><rect x="84" y="38" width="40" height="24" rx="6" fill="#e3eeff"/>' +
      '<rect x="36" y="68" width="88" height="20" rx="6" fill="#ffffff"/>' +
      '<circle cx="50" cy="50" r="6" fill="#e8772e"/><circle cx="62" cy="52" r="4" fill="#f0a33a"/>' +
      '<path d="M92 50h24" stroke="#2d6cdf" stroke-width="5" stroke-linecap="round"/><path d="M48 78h64" stroke="#c9d1dc" stroke-width="4" stroke-linecap="round"/>',
    thumbs:
      '<defs><linearGradient id="ith-g" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#ffb07a"/><stop offset="1" stop-color="#e8772e"/></linearGradient></defs>' +
      '<ellipse cx="80" cy="110" rx="38" ry="6" fill="#1b2430" opacity=".08"/>' +
      '<rect x="42" y="56" width="18" height="44" rx="5" fill="#4a5563"/>' +
      '<path d="M62 56l14-30c8 0 12 6 11 14l-2 12h22c7 0 12 6 10 13l-6 26c-1 5-5 9-10 9H62z" fill="url(#ith-g)"/>' +
      '<path d="M70 70h30" stroke="#ffffff" stroke-opacity=".4" stroke-width="4" stroke-linecap="round"/>' +
      '<circle cx="118" cy="30" r="5" fill="#ffd27a"/><circle cx="40" cy="36" r="4" fill="#5b8ff0"/>',
  };

  function illust(name, className) {
    return '<svg class="ui-illust ' + (className || '') + '" viewBox="0 0 160 120" aria-hidden="true" focusable="false">' +
      (ILLUSTS[name] || ILLUSTS.bowl) + '</svg>';
  }

  /** 빈 상태 공통 화면 */
  function empty(o) {
    return '<div class="ui-empty">' +
      illust(o.illust || 'bowl', 'ui-empty__illust') +
      '<p class="ui-empty__title">' + (o.title || '') + '</p>' +
      (o.desc ? '<p class="ui-empty__desc">' + o.desc + '</p>' : '') +
      (o.actionHtml ? '<div class="ui-empty__actions">' + o.actionHtml + '</div>' : '') +
      '</div>';
  }

  /* ===================== 토스트 ===================== */

  let toastBox = null;

  function toast(message, opts) {
    const type = (opts && opts.type) || 'success';
    if (!toastBox) {
      toastBox = document.createElement('div');
      toastBox.className = 'ui-toasts';
      toastBox.setAttribute('role', 'status');
      toastBox.setAttribute('aria-live', 'polite');
      document.body.appendChild(toastBox);
    }
    const item = document.createElement('div');
    item.className = 'ui-toast ui-toast--' + type;
    item.innerHTML = icon(type === 'error' ? 'info' : 'check', 18) + '<span></span>';
    item.querySelector('span').textContent = message;
    toastBox.appendChild(item);
    setTimeout(() => {
      item.classList.add('is-leaving');
      setTimeout(() => item.remove(), 300);
    }, (opts && opts.duration) || 2600);
  }

  /* ===================== 모달 ===================== */

  const openModals = [];

  /**
   * o.title: 제목 (없으면 머리 없이 닫기 버튼만)
   * o.html: 본문 HTML
   * o.size: 'sm' | 'md' | 'lg' | 'xl'
   * o.onClose: 닫힌 뒤 호출
   * o.flush: 본문 안쪽 여백 없이 (iframe 등)
   */
  function modal(o) {
    const previousFocus = document.activeElement;
    const root = document.createElement('div');
    root.className = 'ui-modal ui-modal--' + (o.size || 'md') + (o.flush ? ' ui-modal--flush' : '');
    root.innerHTML =
      '<div class="ui-modal__backdrop" data-modal-close></div>' +
      '<div class="ui-modal__panel" role="dialog" aria-modal="true"' + (o.title ? ' aria-label="' + escapeAttr(o.title) + '"' : '') + '>' +
      (o.title ? '<div class="ui-modal__head"><h2 class="ui-modal__title"></h2></div>' : '') +
      '<button type="button" class="ui-modal__close" data-modal-close aria-label="닫기">' + icon('close', 22) + '</button>' +
      '<div class="ui-modal__body">' + (o.html || '') + '</div>' +
      '</div>';
    if (o.title) root.querySelector('.ui-modal__title').textContent = o.title;
    document.body.appendChild(root);
    document.documentElement.classList.add('has-modal');

    let closed = false;
    function close(reason) {
      if (closed) return;
      closed = true;
      root.classList.add('is-leaving');
      document.removeEventListener('keydown', onKey);
      setTimeout(() => {
        root.remove();
        openModals.splice(openModals.indexOf(api), 1);
        if (!openModals.length) document.documentElement.classList.remove('has-modal');
        if (previousFocus && previousFocus.focus) previousFocus.focus();
        if (o.onClose) o.onClose(reason);
      }, 200);
    }
    function onKey(event) {
      if (event.key === 'Escape' && openModals[openModals.length - 1] === api) close('escape');
    }
    root.addEventListener('click', (event) => {
      if (event.target.closest('[data-modal-close]')) close('dismiss');
    });
    document.addEventListener('keydown', onKey);

    const api = { el: root, body: root.querySelector('.ui-modal__body'), close };
    openModals.push(api);
    requestAnimationFrame(() => {
      const focusTarget = root.querySelector('[autofocus]') || root.querySelector('.ui-modal__close');
      if (focusTarget) focusTarget.focus();
    });
    return api;
  }

  /** 확인 모달 → Promise<boolean> */
  function confirm(o) {
    return new Promise((resolve) => {
      let answered = false;
      const m = modal({
        size: 'sm',
        title: o.title || '확인',
        html:
          (o.html || '') + // 호출하는 쪽에서 이스케이프한 HTML (예: 식사팟 요약)
          (o.message ? '<p class="ui-confirm__message"></p>' : '') +
          '<div class="ui-confirm__actions">' +
          '<button type="button" class="btn" data-answer="no">' + (o.cancelText || '취소') + '</button>' +
          '<button type="button" class="btn ' + (o.danger ? 'btn--danger' : 'btn--primary') + '" data-answer="yes" autofocus>' +
          (o.confirmText || '확인') + '</button>' +
          '</div>',
        onClose: () => { if (!answered) resolve(false); },
      });
      if (o.message) m.body.querySelector('.ui-confirm__message').textContent = o.message;
      m.body.addEventListener('click', (event) => {
        const button = event.target.closest('[data-answer]');
        if (!button) return;
        answered = true;
        resolve(button.dataset.answer === 'yes');
        m.close('answer');
      });
    });
  }

  /* ===================== 스켈레톤 ===================== */

  function skeleton(type, count) {
    const n = count || 3;
    let one;
    if (type === 'card') {
      one = '<div class="ui-skel ui-skel--card"><span class="ui-skel__line ui-skel__line--lg"></span><span class="ui-skel__line"></span><span class="ui-skel__line ui-skel__line--sm"></span></div>';
    } else if (type === 'text') {
      one = '<span class="ui-skel__line"></span>';
    } else {
      one = '<div class="ui-skel ui-skel--row"><span class="ui-skel__circle"></span><span class="ui-skel__stack"><span class="ui-skel__line ui-skel__line--lg"></span><span class="ui-skel__line ui-skel__line--sm"></span></span></div>';
    }
    return '<div class="ui-skels ui-skels--' + (type || 'row') + '" aria-busy="true" aria-label="불러오는 중">' +
      new Array(n + 1).join(one) + '</div>';
  }

  function escapeAttr(value) {
    return String(value).replace(/[&<>"']/g, (c) =>
      ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
  }

  global.ui = { icon, avatar, illust, empty, toast, modal, confirm, skeleton };

  // HTML에 <span data-ui-illust="bowl"></span> / <span data-ui-icon="clock"></span>처럼 적어 두면 그림을 채운다
  function hydrate() {
    document.querySelectorAll('[data-ui-illust]').forEach((el) => { el.innerHTML = illust(el.dataset.uiIllust); });
    document.querySelectorAll('[data-ui-icon]').forEach((el) => { el.innerHTML = icon(el.dataset.uiIcon, Number(el.dataset.size) || 18); });
  }

  // 뒤로 가기: <a class="ui-back" href="/대신-갈-주소" data-ui-back>뒤로</a>
  // 이 사이트 안에서 왔으면 브라우저 뒤로 가기, 주소를 직접 열었으면 href로 이동
  document.addEventListener('click', (event) => {
    const link = event.target.closest('[data-ui-back]');
    if (!link) return;
    let fromHere = false;
    try {
      fromHere = !!document.referrer && new URL(document.referrer).origin === location.origin;
    } catch (e) {
      fromHere = false;
    }
    if (fromHere && history.length > 1) {
      event.preventDefault();
      history.back();
    }
  });
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', hydrate);
  else hydrate();
})(window);
