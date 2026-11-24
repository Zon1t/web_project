/*
 * auth.js — 회원·인증 API 호출 (C)
 *
 * 화면 JS에서는 이렇게 쓴다.
 *   const result = await authApi.checkId('chef01');      // { loginId, available }
 *   const member = await authApi.login('chef01', 'pw');   // { memberId, loginId, nickname }
 *   const me = await authApi.me();                        // 로그인 안 했으면 401 → client.js가 로그인 화면으로 이동
 *
 * 기준: docs/API_SPEC.md 1장 auth
 */
(function (global) {
  'use strict';

  global.authApi = {
    /** 아이디 중복 확인 — GET /api/auth/check-id → { loginId, available } */
    checkId(loginId) {
      return api.get('/auth/check-id', { loginId });
    },

    /** 닉네임 중복 확인 — GET /api/auth/check-nickname → { nickname(앞뒤 공백 제거), available } */
    checkNickname(nickname) {
      return api.get('/auth/check-nickname', { nickname });
    },

    /**
     * 회원가입 — POST /api/auth/signup → { memberId, loginId, nickname }
     * @param {{loginId: string, password: string, passwordConfirm: string, nickname: string}} body
     */
    signup(body) {
      return api.post('/auth/signup', body);
    },

    /** 로그인 — POST /api/auth/login → { memberId, loginId, nickname } (세션 쿠키 발급) */
    login(loginId, password) {
      return api.post('/auth/login', { loginId, password });
    },

    /** 로그아웃 — POST /api/auth/logout → null */
    logout() {
      return api.post('/auth/logout');
    },

    /** 로그인 사용자 조회 — GET /api/auth/me → { memberId, loginId, nickname } */
    me() {
      return api.get('/auth/me');
    },

    /**
     * 비밀번호 찾기 1) 인증번호 요청 — POST /api/auth/password-reset/request → { email, expiresInSeconds }
     * 아이디가 없어도 같은 응답 (2026-11-24). 지금은 메일 대신 서버 로그에 번호가 찍힌다
     */
    requestPasswordReset(loginId, email) {
      return api.post('/auth/password-reset/request', { loginId, email });
    },

    /** 비밀번호 찾기 2) 인증번호 확인 — POST /api/auth/password-reset/verify → null (번호는 아직 쓰지 않음) */
    verifyPasswordReset(loginId, code) {
      return api.post('/auth/password-reset/verify', { loginId, code });
    },

    /** 비밀번호 찾기 3) 인증번호 + 새 비밀번호 — POST /api/auth/password-reset/confirm → null */
    confirmPasswordReset(body) {
      return api.post('/auth/password-reset/confirm', body);
    },
  };
})(window);
