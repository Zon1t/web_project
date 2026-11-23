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
 * '같은 시간대에는 식사팟 하나만' 안내 팝업 (2026-11-23):
 *   if (partyJoin.isSameMealError(err)) partyJoin.sameMealNotice({ partyDate, mealType });
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

  /* ===================== 한 끼에 팟 하나 안내 (2026-11-23) ===================== */

  const SAME_MEAL_ERROR = 'ALREADY_JOINED_SAME_MEAL';
  const MY_PARTIES_URL = '/mypage.html?tab=parties';

  /** 서버 오류가 '같은 시간대에 이미 참여 중'인지 */
  function isSameMealError(err) {
    return !!err && err.error === SAME_MEAL_ERROR;
  }

  /**
   * '같은 시간대에는 식사팟 하나만' 안내 팝업.
   *
   *   await partyJoin.sameMealNotice({ partyDate, mealType, joinedParty, cancelText });
   *
   * - joinedParty: 이미 참여 중인 팟 { partyId, restaurantName, time } (목록 화면처럼 알고 있을 때)
   *   없으면 그 날짜·끼니 목록에서 내가 참여한 팟을 찾아 본다. 못 찾으면(식사 완료 등) 이름 없이 안내한다.
   * - [내 식사팟 보기] → 참여 중인 팟 상세, 모르면 마이페이지 '함께한 식사팟' 탭
   * - cancelText: 닫기 버튼 글자 (기본 '닫기', 식사팟 만들기에서는 '다른 날짜·끼니 고르기')
   */
  async function sameMealNotice(o) {
    const opts = o || {};
    let joined = opts.joinedParty || null;
    if (!joined && opts.mealType) {
      try {
        const result = await partyApi.getList({ date: opts.partyDate || undefined, mealType: opts.mealType });
        joined = result.parties.find((p) => p.joined) || null;
      } catch (e) {
        joined = null;   // 못 찾아도 안내는 띄운다
      }
    }

    const when = (opts.partyDate && !partyApi.isToday(opts.partyDate) ? partyApi.relativeDateLabel(opts.partyDate) : '오늘') +
      (opts.mealType ? ' ' + partyApi.MEAL_TYPE_LABELS[opts.mealType] : '');
    const body = joined
      ? '<p class="pj-notice__text">' + esc(when) + '에 이미 <b>' + esc(joined.restaurantName) + '</b>' +
        (joined.time ? ' (' + esc(joined.time) + ')' : '') + ' 식사팟에 참여 중이에요.</p>'
      : '<p class="pj-notice__text">' + esc(when) + '에 이미 참여 중인 식사팟이 있어요.</p>';

    const goMine = await ui.confirm({
      title: '같은 시간대에는 식사팟 하나만 참여할 수 있어요',
      html:
        '<div class="pj-notice">' + ui.icon('people', 20) + '<div>' + body +
        '<p class="pj-notice__desc">다른 팟에 들어가려면 지금 팟에서 먼저 나와 주세요.</p></div></div>',
      confirmText: '내 식사팟 보기',
      cancelText: opts.cancelText || '닫기',
    });
    if (goMine) location.href = joined ? partyApi.detailUrl(joined.partyId) : MY_PARTIES_URL;
  }

  global.partyJoin = { ask, isSameMealError, sameMealNotice };
})(window);
