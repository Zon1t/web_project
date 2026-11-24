package com.sseukbap.admin.service;

import com.sseukbap.admin.dto.AdminCafeteriaMenuSaveRequest;
import com.sseukbap.admin.dto.AdminCafeteriaMenuSaveResponse;
import com.sseukbap.admin.dto.AdminCafeteriaMenusResponse;
import com.sseukbap.admin.dto.AdminRestaurantListResponse;
import com.sseukbap.admin.dto.AdminRestaurantResponse;
import com.sseukbap.admin.dto.AdminRestaurantUpdateRequest;
import com.sseukbap.admin.dto.AdminReviewResponse;
import com.sseukbap.admin.dto.AdminReviewUpdateRequest;
import com.sseukbap.admin.dto.AdminSuggestionResponse;
import com.sseukbap.admin.dto.AdminSuggestionUpdateRequest;
import com.sseukbap.admin.repository.AdminQueryRepository;
import com.sseukbap.cafeteria.entity.CafeteriaMenu;
import com.sseukbap.cafeteria.repository.CafeteriaMenuRepository;
import com.sseukbap.common.enums.MealType;
import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import com.sseukbap.restaurant.entity.Restaurant;
import com.sseukbap.restaurant.entity.RestaurantCategory;
import com.sseukbap.restaurant.entity.Suggestion;
import com.sseukbap.restaurant.entity.SuggestionStatus;
import com.sseukbap.restaurant.repository.RestaurantRepository;
import com.sseukbap.restaurant.service.GeoDistance;
import com.sseukbap.review.entity.Review;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 관리자 기능 (2026-11-24 A3). 기준: docs/API_SPEC.md 7장
 * 관리자인지는 AdminCheckInterceptor가 먼저 확인하므로 여기서는 다시 보지 않는다.
 *
 * - 식당: 전체 목록(거리·숨김), 숨김·카테고리 변경
 * - 수정 제안: 목록, 처리 상태 변경
 * - 리뷰: 최근 목록(숨긴 리뷰 포함), 숨김
 * - 구내식당 메뉴: 날짜별 조회, 날짜·끼니별 교체 저장
 */
@Service
@Transactional(readOnly = true)
public class AdminService {

    static final int REVIEW_LIMIT = 100;
    static final int MENU_MAX_COUNT = 15;

    private final AdminQueryRepository adminQueryRepository;
    private final RestaurantRepository restaurantRepository;
    private final CafeteriaMenuRepository cafeteriaMenuRepository;
    private final double radiusKm;

    public AdminService(AdminQueryRepository adminQueryRepository,
                        RestaurantRepository restaurantRepository,
                        CafeteriaMenuRepository cafeteriaMenuRepository,
                        @Value("${restaurant.register-radius-km:4}") double radiusKm) {
        this.adminQueryRepository = adminQueryRepository;
        this.restaurantRepository = restaurantRepository;
        this.cafeteriaMenuRepository = cafeteriaMenuRepository;
        this.radiusKm = radiusKm;
    }

    // ===== 식당 =====

    /** 전체 식당 (숨긴 식당 포함). outOfRange = true면 반경 밖 식당만 */
    public AdminRestaurantListResponse getRestaurantList(Boolean outOfRange) {
        Optional<double[]> office = officeLocation();
        Map<Long, Long> reviewCounts = new HashMap<>();
        for (Object[] row : adminQueryRepository.countVisibleReviewsByRestaurant()) {
            reviewCounts.put((Long) row[0], (Long) row[1]);
        }

        List<AdminRestaurantResponse> items = adminQueryRepository.findAllRestaurants().stream()
                .map(r -> AdminRestaurantResponse.of(r, distanceOf(r, office), radiusKm,
                        reviewCounts.getOrDefault(r.getRestaurantId(), 0L)))
                .filter(item -> !Boolean.TRUE.equals(outOfRange) || item.outOfRange())
                .toList();
        return new AdminRestaurantListResponse(radiusKm, items);
    }

    /** 식당 숨김·카테고리 변경. 보낸 값만 바꾼다 */
    @Transactional
    public AdminRestaurantResponse updateRestaurant(Long restaurantId, AdminRestaurantUpdateRequest request) {
        Restaurant restaurant = adminQueryRepository.findRestaurant(restaurantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));

        if (request == null || (request.hidden() == null && request.category() == null)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "바꿀 값을 확인해 주세요.");
        }

        if (request.category() != null) {
            RestaurantCategory category = parseCategory(request.category());
            // 구내식당으로 바꾸거나, 구내식당을 다른 카테고리로 바꾸는 것은 막는다 (구내식당은 초기 데이터 1곳)
            if (category == RestaurantCategory.CAFETERIA || restaurant.isCafeteria()) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "바꿀 값을 확인해 주세요.");
            }
            restaurant.changeCategory(category);
        }
        if (request.hidden() != null) {
            restaurant.changeHidden(request.hidden());
        }

        return AdminRestaurantResponse.of(restaurant, distanceOf(restaurant, officeLocation()), radiusKm,
                adminQueryRepository.countVisibleReviews(restaurantId));
    }

    // ===== 수정 제안 =====

    /** 수정 제안 목록. status: OPEN(기본) / DONE / ALL */
    public List<AdminSuggestionResponse> getSuggestionList(String status) {
        Set<SuggestionStatus> statuses;
        if (status == null || status.isBlank() || "OPEN".equals(status)) {
            statuses = EnumSet.of(SuggestionStatus.OPEN);
        } else if ("DONE".equals(status)) {
            statuses = EnumSet.of(SuggestionStatus.DONE);
        } else if ("ALL".equals(status)) {
            statuses = EnumSet.allOf(SuggestionStatus.class);
        } else {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "조회 조건을 확인해 주세요.");
        }
        return adminQueryRepository.findSuggestions(statuses).stream()
                .map(AdminSuggestionResponse::from)
                .toList();
    }

    /** 수정 제안 처리 상태 변경 (DONE / OPEN) */
    @Transactional
    public AdminSuggestionResponse updateSuggestion(Long suggestionId, AdminSuggestionUpdateRequest request) {
        Suggestion suggestion = adminQueryRepository.findSuggestion(suggestionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        SuggestionStatus status;
        try {
            status = SuggestionStatus.valueOf(request == null || request.status() == null ? "" : request.status());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "처리 상태를 확인해 주세요.");
        }
        suggestion.changeStatus(status);
        return AdminSuggestionResponse.from(suggestion);
    }

    // ===== 리뷰 =====

    /** 최근 리뷰 최대 100건 (숨긴 리뷰 포함). keyword: 내용·식당 이름·닉네임 부분 일치 */
    public List<AdminReviewResponse> getReviewList(String keyword) {
        String trimmed = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        return adminQueryRepository.findRecentReviews(trimmed, PageRequest.of(0, REVIEW_LIMIT)).stream()
                .map(AdminReviewResponse::from)
                .toList();
    }

    /** 리뷰 숨김·숨김 해제 */
    @Transactional
    public AdminReviewResponse updateReview(Long reviewId, AdminReviewUpdateRequest request) {
        Review review = adminQueryRepository.findReview(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));
        if (request == null || request.hidden() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        review.changeHidden(request.hidden());
        return AdminReviewResponse.from(review);
    }

    // ===== 구내식당 메뉴 =====

    /** 날짜의 점심·저녁 메뉴. date: YYYY-MM-DD */
    public AdminCafeteriaMenusResponse getCafeteriaMenus(String date) {
        LocalDate menuDate = parseDate(date);
        List<String> lunch = new ArrayList<>();
        List<String> dinner = new ArrayList<>();
        for (CafeteriaMenu menu : adminQueryRepository.findMenus(menuDate)) {
            (menu.getMealType() == MealType.LUNCH ? lunch : dinner).add(menu.getMenuName());
        }
        return new AdminCafeteriaMenusResponse(menuDate, lunch, dinner);
    }

    /**
     * 날짜·끼니의 메뉴를 보낸 목록으로 바꾼다. 앞뒤 공백 제거, 빈 줄·중복은 하나로.
     * 빈 목록이면 그 끼니 메뉴를 지운다. (메인 화면은 다음 조회 때 다시 크롤링을 시도한다)
     */
    @Transactional
    public AdminCafeteriaMenuSaveResponse saveCafeteriaMenus(AdminCafeteriaMenuSaveRequest request) {
        if (request == null || request.menuDate() == null || request.mealType() == null || request.menuNames() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "메뉴를 확인해 주세요.");
        }
        Set<String> names = new LinkedHashSet<>();
        for (String raw : request.menuNames()) {
            String name = raw == null ? "" : raw.trim();
            if (name.isEmpty()) {
                continue;
            }
            if (name.length() > CafeteriaMenu.MENU_NAME_MAX) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "메뉴를 확인해 주세요.");
            }
            names.add(name);
        }
        if (names.size() > MENU_MAX_COUNT) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "메뉴를 확인해 주세요.");
        }

        adminQueryRepository.deleteMenus(request.menuDate(), request.mealType());
        for (String name : names) {
            cafeteriaMenuRepository.save(CafeteriaMenu.create(request.menuDate(), request.mealType(), name));
        }
        return new AdminCafeteriaMenuSaveResponse(request.menuDate(), request.mealType(), List.copyOf(names));
    }

    // ===== 도우미 =====

    /** 회사 위치 = 구내식당 좌표 (식당 등록 반경 검사와 같은 기준). 없으면 거리를 계산하지 않는다 */
    private Optional<double[]> officeLocation() {
        return restaurantRepository.findFirstByCategoryOrderByRestaurantIdAsc(RestaurantCategory.CAFETERIA)
                .filter(r -> r.getLat() != null && r.getLng() != null)
                .map(r -> new double[]{r.getLat(), r.getLng()});
    }

    private static Double distanceOf(Restaurant r, Optional<double[]> office) {
        if (r.isCafeteria() || r.getLat() == null || r.getLng() == null || office.isEmpty()) {
            return r.isCafeteria() ? 0.0 : null;
        }
        double[] o = office.get();
        return GeoDistance.round2(GeoDistance.km(o[0], o[1], r.getLat(), r.getLng()));
    }

    private static RestaurantCategory parseCategory(String value) {
        try {
            return RestaurantCategory.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "바꿀 값을 확인해 주세요.");
        }
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value == null ? "" : value);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "날짜를 확인해 주세요.");
        }
    }
}
