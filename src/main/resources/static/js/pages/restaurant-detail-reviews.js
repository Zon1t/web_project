/*
 * restaurant-detail-reviews.js — S-05 식당 상세의 리뷰 영역 (B)
 *
 * restaurant-detail.js가 식당 정보를 그린 뒤 이렇게 붙인다.
 *   restaurantReviews.mount(sectionElement, restaurantId, { onChange: 식당정보다시불러오기 });
 *
 * 하는 일
 *   1. 리뷰 작성: 한줄평(필수, 100자) + 태그(선택, 여러 개). 등록 후 목록 1페이지를 다시 불러온다.
 *   2. 리뷰 목록: 최신순 10개씩, [더보기]로 다음 페이지를 아래에 이어 붙인다.
 *   3. 리뷰 삭제: 내 리뷰에만 [삭제]. 확인 창 → 삭제 → 목록 1페이지 다시 불러오기.
 *   작성·삭제가 성공하면 onChange()를 불러 위쪽 리뷰 수·태그 TOP 3도 갱신한다.
 *
 * 사진(F-09): 작성 폼에서 1장 선택(jpg·png 5MB, 고를 때 바로 검사) + 미리보기, 목록은 썸네일 → 누르면 원본
 */
(function (global) {
  'use strict';

  const PAGE_SIZE = 10;
  const esc = utils.escapeHtml;

  function mount(section, restaurantId, options) {
    const onChange = (options && options.onChange) || function () {};

    // 목록 상태
    const state = {
      items: [],
      page: 0,          // 마지막으로 불러온 페이지
      hasNext: false,
      totalCount: 0,
      loading: false,
      submitting: false,
      requestSeq: 0,    // 1페이지를 다시 불러오는 중에 [더보기] 응답이 섞이지 않게
    };

    section.innerHTML =
      '<h2 id="review-title" class="section-title">리뷰<span id="review-count" class="detail-reviews__count"></span></h2>' +
      renderForm() +
      '<div id="review-list-area" aria-live="polite"></div>';

    const els = {
      count: section.querySelector('#review-count'),
      form: section.querySelector('#review-form'),
      content: section.querySelector('#review-content'),
      length: section.querySelector('#review-length'),
      formMessage: section.querySelector('#review-form-message'),
      submit: section.querySelector('#review-submit'),
      listArea: section.querySelector('#review-list-area'),
      image: section.querySelector('#review-image'),
      photoName: section.querySelector('#review-photo-name'),
      photoPreview: section.querySelector('#review-photo-preview'),
      photoRemove: section.querySelector('#review-photo-remove'),
    };

    // 고른 사진 (없으면 null)과 미리보기용 임시 주소
    let selectedImage = null;
    let previewUrl = null;

    bindEvents();
    loadFirstPage();

    /* ---------- 이벤트 ---------- */

    function bindEvents() {
      els.content.addEventListener('input', () => {
        els.length.textContent = els.content.value.length + ' / ' + reviewApi.CONTENT_MAX;
        if (els.content.value.trim()) setFormMessage('');
      });

      els.form.addEventListener('submit', (event) => {
        event.preventDefault();
        submitReview();
      });

      // 사진 고르기 / 빼기
      els.image.addEventListener('change', () => selectImage(els.image.files[0] || null));
      els.photoRemove.addEventListener('click', () => {
        selectImage(null);
        els.image.focus();
      });

      els.listArea.addEventListener('click', (event) => {
        const button = event.target.closest('[data-action]');
        if (!button) return;
        if (button.dataset.action === 'more') loadNextPage();
        if (button.dataset.action === 'retry-reviews') loadFirstPage();
        if (button.dataset.action === 'delete-review') deleteReview(button.dataset.reviewId, button);
      });
    }

    /* ---------- 사진 ---------- */

    function selectImage(file) {
      if (file) {
        const error = reviewApi.checkImage(file);
        if (error) {
          setFormMessage(error);
          file = null; // 고른 파일은 버리고 이전 상태(사진 없음)로
        } else {
          setFormMessage('');
        }
      }

      selectedImage = file;
      if (previewUrl) URL.revokeObjectURL(previewUrl); // 이전 미리보기 주소 정리
      previewUrl = file ? URL.createObjectURL(file) : null;
      if (!file) els.image.value = ''; // 같은 파일을 다시 골라도 change가 일어나게

      els.photoName.textContent = file ? file.name : '선택된 파일 없음 (선택, jpg·png 5MB)';
      els.photoPreview.hidden = !file;
      els.photoRemove.hidden = !file;
      if (file) els.photoPreview.src = previewUrl;
      else els.photoPreview.removeAttribute('src');
    }

    /* ---------- 작성 ---------- */

    async function submitReview() {
      if (state.submitting) return;

      const content = els.content.value.trim();
      if (!content) {
        setFormMessage('한줄평을 입력해 주세요.');
        els.content.focus();
        return;
      }
      const tagIds = Array.from(els.form.querySelectorAll('input[name="tagIds"]:checked'))
        .map((input) => Number(input.value));

      setSubmitting(true);
      setFormMessage('');
      try {
        await reviewApi.create(restaurantId, { content, tagIds, image: selectedImage });
        els.form.reset();
        selectImage(null);
        els.length.textContent = '0 / ' + reviewApi.CONTENT_MAX;
        await loadFirstPage();
        onChange();
      } catch (err) {
        if (err.statusCode === 401) return; // client.js가 로그인 화면으로 이동시킨다
        setFormMessage(err.message);
      } finally {
        setSubmitting(false);
      }
    }

    function setSubmitting(submitting) {
      state.submitting = submitting;
      els.submit.disabled = submitting;
      els.submit.textContent = submitting ? '등록 중…' : '등록';
    }

    function setFormMessage(message) {
      els.formMessage.textContent = message;
      els.content.classList.toggle('input--error', Boolean(message));
    }

    /* ---------- 목록 ---------- */

    async function loadFirstPage() {
      const seq = ++state.requestSeq;
      state.loading = true;
      if (state.items.length === 0) showLoading(); // 이미 목록이 있으면 깜빡이지 않게 그대로 둔다

      try {
        const result = await reviewApi.getList(restaurantId, { page: 1, size: PAGE_SIZE });
        if (seq !== state.requestSeq) return;
        state.items = result.items;
        applyPage(result);
        renderList();
      } catch (err) {
        if (seq !== state.requestSeq || err.statusCode === 401) return;
        showListError(err.message);
      } finally {
        if (seq === state.requestSeq) state.loading = false;
      }
    }

    async function loadNextPage() {
      if (state.loading || !state.hasNext) return;
      const seq = ++state.requestSeq;
      state.loading = true;
      renderMoreButton(true);

      try {
        const result = await reviewApi.getList(restaurantId, { page: state.page + 1, size: PAGE_SIZE });
        if (seq !== state.requestSeq) return;
        // 그사이 새 리뷰가 생기면 페이지가 밀려 같은 리뷰가 또 올 수 있다 → 이미 있는 리뷰는 빼고 붙인다
        const shown = new Set(state.items.map((item) => item.reviewId));
        state.items = state.items.concat(result.items.filter((item) => !shown.has(item.reviewId)));
        applyPage(result);
        renderList();
      } catch (err) {
        if (seq !== state.requestSeq || err.statusCode === 401) return;
        renderMoreButton(false, err.message);
      } finally {
        if (seq === state.requestSeq) state.loading = false;
      }
    }

    function applyPage(result) {
      state.page = result.page;
      state.hasNext = result.hasNext;
      state.totalCount = result.totalCount;
      els.count.textContent = result.totalCount;
    }

    /* ---------- 삭제 ---------- */

    async function deleteReview(reviewId, button) {
      const ok = await global.ui.confirm({ title: '리뷰를 삭제할까요?', message: '삭제하면 되돌릴 수 없어요.', confirmText: '삭제', danger: true });
      if (!ok) return;

      button.disabled = true;
      try {
        await reviewApi.delete(reviewId);
        await loadFirstPage();
        onChange();
        global.ui.toast('리뷰를 삭제했어요.');
      } catch (err) {
        if (err.statusCode === 401) return;
        if (err.error === 'REVIEW_NOT_FOUND') {
          // 이미 삭제된 리뷰: 목록만 새로 맞춘다
          await loadFirstPage();
          onChange();
          return;
        }
        button.disabled = false;
        global.ui.toast(err.message, { type: 'error' });
      }
    }

    /* ---------- 그리기 ---------- */

    function renderList() {
      if (state.items.length === 0) {
        els.listArea.innerHTML =
          '<div class="state state--empty">' +
          '<p class="state__title">첫 리뷰를 남겨 주세요</p>' +
          '<p class="state__desc">한줄평과 태그로 이 식당을 알려 주세요.</p>' +
          '</div>';
        return;
      }

      els.listArea.innerHTML =
        '<ul class="review-list">' + state.items.map(renderItem).join('') + '</ul>' +
        '<div id="review-more" class="review-more"></div>';
      renderMoreButton(false);
    }

    function renderMoreButton(loading, errorMessage) {
      const more = section.querySelector('#review-more');
      if (!more) return;
      if (!state.hasNext) {
        more.innerHTML = '';
        return;
      }
      more.innerHTML =
        (errorMessage ? '<p class="field__message field__message--error" role="alert">' + esc(errorMessage) + '</p>' : '') +
        '<button type="button" class="btn" data-action="more"' + (loading ? ' disabled' : '') + '>' +
        (loading ? '불러오는 중…' : '리뷰 더보기') + '</button>';
    }

    function renderItem(review) {
      const tags = review.tags.length
        ? '<ul class="review-item__tags">' +
          review.tags.map((name) => '<li class="detail-tag">#' + esc(name) + '</li>').join('') +
          '</ul>'
        : '';
      // 사진 썸네일. 누르면 원본을 새 탭으로 연다. (목업은 blob: 임시 주소)
      const hasImage = typeof review.imageUrl === 'string' &&
        (review.imageUrl.startsWith('/uploads/') || review.imageUrl.startsWith('blob:'));
      const image = hasImage
        ? '<a class="review-item__image-link" href="' + esc(review.imageUrl) + '" target="_blank" rel="noopener">' +
          '<img class="review-item__image" src="' + esc(review.imageUrl) + '" alt="' + esc(review.nickname) + '님의 리뷰 사진 (새 탭에서 크게 보기)" ' +
          'width="56" height="56" loading="lazy"></a>'
        : '';
      const deleteButton = review.mine
        ? '<button type="button" class="btn review-item__delete" data-action="delete-review" ' +
          'data-review-id="' + esc(review.reviewId) + '">삭제</button>'
        : '';

      return (
        '<li class="review-item">' +
        image +
        '<div class="review-item__body">' +
        '<p class="review-item__meta"><strong>' + esc(review.nickname) + '</strong>' +
        '<time datetime="' + esc(review.createdAt) + '">' + esc(formatDate(review.createdAt)) + '</time>' +
        (review.mine ? '<span class="badge">내 리뷰</span>' : '') +
        '</p>' +
        '<p class="review-item__content">' + esc(review.content) + '</p>' +
        tags +
        '</div>' +
        deleteButton +
        '</li>'
      );
    }

    function showLoading() {
      els.listArea.innerHTML = global.ui.skeleton('row', 3);
    }

    function showListError(message) {
      els.listArea.innerHTML =
        '<div class="state state--error">' +
        '<p class="state__title">리뷰를 불러오지 못했어요</p>' +
        '<p class="state__desc">' + esc(message) + '</p>' +
        '<button type="button" class="btn" data-action="retry-reviews">다시 시도</button>' +
        '</div>';
    }
  }

  // 작성 폼 (태그 13개는 reviewApi.TAG_GROUPS 상수로 그린다)
  function renderForm() {
    const groups = reviewApi.TAG_GROUPS.map((group) =>
      '<div class="review-tags__group">' +
      '<span class="review-tags__label">' + group.label + '</span>' +
      '<span class="review-tags__options">' +
      group.tags.map((tag) =>
        '<label class="tag-check">' +
        '<input type="checkbox" name="tagIds" value="' + tag.tagId + '">' +
        '<span>#' + tag.name + '</span>' +
        '</label>').join('') +
      '</span>' +
      '</div>').join('');

    return (
      '<form id="review-form" class="review-form" novalidate>' +
      '<div class="review-form__fields">' +
      '<div class="field">' +
      '<label class="field__label" for="review-content">한줄평 <span class="review-form__required" aria-hidden="true">*</span>' +
      ' <span class="text-muted">(최대 ' + reviewApi.CONTENT_MAX + '자)</span></label>' +
      '<textarea id="review-content" class="textarea review-form__content" maxlength="' + reviewApi.CONTENT_MAX + '" ' +
      'rows="2" required aria-describedby="review-form-message" placeholder="이 식당에 대한 한줄평을 남겨 주세요"></textarea>' +
      '</div>' +
      '<fieldset class="review-tags">' +
      '<legend class="review-tags__legend">태그 <span class="text-muted">(선택, 분류와 관계없이 여러 개 선택 가능)</span></legend>' +
      groups +
      '</fieldset>' +
      // 사진 (선택): 파일 입력은 눈에 안 보이게 두고 [사진 선택] 라벨로 연다
      '<div class="review-photo">' +
      '<input id="review-image" class="sr-only" type="file" accept="image/jpeg,image/png" aria-describedby="review-photo-name">' +
      '<label for="review-image" class="btn review-photo__pick">사진 선택</label>' +
      '<img id="review-photo-preview" class="review-photo__preview" alt="선택한 사진 미리보기" hidden>' +
      '<span id="review-photo-name" class="text-muted review-photo__name">선택된 파일 없음 (선택, jpg·png 5MB)</span>' +
      '<button id="review-photo-remove" type="button" class="btn btn--text review-photo__remove" hidden>사진 빼기</button>' +
      '</div>' +
      '<div class="review-form__footer">' +
      '<p id="review-form-message" class="field__message field__message--error" role="alert"></p>' +
      '<span id="review-length" class="text-muted">0 / ' + reviewApi.CONTENT_MAX + '</span>' +
      '</div>' +
      '</div>' +
      '<button id="review-submit" type="submit" class="btn btn--primary review-form__submit">등록</button>' +
      '</form>'
    );
  }

  // '2026-11-16T12:40:00' → '2026-11-16'
  function formatDate(dateTime) {
    return String(dateTime || '').slice(0, 10);
  }

  global.restaurantReviews = { mount };
})(window);
