/*
 * login.js — 로그인 화면 (S-03, C)
 *
 * - 실패 시 아이디·비밀번호 중 무엇이 틀렸는지 구분하지 않고 서버 문구 하나만 보여 준다.
 * - 성공 시 주소의 redirect 값(직전 화면)으로 돌아간다. 없으면 메인(/).
 *   client.js가 401일 때 /login.html?redirect=현재주소 로 보내 준다.
 * - 회원가입 직후 오면 가입 완료 안내와 가입한 아이디를 채워 준다.
 * - 2026-11-25 T3: 아이디 또는 회사 이메일로 로그인한다 (소문자로 보냄). 이메일이 없는 기존 회원은
 *   서버가 emailRequired = true를 주므로 계정 설정의 이메일 등록으로 보낸다.
 * - 비밀번호 보기·Caps Lock 안내는 password-field.js
 * - '비밀번호를 잊으셨나요?' → password-reset.html. 비밀번호를 바꾸고 돌아오면 완료 안내 + 이메일을 채워 준다
 */
(function () {
  'use strict';

  const DEFAULT_TARGET = '/';
  const SIGNUP_DONE_KEY = 'sseukbap.signupLoginId';   // signup.js와 같은 값
  const RESET_DONE_KEY = 'sseukbap.passwordResetLoginId';   // password-reset.js와 같은 값
  const EMAIL_REQUIRED_URL = '/account.html?emailRequired=1';   // 이메일 등록 안내 (account.js)

  const form = document.getElementById('login-form');
  const notice = document.getElementById('login-notice');
  const idInput = document.getElementById('login-id');
  const passwordInput = document.getElementById('login-password');
  const submitButton = document.getElementById('login-submit');


  const target = getSafeRedirect();

  init();

  async function init() {
    showSignupDone();
    showResetDone();

    // 이미 로그인한 상태면 바로 돌려보낸다. (로그인 화면에서는 401이어도 client.js가 이동시키지 않음)
    try {
      const me = await authApi.me();
      location.replace(me.emailRequired ? EMAIL_REQUIRED_URL : target);
      return;
    } catch (err) {
      // 로그인 안 됨 → 그대로 로그인 화면
    }

    if (!idInput.value) {
      idInput.focus();
    } else {
      passwordInput.focus();
    }
  }

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    hideNotice();

    const account = idInput.value.trim().toLowerCase();   // 아이디 또는 회사 이메일
    const password = passwordInput.value;
    if (!account || !password) {
      showNotice('아이디와 비밀번호를 입력해 주세요.', 'error');
      (account ? passwordInput : idInput).focus();
      return;
    }

    setLoading(true);
    try {
      const member = await authApi.login(account, password);
      // 이메일이 없는 기존 회원: 먼저 회사 이메일을 등록하게 한다
      location.replace(member.emailRequired ? EMAIL_REQUIRED_URL : target);
    } catch (err) {
      showNotice(err.message, 'error');   // 서버 문구는 client.js가 해요체로 바꿔 준다 ('…일치하지 않아요.')
      passwordInput.value = '';
      passwordInput.focus();
      setLoading(false);
    }
  });

  /**
   * redirect 값은 우리 사이트 안의 주소만 허용한다. (/로 시작, //·/\ 로 시작하는 외부 주소 차단)
   * 로그인·회원가입 화면으로 되돌아가는 것도 막는다.
   */
  function getSafeRedirect() {
    const value = new URLSearchParams(location.search).get('redirect');
    if (!value || !value.startsWith('/') || value.startsWith('//') || value.startsWith('/\\')) {
      return DEFAULT_TARGET;
    }
    const path = value.split(/[?#]/)[0];
    if (path === '/login.html' || path === '/signup.html') {
      return DEFAULT_TARGET;
    }
    return value;
  }

  function showSignupDone() {
    let loginId = null;
    try {
      loginId = sessionStorage.getItem(SIGNUP_DONE_KEY);
      sessionStorage.removeItem(SIGNUP_DONE_KEY);
    } catch (e) {
      return;   // 브라우저 저장소를 쓸 수 없는 환경
    }
    if (loginId === null) return;

    showNotice('가입을 마쳤어요. 로그인해 주세요.', 'success');
    idInput.value = loginId;
  }

  /** 비밀번호 찾기에서 바꾸고 돌아왔을 때 */
  function showResetDone() {
    let loginId = null;
    try {
      loginId = sessionStorage.getItem(RESET_DONE_KEY);
      sessionStorage.removeItem(RESET_DONE_KEY);
    } catch (e) {
      return;
    }
    if (loginId === null) return;

    showNotice('비밀번호를 바꿨어요. 새 비밀번호로 로그인해 주세요.', 'success');
    idInput.value = loginId;
  }

  function showNotice(message, type) {
    notice.textContent = message;
    notice.className = 'auth-notice auth-notice--' + type;
    notice.hidden = false;
  }

  function hideNotice() {
    notice.hidden = true;
    notice.textContent = '';
  }

  function setLoading(loading) {
    submitButton.disabled = loading;
    submitButton.textContent = loading ? '로그인 중…' : '로그인';
  }
})();
