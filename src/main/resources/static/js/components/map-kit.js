/*
 * map-kit.js — 네이버 지도 공통 함수 (B, 2026-11-23)
 *
 * 지도를 쓰는 화면이면 어디서나 쓸 수 있게 모았다. (지도 화면 S-04, 식당 상세 S-05, 식사팟 상세 S-12)
 *
 * ▼ 불러오는 순서
 *   <link rel="stylesheet" href="/css/map-kit.css">
 *   <script src="/js/config.js"></script>              ← 지도 키 (Git 제외, 견본은 config.example.js)
 *   <script src="/js/utils.js"></script>
 *   <script src="/js/components/ui.js"></script>        ← 핀 안의 음식 그림 (ui.avatarGlyph)
 *   <script src="/js/components/map-kit.js"></script>
 *
 * ▼ 쓰는 법
 *   mapKit.load()                         네이버 지도 스크립트 불러오기 → Promise (여러 번 불러도 한 번만 받는다)
 *                                         키가 없으면 err.code === 'NO_KEY'로 실패한다
 *   mapKit.pinIcon(restaurant, { active, tipHtml })   네이버 Marker의 icon 값
 *                                         restaurant: { category, cafeteria } 만 있으면 된다
 *   mapKit.pinCategory(restaurant)        핀 종류 ('CAFETERIA' | 'KOREAN' | ...)
 *   mapKit.mini(element, place)           식당 1곳 작은 지도 (place: {lat, lng, category, cafeteria, name, mapUrl})
 *                                         좌표가 없으면 false → 화면에서 지도 칸을 숨긴다
 *   mapKit.DEFAULT_CENTER                 { lat, lng } 세종대 (사옥 좌표가 없을 때 가운데)
 *
 * ▼ 핀 모양
 *   물방울 몸통 = 카테고리 색, 가운데 흰 원 = 목록 아바타와 같은 음식 그림 (색은 css/map-kit.css)
 *   구내식당은 남색에 조금 더 큰 핀 (사옥 위치를 바로 찾게)
 */
(function (global) {
  'use strict';

  const MAP_SCRIPT_URL = 'https://oapi.map.naver.com/openapi/v3/maps.js?ncpKeyId=';
  const DEFAULT_CENTER = Object.freeze({ lat: 37.5509, lng: 127.0738 }); // 세종대

  // 핀 상자 크기 (강조 때 커지는 건 CSS transform이라 상자 크기는 그대로, 핀 끝 = 좌표)
  const PIN_SIZE = { width: 32, height: 42 };
  const CAFETERIA_PIN_SIZE = { width: 38, height: 50 };

  let loading = null; // 스크립트를 한 번만 받도록 Promise를 기억해 둔다

  /* ---------- 지도 불러오기 ---------- */

  function load() {
    if (global.naver && global.naver.maps) return Promise.resolve();
    if (loading) return loading;

    const keyId = global.APP_CONFIG && global.APP_CONFIG.naverMapKeyId;
    if (!keyId) {
      const err = new Error('NO_KEY');
      err.code = 'NO_KEY';
      return Promise.reject(err);
    }

    loading = new Promise((resolve, reject) => {
      // 네이버가 키 인증에 실패하면 이 이름의 함수를 불러 준다
      global.navermap_authFailure = () => reject(new Error('NAVER_MAP_AUTH_FAILURE'));

      const script = document.createElement('script');
      script.src = MAP_SCRIPT_URL + encodeURIComponent(keyId);
      script.async = true;
      script.onload = () => (global.naver && global.naver.maps ? resolve() : reject(new Error('NO_NAVER_MAPS')));
      script.onerror = () => reject(new Error('SCRIPT_LOAD_FAILED'));
      document.head.appendChild(script);
    }).catch((err) => {
      loading = null; // 실패하면 다음에 다시 시도할 수 있게
      throw err;
    });
    return loading;
  }

  /* ---------- 핀 ---------- */

  function pinCategory(restaurant) {
    return restaurant.cafeteria ? 'CAFETERIA' : (restaurant.category || 'ETC');
  }

  /**
   * 네이버 Marker의 icon 값
   * @param {{category: string, cafeteria?: boolean}} restaurant
   * @param {{active?: boolean, tipHtml?: string}} [options] active: 강조(통통 튐), tipHtml: 핀 위 툴팁 HTML
   */
  function pinIcon(restaurant, options) {
    const opts = options || {};
    const category = pinCategory(restaurant);
    const size = category === 'CAFETERIA' ? CAFETERIA_PIN_SIZE : PIN_SIZE;
    const classes = ['map-pin', 'map-pin--' + category.toLowerCase()];
    if (opts.active) classes.push('is-active');

    return {
      content:
        '<div class="' + classes.join(' ') + '" style="width:' + size.width + 'px;height:' + size.height + 'px">' +
        (opts.tipHtml || '') +
        '<svg class="map-pin__shape" viewBox="0 0 40 52" aria-hidden="true" focusable="false">' +
        '<path class="map-pin__body" d="M20 51 C20 51 3.5 32.5 3.5 19.5 A16.5 16.5 0 1 1 36.5 19.5 C36.5 32.5 20 51 20 51 Z"/>' +
        '<circle cx="20" cy="19.5" r="12.5" fill="#fff"/>' +
        '<svg class="map-pin__glyph" x="9.5" y="9" width="21" height="21" viewBox="0 0 40 40">' + global.ui.avatarGlyph(category) + '</svg>' +
        '</svg>' +
        '</div>',
      size: new global.naver.maps.Size(size.width, size.height),
      anchor: new global.naver.maps.Point(size.width / 2, size.height), // 핀 끝이 좌표를 가리키게
    };
  }

  /* ---------- 작은 지도 (식당 1곳) ---------- */

  /**
   * 식당 1곳을 핀으로 보여 주는 작은 지도. 식당 상세(S-05)·식사팟 상세(S-12)에서 쓴다.
   * 좌표가 없으면 아무것도 그리지 않고 false를 돌려준다. (화면에서 지도 칸을 숨기면 된다)
   * 지도를 못 불러오면 지도 칸에 짧은 안내를 보여 주고, [네이버 지도에서 보기]는 그대로 남긴다.
   *
   * @param {HTMLElement} element 지도를 넣을 빈 요소
   * @param {{lat: number, lng: number, category: string, cafeteria?: boolean, name?: string, mapUrl?: string}} place
   * @returns {boolean} 지도를 그렸으면 true
   *
   * 예) const el = document.createElement('div');
   *     if (mapKit.mini(el, detail)) parent.appendChild(el);
   */
  function mini(element, place) {
    if (!place || typeof place.lat !== 'number' || typeof place.lng !== 'number') return false;

    element.classList.add('mini-map');
    element.innerHTML =
      '<div class="mini-map__canvas" role="img" aria-label="' + escapeAttr((place.name || '식당') + ' 위치 지도') + '">' +
      '<p class="mini-map__state">지도를 불러오는 중…</p>' +
      '</div>' +
      mapLinkHtml(place.mapUrl);

    const canvas = element.querySelector('.mini-map__canvas');
    load()
      .then(() => {
        const maps = global.naver.maps;
        const position = new maps.LatLng(place.lat, place.lng);
        canvas.innerHTML = '';
        const map = new maps.Map(canvas, {
          center: position,
          zoom: 17,
          scrollWheel: false,        // 화면을 스크롤할 때 지도가 확대·축소되지 않게
          zoomControl: true,
          zoomControlOptions: { position: maps.Position.TOP_RIGHT, style: maps.ZoomControlStyle.SMALL },
          scaleControl: false,
          mapDataControl: false,
        });
        new maps.Marker({ map, position, title: place.name || '', icon: pinIcon(place), clickable: false });
      })
      .catch(() => {
        canvas.innerHTML = '<p class="mini-map__state">지도를 불러오지 못했어요</p>';
      });
    return true;
  }

  // 네이버 지도 링크는 새 탭으로. http(s) 주소가 아니면 만들지 않는다.
  function mapLinkHtml(mapUrl) {
    if (!/^https?:\/\//i.test(mapUrl || '')) return '';
    return '<a class="mini-map__link" href="' + escapeAttr(mapUrl) + '" target="_blank" rel="noopener noreferrer">' +
      '네이버 지도에서 보기<span class="sr-only">(새 탭)</span>' +
      '<svg viewBox="0 0 16 16" aria-hidden="true" focusable="false"><path d="M6 3h7v7M13 3L5 11" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>' +
      '</a>';
  }

  function escapeAttr(value) {
    return String(value == null ? '' : value).replace(/[&<>"']/g, (c) =>
      ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
  }

  global.mapKit = { load, pinIcon, pinCategory, mini, DEFAULT_CENTER };
})(window);
