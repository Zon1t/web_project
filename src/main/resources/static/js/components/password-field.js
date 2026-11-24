/*
 * password-field.js — 비밀번호 입력칸 공통 부품 (C, 2026-11-24)
 *
 * 1) 비밀번호 보기 버튼 + Caps Lock 안내
 *    <input type="password" class="input" data-password-field ...>
 *    이 파일을 불러오면 data-password-field가 붙은 칸에 자동으로 붙는다. (css/password-field.css 필요)
 *    나중에 그린 칸은 passwordField.enhance(input)
 *
 * 2) 비밀번호 규칙 검사 (서버 PasswordPolicy와 같은 규칙)
 *    passwordField.ruleError('abcd1234', 'user01') → null(통과) 또는 안내 문구
 *    - 8~20자, 영문과 숫자 모두 포함, 공백 불가, 아이디와 같으면 안 됨
 */
(function (global) {
  'use strict';

  const MIN = 8;
  const MAX = 20;
  const RULE_HINT = '영문·숫자를 함께 넣어 8~20자';

  const EYE =
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
    '<path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/></svg>';
  const EYE_OFF =
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
    '<path d="M3 3l18 18"/><path d="M10.6 5.1A10.6 10.6 0 0 1 12 5c6.4 0 10 7 10 7a17.6 17.6 0 0 1-3.2 4.1M6.6 6.6C3.8 8.4 2 12 2 12s3.6 7 10 7a9.7 9.7 0 0 0 5.4-1.6"/>' +
    '<path d="M9.9 9.9a3 3 0 0 0 4.2 4.2"/></svg>';

  /** 규칙에 맞으면 null, 아니면 화면에 보여 줄 문구 */
  function ruleError(password, loginId) {
    const value = String(password || '');
    if (value.length < MIN || value.length > MAX) return '비밀번호는 8~20자로 입력해 주세요.';
    if (/\s/.test(value)) return '비밀번호에는 공백을 넣을 수 없어요.';
    if (!/[A-Za-z]/.test(value) || !/[0-9]/.test(value)) return '영문과 숫자를 함께 넣어 주세요.';
    if (loginId && value.toLowerCase() === String(loginId).toLowerCase()) return '아이디와 다른 비밀번호를 입력해 주세요.';
    return null;
  }

  function enhance(input) {
    if (!input || input.dataset.pwReady) return;
    input.dataset.pwReady = '1';

    // 입력칸을 감싸고 오른쪽 안에 [보기] 버튼
    const wrap = document.createElement('span');
    wrap.className = 'pw-field';
    input.parentNode.insertBefore(wrap, input);
    wrap.appendChild(input);

    const toggle = document.createElement('button');
    toggle.type = 'button';
    toggle.className = 'pw-field__toggle';
    toggle.setAttribute('aria-label', '비밀번호 보기');
    toggle.setAttribute('aria-pressed', 'false');
    toggle.innerHTML = EYE;
    wrap.appendChild(toggle);

    toggle.addEventListener('click', () => {
      const show = input.type === 'password';
      input.type = show ? 'text' : 'password';
      toggle.setAttribute('aria-pressed', String(show));
      toggle.setAttribute('aria-label', show ? '비밀번호 숨기기' : '비밀번호 보기');
      toggle.innerHTML = show ? EYE_OFF : EYE;
      input.focus();
    });

    // Caps Lock 안내 (입력칸 바로 아래)
    const caps = document.createElement('p');
    caps.className = 'pw-caps';
    caps.hidden = true;
    caps.setAttribute('aria-live', 'polite');
    caps.textContent = 'Caps Lock이 켜져 있어요.';
    wrap.insertAdjacentElement('afterend', caps);

    const checkCaps = (event) => {
      if (typeof event.getModifierState === 'function') {
        caps.hidden = !event.getModifierState('CapsLock');
      }
    };
    input.addEventListener('keydown', checkCaps);
    input.addEventListener('keyup', checkCaps);
    input.addEventListener('blur', () => { caps.hidden = true; });
  }

  /** 폼을 다시 쓸 때(전송 후) 보기 상태를 원래대로 */
  function reset(input) {
    if (!input) return;
    input.type = 'password';
    const toggle = input.parentNode && input.parentNode.querySelector('.pw-field__toggle');
    if (toggle) {
      toggle.setAttribute('aria-pressed', 'false');
      toggle.setAttribute('aria-label', '비밀번호 보기');
      toggle.innerHTML = EYE;
    }
  }

  document.querySelectorAll('input[data-password-field]').forEach(enhance);

  global.passwordField = { enhance, reset, ruleError, RULE_HINT, MIN, MAX };
})(window);
