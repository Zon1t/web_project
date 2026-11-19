package com.sseukbap.review.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 리뷰-태그 연결 (REVIEW_TAG 테이블). 기준: docs/sql/01_ddl.sql
 *
 * - 리뷰 1건에 같은 태그는 한 번만 저장된다. (UK_REVIEW_TAG: REVIEW_ID + TAG_ID)
 * - 리뷰가 삭제되면 DB가 CASCADE로 함께 삭제한다.
 */
@Entity
@Table(name = "REVIEW_TAG")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewTag {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_REVIEW_TAG_GENERATOR")
    @SequenceGenerator(name = "SEQ_REVIEW_TAG_GENERATOR", sequenceName = "SEQ_REVIEW_TAG", allocationSize = 1)
    @Column(name = "REVIEW_TAG_ID")
    private Long reviewTagId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "REVIEW_ID", nullable = false)
    private Review review;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "TAG_ID", nullable = false)
    private Tag tag;

    private ReviewTag(Review review, Tag tag) {
        this.review = review;
        this.tag = tag;
    }

    /** 리뷰에 태그 연결 */
    public static ReviewTag create(Review review, Tag tag) {
        return new ReviewTag(review, tag);
    }
}
