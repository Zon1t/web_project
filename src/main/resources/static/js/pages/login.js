/*
 * login.js — 로그인 화면 (S-03, C)
 *
 * - 실패 시 아이디·비밀번호 중 무엇이 틀렸는지 구분하지 않고 서버 문구 하나만 보여 준다.
 * - 성공 시 주소의 redirect 값(직전 화면)으로 돌아간다. 없으면 메인(/).
 *   client.js가 401일 때 /login.html?redirect=현재주소 로 보내 준다.
 * - 회원가입 직후 오면 가입 완료 안내와 가입한 아이디를 채워 준다.
 */
(function () {
  'use strict';

  const DEFAULT_TARGET = '/';
  const SIGNUP_DONE_KEY = 'sseukbap.signupLoginId';   // signup.js와 같은 값

  const form = document.getElementById('login-form');
  const notice = document.getElementById('login-notice');
  const idInput = document.getElementById('login-id');
  const passwordInput = document.getElementById('login-password');
  const submitButton = document.getElementById('login-submit');

  const target = getSafeRedirect();

  init();

  async function init() {
    showSignupDone();

    // 이미 로그인한 상태면 바로 돌려보낸다. (로그인 화면에서는 401이어도 client.js가 이동시키지 않음)
    try {
      await authApi.me();
      location.replace(target);
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

    const loginId = idInput.value.trim();
    const password = passwordInput.value;
    if (!loginId || !password) {
      showNotice('아이디와 비밀번호를 입력해 주세요.', 'error');
      (loginId ? passwordInput : idInput).focus();
      return;
    }

    setLoading(true);
    try {
      await authApi.login(loginId, password);
      location.replace(target);
    } catch (err) {
      showNotice(err.message, 'error');   // 서버 문구: '아이디 또는 비밀번호가 일치하지 않습니다.'
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

    showNotice('회원가입이 완료되었습니다. 로그인해 주세요.', 'success');
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
