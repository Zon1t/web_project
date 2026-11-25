/*
 * auth.js — 회원·인증 API 호출 (C)
 *
 * 화면 JS에서는 이렇게 쓴다.
 *   const result = await authApi.checkId('chef01');                // { loginId, available }
 *   const result = await authApi.checkEmail('jiho@samsung.com');   // { email, available }
 *   const member = await authApi.login('chef01', 'pw');            // { memberId, loginId, nickname, email, admin, emailRequired }
 *   const me = await authApi.me();                                 // 로그인 안 했으면 401 → client.js가 로그인 화면으로 이동
 *
 * 기준: docs/API_SPEC.md 1장 auth (2026-11-25 T3: 아이디 + 회사 이메일)
 */
(function (global) {
  'use strict';

  global.authApi = {
    /** 아이디 중복 확인 — GET /api/auth/check-id → { loginId(소문자), available } */
    checkId(loginId) {
      return api.get('/auth/check-id', { loginId });
    },

    /** 이메일 중복 확인 — GET /api/auth/check-email → { email(소문자), available }. 회사 도메인이 아니면 400 INVALID_EMAIL_DOMAIN */
    checkEmail(email) {
      return api.get('/auth/check-email', { email });
    },

    /** 닉네임 중복 확인 — GET /api/auth/check-nickname → { nickname(앞뒤 공백 제거), available } */
    checkNickname(nickname) {
      return api.get('/auth/check-nickname', { nickname });
    },

    /**
     * 회원가입 — POST /api/auth/signup → { memberId, email, loginId, nickname }
     * @param {{loginId: string, email: string, password: string, passwordConfirm: string, nickname: string}} body
     */
    signup(body) {
      return api.post('/auth/signup', body);
    },

    /**
     * 로그인 — POST /api/auth/login → { memberId, loginId, nickname, email, admin, emailRequired } (세션 쿠키 발급)
     * loginId: 아이디 또는 회사 이메일. 이메일이 없는 기존 회원은 emailRequired = true
     */
    login(loginId, password) {
      return api.post('/auth/login', { loginId, password });
    },

    /** 로그아웃 — POST /api/auth/logout → null */
    logout() {
      return api.post('/auth/logout');
    },

    /** 로그인 사용자 조회 — GET /api/auth/me → { memberId, loginId, nickname, email, admin, emailRequired } */
    me() {
      return api.get('/auth/me');
    },

    /**
     * 비밀번호 찾기 1) 인증번호 요청 — POST /api/auth/password-reset/request → { email, expiresInSeconds }
     * 가입하지 않은 이메일이어도 같은 응답. 지금은 메일 대신 서버 로그에 번호가 찍힌다
     */
    requestPasswordReset(email) {
      return api.post('/auth/password-reset/request', { email });
    },

    /** 비밀번호 찾기 2) 인증번호 확인 — POST /api/auth/password-reset/verify → null (번호는 아직 쓰지 않음) */
    verifyPasswordReset(email, code) {
      return api.post('/auth/password-reset/verify', { email, code });
    },

    /** 비밀번호 찾기 3) 인증번호 + 새 비밀번호 — POST /api/auth/password-reset/confirm → null */
    confirmPasswordReset(body) {
      return api.post('/auth/password-reset/confirm', body);
    },
  };
})(window);
