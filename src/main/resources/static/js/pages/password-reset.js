/*
 * password-reset.js — 비밀번호 찾기 화면 (C, 2026-11-24)
 *
 * 한 화면에서 차례로 진행한다.
 * 1) 아이디 + 회사 이메일 → [인증번호 받기] (POST /api/auth/password-reset/request)
 *    서버는 아이디·이메일이 틀려도 같은 응답을 준다 → 화면도 항상 "보냈어요"로 안내한다
 * 2) 인증번호 6자리 → [인증번호 확인] (POST /api/auth/password-reset/verify) → 맞으면 새 비밀번호 칸이 열린다
 * 3) 새 비밀번호 → [비밀번호 바꾸기] (POST /api/auth/password-reset/confirm) → 로그인 화면으로
 * - 번호를 받은 뒤 아이디·이메일을 고치면 처음부터 다시 (번호를 다시 받아야 함)
 * - 남은 시간(5분)을 보여 주고, 끝나면 [다시 받기] 안내
 * - 실제 메일은 보내지 않는다. 인증번호는 서버 콘솔에 찍힌다 (LogMailSender)
 * - 아이디 찾기 탭은 없앴다 (2026-11-24)
 */
(function () {
  'use strict';

  const RESET_DONE_KEY = 'sseukbap.passwordResetLoginId';   // login.js와 같은 값
  const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  const CODE_PATTERN = /^\d{6}$/;

  const notice = document.getElementById('reset-notice');
  const form = document.getElementById('reset-form');

  const loginId = field('reset-id');
  const email = field('reset-email');
  const requestButton = document.getElementById('request-submit');

  const codeStep = document.getElementById('code-step');
  const code = field('reset-code');
  const verifyButton = document.getElementById('verify-submit');
  const timerEl = document.getElementById('reset-timer');

  const passwordStep = document.getElementById('password-step');
  const next = field('reset-new-password');
  const confirm = field('reset-new-password-confirm');
  const confirmSubmit = document.getElementById('confirm-submit');

  let sentTo = null;       // 번호를 보낸 { loginId, email }
  let verified = false;    // 인증번호 확인을 통과했는지
  let timer = null;
  let expiresAt = 0;

  loginId.input.focus();

  /* ---------- Enter 키: 지금 커서가 있는 단계의 버튼을 누른 것처럼 ---------- */

  form.addEventListener('submit', (event) => {
    event.preventDefault();
    const active = document.activeElement;
    if (active === loginId.input || active === email.input) {
      requestCode();
    } else if (active === code.input) {
      verifyCode();
    } else if (verified) {
      changePassword();
    }
  });

  /* ---------- 1) 인증번호 받기 ---------- */

  loginId.input.addEventListener('input', () => {
    const lower = loginId.input.value.toLowerCase();   // 아이디는 소문자로 (서버도 대소문자 무시)
    if (lower !== loginId.input.value) {
      const caret = loginId.input.selectionStart;
      loginId.input.value = lower;
      loginId.input.setSelectionRange(caret, caret);
    }
    clearMessage(loginId);
    resetIfChanged();
  });
  email.input.addEventListener('input', () => {
    clearMessage(email);
    resetIfChanged();
  });

  requestButton.addEventListener('click', requestCode);

  async function requestCode() {
    hideNotice();
    const id = loginId.input.value.trim();
    const mail = email.input.value.trim();

    let ok = true;
    if (!id) {
      setMessage(loginId, '아이디를 입력해 주세요.', 'error');
      ok = false;
    }
    if (!EMAIL_PATTERN.test(mail)) {
      setMessage(email, mail ? '이메일 형식을 확인해 주세요.' : '이메일을 입력해 주세요.', 'error');
      ok = false;
    }
    if (!ok) {
      (id ? email : loginId).input.focus();
      return;
    }

    requestButton.disabled = true;
    try {
      const result = await authApi.requestPasswordReset(id, mail);
      sentTo = { loginId: id, email: mail };
      showCodeStep(result.email, result.expiresInSeconds);
    } catch (err) {
      showNotice(err.message, 'error');
    } finally {
      requestButton.disabled = false;
    }
  }

  function showCodeStep(sentEmail, seconds) {
    setVerified(false);
    codeStep.hidden = false;
    requestButton.textContent = '인증번호 다시 받기';
    requestButton.classList.remove('btn--primary');
    showNotice(sentEmail + '으로 인증번호를 보냈어요. 메일이 오지 않으면 아이디와 등록한 이메일이 맞는지 확인해 주세요.', 'info');
    code.input.value = '';
    clearMessage(code);
    startTimer(seconds);
    code.input.focus();
  }

  /** 번호를 받은 뒤 아이디·이메일을 고치면 처음 상태로 */
  function resetIfChanged() {
    if (!sentTo) return;
    if (loginId.input.value.trim() === sentTo.loginId && email.input.value.trim() === sentTo.email) return;
    sentTo = null;
    setVerified(false);
    stopTimer();
    codeStep.hidden = true;
    requestButton.textContent = '인증번호 받기';
    requestButton.classList.add('btn--primary');
    hideNotice();
  }

  /* ---------- 2) 인증번호 확인 ---------- */

  code.input.addEventListener('input', () => {
    code.input.value = code.input.value.replace(/\D/g, '').slice(0, 6);   // 숫자만
    clearMessage(code);
  });

  verifyButton.addEventListener('click', verifyCode);

  async function verifyCode() {
    if (!sentTo || verified) return;
    hideNotice();
    if (!CODE_PATTERN.test(code.input.value)) {
      setMessage(code, '인증번호 6자리를 입력해 주세요.', 'error');
      code.input.focus();
      return;
    }

    verifyButton.disabled = true;
    try {
      await authApi.verifyPasswordReset(sentTo.loginId, code.input.value);
      setVerified(true);
      setMessage(code, '인증되었어요. 새 비밀번호를 정해 주세요.', 'success');
      next.input.focus();
    } catch (err) {
      verifyButton.disabled = false;
      if (err.error === 'INVALID_RESET_CODE' || err.error === 'INVALID_REQUEST') {
        setMessage(code, err.message, 'error');
        code.input.focus();
      } else {
        showNotice(err.message, 'error');
      }
    }
  }

  /** 인증 상태에 맞춰 인증번호 칸을 잠그고 새 비밀번호 칸을 연다 */
  function setVerified(on) {
    verified = on;
    code.input.readOnly = on;
    verifyButton.disabled = on;
    verifyButton.textContent = on ? '확인 완료' : '인증번호 확인';
    passwordStep.hidden = !on;
    if (!on) {
      next.input.value = '';
      confirm.input.value = '';
      clearMessage(next);
      clearMessage(confirm);
    }
  }

  /* ---------- 남은 시간 ---------- */

  function startTimer(seconds) {
    stopTimer();
    expiresAt = Date.now() + seconds * 1000;
    tick();
    timer = setInterval(tick, 1000);
  }

  function stopTimer() {
    if (timer) clearInterval(timer);
    timer = null;
    timerEl.textContent = '';
  }

  function tick() {
    const left = Math.max(0, Math.round((expiresAt - Date.now()) / 1000));
    if (left === 0) {
      stopTimer();
      setVerified(false);
      timerEl.textContent = '인증 시간이 지났어요. [인증번호 다시 받기]를 눌러 주세요.';
      timerEl.className = 'field__message field__message--error reset-timer';
      return;
    }
    const m = Math.floor(left / 60);
    const s = String(left % 60).padStart(2, '0');
    timerEl.textContent = '남은 시간 ' + m + ':' + s;
    timerEl.className = 'field__message reset-timer';
  }

  /* ---------- 3) 새 비밀번호 ---------- */

  next.input.addEventListener('input', () => clearMessage(next));
  confirm.input.addEventListener('input', () => clearMessage(confirm));

  async function changePassword() {
    hideNotice();
    [next, confirm].forEach(clearMessage);

    let firstError = null;
    const ruleError = passwordField.ruleError(next.input.value, sentTo.loginId);
    if (ruleError) {
      setMessage(next, ruleError, 'error');
      firstError = next;
    }
    if (confirm.input.value !== next.input.value || !confirm.input.value) {
      setMessage(confirm, confirm.input.value ? '비밀번호가 일치하지 않아요.' : '새 비밀번호를 한 번 더 입력해 주세요.', 'error');
      firstError = firstError || confirm;
    }
    if (firstError) {
      firstError.input.focus();
      return;
    }

    confirmSubmit.disabled = true;
    try {
      await authApi.confirmPasswordReset({
        loginId: sentTo.loginId,
        code: code.input.value,
        newPassword: next.input.value,
        newPasswordConfirm: confirm.input.value,
      });
      stopTimer();
      try {
        sessionStorage.setItem(RESET_DONE_KEY, sentTo.loginId);   // 로그인 화면에서 완료 안내
      } catch (e) {
        // 저장소를 못 써도 변경은 끝났다
      }
      location.href = '/login.html';
    } catch (err) {
      confirmSubmit.disabled = false;
      if (err.error === 'INVALID_RESET_CODE') {
        // 시간이 지났거나 번호가 사라짐 → 인증번호부터 다시
        setVerified(false);
        setMessage(code, err.message, 'error');
        code.input.focus();
      } else if (err.error === 'PASSWORD_MISMATCH') {
        setMessage(confirm, err.message, 'error');
        confirm.input.focus();
      } else if (err.error === 'INVALID_REQUEST') {
        setMessage(next, err.message, 'error');
        next.input.focus();
      } else {
        showNotice(err.message, 'error');
      }
    }
  }

  /* ---------- 공통 ---------- */

  function field(id) {
    return { input: document.getElementById(id), message: document.getElementById(id + '-message') };
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

  function showNotice(text, type) {
    notice.textContent = text;
    notice.className = 'auth-notice auth-notice--' + type;
    notice.hidden = false;
  }

  function hideNotice() {
    notice.hidden = true;
    notice.textContent = '';
  }
})();
