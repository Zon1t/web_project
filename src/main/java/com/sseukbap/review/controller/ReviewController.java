package com.sseukbap.review.controller;

import com.sseukbap.common.interceptor.LoginMember;
import com.sseukbap.common.response.ApiResponse;
import com.sseukbap.review.dto.ReviewCreateResponse;
import com.sseukbap.review.dto.ReviewListResponse;
import com.sseukbap.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 리뷰 API (B 담당). 로그인 필요
 *
 * 값 검증은 API별 오류 문구를 맞추기 위해 Service에서 한다. (required = false로 받는 이유)
 */
@RestController
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    /** 리뷰 목록 (페이징) */
    @GetMapping("/api/restaurants/{restaurantId}/reviews")
    public ApiResponse<ReviewListResponse> list(@PathVariable Long restaurantId,
                                                @RequestParam(required = false) Integer page,
                                                @RequestParam(required = false) Integer size,
                                                @LoginMember Long memberId) {
        return ApiResponse.success(reviewService.getReviewList(restaurantId, page, size, memberId));
    }

    /**
     * 리뷰 작성 (multipart/form-data)
     * content: 한줄평, tagIds: 태그 (여러 개면 같은 이름으로 반복, 예: tagIds=1&tagIds=4),
     * image: 사진 1장 (선택, jpg·png 5MB 이하)
     */
    @PostMapping("/api/restaurants/{restaurantId}/reviews")
    public ApiResponse<ReviewCreateResponse> create(@PathVariable Long restaurantId,
                                                    @RequestParam(required = false) String content,
                                                    @RequestParam(required = false) List<String> tagIds,
                                                    @RequestParam(required = false) MultipartFile image,
                                                    @LoginMember Long memberId) {
        return ApiResponse.success(reviewService.createReview(restaurantId, memberId, content, tagIds, image));
    }

    /** 리뷰 삭제 (작성자만) */
    @DeleteMapping("/api/reviews/{reviewId}")
    public ApiResponse<Void> delete(@PathVariable Long reviewId,
                                    @LoginMember Long memberId) {
        reviewService.deleteReview(reviewId, memberId);
        return ApiResponse.success();
    }
}
