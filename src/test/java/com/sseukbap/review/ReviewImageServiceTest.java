package com.sseukbap.review;

import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import com.sseukbap.member.entity.Member;
import com.sseukbap.member.repository.MemberRepository;
import com.sseukbap.restaurant.entity.Restaurant;
import com.sseukbap.restaurant.repository.RestaurantRepository;
import com.sseukbap.review.dto.ReviewItemResponse;
import com.sseukbap.review.repository.ReviewRepository;
import com.sseukbap.review.service.ReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 리뷰 작성·삭제와 사진 파일.
 * 사진은 프로젝트의 uploads/가 아니라 build/test-uploads/에 저장한다. (gradle clean 하면 지워짐)
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = "upload.dir=build/test-uploads")
class ReviewImageServiceTest {

    private static final Path REVIEW_DIR = Path.of("build/test-uploads/reviews");
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'};

    @Autowired ReviewService reviewService;
    @Autowired ReviewRepository reviewRepository;
    @Autowired RestaurantRepository restaurantRepository;
    @Autowired MemberRepository memberRepository;

    Long hongId;
    Long user03;

    @BeforeEach
    void setUp() {
        // DB에 있는 식당 아무 곳 (숨김·구내식당 제외). 이름으로 찾으면 데이터에 따라 없을 수 있다 (2026-11-25)
        // 테스트 안에서 새로 만들지 않는 이유: 삭제 테스트가 작성을 실제로 커밋해서 새 식당이 DB에 남는다
        hongId = restaurantRepository.findAll().stream()
                .filter(r -> !r.isHidden() && !r.isCafeteria())
                .findFirst()
                .map(Restaurant::getRestaurantId)
                .orElseThrow();
        user03 = memberRepository.findByLoginId("user03").map(Member::getMemberId).orElseThrow();
    }

    @Test
    void 사진과_함께_쓰면_파일이_저장되고_목록에_imageUrl이_나온다() {
        Long reviewId = reviewService.createReview(hongId, user03, "사진 리뷰", List.of("1"), jpeg()).reviewId();

        ReviewItemResponse item = reviewService.getReviewList(hongId, 1, 10, user03).items().stream()
                .filter(i -> i.reviewId().equals(reviewId))
                .findFirst()
                .orElseThrow();
        assertThat(item.imageUrl()).matches("/uploads/reviews/[0-9a-f]{32}\\.jpg");
        assertThat(file(item.imageUrl())).exists();
    }

    @Test
    void 잘못된_사진이면_INVALID_IMAGE이고_리뷰도_저장되지_않는다() {
        long before = reviewRepository.countByRestaurant_RestaurantId(hongId);
        MockMultipartFile fake = new MockMultipartFile("image", "a.jpg", "image/jpeg", "not image".getBytes());

        assertThatThrownBy(() -> reviewService.createReview(hongId, user03, "사진 리뷰", null, fake))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_IMAGE);
        assertThat(reviewRepository.countByRestaurant_RestaurantId(hongId)).isEqualTo(before);
    }

    @Test
    void 빈_파일은_사진_없이_저장한다() {
        MockMultipartFile empty = new MockMultipartFile("image", "", "application/octet-stream", new byte[0]);

        Long reviewId = reviewService.createReview(hongId, user03, "사진 없음", null, empty).reviewId();

        assertThat(reviewRepository.findById(reviewId).orElseThrow().getImagePath()).isNull();
    }

    @Test
    void 작성이_롤백되면_저장한_사진_파일도_지운다() {
        Long reviewId = reviewService.createReview(hongId, user03, "롤백될 리뷰", null, jpeg()).reviewId();
        Path saved = file(reviewRepository.findById(reviewId).orElseThrow().getImagePath());
        assertThat(saved).exists();

        TestTransaction.end(); // 테스트 트랜잭션 롤백 → 파일 삭제

        assertThat(saved).doesNotExist();
    }

    @Test
    void 리뷰를_삭제하면_커밋된_뒤_사진_파일도_지운다() {
        // 1) 리뷰 작성을 실제로 커밋
        TestTransaction.flagForCommit();
        Long reviewId = reviewService.createReview(hongId, user03, "삭제할 사진 리뷰", null, jpeg()).reviewId();
        TestTransaction.end();
        Path saved = file(reviewRepository.findById(reviewId).orElseThrow().getImagePath());
        assertThat(saved).exists();

        // 2) 삭제도 커밋 → DB와 파일 모두 사라져 테스트 데이터가 남지 않는다
        TestTransaction.start();
        TestTransaction.flagForCommit();
        reviewService.deleteReview(reviewId, user03);
        assertThat(saved).exists(); // 커밋 전에는 아직 남아 있다
        TestTransaction.end();

        assertThat(saved).doesNotExist();
        assertThat(reviewRepository.findById(reviewId)).isEmpty();
    }

    private static MockMultipartFile jpeg() {
        return new MockMultipartFile("image", "photo.jpg", "image/jpeg", JPEG);
    }

    private static Path file(String imageUrl) {
        return REVIEW_DIR.resolve(imageUrl.substring("/uploads/reviews/".length()));
    }
}
