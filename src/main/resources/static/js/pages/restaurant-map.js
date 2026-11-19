/*
 * restaurant-map.js — S-04 식당 목록 오른쪽 네이버 지도 (B, F-17)
 *
 * restaurant-list.js에서 이렇게 쓴다.
 *   restaurantMap.init(document.getElementById('restaurant-map'));  // 지도 불러오기 (한 번)
 *   restaurantMap.setRestaurants(list);   // 목록과 같은 데이터로 핀 다시 그리기 (검색·필터 때마다)
 *   restaurantMap.highlight(12);          // 목록 항목에 마우스를 올리면 핀 강조, null이면 해제
 *
 * - 지도 키: js/config.js의 window.APP_CONFIG.naverMapKeyId (Git 제외, 견본은 config.example.js)
 * - 가운데: 사옥(구내식당) 위치. 목록에 구내식당 좌표가 없으면 세종대 좌표
 * - 핀을 누르면 말풍선: 이름, 카테고리 · 따봉 수, [상세 보기]
 * - 키가 없거나 지도를 못 불러오면 지도 자리에 안내만 보여 주고, 목록은 그대로 동작한다.
 */
(function (global) {
  'use strict';

  const MAP_SCRIPT_URL = 'https://oapi.map.naver.com/openapi/v3/maps.js?ncpKeyId=';
  const DEFAULT_CENTER = { lat: 37.5509, lng: 127.0738 }; // 세종대 (사옥 좌표가 없을 때)
  const DEFAULT_ZOOM = 16;
  const PIN_SIZE = 28;

  const esc = utils.escapeHtml;

  let container = null;
  let map = null;
  let infoWindow = null;
  let ready = false;
  let pendingList = null;       // 지도가 뜨기 전에 받은 목록 (뜨면 그린다)
  let centered = false;         // 처음 한 번만 사옥으로 가운데 맞춤
  const markers = new Map();    // restaurantId → { marker, restaurant }
  let highlightedId = null;

  /* ---------- 밖에서 쓰는 함수 ---------- */

  function init(element) {
    container = element;
    const keyId = global.APP_CONFIG && global.APP_CONFIG.naverMapKeyId;
    if (!keyId) {
      showError('지도 키가 설정되지 않았습니다', 'js/config.example.js를 config.js로 복사하고 naverMapKeyId를 넣어 주세요.');
      return;
    }

    showLoading();
    loadScript(keyId)
      .then(createMap)
      .catch(() => showError('지도를 불러오지 못했습니다', '지도 키와 네이버 클라우드에 등록한 Web 서비스 URL(http://localhost)을 확인해 주세요.'));
  }

  function setRestaurants(list) {
    if (!ready) {
      pendingList = list;
      return;
    }
    renderMarkers(list);
  }

  function highlight(restaurantId) {
    const id = restaurantId == null ? null : Number(restaurantId);
    if (id === highlightedId) return;
    if (highlightedId != null) setPinActive(highlightedId, false);
    highlightedId = id;
    if (id != null) setPinActive(id, true);
  }

  /* ---------- 지도 불러오기 ---------- */

  function loadScript(keyId) {
    return new Promise((resolve, reject) => {
      if (global.naver && global.naver.maps) {
        resolve();
        return;
      }
      // 네이버가 키 인증에 실패하면 이 이름의 함수를 불러 준다
      global.navermap_authFailure = () => reject(new Error('NAVER_MAP_AUTH_FAILURE'));

      const script = document.createElement('script');
      script.src = MAP_SCRIPT_URL + encodeURIComponent(keyId);
      script.async = true;
      script.onload = () => (global.naver && global.naver.maps ? resolve() : reject(new Error('NO_NAVER_MAPS')));
      script.onerror = () => reject(new Error('SCRIPT_LOAD_FAILED'));
      document.head.appendChild(script);
    });
  }

  function createMap() {
    const maps = global.naver.maps;
    container.innerHTML = '';
    map = new maps.Map(container, {
      center: new maps.LatLng(DEFAULT_CENTER.lat, DEFAULT_CENTER.lng),
      zoom: DEFAULT_ZOOM,
      zoomControl: true, // [+]/[−]
      zoomControlOptions: { position: maps.Position.TOP_RIGHT },
    });
    infoWindow = new maps.InfoWindow({
      content: '',
      borderWidth: 0,
      backgroundColor: 'transparent',
      disableAnchor: true,
      pixelOffset: new maps.Point(0, -6),
    });
    maps.Event.addListener(map, 'click', () => infoWindow.close()); // 빈 곳을 누르면 말풍선 닫기

    ready = true;
    if (pendingList) {
      renderMarkers(pendingList);
      pendingList = null;
    }
  }

  /* ---------- 핀 ---------- */

  function renderMarkers(list) {
    const maps = global.naver.maps;
    infoWindow.close();
    markers.forEach(({ marker }) => marker.setMap(null));
    markers.clear();

    list
      .filter((r) => typeof r.lat === 'number' && typeof r.lng === 'number')
      .forEach((restaurant) => {
        const marker = new maps.Marker({
          map,
          position: new maps.LatLng(restaurant.lat, restaurant.lng),
          title: restaurant.name,
          icon: pinIcon(restaurant, false),
          zIndex: restaurant.cafeteria ? 50 : 10,
        });
        maps.Event.addListener(marker, 'click', () => openInfo(restaurant, marker));
        markers.set(restaurant.restaurantId, { marker, restaurant });
      });

    if (highlightedId != null) setPinActive(highlightedId, true);

    // 처음 한 번: 사옥(구내식당)으로 가운데 맞춤
    if (!centered) {
      const cafeteria = list.find((r) => r.cafeteria && typeof r.lat === 'number');
      if (cafeteria) map.setCenter(new maps.LatLng(cafeteria.lat, cafeteria.lng));
      centered = true;
    }
  }

  function pinIcon(restaurant, active) {
    const classes = ['map-pin'];
    if (restaurant.cafeteria) classes.push('map-pin--cafeteria');
    if (active) classes.push('is-active');
    return {
      content: '<div class="' + classes.join(' ') + '"><span class="map-pin__dot"></span></div>',
      size: new global.naver.maps.Size(PIN_SIZE, PIN_SIZE),
      anchor: new global.naver.maps.Point(PIN_SIZE / 2, PIN_SIZE), // 핀 끝이 좌표를 가리키게
    };
  }

  function setPinActive(restaurantId, active) {
    const item = markers.get(restaurantId);
    if (!item) return; // 좌표가 없어서 핀이 없는 식당
    item.marker.setIcon(pinIcon(item.restaurant, active));
    item.marker.setZIndex(active ? 100 : (item.restaurant.cafeteria ? 50 : 10));
  }

  /* ---------- 말풍선 ---------- */

  function openInfo(restaurant, marker) {
    const categoryText = restaurantApi.CATEGORY_LABELS[restaurant.category] || restaurant.category;
    infoWindow.setContent(
      '<div class="map-info">' +
      '<p class="map-info__name">' + esc(restaurant.name) + '</p>' +
      '<p class="map-info__meta">' + esc(categoryText) + ' · 따봉 ' + restaurant.thumbsCount30d + '</p>' +
      '<a class="map-info__link" href="/restaurant-detail.html?id=' + encodeURIComponent(restaurant.restaurantId) + '">상세 보기</a>' +
      '</div>'
    );
    infoWindow.open(map, marker);
  }

  /* ---------- 상태 화면 ---------- */

  function showLoading() {
    container.innerHTML =
      '<div class="restaurant-map__state state state--loading">' +
      '<span class="spinner" aria-hidden="true"></span>' +
      '<p class="state__desc">지도를 불러오는 중…</p>' +
      '</div>';
  }

  function showError(title, desc) {
    container.innerHTML =
      '<div class="restaurant-map__state state state--error">' +
      '<p class="state__title">' + esc(title) + '</p>' +
      '<p class="state__desc">' + esc(desc) + '</p>' +
      '</div>';
  }

  global.restaurantMap = { init, setRestaurants, highlight };
})(window);
