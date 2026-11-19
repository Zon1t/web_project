/*
 * utils.js — 여러 화면에서 같이 쓰는 작은 도우미 함수 (B)
 *
 * HTML에서 client.js 다음, 화면 JS보다 먼저 불러온다.
 *   <script src="/js/utils.js"></script>
 */
(function (global) {
  'use strict';

  const HTML_ESCAPES = {
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;',
  };

  global.utils = {
    /**
     * innerHTML에 넣기 전에 특수문자를 바꾼다.
     * 식당 이름·리뷰처럼 사용자가 입력한 글자는 반드시 이 함수를 거쳐서 넣는다.
     * (이름에 <script>가 들어 있어도 글자로만 보이게 하기 위함)
     */
    escapeHtml(value) {
      if (value === null || value === undefined) return '';
      return String(value).replace(/[&<>"']/g, (ch) => HTML_ESCAPES[ch]);
    },

    /** 따봉 아이콘 (이모지 대신 SVG) */
    thumbsUpIcon(size) {
      const px = size || 16;
      return (
        '<svg width="' + px + '" height="' + px + '" viewBox="0 0 24 24" fill="none" ' +
        'stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
        '<path d="M7 10v11H3V10h4zm0 0l4-8a3 3 0 0 1 3 3v4h5.5a2 2 0 0 1 2 2.3l-1.3 8A2 2 0 0 1 18.2 21H7"></path>' +
        '</svg>'
      );
    },
  };
})(window);
