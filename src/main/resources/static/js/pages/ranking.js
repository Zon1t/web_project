/*
 * ranking.js — 랭킹 화면 (S-09, C · 2026-11-20 개편)
 *
 * - 1위 배너: 지금 보는 범위(전체/카테고리)의 1위. 기준이 최근 30일이라 '11월의 식당' 같은 달 표현은 쓰지 않는다 (2026-11-23)
 * - 1위도 따봉이 0개면(예: 카페) 배너·시상대 없이 안내 문구 + 목록만 보여 준다 (2026-11-23)
 * - 따봉 0개인 식당은 막대를 그리지 않는다 (2026-11-23)
 * - 1~3위 시상대 (2위 · 1위 · 3위), 4위부터 리스트 (따봉 막대로 1위와 차이 표시)
 * - 카테고리 필터
 *     전체: GET /api/restaurants/rankings?limit=10 (서버 순위)
 *     카테고리: GET /api/restaurants/list?category= — 이 API가 이미 '최근 30일 따봉 → 리뷰 수 → 등록 순'으로
 *              정렬해 주므로 앞에서 10곳을 순위로 쓴다 (백엔드 변경 없음)
 *     2026-11-23: 따봉이 0개인 식당도 리뷰 수 → 등록 순으로 이어서 보여 준다 (기존 '따봉 1개 이상만')
 * - 태그: 랭킹 응답에 태그가 없어서, 보이는 식당마다 식당 상세(GET /api/restaurants/{id})의 TOP 3 태그를
 *         나중에 채운다 (순위는 먼저 보여 주고 태그만 뒤이어 나타남, 한 번 받은 태그는 기억)
 * - 관심 수는 랭킹에 쓰지 않는다
 */
(function () {
  'use strict';

  const esc = utils.escapeHtml;
  const CATEGORY_LABELS = restaurantApi.CATEGORY_LABELS;
  const CATEGORIES = [null, 'KOREAN', 'CHINESE', 'JAPANESE', 'WESTERN', 'SNACK', 'CAFE', 'ETC'];   // 전체(null)에는 카페 없음 (서버에서 제외)
  const RANKING_LIMIT = 10;
  const PODIUM = 3;
  const DETAIL_URL = '/restaurant-detail.html?id=';

  const bannerEl = document.getElementById('rk-banner');
  const categoriesEl = document.getElementById('rk-categories');
  const body = document.getElementById('ranking-body');
  const periodEl = document.getElementById('ranking-period');

  let category = null;
  let loadSeq = 0;
  const tagCache = new Map();     // restaurantId → ['혼밥가능', ...]

  renderCategories();
  load();

  categoriesEl.addEventListener('click', (event) => {
    const chip = event.target.closest('[data-category]');
    if (!chip) return;
    const next = chip.dataset.category || null;
    if (next === category) return;
    category = next;
    renderCategories();
    load();
  });

  body.addEventListener('click', (event) => {
    if (event.target.closest('[data-action="retry"]')) load();
  });

  /* ---------- 불러오기 ---------- */

  async function load() {
    const seq = ++loadSeq;
    body.innerHTML = ui.skeleton('row', 5);

    try {
      const items = await fetchRankings();
      if (seq !== loadSeq) return;

      if (!items.length) {
        bannerEl.hidden = true;
        body.innerHTML = ui.empty({
          illust: 'thumbs',
          title: category
            ? '등록된 ' + esc(CATEGORY_LABELS[category]) + ' 식당이 없어요'
            : '등록된 식당이 없어요',
          desc: '식당을 등록하고 식사팟으로 함께 식사한 뒤 따봉을 남기면 순위가 바뀌어요.',
          actionHtml: '<a class="btn btn--primary" href="' + partyApi.LIST_URL + '">식사팟 보기</a>',
        });
        return;
      }

      // 카페·베이커리: 따봉 없이 리뷰 많은 순 → 먼저 등록된 순, 따봉 숫자·막대·시상대 없음 (2026-11-25)
      if (category === 'CAFE') {
        bannerEl.hidden = true;
        body.innerHTML = cafeNoticeHtml() + listHtml(items, 0, true);
        fillTags(items, seq);
        return;
      }

      // 1위도 따봉이 0개: 트로피·시상대가 어색하므로 안내 문구 + 목록만
      if (items[0].thumbsCount30d === 0) {
        bannerEl.hidden = true;
        body.innerHTML = noThumbsNoticeHtml() + listHtml(items, 0);
        fillTags(items, seq);
        return;
      }

      renderBanner(items[0]);
      body.innerHTML = podiumHtml(items.slice(0, PODIUM)) + listHtml(items.slice(PODIUM), items[0].thumbsCount30d);
      fillTags(items, seq);
    } catch (err) {
      if (seq !== loadSeq || err.statusCode === 401) return;   // 401은 client.js가 로그인 화면으로 이동
      bannerEl.hidden = true;
      body.innerHTML =
        '<div class="state state--error rk-state"><p class="state__title">랭킹을 불러오지 못했어요</p>' +
        '<p class="state__desc">' + esc(err.message) + '</p>' +
        '<button class="btn" type="button" data-action="retry">다시 시도</button></div>';
    }
  }

  /** 지금 범위의 순위 목록: [{ rank, restaurantId, name, category, thumbsCount30d, reviewCount }] */
  async function fetchRankings() {
    if (!category) {
      const result = await rankingApi.getRankings(RANKING_LIMIT);
      periodEl.textContent = '최근 30일(' + result.from + ' ~ ' + result.to + ') 따봉 수 기준 · 같으면 리뷰가 많은 순이에요.';
      return result.rankings;
    }
    let list = await restaurantApi.getList({ category });
    if (category === 'CAFE') {
      periodEl.textContent = CATEGORY_LABELS[category] + '는 따봉 없이 리뷰가 많은 순 · 같으면 먼저 등록된 순이에요.';
      list = list.slice().sort((a, b) => (b.reviewCount - a.reviewCount) || (a.restaurantId - b.restaurantId));
    } else {
      periodEl.textContent = CATEGORY_LABELS[category] + ' 식당 중 최근 30일 따봉 수 기준 · 같으면 리뷰가 많은 순이에요.';
    }
    return list
      .slice(0, RANKING_LIMIT)
      .map((r, i) => ({
        rank: i + 1,
        restaurantId: r.restaurantId,
        name: r.name,
        category: r.category,
        thumbsCount30d: r.thumbsCount30d,
        reviewCount: r.reviewCount,
      }));
  }

  /* ---------- 카테고리 ---------- */

  function renderCategories() {
    categoriesEl.innerHTML = CATEGORIES.map((value) => {
      const selected = value === category;
      const label = value ? CATEGORY_LABELS[value] : '전체';
      return '<button type="button" class="chip' + (selected ? ' is-selected' : '') + '" data-category="' + (value || '') +
        '" aria-pressed="' + selected + '">' + esc(label) + '</button>';
    }).join('');
  }

  /** 카페·베이커리 안내 (따봉 대신 리뷰 순, 2026-11-25) */
  function cafeNoticeHtml() {
    return (
      '<div class="rk-notice">' + ui.icon('chat', 20) +
      '<div><p class="rk-notice__title">카페·베이커리는 리뷰가 많은 순이에요</p>' +
      '<p class="rk-notice__desc">따봉은 함께 식사한 식당에 남기는 거라 카페는 따봉 없이 리뷰 수로 보여 드려요.</p></div>' +
      '</div>'
    );
  }

  /** 따봉이 아직 없는 범위의 안내 (배너 대신) */
  function noThumbsNoticeHtml() {
    // 카테고리 이름 뒤 조사(이/가)가 달라지지 않게 '○○ 중에 … 곳이' 형태로 쓴다 (예: 아직 카페 중에 따봉을 받은 곳이 없어요)
    const title = category
      ? '아직 ' + esc(CATEGORY_LABELS[category]) + ' 중에 따봉을 받은 곳이 없어요'
      : '아직 따봉을 받은 식당이 없어요';
    return (
      '<div class="rk-notice">' + ui.icon('thumbs', 20) +
      '<div><p class="rk-notice__title">' + title + '</p>' +
      '<p class="rk-notice__desc">따봉이 없을 때는 리뷰가 많은 순으로 보여 드려요. 함께 식사하고 따봉을 남기면 순위가 생겨요.</p></div>' +
      '</div>'
    );
  }

  /* ---------- 1위 배너 ---------- */

  function renderBanner(top) {
    const scope = category ? CATEGORY_LABELS[category] + ' 따봉 1위' : '따봉 1위';
    bannerEl.innerHTML =
      '<div class="rk-banner__text">' +
      '<p class="rk-banner__eyebrow">' + ui.icon('trophy', 16) + '최근 30일 · ' + esc(scope) + '</p>' +
      '<h2 class="rk-banner__name"><a href="' + DETAIL_URL + encodeURIComponent(top.restaurantId) + '">' + esc(top.name) + '</a></h2>' +
      '<p class="rk-banner__meta">' + ui.avatar(top.category, 28) +
      '<span>' + esc(CATEGORY_LABELS[top.category] || top.category) + '</span>' +
      '<span class="rk-dot" aria-hidden="true">·</span>' +
      '<span class="rk-thumbs">' + ui.icon('thumbs', 16) + '따봉 <b>' + top.thumbsCount30d + '</b></span>' +
      '<span class="rk-dot" aria-hidden="true">·</span><span>리뷰 ' + top.reviewCount + '</span></p>' +
      '<p class="rk-tags rk-banner__tags" data-tags-for="' + top.restaurantId + '"></p>' +
      '<div class="rk-banner__actions">' +
      '<a class="btn btn--primary" href="' + partyApi.createUrl(top.restaurantId) + '">' + ui.icon('plus', 18) + '이 식당으로 식사팟 만들기</a>' +
      '<a class="btn" href="' + DETAIL_URL + encodeURIComponent(top.restaurantId) + '">식당 보기</a>' +
      '</div>' +
      '</div>' +
      '<div class="rk-banner__art">' + ui.illust('trophy', 'rk-banner__illust') + '</div>';
    bannerEl.hidden = false;
    // 범위를 바꿀 때마다 배너가 새로 등장하는 느낌
    bannerEl.classList.remove('is-in');
    void bannerEl.offsetWidth;
    bannerEl.classList.add('is-in');
  }

  /* ---------- 시상대 ---------- */

  function podiumHtml(top3) {
    // 화면 순서: 2위 · 1위 · 3위
    const order = [top3[1], top3[0], top3[2]];
    return (
      '<ol class="rk-podium" aria-label="1위부터 3위">' +
      order.map((item) => (item ? podiumItemHtml(item) : '<li class="rk-podium__slot" aria-hidden="true"></li>')).join('') +
      '</ol>'
    );
  }

  function podiumItemHtml(item) {
    return (
      '<li class="rk-podium__slot rk-podium__slot--' + item.rank + '">' +
      '<a class="rk-podium__item" href="' + DETAIL_URL + encodeURIComponent(item.restaurantId) + '">' +
      '<span class="rk-medal rk-medal--' + item.rank + '">' + item.rank + '</span>' +
      ui.avatar(item.category, item.rank === 1 ? 64 : 52) +
      '<span class="rk-podium__name">' + esc(item.name) + '</span>' +
      '<span class="rk-podium__category">' + esc(CATEGORY_LABELS[item.category] || item.category) + ' · 리뷰 ' + item.reviewCount + '</span>' +
      '<span class="rk-thumbs rk-podium__thumbs">' + ui.icon('thumbs', 18) + item.thumbsCount30d + '</span>' +
      '<span class="rk-tags" data-tags-for="' + item.restaurantId + '"></span>' +
      '</a>' +
      '<div class="rk-podium__stand" aria-hidden="true"><span>' + item.rank + '</span></div>' +
      '</li>'
    );
  }

  /* ---------- 4위부터 리스트 ---------- */

  function listHtml(rest, topThumbs, noThumbs) {
    if (!rest.length) return '';
    return '<ol class="rk-list" aria-label="4위부터">' + rest.map((item, i) => rowHtml(item, topThumbs, i, noThumbs)).join('') + '</ol>';
  }

  /** noThumbs: 따봉 숫자·막대를 그리지 않는다 (카페·베이커리) */
  function rowHtml(item, topThumbs, i, noThumbs) {
    const ratio = topThumbs ? Math.max(6, Math.round((item.thumbsCount30d / topThumbs) * 100)) : 0;
    const bar = item.thumbsCount30d > 0
      ? '<span class="rk-bar" aria-hidden="true"><span style="width:' + ratio + '%"></span></span>'
      : '';   // 따봉 0개는 막대 없음
    return (
      '<li style="--rk-delay:' + (i * 40) + 'ms">' +
      '<a class="rk-row" href="' + DETAIL_URL + encodeURIComponent(item.restaurantId) + '">' +
      '<span class="rk-row__rank">' + item.rank + '</span>' +
      ui.avatar(item.category, 44) +
      '<span class="rk-row__main">' +
      '<span class="rk-row__name">' + esc(item.name) + '</span>' +
      '<span class="rk-row__sub">' + esc(CATEGORY_LABELS[item.category] || item.category) + ' · 리뷰 ' + item.reviewCount + '</span>' +
      '<span class="rk-tags" data-tags-for="' + item.restaurantId + '"></span>' +
      '</span>' +
      (noThumbs ? '' :
        '<span class="rk-row__score">' +
        '<span class="rk-thumbs">' + ui.icon('thumbs', 16) + item.thumbsCount30d + '</span>' +
        bar +
        '</span>') +
      '</a>' +
      '</li>'
    );
  }

  /* ---------- 태그 ---------- */

  function fillTags(items, seq) {
    items.forEach(async (item) => {
      let tags = tagCache.get(item.restaurantId);
      if (!tags) {
        try {
          const detail = await restaurantApi.getDetail(item.restaurantId);
          tags = (detail.topTags || []).map((t) => t.name);
          tagCache.set(item.restaurantId, tags);
        } catch (err) {
          return;   // 태그는 부가 정보라 실패하면 비워 둔다
        }
      }
      if (seq !== loadSeq) return;
      document.querySelectorAll('[data-tags-for="' + item.restaurantId + '"]').forEach((el) => {
        el.innerHTML = tags.map((name) => '<span class="rk-tag">#' + esc(name) + '</span>').join('');
        el.classList.add('is-filled');
      });
    });
  }
})();
