/*
 * account.js — 계정 설정 화면 (C, 2026-11-24)
 *
 * - 내 정보: 아이디·근무지 표시, 닉네임 변경 (PATCH /api/users/me/nickname)
 *   · 앞뒤 공백 제거 후 2~10자, 다른 회원이 쓰는 닉네임이면 서버가 409
 *   · 바꾸면 헤더 닉네임도 바로 바뀐다 ('sseukbap:nickname-changed' 신호 → app-header.js)
 * - 비밀번호 변경 (PATCH /api/users/me/password)
 *   · 화면에서 먼저 규칙 검사(password-field.js의 ruleError), 최종 판단은 서버
 *   · 바꾼 뒤에도 로그인은 그대로 유지된다
 * - 회원 탈퇴는 C2에서 이 화면 아래에 추가한다
 */
(function () {
  'use strict';

  const NICKNAME_MIN = 2;
  const NICKNAME_MAX = 10;

  const loginIdEl = document.getElementById('account-login-id');

  const nicknameForm = document.getElementById('nickname-form');
  const nicknameSubmit = document.getElementById('nickname-submit');
  const nickname = field('account-nickname');

  const passwordForm = document.getElementById('password-form');
  const passwordSubmit = document.getElementById('password-submit');
  const current = field('account-current-password');
  const next = field('account-new-password');
  const confirm = field('account-new-password-confirm');

  let me = null;   // { memberId, loginId, nickname }

  init();

  async function init() {
    try {
      me = await authApi.me();   // 401이면 client.js가 로그인 화면으로 이동
      loginIdEl.textContent = me.loginId;
      nickname.input.value = me.nickname;
    } catch (err) {
      if (err.statusCode !== 401) ui.toast(err.message, { type: 'error' });
    }
  }

  /* ---------- 닉네임 ---------- */

  nickname.input.addEventListener('input', () => clearMessage(nickname));

  nicknameForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    const value = nickname.input.value.trim();

    if (value.length < NICKNAME_MIN || value.length > NICKNAME_MAX) {
      setMessage(nickname, '닉네임은 2~10자로 입력해 주세요.', 'error');
      nickname.input.focus();
      return;
    }
    if (me && value === me.nickname) {
      setMessage(nickname, '지금 쓰는 닉네임이에요.', 'error');
      return;
    }

    nicknameSubmit.disabled = true;
    try {
      const result = await accountApi.changeNickname(value);
      if (me) me.nickname = result.nickname;
      nickname.input.value = result.nickname;
      setMessage(nickname, '닉네임을 바꿨어요.', 'success');
      ui.toast('닉네임을 바꿨어요');
      document.dispatchEvent(new CustomEvent('sseukbap:nickname-changed', { detail: { nickname: result.nickname } }));
    } catch (err) {
      if (err.statusCode === 401) return;
      setMessage(nickname, err.message, 'error');   // 409: 이미 사용 중인 닉네임이에요.
      nickname.input.focus();
    } finally {
      nicknameSubmit.disabled = false;
    }
  });

  /* ---------- 비밀번호 ---------- */

  [current, next, confirm].forEach((f) => f.input.addEventListener('input', () => clearMessage(f)));
  next.input.addEventListener('input', () => {
    if (confirm.input.value) checkConfirm();
  });
  confirm.input.addEventListener('input', checkConfirm);

  function checkConfirm() {
    if (!confirm.input.value) return clearMessage(confirm);
    if (confirm.input.value !== next.input.value) {
      setMessage(confirm, '비밀번호가 일치하지 않아요.', 'error');
    } else {
      setMessage(confirm, '비밀번호가 일치해요.', 'success');
    }
  }

  passwordForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    [current, next, confirm].forEach(clearMessage);

    // 화면에서 먼저 확인 (위에서부터 첫 오류에 커서)
    let firstError = null;
    if (!current.input.value) {
      setMessage(current, '현재 비밀번호를 입력해 주세요.', 'error');
      firstError = firstError || current;
    }
    const ruleError = passwordField.ruleError(next.input.value, me && me.loginId);
    if (ruleError) {
      setMessage(next, ruleError, 'error');
      firstError = firstError || next;
    } else if (next.input.value === current.input.value) {
      setMessage(next, '지금 쓰는 비밀번호와 다른 비밀번호를 입력해 주세요.', 'error');
      firstError = firstError || next;
    }
    if (!confirm.input.value || confirm.input.value !== next.input.value) {
      setMessage(confirm, confirm.input.value ? '비밀번호가 일치하지 않아요.' : '새 비밀번호를 한 번 더 입력해 주세요.', 'error');
      firstError = firstError || confirm;
    }
    if (firstError) {
      firstError.input.focus();
      return;
    }

    passwordSubmit.disabled = true;
    try {
      await accountApi.changePassword({
        currentPassword: current.input.value,
        newPassword: next.input.value,
        newPasswordConfirm: confirm.input.value,
      });
      passwordForm.reset();
      [current, next, confirm].forEach((f) => {
        clearMessage(f);
        passwordField.reset(f.input);
      });
      ui.toast('비밀번호를 바꿨어요');
    } catch (err) {
      if (err.statusCode === 401) return;
      // 서버 오류 코드에 맞는 칸 아래에 보여 준다
      const target = err.error === 'INVALID_PASSWORD' ? current
        : err.error === 'PASSWORD_MISMATCH' ? confirm
          : next;
      setMessage(target, err.message, 'error');
      target.input.focus();
    } finally {
      passwordSubmit.disabled = false;
    }
  });

  /* ---------- 공통 ---------- */

  function field(id) {
    return {
      input: document.getElementById(id),
      message: document.getElementById(id + '-message'),
    };
  }

  function setMessage(f, text, type) {
    f.message.textContent = text;
    f.message.className = 'field__message field__message--' + type;
    f.input.classList.toggle('input--error', type === 'error');
    if (type === 'error') f.input.setAttribute('aria-invalid', 'true');
    else f.input.removeAttribute('aria-invalid');
  }

  function clearMessage(f) {
    f.message.textContent = '';
    f.message.className = 'field__message';
    f.input.classList.remove('input--error');
    f.input.removeAttribute('aria-invalid');
  }
})();
