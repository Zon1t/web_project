package com.sseukbap.review.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 리뷰 태그 (TAG 테이블). 기준: docs/sql/01_ddl.sql, 03_init_data.sql
 *
 * - TAG_ID 1~13 고정값. 03_init_data.sql로만 넣고 코드에서 만들지 않는다. (시퀀스 없음, 생성 메서드 없음)
 * - 이름에는 '#'이 없다. 화면에서 붙인다.
 */
@Entity
@Table(name = "TAG")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Tag {

    @Id
    @Column(name = "TAG_ID")
    private Long tagId;

    @Column(name = "TAG_NAME", nullable = false, length = 30)
    private String tagName;

    @Enumerated(EnumType.STRING)
    @Column(name = "TAG_CATEGORY", nullable = false, length = 20)
    private TagCategory tagCategory;
}
