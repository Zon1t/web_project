/*
 * account.js — 계정 설정 API 호출 (C, 2026-11-24)
 *
 *   await accountApi.changePassword({ currentPassword, newPassword, newPasswordConfirm });
 *   const { nickname } = await accountApi.changeNickname('새닉네임');
 *   const { email } = await accountApi.changeEmail('me@samsung.com');
 *   await accountApi.withdraw('비밀번호');   // 성공하면 세션이 끊긴다
 */
(function (global) {
  'use strict';

  global.accountApi = {
    /** 계정 설정 화면 주소 */
    PAGE_URL: '/account.html',

    /** 비밀번호 변경 — PATCH /api/users/me/password → null */
    changePassword(body) {
      return api.patch('/users/me/password', body);
    },

    /** 닉네임 변경 — PATCH /api/users/me/nickname → { nickname(앞뒤 공백 제거) } */
    changeNickname(nickname) {
      return api.patch('/users/me/nickname', { nickname });
    },

    /** 회사 이메일 등록·변경 — PATCH /api/users/me/email → { email(소문자) } (비밀번호·아이디 찾기용) */
    changeEmail(email) {
      return api.patch('/users/me/email', { email });
    },

    /** 회원 탈퇴 — POST /api/users/me/withdraw → null. 성공하면 서버가 세션을 끊는다 */
    withdraw(password) {
      return api.post('/users/me/withdraw', { password });
    },
  };
})(window);
