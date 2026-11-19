/*
 * app-header.js — 공통 헤더 오른쪽: 닉네임 표시·로그아웃 (C)
 *
 * 로그인이 필요한 화면에서 쓴다. 헤더 HTML 안에 아래 두 요소를 두고 이 파일을 불러오면 된다.
 *   <div class="app-header__user">
 *     <a id="header-nickname" class="app-header__nickname" href="/mypage.html" hidden></a>
 *     <button id="header-logout" class="btn" type="button">로그아웃</button>
 *   </div>
 *
 *   <script src="/js/api/client.js"></script>
 *   <script src="/js/api/auth.js"></script>
 *   <script src="/js/components/app-header.js"></script>
 *
 * - /api/auth/me로 닉네임을 표시한다. 로그인하지 않았으면 client.js가 로그인 화면으로 보낸다.
 * - 닉네임을 누르면 마이페이지(S-10), [로그아웃]을 누르면 로그인 화면으로 이동한다.
 */
(function () {
  'use strict';

  const nicknameLink = document.getElementById('header-nickname');
  const logoutButton = document.getElementById('header-logout');

  if (nicknameLink) {
    showNickname();
  }
  if (logoutButton) {
    logoutButton.addEventListener('click', logout);
  }

  async function showNickname() {
    try {
      const me = await authApi.me();   // 401이면 client.js가 로그인 화면으로 이동
      nicknameLink.textContent = me.nickname + ' 님';
      nicknameLink.hidden = false;
    } catch (err) {
      // 401은 이동 처리됨. 그 외 오류는 닉네임만 안 보이게 둔다.
    }
  }

  async function logout() {
    logoutButton.disabled = true;
    try {
      await authApi.logout();
    } catch (err) {
      // 이미 세션이 끝났어도 로그인 화면으로 보낸다
    }
    location.href = '/login.html';
  }
})();
