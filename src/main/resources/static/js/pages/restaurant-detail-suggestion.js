/*
 * restaurant-detail-suggestion.js — S-05 식당 상세의 '식당 정보 수정 제안' (B)
 *
 * restaurant-detail.js가 상세 맨 아래에 링크를 그린 뒤 이렇게 연결한다.
 *   restaurantSuggestion.open(restaurantId);   // 링크를 누르면 입력 창 열기
 *
 * 입력 창: restaurant-detail.html의 <dialog id="suggestion-dialog">
 *   제출 성공 → 창을 닫고 '제안이 접수되었습니다' 안내 / 실패 → 창 안에 오류 문구
 *   제안 처리 방식은 미정이라 서버는 저장만 한다.
 */
(function (global) {
  'use strict';

  const CONTENT_MAX = 500;
  const TOAST_MILLIS = 3000;

  const els = {
    dialog: document.getElementById('suggestion-dialog'),
    form: document.getElementById('suggestion-form'),
    content: document.getElementById('suggestion-content'),
    length: document.getElementById('suggestion-length'),
    message: document.getElementById('suggestion-message'),
    submit: document.getElementById('suggestion-submit'),
    toast: document.getElementById('toast'),
  };

  let currentRestaurantId = null;
  let submitting = false;
  let toastTimer = null;

  els.content.addEventListener('input', () => {
    els.length.textContent = els.content.value.length + ' / ' + CONTENT_MAX;
    if (els.content.value.trim()) setMessage('');
  });

  els.form.addEventListener('submit', (event) => {
    event.preventDefault(); // method="dialog"라도 직접 처리 (성공했을 때만 닫는다)
    submit();
  });

  els.dialog.addEventListener('click', (event) => {
    if (event.target.closest('[data-action="close-suggestion"]')) close();
    if (event.target === els.dialog) close(); // 창 바깥(어두운 배경) 클릭
  });

  function open(restaurantId) {
    currentRestaurantId = restaurantId;
    els.form.reset();
    els.length.textContent = '0 / ' + CONTENT_MAX;
    setMessage('');
    els.dialog.showModal(); // Esc로 닫히고, 열려 있는 동안 뒤 화면은 누를 수 없다
    els.content.focus();
  }

  function close() {
    if (submitting) return;
    els.dialog.close();
  }

  async function submit() {
    if (submitting) return;
    const content = els.content.value.trim();
    if (!content) {
      setMessage('제안 내용을 입력해 주세요.');
      els.content.focus();
      return;
    }

    submitting = true;
    els.submit.disabled = true;
    els.submit.textContent = '제출 중…';
    setMessage('');
    try {
      await restaurantApi.createSuggestion(currentRestaurantId, content);
      submitting = false;
      close();
      showToast('제안이 접수되었습니다. 고마워요!');
    } catch (err) {
      if (err.statusCode === 401) return; // client.js가 로그인 화면으로 이동시킨다
      setMessage(err.message);
    } finally {
      submitting = false;
      els.submit.disabled = false;
      els.submit.textContent = '제출';
    }
  }

  function setMessage(message) {
    els.message.textContent = message;
    els.content.classList.toggle('input--error', Boolean(message));
  }

  function showToast(message) {
    clearTimeout(toastTimer);
    els.toast.textContent = message;
    els.toast.classList.add('is-visible');
    toastTimer = setTimeout(() => {
      els.toast.classList.remove('is-visible');
      els.toast.textContent = '';
    }, TOAST_MILLIS);
  }

  global.restaurantSuggestion = { open };
})(window);
