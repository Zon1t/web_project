package com.sseukbap.admin;

import com.sseukbap.admin.dto.AdminCafeteriaMenuSaveRequest;
import com.sseukbap.admin.dto.AdminRestaurantResponse;
import com.sseukbap.admin.dto.AdminRestaurantUpdateRequest;
import com.sseukbap.admin.dto.AdminReviewUpdateRequest;
import com.sseukbap.admin.dto.AdminSuggestionResponse;
import com.sseukbap.admin.dto.AdminSuggestionUpdateRequest;
import com.sseukbap.admin.service.AdminService;
import com.sseukbap.common.enums.MealType;
import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import com.sseukbap.common.interceptor.AdminCheckInterceptor;
import com.sseukbap.common.interceptor.SessionConst;
import com.sseukbap.member.entity.Member;
import com.sseukbap.member.service.MyPageService;
import com.sseukbap.restaurant.dto.RestaurantAutocompleteResponse;
import com.sseukbap.restaurant.dto.RestaurantListResponse;
import com.sseukbap.restaurant.dto.RouletteSpinRequest;
import com.sseukbap.restaurant.dto.TopTagResponse;
import com.sseukbap.restaurant.entity.Restaurant;
import com.sseukbap.restaurant.entity.RestaurantCategory;
import com.sseukbap.restaurant.service.RankingService;
import com.sseukbap.restaurant.service.RestaurantService;
import com.sseukbap.restaurant.service.RouletteService;
import com.sseukbap.restaurant.service.SuggestionService;
import com.sseukbap.review.entity.Review;
import com.sseukbap.review.service.ReviewService;
import com.sseukbap.support.TestData;
import jakarta.persistence.EntityManager;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 관리자 기능 + 숨김 반영 확인 (A3, 2026-11-24). 실제 DB, 끝나면 롤백.
 * 회원·식당·리뷰는 TestData로 테스트 안에서 만든다. (DB의 더미·시연 데이터에 기대지 않음)
 */
@SpringBootTest
@Transactional
class AdminServiceTest {

    @Autowired AdminService adminService;
    @Autowired AdminCheckInterceptor adminCheckInterceptor;
    @Autowired RestaurantService restaurantService;
    @Autowired RankingService rankingService;
    @Autowired RouletteService rouletteService;
    @Autowired ReviewService reviewService;
    @Autowired SuggestionService suggestionService;
    @Autowired MyPageService myPageService;
    @Autowired TestData data;
    @Autowired EntityManager em;

    private String prefix;
    private Member member;

    @BeforeEach
    void setUp() {
        prefix = "숨김" + TestData.uniqueNumber();   // 다른 식당과 겹치지 않는 이름
        member = data.member();
    }

    // ===== 권한 =====

    @Test
    void 관리자만_통과하고_일반_회원은_403_로그인_안_하면_401() {
        Member admin = data.member();
        em.createNativeQuery("UPDATE MEMBER SET ROLE = 'ADMIN' WHERE MEMBER_ID = :id")
                .setParameter("id", admin.getMemberId())
                .executeUpdate();
        em.clear();

        assertThat(adminCheckInterceptor.preHandle(requestOf(admin.getMemberId()), new MockHttpServletResponse(), null))
                .isTrue();
        assertErrorCode(() -> adminCheckInterceptor.preHandle(requestOf(member.getMemberId()),
                new MockHttpServletResponse(), null), ErrorCode.FORBIDDEN);
        assertErrorCode(() -> adminCheckInterceptor.preHandle(new MockHttpServletRequest(),
                new MockHttpServletResponse(), null), ErrorCode.UNAUTHORIZED);
    }

    // ===== 식당 숨김 =====

    @Test
    void 숨긴_식당은_목록_자동완성_룰렛에서_빠지고_상세_리뷰_관심은_404() {
        Restaurant hidden = data.restaurant(prefix + "가", RestaurantCategory.KOREAN);
        Restaurant visible = data.restaurant(prefix + "나", RestaurantCategory.KOREAN);
        long koreanBefore = rouletteService.getCandidates("KOREAN").totalCount();

        AdminRestaurantResponse updated =
                adminService.updateRestaurant(hidden.getRestaurantId(), new AdminRestaurantUpdateRequest(true, null));
        em.flush();
        em.clear();

        assertThat(updated.hidden()).isTrue();
        // 목록·자동완성
        assertThat(restaurantService.getRestaurantList(prefix, null))
                .extracting(RestaurantListResponse::restaurantId)
                .containsExactly(visible.getRestaurantId());
        assertThat(restaurantService.getAutocompleteList(prefix))
                .extracting(RestaurantAutocompleteResponse::restaurantId)
                .containsExactly(visible.getRestaurantId());
        // 랭킹
        assertThat(rankingService.getRankings("10").rankings())
                .noneMatch(item -> item.restaurantId().equals(hidden.getRestaurantId()));
        // 룰렛: 후보 수가 하나 줄고, 숨긴 식당을 넣은 spin은 후보 부족
        assertThat(rouletteService.getCandidates("KOREAN").totalCount()).isEqualTo(koreanBefore - 1);
        assertErrorCode(() -> rouletteService.spin(new RouletteSpinRequest(
                List.of(hidden.getRestaurantId(), visible.getRestaurantId()))), ErrorCode.NOT_ENOUGH_CANDIDATES);
        // 상세·리뷰·수정 제안은 없는 식당처럼 404
        Long hiddenId = hidden.getRestaurantId();
        assertErrorCode(() -> restaurantService.getRestaurant(hiddenId, member.getMemberId()), ErrorCode.RESTAURANT_NOT_FOUND);
        assertErrorCode(() -> reviewService.getReviewList(hiddenId, 1, 10, member.getMemberId()), ErrorCode.RESTAURANT_NOT_FOUND);
        assertErrorCode(() -> suggestionService.createSuggestion(hiddenId, member.getMemberId(), "내용"), ErrorCode.RESTAURANT_NOT_FOUND);
        // 관리자 목록에는 남아 있다
        assertThat(adminService.getRestaurantList(null).items())
                .filteredOn(item -> item.restaurantId().equals(hiddenId))
                .singleElement()
                .satisfies(item -> assertThat(item.hidden()).isTrue());

        // 숨김 해제하면 다시 보인다
        adminService.updateRestaurant(hiddenId, new AdminRestaurantUpdateRequest(false, null));
        em.flush();
        em.clear();
        assertThat(restaurantService.getRestaurantList(prefix, null)).hasSize(2);
    }

    @Test
    void 등록한_식당이_숨겨지면_마이페이지에서도_빠진다() {
        Restaurant mine = data.restaurant(prefix, RestaurantCategory.KOREAN);
        Long ownerId = mine.getMember().getMemberId();
        assertThat(myPageService.getRestaurants(ownerId)).hasSize(1);

        adminService.updateRestaurant(mine.getRestaurantId(), new AdminRestaurantUpdateRequest(true, null));
        em.flush();
        em.clear();

        assertThat(myPageService.getRestaurants(ownerId)).isEmpty();
        assertThat(myPageService.getSummary(ownerId).restaurantCount()).isZero();
    }

    @Test
    void 카테고리를_바꿀_수_있고_구내식당_관련_변경과_빈_요청은_400() {
        Restaurant r = data.restaurant(prefix, RestaurantCategory.KOREAN);

        assertThat(adminService.updateRestaurant(r.getRestaurantId(), new AdminRestaurantUpdateRequest(null, "CAFE"))
                .category()).isEqualTo(RestaurantCategory.CAFE);

        Long id = r.getRestaurantId();
        assertErrorCode(() -> adminService.updateRestaurant(id, new AdminRestaurantUpdateRequest(null, "CAFETERIA")), ErrorCode.INVALID_REQUEST);
        assertErrorCode(() -> adminService.updateRestaurant(id, new AdminRestaurantUpdateRequest(null, "PIZZA")), ErrorCode.INVALID_REQUEST);
        assertErrorCode(() -> adminService.updateRestaurant(id, new AdminRestaurantUpdateRequest(null, null)), ErrorCode.INVALID_REQUEST);
        assertErrorCode(() -> adminService.updateRestaurant(-1L, new AdminRestaurantUpdateRequest(true, null)), ErrorCode.RESTAURANT_NOT_FOUND);
    }

    // ===== 리뷰 숨김 =====

    @Test
    void 숨긴_리뷰는_목록_개수_태그_TOP3_내_리뷰에서_빠진다() {
        Restaurant r = data.restaurant(prefix, RestaurantCategory.KOREAN);
        data.review(r, member, 1L);
        Review bad = data.review(r, member, 9L, 10L);

        adminService.updateReview(bad.getReviewId(), new AdminReviewUpdateRequest(true));
        em.flush();
        em.clear();

        var detail = restaurantService.getRestaurant(r.getRestaurantId(), member.getMemberId());
        assertThat(detail.reviewCount()).isEqualTo(1);
        assertThat(detail.topTags()).extracting(TopTagResponse::tagId).containsExactly(1L);
        assertThat(reviewService.getReviewList(r.getRestaurantId(), 1, 10, member.getMemberId()).totalCount()).isEqualTo(1);
        assertThat(restaurantService.getRestaurantList(prefix, null).get(0).reviewCount()).isEqualTo(1);
        assertThat(myPageService.getReviews(member.getMemberId())).hasSize(1);
        // 관리자 목록에는 숨긴 리뷰도 보인다
        assertThat(adminService.getReviewList(prefix)).hasSize(2);

        assertErrorCode(() -> adminService.updateReview(-1L, new AdminReviewUpdateRequest(true)), ErrorCode.REVIEW_NOT_FOUND);
        assertErrorCode(() -> adminService.updateReview(bad.getReviewId(), new AdminReviewUpdateRequest(null)), ErrorCode.INVALID_REQUEST);
    }

    // ===== 수정 제안 =====

    @Test
    void 수정_제안을_처리하면_OPEN_목록에서_DONE_목록으로_옮겨진다() {
        Restaurant r = data.restaurant(prefix, RestaurantCategory.KOREAN);
        Long suggestionId = suggestionService.createSuggestion(r.getRestaurantId(), member.getMemberId(), "영업시간이 바뀌었어요")
                .suggestionId();

        assertThat(adminService.getSuggestionList(null)).extracting(AdminSuggestionResponse::suggestionId).contains(suggestionId);

        adminService.updateSuggestion(suggestionId, new AdminSuggestionUpdateRequest("DONE"));
        em.flush();
        em.clear();

        assertThat(adminService.getSuggestionList("OPEN")).extracting(AdminSuggestionResponse::suggestionId).doesNotContain(suggestionId);
        assertThat(adminService.getSuggestionList("DONE")).extracting(AdminSuggestionResponse::suggestionId).contains(suggestionId);
        assertErrorCode(() -> adminService.updateSuggestion(suggestionId, new AdminSuggestionUpdateRequest("WAIT")), ErrorCode.INVALID_REQUEST);
        assertErrorCode(() -> adminService.getSuggestionList("WAIT"), ErrorCode.INVALID_REQUEST);
        assertErrorCode(() -> adminService.updateSuggestion(-1L, new AdminSuggestionUpdateRequest("DONE")), ErrorCode.NOT_FOUND);
    }

    // ===== 구내식당 메뉴 =====

    @Test
    void 구내식당_메뉴는_날짜_끼니별로_교체된다() {
        LocalDate date = LocalDate.now().plusDays(40);   // 크롤링 데이터와 겹치지 않는 날짜

        adminService.saveCafeteriaMenus(new AdminCafeteriaMenuSaveRequest(date, MealType.LUNCH,
                List.of(" 현미밥 ", "김치찌개", "김치찌개", "  ")));
        adminService.saveCafeteriaMenus(new AdminCafeteriaMenuSaveRequest(date, MealType.DINNER, List.of("잡곡밥")));
        assertThat(adminService.getCafeteriaMenus(date.toString()).lunch()).containsExactly("현미밥", "김치찌개");

        // 다시 저장하면 그 끼니만 교체
        adminService.saveCafeteriaMenus(new AdminCafeteriaMenuSaveRequest(date, MealType.LUNCH, List.of("비빔밥")));
        var menus = adminService.getCafeteriaMenus(date.toString());
        assertThat(menus.lunch()).containsExactly("비빔밥");
        assertThat(menus.dinner()).containsExactly("잡곡밥");

        assertErrorCode(() -> adminService.saveCafeteriaMenus(new AdminCafeteriaMenuSaveRequest(date, MealType.LUNCH,
                List.of("가".repeat(51)))), ErrorCode.INVALID_REQUEST);
        assertErrorCode(() -> adminService.saveCafeteriaMenus(new AdminCafeteriaMenuSaveRequest(null, MealType.LUNCH,
                List.of("밥"))), ErrorCode.INVALID_REQUEST);
        assertErrorCode(() -> adminService.getCafeteriaMenus("2026/11/26"), ErrorCode.INVALID_REQUEST);
    }

    // ===== 도우미 =====

    private static MockHttpServletRequest requestOf(Long memberId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true).setAttribute(SessionConst.LOGIN_MEMBER_ID, memberId);
        return request;
    }

    private static void assertErrorCode(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(expected);
    }
}
