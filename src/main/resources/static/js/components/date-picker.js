/*
 * date-picker.js — 달력으로 날짜 고르기 부품 (A)
 *
 * 버튼을 누르면 달력이 열리고, 날짜를 누르면 닫히면서 onChange가 불린다.
 * min ~ max 밖의 날짜와 isDisabled가 true인 날짜는 누를 수 없다.
 * 키보드: 방향키로 날짜 이동, Enter·Space로 선택, Esc로 닫기
 *
 * 사용 예 (식사팟 만들기·목록)
 *   const picker = datePicker.create(document.getElementById('date-picker'), {
 *     value: '2026-11-21',                 // 'YYYY-MM-DD'
 *     min: '2026-11-20', max: '2026-12-04',
 *     isDisabled: (date) => false,          // 선택: 범위 안이지만 막을 날짜
 *     formatLabel: (date) => '내일 · 11월 21일 (토)',   // 버튼에 보일 글자
 *     hint: '오늘부터 2주 후까지 고를 수 있어요.',
 *     label: '식사 날짜',                   // 화면 낭독기용 이름
 *     onChange: (date) => { ... },
 *   });
 *   picker.setValue('2026-11-22');   // onChange는 부르지 않는다
 *   picker.setRange(min, max);       // 자정이 지나 고를 수 있는 범위가 바뀌었을 때
 *
 * 필요한 파일: <link rel="stylesheet" href="/css/date-picker.css">
 */
(function (global) {
  'use strict';

  const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];
  const CALENDAR_ICON =
    '<svg class="date-picker__icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" ' +
    'stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
    '<rect x="3" y="4" width="18" height="18" rx="2"></rect><path d="M16 2v4M8 2v4M3 10h18"></path></svg>';

  let pickerCount = 0;

  /* ---------- 날짜 계산 ('YYYY-MM-DD' 문자열로 주고받는다) ---------- */

  function parse(iso) {
    const [y, m, d] = String(iso).split('-').map(Number);
    return new Date(y, m - 1, d);
  }

  function format(date) {
    const pad = (n) => String(n).padStart(2, '0');
    return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate());
  }

  function addDays(iso, days) {
    const d = parse(iso);
    d.setDate(d.getDate() + days);
    return format(d);
  }

  function todayIso() {
    return format(new Date());
  }

  /** 'YYYY-MM' 끼리 비교할 수 있게 달 번호로 (2026년 11월 → 2026*12+10) */
  function monthIndex(iso) {
    const d = parse(iso);
    return d.getFullYear() * 12 + d.getMonth();
  }

  /* ---------- 부품 ---------- */

  function create(container, options) {
    const id = 'date-picker-' + (++pickerCount);
    const opts = Object.assign({
      isDisabled: () => false,
      formatLabel: (date) => date,
      hint: '',
      label: '날짜',
      onChange: () => {},
    }, options);

    const state = {
      value: opts.value,
      viewMonth: monthIndex(opts.value),   // 지금 보고 있는 달
      focusDate: opts.value,               // 키보드로 옮겨 다니는 날짜
      open: false,
    };

    container.classList.add('date-picker');
    container.innerHTML =
      '<button type="button" class="date-picker__trigger" aria-haspopup="dialog" aria-expanded="false" ' +
      'aria-controls="' + id + '-panel">' + CALENDAR_ICON +
      '<span class="date-picker__value"></span>' +
      '<span class="sr-only"> · ' + opts.label + ' 바꾸기</span></button>' +
      '<div class="date-picker__panel" id="' + id + '-panel" role="dialog" aria-label="' + opts.label + ' 선택" hidden>' +
      '<div class="date-picker__head">' +
      '<button type="button" class="date-picker__nav" data-nav="-1" aria-label="이전 달">&lsaquo;</button>' +
      '<span class="date-picker__month" aria-live="polite"></span>' +
      '<button type="button" class="date-picker__nav" data-nav="1" aria-label="다음 달">&rsaquo;</button>' +
      '</div>' +
      '<table class="date-picker__grid" role="grid"><thead><tr>' +
      WEEKDAYS.map((w, i) => '<th scope="col" class="date-picker__weekday' + weekendClass(i) + '">' + w + '</th>').join('') +
      '</tr></thead><tbody></tbody></table>' +
      (opts.hint ? '<p class="date-picker__hint">' + opts.hint + '</p>' : '') +
      '</div>';

    const trigger = container.querySelector('.date-picker__trigger');
    const valueText = container.querySelector('.date-picker__value');
    const panel = container.querySelector('.date-picker__panel');
    const monthText = container.querySelector('.date-picker__month');
    const body = container.querySelector('tbody');
    const navButtons = Array.from(container.querySelectorAll('.date-picker__nav'));

    trigger.addEventListener('click', () => (state.open ? close(true) : open()));
    navButtons.forEach((btn) => btn.addEventListener('click', () => {
      state.viewMonth += Number(btn.dataset.nav);
      renderMonth();
    }));
    body.addEventListener('click', (event) => {
      const day = event.target.closest('[data-date]');
      if (day && !day.disabled) choose(day.dataset.date);
    });
    panel.addEventListener('keydown', onKeydown);
    // 달력 밖을 누르면 닫는다
    document.addEventListener('mousedown', (event) => {
      if (state.open && !container.contains(event.target)) close(false);
    });

    renderValue();

    /* ---- 열기·닫기 ---- */

    function open() {
      state.open = true;
      state.viewMonth = monthIndex(state.value);
      state.focusDate = state.value;
      panel.hidden = false;
      trigger.setAttribute('aria-expanded', 'true');
      renderMonth();
      focusDay(state.focusDate);
    }

    function close(returnFocus) {
      state.open = false;
      panel.hidden = true;
      trigger.setAttribute('aria-expanded', 'false');
      if (returnFocus) trigger.focus();
    }

    function choose(date) {
      const changed = date !== state.value;
      state.value = date;
      renderValue();
      close(true);
      if (changed) opts.onChange(date);
    }

    /* ---- 그리기 ---- */

    function renderValue() {
      valueText.textContent = opts.formatLabel(state.value);
    }

    function isSelectable(date) {
      return date >= opts.min && date <= opts.max && !opts.isDisabled(date);
    }

    function renderMonth() {
      const year = Math.floor(state.viewMonth / 12);
      const month = state.viewMonth % 12;
      monthText.textContent = year + '년 ' + (month + 1) + '월';
      navButtons[0].disabled = state.viewMonth <= monthIndex(opts.min);
      navButtons[1].disabled = state.viewMonth >= monthIndex(opts.max);

      const first = new Date(year, month, 1);
      const lastDay = new Date(year, month + 1, 0).getDate();
      const today = todayIso();
      const cells = [];
      for (let i = 0; i < first.getDay(); i++) cells.push('<td></td>');   // 1일 앞 빈칸
      for (let d = 1; d <= lastDay; d++) {
        const date = format(new Date(year, month, d));
        const weekday = new Date(year, month, d).getDay();
        const classes = ['date-picker__day'];
        if (date === today) classes.push('is-today');
        if (date === state.value) classes.push('is-selected');
        const selectable = isSelectable(date);
        cells.push(
          '<td><button type="button" class="' + classes.join(' ') + weekendClass(weekday) + '" data-date="' + date + '"' +
          ' tabindex="-1" aria-label="' + (month + 1) + '월 ' + d + '일 ' + WEEKDAYS[weekday] + '요일' +
          (date === today ? ', 오늘' : '') + (selectable ? '' : ', 선택할 수 없음') + '"' +
          (date === state.value ? ' aria-pressed="true"' : '') +
          (selectable ? '' : ' disabled') + '>' + d + '</button></td>'
        );
      }
      const rows = [];
      for (let i = 0; i < cells.length; i += 7) rows.push('<tr>' + cells.slice(i, i + 7).join('') + '</tr>');
      body.innerHTML = rows.join('');
    }

    /* ---- 키보드 ---- */

    function focusDay(date) {
      const btn = body.querySelector('[data-date="' + date + '"]');
      if (btn && !btn.disabled) {
        body.querySelectorAll('[data-date]').forEach((b) => { b.tabIndex = -1; });
        btn.tabIndex = 0;
        btn.focus();
      } else {
        const firstEnabled = body.querySelector('[data-date]:not(:disabled)');
        if (firstEnabled) {
          firstEnabled.tabIndex = 0;
          firstEnabled.focus();
        }
      }
    }

    function onKeydown(event) {
      if (event.key === 'Escape') {
        event.preventDefault();
        close(true);
        return;
      }
      const moves = { ArrowLeft: -1, ArrowRight: 1, ArrowUp: -7, ArrowDown: 7 };
      if (!(event.key in moves) || !event.target.closest('[data-date]')) return;
      event.preventDefault();
      // 고를 수 있는 날짜를 만날 때까지 같은 방향으로 이동 (범위 끝이면 멈춤)
      let next = event.target.dataset.date;
      for (let i = 0; i < 31; i++) {
        next = addDays(next, moves[event.key]);
        if (next < opts.min || next > opts.max) return;
        if (isSelectable(next)) break;
      }
      state.focusDate = next;
      if (monthIndex(next) !== state.viewMonth) {
        state.viewMonth = monthIndex(next);
        renderMonth();
      }
      focusDay(next);
    }

    return {
      /** 값을 바꾼다 (onChange는 부르지 않음) */
      setValue(date) {
        state.value = date;
        renderValue();
        if (state.open) renderMonth();
      },
      getValue() {
        return state.value;
      },
      /** 고를 수 있는 범위를 바꾼다 */
      setRange(min, max) {
        opts.min = min;
        opts.max = max;
        if (state.open) renderMonth();
      },
      /** isDisabled 결과가 바뀌었을 때 다시 그린다 */
      refresh() {
        renderValue();
        if (state.open) renderMonth();
      },
    };
  }

  function weekendClass(weekday) {
    if (weekday === 0) return ' is-sunday';
    if (weekday === 6) return ' is-saturday';
    return '';
  }

  global.datePicker = { create };
})(window);
