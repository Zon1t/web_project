/*
 * password-reset.js — 비밀번호 찾기 화면 (C, 2026-11-24)
 *
 * 1단계: 아이디 + 회사 이메일 → [인증번호 받기] (POST /api/auth/password-reset/request)
 *        서버는 아이디가 없어도 같은 응답을 준다 → 화면도 항상 "보냈어요"로 안내한다
 * 2단계: 인증번호 6자리 + 새 비밀번호 → [비밀번호 바꾸기] (POST /api/auth/password-reset/confirm)
 *        성공하면 로그인 화면으로 (완료 안내 + 아이디 채움)
 * - 남은 시간(5분)을 보여 주고, 끝나면 [다시 받기] 안내
 * - 실제 메일은 보내지 않는다. 인증번호는 서버 콘솔에 찍힌다 (LogMailSender)
 * - 2026-11-24: 가입·계정 설정에서 등록한 회사 이메일과 같아야 메일이 간다 (화면 응답은 항상 같음)
 * - [아이디 찾기] 탭: 회사 이메일 → 등록된 이메일이면 아이디를 메일로 (POST /api/auth/find-id)
 */
(function () {
  'use strict';

  const RESET_DONE_KEY = 'sseukbap.passwordResetLoginId';   // login.js와 같은 값
  const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  const CODE_PATTERN = /^\d{6}$/;

  const desc = document.getElementById('reset-desc');
  const tabs = Array.from(document.querySelectorAll('.auth-tab'));
  const findIdForm = document.getElementById('find-id-form');
  const findIdSubmit = document.getElementById('find-id-submit');
  const findEmail = field('find-email');
  const DESC = {
    password: '아이디와 가입할 때 등록한 회사 이메일을 입력하면 인증번호를 보내 드려요.',
    id: '등록한 회사 이메일을 입력하면 아이디를 메일로 보내 드려요.',
  };
  const notice = document.getElementById('reset-notice');

  const requestForm = document.getElementById('request-form');
  const requestSubmit = document.getElementById('request-submit');
  const loginId = field('reset-id');
  const email = field('reset-email');

  const confirmForm = document.getElementById('confirm-form');
  const confirmSubmit = document.getElementById('confirm-submit');
  const resendButton = document.getElementById('resend');
  const changeEmailButton = document.getElementById('change-email');
  const timerEl = document.getElementById('reset-timer');
  const code = field('reset-code');
  const next = field('reset-new-password');
  const confirm = field('reset-new-password-confirm');

  let sentTo = null;      // { loginId, email }
  let timer = null;
  let expiresAt = 0;

  loginId.input.focus();

  /* ---------- 탭: 비밀번호 찾기 | 아이디 찾기 ---------- */

  tabs.forEach((tab) => tab.addEventListener('click', () => selectTab(tab.dataset.tab)));

  function selectTab(name) {
    tabs.forEach((tab) => {
      const on = tab.dataset.tab === name;
      tab.classList.toggle('is-selected', on);
      tab.setAttribute('aria-selected', String(on));
    });
    hideNotice();
    stopTimer();
    findIdForm.hidden = name !== 'id';
    requestForm.hidden = name !== 'password';
    confirmForm.hidden = true;
    desc.textContent = DESC[name];
    (name === 'id' ? findEmail : loginId).input.focus();
  }

  findEmail.input.addEventListener('input', () => clearMessage(findEmail));

  findIdForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    hideNotice();
    const mail = findEmail.input.value.trim();
    if (!EMAIL_PATTERN.test(mail)) {
      setMessage(findEmail, mail ? '이메일 형식을 확인해 주세요.' : '이메일을 입력해 주세요.', 'error');
      findEmail.input.focus();
      return;
    }
    findIdSubmit.disabled = true;
    try {
      const result = await authApi.findId(mail);
      showNotice(result.email + '으로 아이디를 보냈어요. 메일이 오지 않으면 등록한 이메일이 맞는지 확인해 주세요.', 'info');
    } catch (err) {
      showNotice(err.message, 'error');
    } finally {
      findIdSubmit.disabled = false;
    }
  });

  /* ---------- 1단계: 인증번호 받기 ---------- */

  loginId.input.addEventListener('input', () => {
    const lower = loginId.input.value.toLowerCase();   // 아이디는 소문자로 (서버도 대소문자 무시)
    if (lower !== loginId.input.value) {
      const caret = loginId.input.selectionStart;
      loginId.input.value = lower;
      loginId.input.setSelectionRange(caret, caret);
    }
    clearMessage(loginId);
  });
  email.input.addEventListener('input', () => clearMessage(email));

  requestForm.addEventListener('submit', async (event) => {
    event.preventDefault();
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

    await sendCode(id, mail, requestSubmit);
  });

  async function sendCode(id, mail, button) {
    button.disabled = true;
    try {
      const result = await authApi.requestPasswordReset(id, mail);
      sentTo = { loginId: id, email: result.email };
      showStep2(result.expiresInSeconds);
    } catch (err) {
      showNotice(err.message, 'error');
    } finally {
      button.disabled = false;
    }
  }

  function showStep2(seconds) {
    requestForm.hidden = true;
    confirmForm.hidden = false;
    desc.textContent = '메일로 받은 인증번호와 새 비밀번호를 입력해 주세요.';
    showNotice(sentTo.email + '으로 인증번호를 보냈어요. 메일이 오지 않으면 아이디와 등록한 이메일이 맞는지 확인해 주세요.', 'info');
    code.input.value = '';
    clearMessage(code);
    startTimer(seconds);
    code.input.focus();
  }

  resendButton.addEventListener('click', () => {
    if (!sentTo) return;
    hideNotice();
    sendCode(sentTo.loginId, sentTo.email, resendButton);
  });

  changeEmailButton.addEventListener('click', () => {
    stopTimer();
    confirmForm.hidden = true;
    requestForm.hidden = false;
    desc.textContent = DESC.password;
    hideNotice();
    loginId.input.focus();
  });

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
  }

  function tick() {
    const left = Math.max(0, Math.round((expiresAt - Date.now()) / 1000));
    if (left === 0) {
      stopTimer();
      timerEl.textContent = '인증 시간이 지났어요. [다시 받기]를 눌러 주세요.';
      timerEl.className = 'field__message field__message--error reset-timer';
      return;
    }
    const m = Math.floor(left / 60);
    const s = String(left % 60).padStart(2, '0');
    timerEl.textContent = '남은 시간 ' + m + ':' + s;
    timerEl.className = 'field__message reset-timer';
  }

  /* ---------- 2단계: 새 비밀번호 ---------- */

  code.input.addEventListener('input', () => {
    code.input.value = code.input.value.replace(/\D/g, '').slice(0, 6);   // 숫자만
    clearMessage(code);
  });
  next.input.addEventListener('input', () => clearMessage(next));
  confirm.input.addEventListener('input', () => clearMessage(confirm));

  confirmForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    hideNotice();
    [code, next, confirm].forEach(clearMessage);

    let firstError = null;
    if (!CODE_PATTERN.test(code.input.value)) {
      setMessage(code, '인증번호 6자리를 입력해 주세요.', 'error');
      firstError = firstError || code;
    }
    const ruleError = passwordField.ruleError(next.input.value, sentTo && sentTo.loginId);
    if (ruleError) {
      setMessage(next, ruleError, 'error');
      firstError = firstError || next;
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
      const target = err.error === 'INVALID_RESET_CODE' ? code
        : err.error === 'PASSWORD_MISMATCH' ? confirm
          : err.error === 'INVALID_REQUEST' ? next
            : null;
      if (target) {
        setMessage(target, err.message, 'error');
        target.input.focus();
      } else {
        showNotice(err.message, 'error');
      }
      confirmSubmit.disabled = false;
    }
  });

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
