/*
 * app-footer.js — 공통 푸터 (B, 2026-11-20)
 *
 * ▼ HTML: </main> 바로 다음에 자리만 둔다
 *   <footer id="app-footer" class="app-footer"></footer>
 *
 * ▼ CSS·JS
 *   <link rel="stylesheet" href="/css/footer.css">
 *   <script src="/js/components/brand.js"></script>
 *   <script src="/js/components/app-footer.js"></script>
 */
(function (global) {
  'use strict';

  const footer = document.getElementById('app-footer');
  if (!footer) return;

  footer.innerHTML =
    '<div class="app-footer__inner">' +
    '<div class="app-footer__brand">' +
    '<span class="brand-logo brand-logo--sm">' + global.brand.wordmarkHtml() + '</span>' +
    '<p class="app-footer__desc">식사 메뉴 추천과 식사팟 모집 서비스</p>' +
    '</div>' +
    '<dl class="app-footer__info">' +
    '<div><dt>식당 정보</dt><dd>네이버 지도</dd></div>' +
    '<div><dt>구내식당 메뉴</dt><dd>세종대 계절밥상</dd></div>' +
    '</dl>' +
    '<p class="app-footer__copy">SCSA 27기 팀 프로젝트 · © 2026 슥밥</p>' +
    '</div>';
})(window);
