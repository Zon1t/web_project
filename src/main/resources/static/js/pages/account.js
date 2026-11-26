/*
 * account.js — 계정 설정 화면 (C, 2026-11-24)
 *
 * - 내 정보: 아이디·근무지 표시, 닉네임 변경 (PATCH /api/users/me/nickname)
 *   · 앞뒤 공백 제거 후 2~12자, 다른 회원이 쓰는 닉네임이면 서버가 409
 *   · 바꾸면 헤더 닉네임도 바로 바뀐다 ('sseukbap:nickname-changed' 신호 → app-header.js)
 * - 비밀번호 변경 (PATCH /api/users/me/password)
 *   · 화면에서 먼저 규칙 검사(password-field.js의 ruleError), 최종 판단은 서버
 *   · 바꾼 뒤에도 로그인은 그대로 유지된다
 * - 회사 이메일 등록·변경 (PATCH /api/users/me/email) — 2026-11-25 T3: @samsung.com만, 로그인에도 쓸 수 있음
 *   · 이메일이 없는 기존 회원(emailRequired)이면 맨 위에 등록 안내를 띄우고 이메일 칸으로 보낸다
 * - 회원 탈퇴 (POST /api/users/me/withdraw, C2 2026-11-24)
 *   · 비밀번호 확인 + '되돌릴 수 없어요' 확인 창 → 성공하면 세션이 끊기고 랜딩 화면으로
 */
(function () {
  'use strict';

  const NICKNAME_MIN = 2;
  const NICKNAME_MAX = 12;
  const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  const LANDING_URL = '/landing.html';

  const loginIdEl = document.getElementById('account-login-id');
  const emailRequiredEl = document.getElementById('account-email-required');

  const nicknameForm = document.getElementById('nickname-form');
  const nicknameSubmit = document.getElementById('nickname-submit');
  const nickname = field('account-nickname');

  const emailForm = document.getElementById('email-form');
  const emailSubmit = document.getElementById('email-submit');
  const email = field('account-email');

  const withdrawForm = document.getElementById('withdraw-form');
  const withdrawSubmit = document.getElementById('withdraw-submit');
  const withdrawPassword = field('account-withdraw-password');

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
      showLoginAccount();
      nickname.input.value = me.nickname;
      email.input.value = me.email || '';
      emailSubmit.textContent = me.email ? '변경' : '등록';
      if (me.emailRequired) {
        emailRequiredEl.hidden = false;
        email.input.focus();
      }
    } catch (err) {
      if (err.statusCode !== 401) ui.toast(err.message, { type: 'error' });
    }
  }

  /** 아이디 표시 */
  function showLoginAccount() {
    loginIdEl.textContent = me.loginId;
  }

  /* ---------- 닉네임 ---------- */

  nickname.input.addEventListener('input', () => clearMessage(nickname));

  nicknameForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    const value = nickname.input.value.trim();

    if (value.length < NICKNAME_MIN || value.length > NICKNAME_MAX) {
      setMessage(nickname, '닉네임은 2~12자로 입력해 주세요.', 'error');
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

  /* ---------- 회사 이메일 ---------- */

  email.input.addEventListener('input', () => clearMessage(email));

  emailForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    const value = email.input.value.trim().toLowerCase();

    if (!EMAIL_PATTERN.test(value)) {
      setMessage(email, value ? '이메일 형식을 확인해 주세요.' : '이메일을 입력해 주세요.', 'error');
      email.input.focus();
      return;
    }
    if (me && value === me.email) {
      setMessage(email, '지금 등록된 이메일이에요.', 'error');
      return;
    }

    emailSubmit.disabled = true;
    try {
      const result = await accountApi.changeEmail(value);
      if (me) {
        me.email = result.email;
        me.emailRequired = false;
        showLoginAccount();
      }
      emailRequiredEl.hidden = true;
      email.input.value = result.email;
      emailSubmit.textContent = '변경';
      setMessage(email, '이메일을 저장했어요. 이 이메일로도 로그인할 수 있어요.', 'success');
      ui.toast('회사 이메일을 저장했어요');
    } catch (err) {
      if (err.statusCode === 401) return;
      setMessage(email, err.message, 'error');   // 409: 이미 등록된 이메일이에요.
      email.input.focus();
    } finally {
      emailSubmit.disabled = false;
    }
  });

  /* ---------- 회원 탈퇴 ---------- */

  withdrawPassword.input.addEventListener('input', () => clearMessage(withdrawPassword));

  withdrawForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (!withdrawPassword.input.value) {
      setMessage(withdrawPassword, '비밀번호를 입력해 주세요.', 'error');
      withdrawPassword.input.focus();
      return;
    }

    const ok = await ui.confirm({
      title: '정말 탈퇴할까요?',
      message: '탈퇴하면 되돌릴 수 없어요. 방장인 식사팟은 삭제되고, 참여 중인 식사팟에서는 나가요.',
      confirmText: '탈퇴하기',
      danger: true,
    });
    if (!ok) return;

    withdrawSubmit.disabled = true;
    try {
      await accountApi.withdraw(withdrawPassword.input.value);
      location.replace(LANDING_URL);   // 세션이 끊겼으므로 로그인 없이 볼 수 있는 랜딩으로
    } catch (err) {
      if (err.statusCode === 401) return;
      setMessage(withdrawPassword, err.message, 'error');   // 400 INVALID_PASSWORD: 현재 비밀번호가 일치하지 않아요.
      withdrawPassword.input.focus();
      withdrawSubmit.disabled = false;
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
    // 아이디와 다르게
    const account = me && me.loginId;
    const ruleError = passwordField.ruleError(next.input.value, account);
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
