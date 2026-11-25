/*
 * restaurant-map.js — S-04 식당 목록 오른쪽 네이버 지도 (B, F-17)
 *
 * restaurant-list.js에서 이렇게 쓴다.
 *   restaurantMap.init(document.getElementById('restaurant-map'));  // 지도 불러오기 (한 번)
 *   restaurantMap.setRestaurants(list);   // 목록과 같은 데이터로 핀 다시 그리기 (검색·필터 때마다)
 *   restaurantMap.highlight(12);          // 목록 항목에 마우스를 올리면 핀 강조, null이면 해제
 *   restaurantMap.onHover((id) => { ... }); // 지도 핀에 마우스를 올리면 목록도 강조하도록 (id 또는 null)
 *   restaurantMap.onSelect((id) => { ... }); // 핀을 누르면 (식당 상세 카드 열기). 등록하지 않으면 말풍선
 *   restaurantMap.refresh();              // 지도 칸이 숨겨졌다 다시 보일 때 크기 다시 맞추기 (좁은 화면 [지도 보기])
 *
 * - 지도 불러오기·핀 모양은 공통 함수 js/components/map-kit.js (2026-11-23 분리)
 * - 지도 키: js/config.js의 window.APP_CONFIG.naverMapKeyId (Git 제외, 견본은 config.example.js)
 * - 가운데: 사옥(구내식당) 위치. 목록에 구내식당 좌표가 없으면 세종대 좌표
 * - 핀: 카테고리별 색 + 목록 아바타와 같은 음식 그림. 구내식당은 남색에 조금 큰 핀
 * - 강조된 핀(목록·핀에 마우스): 통통 튀며 커지고, 위에 툴팁(이름, 카테고리 · 따봉 수)
 * - 핀을 누르면 말풍선: 이름, 카테고리 · 따봉 수, [상세 보기]
 * - 키가 없거나 지도를 못 불러오면 지도 자리에 안내만 보여 주고, 목록은 그대로 동작한다.
 */
(function (global) {
  'use strict';

  const DEFAULT_ZOOM = 16;

  const esc = utils.escapeHtml;

  let container = null;
  let map = null;
  let infoWindow = null;
  let ready = false;
  let pendingList = null;       // 지도가 뜨기 전에 받은 목록 (뜨면 그린다)
  let centered = false;         // 처음 한 번만 사옥으로 가운데 맞춤
  const markers = new Map();    // restaurantId → { marker, restaurant }
  let highlightedId = null;
  let infoOpenId = null;        // 말풍선이 열린 핀 (툴팁과 겹치지 않게)
  let hoverCallback = null;     // 지도 핀 마우스 → 목록 강조
  let selectCallback = null;    // 지도 핀 클릭 → 식당 상세 카드

  /* ---------- 밖에서 쓰는 함수 ---------- */

  function init(element) {
    container = element;
    showLoading();
    global.mapKit.load()
      .then(createMap)
      .catch((err) => {
        if (err.code === 'NO_KEY') {
          showError('지도 키가 설정되지 않았어요', 'js/config.example.js를 config.js로 복사하고 naverMapKeyId를 넣어 주세요.');
        } else {
          showError('지도를 불러오지 못했어요', '지도 키와 네이버 클라우드에 등록한 Web 서비스 URL(http://localhost)을 확인해 주세요.');
        }
      });
  }

  function setRestaurants(list) {
    if (!ready) {
      pendingList = list;
      return;
    }
    renderMarkers(list);
  }

  function onHover(callback) {
    hoverCallback = callback;
  }

  function onSelect(callback) {
    selectCallback = callback;
  }

  /**
   * 지도 칸 크기가 바뀐 뒤 다시 맞춘다. (2026-11-25)
   * 좁은 화면에서 숨겨져 있던(display: none) 지도는 크기를 0으로 기억해서, 다시 보일 때 빈칸·깨진 지도가 된다.
   * 화면이 다시 그려진 다음 프레임에 칸 크기를 다시 재고, 보던 가운데를 그대로 유지한다.
   */
  function refresh() {
    if (!ready) return; // 아직 지도가 없으면 만들 때 보이는 크기로 그려진다
    global.requestAnimationFrame(() => {
      const width = container.clientWidth;
      const height = container.clientHeight;
      if (!width || !height) return; // 아직 숨겨져 있음
      const center = map.getCenter();
      // autoResize: 지금 칸 크기를 재서 맞춘다. (setSize는 칸에 px 크기를 박아 넣어 넓은 화면에서도 작게 남는다)
      map.autoResize();
      map.setCenter(center);
    });
  }

  function highlight(restaurantId) {
    const id = restaurantId == null ? null : Number(restaurantId);
    if (id === highlightedId) return;
    if (highlightedId != null) setPinActive(highlightedId, false);
    highlightedId = id;
    if (id != null) setPinActive(id, true);
  }

  /* ---------- 지도 만들기 ---------- */

  function createMap() {
    const maps = global.naver.maps;
    const center = global.mapKit.DEFAULT_CENTER; // 사옥 좌표가 없을 때
    container.innerHTML = '';
    map = new maps.Map(container, {
      center: new maps.LatLng(center.lat, center.lng),
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
    maps.Event.addListener(map, 'click', closeInfo); // 빈 곳을 누르면 말풍선 닫기

    // 창 크기가 바뀌면(좁은 화면 ↔ 넓은 화면, 휴대폰 가로·세로) 지도 칸도 다시 맞춘다
    let resizeTimer = null;
    global.addEventListener('resize', () => {
      clearTimeout(resizeTimer);
      resizeTimer = setTimeout(refresh, 150);
    });

    ready = true;
    if (pendingList) {
      renderMarkers(pendingList);
      pendingList = null;
    }
  }

  /* ---------- 핀 ---------- */

  function renderMarkers(list) {
    const maps = global.naver.maps;
    closeInfo();
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
        maps.Event.addListener(marker, 'click', () => {
          if (selectCallback) selectCallback(restaurant.restaurantId);
          else openInfo(restaurant, marker);
        });
        maps.Event.addListener(marker, 'mouseover', () => {
          highlight(restaurant.restaurantId);
          if (hoverCallback) hoverCallback(restaurant.restaurantId);
        });
        maps.Event.addListener(marker, 'mouseout', () => {
          highlight(null);
          if (hoverCallback) hoverCallback(null);
        });
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

  // 핀 모양은 map-kit.js. 여기서는 강조 여부와 툴팁(말풍선이 열린 핀은 툴팁 숨김)만 정한다
  function pinIcon(restaurant, active) {
    const showTip = active && infoOpenId !== restaurant.restaurantId;
    return global.mapKit.pinIcon(restaurant, { active, tipHtml: showTip ? tipHtml(restaurant) : '' });
  }

  function tipHtml(restaurant) {
    return '<div class="map-pin__tip" role="tooltip">' +
      '<span class="map-pin__tip-name">' + esc(restaurant.name) + '</span>' +
      '<span class="map-pin__tip-meta">' + esc(categoryLabel(restaurant)) + ' · 따봉 ' + Number(restaurant.thumbsCount30d || 0) + '</span>' +
      '</div>';
  }

  function categoryLabel(restaurant) {
    return restaurantApi.CATEGORY_LABELS[restaurant.category] || restaurant.category;
  }

  function setPinActive(restaurantId, active) {
    const item = markers.get(restaurantId);
    if (!item) return; // 좌표가 없어서 핀이 없는 식당
    item.marker.setIcon(pinIcon(item.restaurant, active));
    item.marker.setZIndex(active ? 100 : (item.restaurant.cafeteria ? 50 : 10));
  }

  /* ---------- 말풍선 ---------- */

  function openInfo(restaurant, marker) {
    const categoryText = categoryLabel(restaurant);
    infoOpenId = restaurant.restaurantId;
    if (highlightedId === infoOpenId) setPinActive(infoOpenId, true); // 툴팁 숨김
    infoWindow.setContent(
      '<div class="map-info">' +
      '<p class="map-info__name">' + esc(restaurant.name) + '</p>' +
      '<p class="map-info__meta">' + esc(categoryText) + ' · 따봉 ' + restaurant.thumbsCount30d + '</p>' +
      '<a class="map-info__link" href="/restaurant-detail.html?id=' + encodeURIComponent(restaurant.restaurantId) + '">상세 보기</a>' +
      '</div>'
    );
    infoWindow.open(map, marker);
  }

  function closeInfo() {
    const id = infoOpenId;
    infoOpenId = null;
    if (infoWindow) infoWindow.close();
    if (id != null && id === highlightedId) setPinActive(id, true); // 툴팁 다시 표시
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

  global.restaurantMap = { init, setRestaurants, highlight, onHover, onSelect, refresh };
})(window);
