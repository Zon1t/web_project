/*
 * account.js — 계정 설정 API 호출 (C, 2026-11-24)
 *
 *   await accountApi.changePassword({ currentPassword, newPassword, newPasswordConfirm });
 *   const { nickname } = await accountApi.changeNickname('새닉네임');
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
  };
})(window);
