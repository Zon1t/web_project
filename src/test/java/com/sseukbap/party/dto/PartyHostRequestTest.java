package com.sseukbap.party.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 방장 기능 요청값 검증 (스프링·DB 없이 실행)
 * 여기서 걸리는 값은 Controller의 @Valid에서 400 INVALID_REQUEST가 된다.
 */
class PartyHostRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    // ===== 일정 =====

    @Test
    void 일정_정상_요청() {
        assertThat(validator.validate(new PartyScheduleUpdateRequest(LocalTime.of(12, 0), "1층 로비", null))).isEmpty();
        assertThat(validator.validate(new PartyScheduleUpdateRequest(LocalTime.of(12, 0), "가".repeat(30), "나".repeat(50)))).isEmpty();
    }

    @Test
    void 일정_필수값_길이_위반() {
        assertThat(validator.validate(new PartyScheduleUpdateRequest(null, "1층 로비", null))).isNotEmpty();
        assertThat(validator.validate(new PartyScheduleUpdateRequest(LocalTime.of(12, 0), "   ", null))).isNotEmpty();
        assertThat(validator.validate(new PartyScheduleUpdateRequest(LocalTime.of(12, 0), "가".repeat(31), null))).isNotEmpty();
        assertThat(validator.validate(new PartyScheduleUpdateRequest(LocalTime.of(12, 0), "정문", "나".repeat(51)))).isNotEmpty();
    }

    @Test
    void 일정_공백_정리() {
        PartyScheduleUpdateRequest request = new PartyScheduleUpdateRequest(LocalTime.of(12, 0), "  정문  ", "  ");

        assertThat(request.meetPlace()).isEqualTo("정문");
        assertThat(request.confirmMemo()).isNull();
    }

    // ===== 모집 마감·재개 =====

    @Test
    void action은_CLOSE_REOPEN만() {
        assertThat(validator.validate(new PartyStatusUpdateRequest("CLOSE"))).isEmpty();
        assertThat(validator.validate(new PartyStatusUpdateRequest("REOPEN"))).isEmpty();
        assertThat(new PartyStatusUpdateRequest("CLOSE").isClose()).isTrue();
        assertThat(new PartyStatusUpdateRequest("REOPEN").isClose()).isFalse();
    }

    @Test
    void action이_없거나_잘못되면_잘못된_요청입니다() {
        for (String action : new String[]{null, "", "OPEN", "close"}) {
            Set<ConstraintViolation<PartyStatusUpdateRequest>> violations =
                    validator.validate(new PartyStatusUpdateRequest(action));
            assertThat(violations).isNotEmpty();
            assertThat(violations).extracting(ConstraintViolation::getMessage).containsOnly("잘못된 요청입니다.");
        }
    }
}
