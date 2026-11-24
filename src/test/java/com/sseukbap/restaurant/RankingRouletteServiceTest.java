package com.sseukbap.restaurant;

import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import com.sseukbap.restaurant.dto.RankingItemResponse;
import com.sseukbap.restaurant.dto.RankingResponse;
import com.sseukbap.restaurant.dto.RouletteCandidateResponse;
import com.sseukbap.restaurant.dto.RouletteCandidatesResponse;
import com.sseukbap.restaurant.dto.RouletteSpinRequest;
import com.sseukbap.restaurant.dto.RouletteSpinResponse;
import com.sseukbap.restaurant.entity.Restaurant;
import com.sseukbap.restaurant.entity.RestaurantCategory;
import com.sseukbap.restaurant.repository.RestaurantRepository;
import com.sseukbap.restaurant.service.RankingService;
import com.sseukbap.restaurant.service.RouletteService;
import com.sseukbap.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 랭킹·룰렛 확인 (C 담당, C4 2026-11-24).
 * DB에 어떤 식당이 있든 통과하도록, 규칙(제외 카테고리·정렬·순위 번호·검증)만 본다.
 * 카페 룰렛처럼 데이터가 필요한 경우는 테스트 안에서 만든다. (@Transactional로 되돌림)
 */
@SpringBootTest
@Transactional
class RankingRouletteServiceTest {

    @Autowired RankingService rankingService;
    @Autowired RouletteService rouletteService;
    @Autowired RestaurantRepository restaurantRepository;
    @Autowired TestData data;

    // ===== 랭킹 =====

    @Test
    void 랭킹은_최근_30일_기간과_1부터_이어지는_순위를_준다() {
        RankingResponse result = rankingService.getRankings(null);

        assertThat(result.to()).isEqualTo(LocalDate.now());
        assertThat(result.from()).isEqualTo(LocalDate.now().minusDays(29));
        assertThat(result.rankings()).hasSizeLessThanOrEqualTo(10);
        for (int i = 0; i < result.rankings().size(); i++) {
            assertThat(result.rankings().get(i).rank()).isEqualTo(i + 1);
        }
    }

    @Test
    void 랭킹은_따봉_내림차순_같으면_리뷰_수_내림차순() {
        List<RankingItemResponse> rankings = rankingService.getRankings("10").rankings();

        assertThat(rankings).isSortedAccordingTo(Comparator
                .comparingLong(RankingItemResponse::thumbsCount30d).reversed()
                .thenComparing(Comparator.comparingLong(RankingItemResponse::reviewCount).reversed()));
    }

    @Test
    void 랭킹에는_구내식당과_카페가_나오지_않는다() {
        // 카페·구내식당이 DB에 확실히 있도록 하나씩 만들어 둔다 (따봉 0건 식당도 랭킹 대상이라 빠지는지 확인할 수 있음)
        data.restaurant("랭킹카페" + TestData.uniqueNumber(), RestaurantCategory.CAFE);
        data.restaurant("랭킹구내" + TestData.uniqueNumber(), RestaurantCategory.CAFETERIA);

        assertThat(rankingService.getRankings("10").rankings())
                .extracting(RankingItemResponse::category)
                .doesNotContain("CAFETERIA", "CAFE");
    }

    @Test
    void 랭킹_limit이_1에서_10이_아니면_400() {
        for (String limit : new String[]{"0", "11", "abc", "-1"}) {
            assertError(() -> rankingService.getRankings(limit), ErrorCode.INVALID_REQUEST);
        }
        assertThat(rankingService.getRankings("3").rankings()).hasSizeLessThanOrEqualTo(3);
    }

    // ===== 룰렛 후보 =====

    @Test
    void 룰렛_전체에는_구내식당과_카페가_없다() {
        data.restaurant("룰렛카페" + TestData.uniqueNumber(), RestaurantCategory.CAFE);

        RouletteCandidatesResponse result = rouletteService.getCandidates(null);

        assertThat(result.category()).isNull();
        assertThat(result.candidates()).hasSizeLessThanOrEqualTo(8);
        assertThat(categoriesOf(result.candidates()))
                .doesNotContain(RestaurantCategory.CAFETERIA, RestaurantCategory.CAFE);
    }

    @Test
    void 룰렛_카페를_고르면_카페만_나온다() {
        data.restaurant("룰렛카페A" + TestData.uniqueNumber(), RestaurantCategory.CAFE);
        data.restaurant("룰렛카페B" + TestData.uniqueNumber(), RestaurantCategory.CAFE);

        RouletteCandidatesResponse result = rouletteService.getCandidates("CAFE");

        assertThat(result.category()).isEqualTo("CAFE");
        assertThat(result.spinnable()).isTrue();
        assertThat(categoriesOf(result.candidates())).containsOnly(RestaurantCategory.CAFE);
    }

    @Test
    void 룰렛_카테고리가_구내식당이거나_없는_값이면_400() {
        assertError(() -> rouletteService.getCandidates("CAFETERIA"), ErrorCode.INVALID_REQUEST);
        assertError(() -> rouletteService.getCandidates("PIZZA"), ErrorCode.INVALID_REQUEST);
    }

    // ===== 룰렛 실행 =====

    @Test
    void 룰렛을_돌리면_후보_중_하나가_당첨된다() {
        Restaurant a = data.restaurant("당첨A" + TestData.uniqueNumber(), RestaurantCategory.KOREAN);
        Restaurant b = data.restaurant("당첨B" + TestData.uniqueNumber(), RestaurantCategory.CAFETERIA);   // 내 룰렛은 구내식당도 가능

        RouletteSpinResponse result = rouletteService.spin(
                new RouletteSpinRequest(List.of(a.getRestaurantId(), b.getRestaurantId())));

        assertThat(result.winnerId()).isIn(a.getRestaurantId(), b.getRestaurantId());
        assertThat(result.winner().restaurantId()).isEqualTo(result.winnerId());
    }

    @Test
    void 룰렛_후보가_2개_미만이거나_중복이면_400() {
        Restaurant a = data.restaurant("검증A" + TestData.uniqueNumber(), RestaurantCategory.KOREAN);

        assertError(() -> rouletteService.spin(new RouletteSpinRequest(List.of(a.getRestaurantId()))), ErrorCode.INVALID_REQUEST);
        assertError(() -> rouletteService.spin(new RouletteSpinRequest(
                List.of(a.getRestaurantId(), a.getRestaurantId()))), ErrorCode.INVALID_REQUEST);
        assertError(() -> rouletteService.spin(new RouletteSpinRequest(null)), ErrorCode.INVALID_REQUEST);
    }

    @Test
    void 룰렛_없는_식당을_빼면_2곳_미만이면_NOT_ENOUGH_CANDIDATES() {
        Restaurant a = data.restaurant("부족A" + TestData.uniqueNumber(), RestaurantCategory.KOREAN);

        assertError(() -> rouletteService.spin(new RouletteSpinRequest(List.of(a.getRestaurantId(), -1L))),
                ErrorCode.NOT_ENOUGH_CANDIDATES);
    }

    // ===== 도우미 =====

    private List<RestaurantCategory> categoriesOf(List<RouletteCandidateResponse> candidates) {
        List<Long> ids = candidates.stream().map(RouletteCandidateResponse::restaurantId).toList();
        return restaurantRepository.findAllById(ids).stream().map(Restaurant::getCategory).toList();
    }

    private static void assertError(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(errorCode);
    }
}
