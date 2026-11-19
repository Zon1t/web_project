/*
 * ranking.js — 랭킹 화면 (S-09, C)
 *
 * - 최근 30일 따봉 수 기준 TOP 10 (기간은 매일 바뀐다: 오늘-29일 ~ 오늘)
 * - 정렬·순위는 서버가 정한다: 따봉 수 → 리뷰 수 → 먼저 등록된 순, 동점이어도 순위는 다르게
 * - 1~3위 강조, 식당 이름을 누르면 식당 상세(S-05)
 * - 따봉 1건 이상인 식당만. 없으면 '최근 30일간 따봉을 받은 식당이 없습니다'
 * - 관심(구 추천) 수는 랭킹에 쓰지 않는다
 */
(function () {
  'use strict';

  const esc = utils.escapeHtml;
  const CATEGORY_LABELS = restaurantApi.CATEGORY_LABELS;
  const RANKING_LIMIT = 10;
  const HIGHLIGHT_UNTIL = 3;   // 1~3위 강조
  const RESTAURANT_DETAIL_URL = '/restaurant-detail.html?id=';

  const body = document.getElementById('ranking-body');
  const period = document.getElementById('ranking-period');

  body.addEventListener('click', (event) => {
    if (event.target.closest('[data-action="retry"]')) loadRankings();
  });

  loadRankings();

  async function loadRankings() {
    body.innerHTML =
      '<div class="state state--loading"><span class="spinner" aria-hidden="true"></span>' +
      '<p class="state__desc">랭킹을 불러오는 중…</p></div>';

    try {
      const result = await rankingApi.getRankings(RANKING_LIMIT);
      period.textContent =
        '최근 30일(' + result.from + ' ~ ' + result.to + ') 따봉 수 기준 · 동점 시 리뷰 수가 많은 순';

      if (!result.rankings.length) {
        body.innerHTML =
          '<div class="state ranking-state"><p class="state__title">최근 30일간 따봉을 받은 식당이 없습니다</p>' +
          '<p class="state__desc">식사팟으로 함께 식사하고 따봉을 남겨 보세요.</p></div>';
        return;
      }
      body.innerHTML = tableHtml(result.rankings);
    } catch (err) {
      if (err.statusCode === 401) return;   // client.js가 로그인 화면으로 이동
      body.innerHTML =
        '<div class="state state--error ranking-state"><p class="state__title">랭킹을 불러오지 못했습니다</p>' +
        '<p class="state__desc">' + esc(err.message) + '</p>' +
        '<button class="btn" type="button" data-action="retry">다시 시도</button></div>';
    }
  }

  function tableHtml(rankings) {
    return (
      '<table class="ranking-table">' +
      '<caption class="sr-only">최근 30일 따봉 랭킹</caption>' +
      '<thead><tr>' +
      '<th scope="col" class="ranking-table__rank">순위</th>' +
      '<th scope="col">식당</th>' +
      '<th scope="col" class="ranking-table__thumbs">따봉 (30일)</th>' +
      '<th scope="col" class="ranking-table__reviews">리뷰</th>' +
      '</tr></thead>' +
      '<tbody>' + rankings.map(rowHtml).join('') + '</tbody>' +
      '</table>'
    );
  }

  function rowHtml(item) {
    const top = item.rank <= HIGHLIGHT_UNTIL;
    const category = CATEGORY_LABELS[item.category] || item.category;
    return (
      '<tr class="ranking-row' + (top ? ' ranking-row--top' : '') + '">' +
      '<td class="ranking-table__rank">' + item.rank + '</td>' +
      '<td>' +
      '<a class="ranking-row__name" href="' + RESTAURANT_DETAIL_URL + encodeURIComponent(item.restaurantId) + '">' +
      esc(item.name) + '</a>' +
      '<span class="ranking-row__category">' + esc(category) + '</span>' +
      '</td>' +
      '<td class="ranking-table__thumbs">' +
      '<span class="ranking-row__thumbs">' + utils.thumbsUpIcon(16) + item.thumbsCount30d + '</span>' +
      '</td>' +
      '<td class="ranking-table__reviews">리뷰 ' + item.reviewCount + '</td>' +
      '</tr>'
    );
  }
})();
