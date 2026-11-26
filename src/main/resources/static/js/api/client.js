/*
 * client.js — 서버 API 호출 공통 함수 (B 담당, 수정은 B에게 요청)
 *
 * 화면 JS에서 fetch를 직접 쓰지 않는다. 도메인별 api 파일(js/api/도메인.js)만 이 파일을 쓴다.
 *
 * HTML에서 불러오는 순서 (일반 <script>, type="module" 아님):
 *   <script src="/js/api/client.js"></script>
 *   <script src="/js/api/mock/restaurant.mock.js"></script>  ← 목업이 필요할 때만
 *   <script src="/js/api/restaurant.js"></script>
 *   <script src="/js/pages/restaurant-list.js"></script>
 *
 * 하는 일
 *   1. 공통 응답 {statusCode, timestamp, path, message, data, error}에서 data만 돌려준다.
 *   2. 오류면 ApiError를 던진다. 화면에는 err.message(서버가 보낸 한글 문구)를 보여 주면 된다.
 *   3. 401이면 현재 주소를 redirect 값으로 붙여 로그인 화면으로 이동한다.
 *      단, 홈(/, /index.html)이면 처음 온 사람일 수 있어서 서비스 소개 랜딩(/landing.html)으로 보낸다. (2026-11-24)
 *      초대 링크 같은 다른 화면은 그대로 로그인 → 원래 화면으로 돌아간다.
 *   4. USE_MOCK이 true이고 등록된 목업이 있으면 서버 대신 목업 데이터를 돌려준다.
 *
 * 사용 예 (도메인 api 파일 안에서)
 *   api.get('/restaurants/list', { keyword: '국밥', category: 'KOREAN' })
 *   api.post('/restaurants/register', { mapUrl, name, category })
 *   api.postForm('/restaurants/12/reviews', formData)   // multipart (리뷰 작성)
 *   api.patch('/parties/31/status', { action: 'CLOSE' })
 *   api.delete('/reviews/7')
 */
(function (global) {
  'use strict';

  /* ---------- 설정 ---------- */

  // 백엔드 완성(2026-11-25)으로 false. 화면만 따로 확인할 때만 true로 바꾸고 HTML의 목업 스크립트 주석을 푼다.
  // true여도 목업이 없는 API는 실제 서버를 호출한다.
  const USE_MOCK = false;

  // 목업 응답을 늦게 돌려주는 시간(ms). 로딩 화면을 확인하기 위함.
  const MOCK_DELAY = 300;

  const BASE_URL = '/api';

  // TODO(C와 확인): 로그인 화면 파일 이름
  const LOGIN_PAGE = '/login.html';

  // 로그인 안 한 채 홈에 오면 로그인 대신 보여 줄 서비스 소개 화면 (2026-11-24)
  const LANDING_PAGE = '/landing.html';
  const HOME_PATHS = ['/', '/index.html'];

  const NETWORK_ERROR_MESSAGE = '서버와 연결할 수 없어요. 잠시 후 다시 시도해 주세요.';
  const UNKNOWN_ERROR_MESSAGE = '요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요.';

  /*
   * 서버 문구(합쇼체)를 화면 말투(해요체)로 바꾼다. 서버 코드는 그대로 두고 화면에서만 바꾼다.
   *   '아이디 또는 비밀번호가 일치하지 않습니다.' → '아이디 또는 비밀번호가 일치하지 않아요.'
   * 자주 나오는 끝맺음만 바꾸고, 표에 없는 문장은 그대로 둔다.
   */
  const POLITE_ENDINGS = [
    ['없습니다', '없어요'], ['있습니다', '있어요'], ['않습니다', '않아요'],
    ['했습니다', '했어요'], ['였습니다', '였어요'], ['었습니다', '었어요'], ['았습니다', '았어요'],
    ['됐습니다', '됐어요'], ['됩니다', '돼요'], ['합니다', '해요'],
    ['주십시오', '주세요'], ['하십시오', '하세요'],
  ];

  function toFriendly(message) {
    if (!message) return message;
    let text = String(message);
    POLITE_ENDINGS.forEach(([from, to]) => {
      text = text.split(from).join(to);
    });
    // '…입니다' → 받침이 있으면 '이에요', 없으면 '예요' (예: '필수 항목입니다' → '필수 항목이에요')
    text = text.replace(/([가-힣])입니다/g, (all, prev) => {
      const hasFinal = (prev.charCodeAt(0) - 0xac00) % 28 !== 0;
      return prev + (hasFinal ? '이에요' : '예요');
    });
    return text;
  }

  /* ---------- 오류 객체 ---------- */

  // 화면에서는 이렇게 쓴다.
  //   try { ... } catch (err) {
  //     if (err.error === 'ALREADY_INTERESTED') { ... }   // 오류 코드로 분기
  //     showError(err.message);                           // 서버 문구 그대로 표시
  //   }
  class ApiError extends Error {
    constructor(statusCode, error, message) {
      super(toFriendly(message) || UNKNOWN_ERROR_MESSAGE);
      this.name = 'ApiError';
      this.statusCode = statusCode; // HTTP 상태 (네트워크 오류면 0)
      this.error = error;           // 오류 코드 (예: 'RESTAURANT_NOT_FOUND')
    }
  }

  /* ---------- 목업 ---------- */

  const mockRoutes = [];

  // 목업 등록. path의 {이름} 부분은 아무 값이나 받아서 params로 넘겨준다.
  //   api.mock('GET', '/restaurants/{restaurantId}', ({ params, query, body }) => { ... return data; });
  // handler는 응답의 data 부분만 돌려준다. 오류를 흉내 내려면 api.mockError(...)를 throw 한다.
  function mock(method, path, handler) {
    const names = [];
    const pattern = path.replace(/\{(\w+)\}/g, (_, name) => {
      names.push(name);
      return '([^/]+)';
    });
    mockRoutes.push({
      method: method.toUpperCase(),
      regex: new RegExp('^' + pattern + '$'),
      names,
      handler,
    });
  }

  function mockError(statusCode, error, message) {
    return new ApiError(statusCode, error, message);
  }

  function findMock(method, path) {
    for (const route of mockRoutes) {
      if (route.method !== method) continue;
      const match = route.regex.exec(path);
      if (!match) continue;
      const params = {};
      route.names.forEach((name, i) => {
        params[name] = decodeURIComponent(match[i + 1]);
      });
      return { route, params };
    }
    return null;
  }

  function formDataToObject(formData) {
    const result = {};
    formData.forEach((value, key) => {
      if (key in result) {
        result[key] = [].concat(result[key], value); // tagIds처럼 같은 이름이 여러 개인 경우
      } else {
        result[key] = value;
      }
    });
    return result;
  }

  async function callMock(found, method, path, query, body) {
    await new Promise((resolve) => setTimeout(resolve, MOCK_DELAY));
    const input = {
      params: found.params,
      query: query || {},
      body: body instanceof FormData ? formDataToObject(body) : body,
    };
    try {
      // JSON으로 한 번 바꿨다가 되돌려서, 화면에서 값을 바꿔도 목업 원본이 바뀌지 않게 한다.
      const data = await found.route.handler(input);
      return data === undefined ? null : JSON.parse(JSON.stringify(data));
    } catch (err) {
      if (err instanceof ApiError) {
        console.info('[mock]', method, path, '→', err.statusCode, err.error);
        if (err.statusCode === 401) redirectToLogin();
      }
      throw err;
    }
  }

  /* ---------- 공통 처리 ---------- */

  function buildUrl(path, query) {
    const url = BASE_URL + path;
    if (!query) return url;

    const search = new URLSearchParams();
    Object.keys(query).forEach((key) => {
      const value = query[key];
      if (value === undefined || value === null || value === '') return; // 빈 값은 보내지 않음
      if (Array.isArray(value)) {
        value.forEach((v) => search.append(key, v));
      } else {
        search.append(key, value);
      }
    });
    const text = search.toString();
    return text ? url + '?' + text : url;
  }

  function redirectToLogin() {
    if (global.location.pathname === LOGIN_PAGE) return; // 로그인 화면에서는 이동하지 않음
    if (HOME_PATHS.includes(global.location.pathname)) {
      // replace: 뒤로 가기를 눌러도 홈 → 랜딩으로 다시 튕기지 않게
      global.location.replace(LANDING_PAGE);
      return;
    }
    const current = global.location.pathname + global.location.search;
    global.location.href = LOGIN_PAGE + '?redirect=' + encodeURIComponent(current);
  }

  async function readJson(response) {
    try {
      return await response.json();
    } catch (e) {
      return null; // 응답 본문이 JSON이 아닌 경우 (서버가 꺼져 있거나 프록시 오류)
    }
  }

  // 모든 요청이 지나가는 함수
  async function request(method, path, options) {
    const query = options && options.query;
    const body = options && options.body;

    if (USE_MOCK) {
      const found = findMock(method, path);
      if (found) return callMock(found, method, path, query, body);
    }

    const init = {
      method,
      credentials: 'same-origin', // 세션 쿠키(JSESSIONID) 포함
      headers: { Accept: 'application/json' },
    };
    if (body instanceof FormData) {
      init.body = body; // Content-Type은 브라우저가 multipart 경계값과 함께 자동으로 붙인다
    } else if (body !== undefined) {
      init.headers['Content-Type'] = 'application/json';
      init.body = JSON.stringify(body);
    }

    let response;
    try {
      response = await fetch(buildUrl(path, query), init);
    } catch (e) {
      throw new ApiError(0, 'NETWORK_ERROR', NETWORK_ERROR_MESSAGE);
    }

    const json = await readJson(response);

    if (response.status === 401) {
      redirectToLogin();
    }

    if (!response.ok || (json && json.error)) {
      throw new ApiError(
        response.status,
        json && json.error ? json.error : 'INTERNAL_SERVER_ERROR',
        json && json.message ? json.message : UNKNOWN_ERROR_MESSAGE
      );
    }

    return json ? json.data : null;
  }

  /* ---------- 밖에서 쓰는 함수 ---------- */

  global.ApiError = ApiError;
  global.api = {
    get: (path, query) => request('GET', path, { query }),
    post: (path, body) => request('POST', path, { body }),
    postForm: (path, formData) => request('POST', path, { body: formData }),
    patch: (path, body) => request('PATCH', path, { body }),
    delete: (path, query) => request('DELETE', path, { query }),
    mock,
    mockError,
    USE_MOCK,
  };
})(window);
