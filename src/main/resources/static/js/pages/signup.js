/*
 * signup.js — 회원가입 화면 (S-02, C)
 *
 * 규칙 (docs/API_SPEC.md 1장 auth)
 * - 아이디: 영문·숫자 4~20자, [중복 확인] 필수
 * - 비밀번호: 8~20자, 영문·숫자 모두 포함, 공백 불가, 아이디와 다르게 (2026-11-24, password-field.js의 ruleError)
 *            비밀번호 확인과 같아야 함
 * - 아이디는 입력하는 대로 소문자로 바꿔 보여 준다 (서버도 소문자로 저장, 2026-11-24)
 * - 회사 이메일(선택): 넣으면 형식 확인 후 함께 보낸다. 비밀번호·아이디 찾기에 쓴다 (2026-11-24)
 * - 닉네임: 앞뒤 공백 제거 후 2~10자, [중복 확인] 필수
 * - 중복 확인 후 값을 고치면 다시 확인해야 한다. 서버도 가입 시 다시 검사한다.
 * - 가입 후 자동 로그인하지 않고 로그인 화면으로 이동한다.
 */
(function () {
  'use strict';

  const LOGIN_ID_PATTERN = /^[A-Za-z0-9]{4,20}$/;
  const NICKNAME_MIN = 2;
  const NICKNAME_MAX = 10;
  const SIGNUP_DONE_KEY = 'sseukbap.signupLoginId';   // login.js와 같은 값

  const MSG = {
    idFormat: '아이디는 영문·숫자 4~20자로 입력해 주세요.',
    idAvailable: '사용할 수 있는 아이디입니다.',
    idTaken: '이미 사용 중인 아이디입니다.',
    idNeedCheck: '아이디 중복 확인을 해 주세요.',
    passwordOk: '사용할 수 있는 비밀번호예요.',
    passwordConfirmEmpty: '비밀번호를 한 번 더 입력해 주세요.',
    passwordMismatch: '비밀번호가 일치하지 않아요.',
    passwordMatch: '비밀번호가 일치합니다.',
    nicknameFormat: '닉네임은 2~10자로 입력해 주세요.',
    nicknameAvailable: '사용할 수 있는 닉네임입니다.',
    nicknameTaken: '이미 사용 중인 닉네임입니다.',
    nicknameNeedCheck: '닉네임 중복 확인을 해 주세요.',
  };

  const form = document.getElementById('signup-form');
  const notice = document.getElementById('signup-notice');
  const submitButton = document.getElementById('signup-submit');

  const id = field('signup-id');
  const password = field('signup-password');
  const passwordConfirm = field('signup-password-confirm');
  const nickname = field('signup-nickname');
  const email = field('signup-email');
  const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  const EMAIL_HINT = email.message.textContent;

  email.input.addEventListener('input', () => {
    email.message.textContent = EMAIL_HINT;
    email.message.className = 'field__message';
    email.input.classList.remove('input--error');
  });

  /** 비어 있으면 통과(선택 항목), 있으면 형식 확인 */
  function validateEmail() {
    const value = email.input.value.trim();
    if (!value || EMAIL_PATTERN.test(value)) return true;
    setMessage(email, '이메일 형식을 확인해 주세요.', 'error');
    return false;
  }
  const idCheckButton = document.getElementById('signup-id-check');
  const nicknameCheckButton = document.getElementById('signup-nickname-check');

  // 중복 확인을 통과한 값. 입력값이 이 값과 다르면 다시 확인해야 한다.
  let checkedLoginId = null;
  let checkedNickname = null;

  id.input.focus();

  /* ---------- 아이디 ---------- */

  id.input.addEventListener('input', () => {
    // 대문자를 치면 바로 소문자로 (글자 수가 같아서 커서 위치는 그대로 둔다)
    const lower = id.input.value.toLowerCase();
    if (lower !== id.input.value) {
      const caret = id.input.selectionStart;
      id.input.value = lower;
      id.input.setSelectionRange(caret, caret);
    }
    if (password.input.value) validatePassword(false);   // '아이디와 같으면 안 됨' 다시 확인
    if (id.input.value !== checkedLoginId) {
      checkedLoginId = null;
      clearMessage(id);
    }
  });

  idCheckButton.addEventListener('click', checkLoginId);

  async function checkLoginId() {
    const value = id.input.value;
    if (!LOGIN_ID_PATTERN.test(value)) {
      setMessage(id, MSG.idFormat, 'error');
      id.input.focus();
      return false;
    }

    idCheckButton.disabled = true;
    try {
      const result = await authApi.checkId(value);
      if (result.available) {
        checkedLoginId = value;
        setMessage(id, MSG.idAvailable, 'success');
        return true;
      }
      setMessage(id, MSG.idTaken, 'error');
      return false;
    } catch (err) {
      setMessage(id, err.message, 'error');
      return false;
    } finally {
      idCheckButton.disabled = false;
    }
  }

  /* ---------- 비밀번호 ---------- */

  password.input.addEventListener('input', () => {
    validatePassword(false);
    if (passwordConfirm.input.value) validatePasswordConfirm(false);
  });

  passwordConfirm.input.addEventListener('input', () => validatePasswordConfirm(false));

  /** @param {boolean} strict 가입하기를 눌렀을 때만 true (빈 칸도 오류로 표시) */
  function validatePassword(strict) {
    const value = password.input.value;
    if (value.length === 0 && !strict) {
      clearMessage(password);
      return false;
    }
    const error = passwordField.ruleError(value, id.input.value);
    if (error) {
      setMessage(password, error, 'error');
      return false;
    }
    setMessage(password, MSG.passwordOk, 'success');
    return true;
  }

  function validatePasswordConfirm(strict) {
    const value = passwordConfirm.input.value;
    if (!value) {
      if (strict) {
        setMessage(passwordConfirm, MSG.passwordConfirmEmpty, 'error');
      } else {
        clearMessage(passwordConfirm);
      }
      return false;
    }
    if (value !== password.input.value) {
      setMessage(passwordConfirm, MSG.passwordMismatch, 'error');
      return false;
    }
    setMessage(passwordConfirm, MSG.passwordMatch, 'success');
    return true;
  }

  /* ---------- 닉네임 ---------- */

  nickname.input.addEventListener('input', () => {
    if (nickname.input.value.trim() !== checkedNickname) {
      checkedNickname = null;
      clearMessage(nickname);
    }
  });

  nicknameCheckButton.addEventListener('click', checkNickname);

  async function checkNickname() {
    const value = nickname.input.value.trim();   // 앞뒤 공백 제거 후 비교 (서버와 같은 기준)
    if (value.length < NICKNAME_MIN || value.length > NICKNAME_MAX) {
      setMessage(nickname, MSG.nicknameFormat, 'error');
      nickname.input.focus();
      return false;
    }

    nicknameCheckButton.disabled = true;
    try {
      const result = await authApi.checkNickname(value);
      if (result.available) {
        checkedNickname = result.nickname;
        nickname.input.value = result.nickname;   // 공백 제거된 값으로 맞춤
        setMessage(nickname, MSG.nicknameAvailable, 'success');
        return true;
      }
      setMessage(nickname, MSG.nicknameTaken, 'error');
      return false;
    } catch (err) {
      setMessage(nickname, err.message, 'error');
      return false;
    } finally {
      nicknameCheckButton.disabled = false;
    }
  }

  /* ---------- 가입하기 ---------- */

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    hideNotice();

    const idOk = validateLoginIdChecked();
    const passwordOk = validatePassword(true);
    const confirmOk = validatePasswordConfirm(true);
    const nicknameOk = validateNicknameChecked();
    const emailOk = validateEmail();

    if (!idOk || !passwordOk || !confirmOk || !nicknameOk || !emailOk) {
      focusFirstError();
      return;
    }

    setLoading(true);
    try {
      const member = await authApi.signup({
        loginId: id.input.value,
        password: password.input.value,
        passwordConfirm: passwordConfirm.input.value,
        nickname: nickname.input.value.trim(),
        email: email.input.value.trim() || null,
      });
      try {
        sessionStorage.setItem(SIGNUP_DONE_KEY, member.loginId);   // 로그인 화면에서 가입 완료 안내
      } catch (e) {
        // 저장소를 못 써도 가입은 완료됨
      }
      location.href = '/login.html';
    } catch (err) {
      handleSignupError(err);
      setLoading(false);
    }
  });

  function validateLoginIdChecked() {
    if (!LOGIN_ID_PATTERN.test(id.input.value)) {
      setMessage(id, MSG.idFormat, 'error');
      return false;
    }
    if (checkedLoginId !== id.input.value) {
      setMessage(id, MSG.idNeedCheck, 'error');
      return false;
    }
    return true;
  }

  function validateNicknameChecked() {
    const value = nickname.input.value.trim();
    if (value.length < NICKNAME_MIN || value.length > NICKNAME_MAX) {
      setMessage(nickname, MSG.nicknameFormat, 'error');
      return false;
    }
    if (checkedNickname !== value) {
      setMessage(nickname, MSG.nicknameNeedCheck, 'error');
      return false;
    }
    return true;
  }

  /** 서버 오류 코드별로 해당 입력창 아래에 표시 (그 사이 다른 사람이 같은 값으로 가입한 경우 등) */
  function handleSignupError(err) {
    switch (err.error) {
      case 'LOGIN_ID_ALREADY_EXISTS':
        checkedLoginId = null;
        setMessage(id, err.message, 'error');
        id.input.focus();
        break;
      case 'NICKNAME_ALREADY_EXISTS':
        checkedNickname = null;
        setMessage(nickname, err.message, 'error');
        nickname.input.focus();
        break;
      case 'EMAIL_ALREADY_EXISTS':
        setMessage(email, err.message, 'error');
        email.input.focus();
        break;
      case 'PASSWORD_MISMATCH':
        setMessage(passwordConfirm, err.message, 'error');
        passwordConfirm.input.focus();
        break;
      default:
        showNotice(err.message);   // 입력값 오류(INVALID_REQUEST), 서버 오류 등
    }
  }

  /* ---------- 공통 ---------- */

  function field(inputId) {
    return {
      input: document.getElementById(inputId),
      message: document.getElementById(inputId + '-message'),
    };
  }

  function setMessage(target, text, type) {
    target.message.textContent = text;
    target.message.className = 'field__message field__message--' + type;
    target.input.classList.toggle('input--error', type === 'error');
    target.input.setAttribute('aria-invalid', String(type === 'error'));
  }

  function clearMessage(target) {
    target.message.textContent = '';
    target.message.className = 'field__message';
    target.input.classList.remove('input--error');
    target.input.removeAttribute('aria-invalid');
  }

  function focusFirstError() {
    const first = form.querySelector('.input--error');
    if (first) first.focus();
  }

  function showNotice(message) {
    notice.textContent = message;
    notice.hidden = false;
  }

  function hideNotice() {
    notice.hidden = true;
    notice.textContent = '';
  }

  function setLoading(loading) {
    submitButton.disabled = loading;
    submitButton.textContent = loading ? '가입하는 중…' : '가입하기';
  }
})();
