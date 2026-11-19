package com.sseukbap.restaurant.service;

import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 네이버 지도 URL에서 장소 ID를 찾아 표준 URL로 바꾼다.
 *
 * 받는 URL
 *   - map.naver.com, m.map.naver.com: 경로에 /place/{숫자}가 있어야 한다.
 *       예) https://map.naver.com/p/search/대쿠이/place/1132965955?c=...
 *   - naver.me 단축 링크: 연결된 주소(Location 헤더)만 따라가서 그 주소에서 장소 ID를 찾는다. 페이지 내용은 읽지 않는다.
 * 표준 URL: https://map.naver.com/p/entry/place/{장소ID}  (DB MAP_URL에 저장, 중복 판단 기준)
 */
@Slf4j
@Component
public class MapUrlParser {

    public static final String STANDARD_URL_PREFIX = "https://map.naver.com/p/entry/place/";

    private static final Set<String> MAP_HOSTS = Set.of("map.naver.com", "m.map.naver.com");
    private static final String SHORT_LINK_HOST = "naver.me";
    // 단축 링크가 연결되는 장소 페이지 (m.place.naver.com/restaurant/123 등)
    private static final Set<String> PLACE_HOSTS = Set.of("place.naver.com", "m.place.naver.com", "pcmap.place.naver.com");

    private static final Pattern MAP_PLACE_ID = Pattern.compile("/place/(\\d{1,20})(?:/|$)");
    private static final Pattern PLACE_PAGE_ID = Pattern.compile("^/(?:place|restaurant|cafe)/(\\d{1,20})(?:/|$)");
    private static final int MAX_REDIRECTS = 3;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER) // 연결된 주소만 확인하고 직접 따라가지 않는다
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    /** 장소 ID를 찾으면 표준 URL, 네이버 지도 링크가 아니거나 장소 ID가 없으면 빈 값 */
    public Optional<String> toStandardUrl(String url) {
        return extractPlaceId(url).map(placeId -> STANDARD_URL_PREFIX + placeId);
    }

    /** 장소 ID 찾기 */
    public Optional<String> extractPlaceId(String url) {
        Optional<URI> uri = parse(url);
        if (uri.isEmpty()) {
            return Optional.empty();
        }
        String host = uri.get().getHost().toLowerCase(Locale.ROOT);
        if (MAP_HOSTS.contains(host)) {
            return findMapPlaceId(uri.get());
        }
        if (SHORT_LINK_HOST.equals(host)) {
            return resolveShortLink(uri.get());
        }
        return Optional.empty();
    }

    // ===== 내부 =====

    /** http(s) URL로 읽을 수 있으면 URI, 아니면 빈 값 */
    private Optional<URI> parse(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        try {
            URI uri = new URI(url.trim());
            String scheme = uri.getScheme();
            if (scheme == null || uri.getHost() == null
                    || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                return Optional.empty();
            }
            return Optional.of(uri);
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }

    private Optional<String> findMapPlaceId(URI uri) {
        return find(MAP_PLACE_ID, uri.getRawPath());
    }

    /** naver.me → (최대 3번) 연결된 주소를 따라가 지도·장소 페이지 주소에서 ID를 찾는다 */
    private Optional<String> resolveShortLink(URI shortLink) {
        URI current = shortLink;
        for (int i = 0; i < MAX_REDIRECTS; i++) {
            Optional<URI> next = fetchLocation(current);
            if (next.isEmpty()) {
                return Optional.empty();
            }
            current = next.get();
            String host = current.getHost() == null ? "" : current.getHost().toLowerCase(Locale.ROOT);
            if (MAP_HOSTS.contains(host)) {
                return findMapPlaceId(current);
            }
            if (PLACE_HOSTS.contains(host)) {
                return find(PLACE_PAGE_ID, current.getRawPath());
            }
            if (!SHORT_LINK_HOST.equals(host)) {
                return Optional.empty(); // 네이버 지도가 아닌 곳으로 연결되는 링크
            }
        }
        return Optional.empty();
    }

    /** 요청을 보내 Location 헤더만 읽는다. 연결 실패는 502 MAP_API_ERROR */
    private Optional<URI> fetchLocation(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();
        try {
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() / 100 != 3) {
                return Optional.empty(); // 없는 단축 링크 등
            }
            return response.headers().firstValue("Location")
                    .flatMap(location -> parse(uri.resolve(location).toString()));
        } catch (IOException e) {
            log.warn("단축 링크 확인 실패: {} ({})", uri, e.getMessage());
            throw new BusinessException(ErrorCode.MAP_API_ERROR);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.MAP_API_ERROR);
        }
    }

    private Optional<String> find(Pattern pattern, String path) {
        if (path == null) {
            return Optional.empty();
        }
        Matcher matcher = pattern.matcher(path);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }
}
