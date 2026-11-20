/*
 * party-join.js — 식사팟 [참여하기] 전 확인 창 (공통)
 *
 * 홈(오늘의 식사 카드), 식사팟 목록, 식사팟 상세에서 같은 확인 창을 쓴다.
 *
 *   const ok = await partyJoin.ask({
 *     restaurantName, category, mealType, time, partyDate, memberCount, capacity, memo, status,
 *   });
 *   if (ok) await partyApi.join(partyId);
 *
 * 필요: utils.js, party.js, restaurant.js, ui.js
 */
(function (global) {
  'use strict';

  const esc = utils.escapeHtml;

  function summaryHtml(p) {
    const mealLabel = p.mealType ? partyApi.MEAL_TYPE_LABELS[p.mealType] + ' ' : '';
    const when = p.partyDate && !partyApi.isToday(p.partyDate) ? partyApi.relativeDateLabel(p.partyDate) + ' ' : '오늘 ';
    const category = restaurantApi.CATEGORY_LABELS[p.category] || '';
    const left = p.capacity ? p.capacity - p.memberCount : null;
    return (
      '<div class="pj-summary">' +
      '<div class="pj-summary__top">' +
      ui.avatar(p.category, 44) +
      '<div class="pj-summary__text">' +
      '<p class="pj-summary__name">' + esc(p.restaurantName) + (category ? ' <small>' + esc(category) + '</small>' : '') + '</p>' +
      '<p class="pj-summary__meta">' + ui.icon('clock', 14) + esc(when + mealLabel + (p.time || '')) +
      ' · ' + ui.icon('people', 14) + p.memberCount + (p.capacity ? ' / ' + p.capacity : '') + '명' +
      (left === 1 ? ' <span class="pj-badge">마지막 1자리</span>' : '') + '</p>' +
      '</div>' +
      '</div>' +
      (p.memo ? '<p class="pj-summary__desc">“' + esc(p.memo) + '”</p>' : '') +
      '</div>' +
      '<ol class="pj-steps">' +
      '<li class="is-now"><b>지금</b> 팟에 참여하고 대화방에 들어가요</li>' +
      '<li><b>다음</b> 방장이 시간·장소를 정해요</li>' +
      '<li><b>마지막</b> 갈 수 있으면 <b>참석 확정</b>을 눌러요</li>' +
      '</ol>'
    );
  }

  function ask(p) {
    return ui.confirm({
      title: '이 식사팟에 참여할까요?',
      html: summaryHtml(p),
      confirmText: '참여하기',
    });
  }

  global.partyJoin = { ask };
})(window);
