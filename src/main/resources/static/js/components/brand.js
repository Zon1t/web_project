/*
 * brand.js — 슥밥 로고 심볼 (B, 2026-11-20 확정: 시안 A '밥그릇 + 숟가락')
 *
 * 헤더·푸터·지도 핀이 같은 로고를 쓰도록 SVG 문자열을 한곳에 둔다.
 * 색은 currentColor라서 CSS의 color로 바꾼다.
 *   el.innerHTML = brand.logoSvg('app-logo__mark');
 *
 * 파비콘은 /images/favicon.svg (같은 모양, 파랑 바탕 + 흰 심볼)
 */
(function (global) {
  'use strict';

  const LOGO_PATHS =
    '<path d="M12 23 Q15 14.5 24 14.5 Q33 14.5 36 23 Z" fill="currentColor" opacity=".45"/>' +
    '<path d="M7 23 H41 A17 16 0 0 1 7 23 Z" fill="currentColor"/>' +
    '<rect x="18" y="39.5" width="12" height="3.5" rx="1.75" fill="currentColor"/>' +
    // 숟가락: 머리가 밥 쪽으로 오게 위아래 뒤집음 (2026-11-26, 밥을 뜨는 모양)
    '<g transform="rotate(180 36 13.5) rotate(38 36 12)">' +
    '<ellipse cx="36" cy="8.5" rx="4" ry="5.6" fill="currentColor"/>' +
    '<rect x="34.6" y="12" width="2.8" height="11" rx="1.4" fill="currentColor"/>' +
    '</g>';

  // className: SVG에 붙일 클래스 (크기·색은 CSS에서)
  function logoSvg(className) {
    return '<svg class="' + (className || '') + '" viewBox="0 0 48 48" aria-hidden="true" focusable="false">' +
      LOGO_PATHS + '</svg>';
  }

  // 헤더·푸터 공통 워드마크: [심볼] 슥밥 ('밥'만 파랑)
  function wordmarkHtml() {
    return logoSvg('brand-logo__mark') +
      '<span class="brand-logo__text">슥<span class="brand-logo__accent">밥</span></span>';
  }

  global.brand = { LOGO_PATHS, logoSvg, wordmarkHtml };
})(window);
