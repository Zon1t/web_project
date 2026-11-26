/*
 * config.example.js — 화면 설정 견본 (Git에 올리는 파일)
 *
 * 사용법: 이 파일을 같은 폴더에 config.js로 복사하고 값을 채운다.
 *   config.js는 .gitignore에 있어서 Git에 올라가지 않는다. (application-local.yml과 같은 방식)
 *
 * naverMapKeyId: 네이버 클라우드 플랫폼 콘솔 > Maps > Application의 Client ID (Dynamic Map)
 *   - 'Web 서비스 URL'에 http://localhost 가 등록되어 있어야 지도가 뜬다.
 *   - Client Secret은 필요 없다.
 */
window.APP_CONFIG = {
  naverMapKeyId: '',
};
