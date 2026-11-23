package com.sseukbap.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 오류 코드 목록. 기준: docs/API_SPEC.md "부록. 전체 오류 코드"
 *
 * - status : HTTP 상태
 * - code   : 응답의 error 값 (예: "PARTY_FULL")
 * - message: 대표 문구. API마다 문구가 다르면 아래처럼 직접 넘긴다.
 *     throw new BusinessException(ErrorCode.NOT_HOST, "방장만 삭제할 수 있습니다.");
 *
 * 새 코드가 필요하면 직접 고치지 말고 C에게 요청한다. (common은 C 담당)
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ===== 400 =====
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "입력값을 확인해 주세요."),
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "PASSWORD_MISMATCH", "비밀번호가 일치하지 않습니다."),
    INVALID_MAP_URL(HttpStatus.BAD_REQUEST, "INVALID_MAP_URL", "네이버 지도 URL만 입력할 수 있습니다."),
    INVALID_TAG(HttpStatus.BAD_REQUEST, "INVALID_TAG", "선택할 수 없는 태그입니다."),
    INVALID_IMAGE(HttpStatus.BAD_REQUEST, "INVALID_IMAGE", "jpg·png 형식, 5MB 이하 사진만 올릴 수 있습니다."),
    INVALID_MEAL_TIME(HttpStatus.BAD_REQUEST, "INVALID_MEAL_TIME", "선택할 수 없는 식사 시간입니다."),

    // ===== 401 =====
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요합니다."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "LOGIN_FAILED", "아이디 또는 비밀번호가 일치하지 않습니다."),

    // ===== 403 =====
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "권한이 없습니다."),
    NOT_HOST(HttpStatus.FORBIDDEN, "NOT_HOST", "방장만 할 수 있습니다."),
    /** 대화 조회·전송, 초대 링크용 (403). 나가기·확정에는 NOT_JOINED(409)를 쓴다. 응답 error 값은 둘 다 "NOT_JOINED" */
    NOT_JOINED_FORBIDDEN(HttpStatus.FORBIDDEN, "NOT_JOINED", "참여자만 대화를 볼 수 있습니다."),
    NOT_CONFIRMED_MEMBER(HttpStatus.FORBIDDEN, "NOT_CONFIRMED_MEMBER", "참여를 확정한 식사에만 따봉을 남길 수 있습니다."),

    // ===== 404 =====
    NOT_FOUND(HttpStatus.NOT_FOUND, "NOT_FOUND", "대상을 찾을 수 없습니다."),
    RESTAURANT_NOT_FOUND(HttpStatus.NOT_FOUND, "RESTAURANT_NOT_FOUND", "식당을 찾을 수 없습니다."),
    MAP_PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "MAP_PLACE_NOT_FOUND", "존재하지 않는 주소입니다."),
    INTEREST_NOT_FOUND(HttpStatus.NOT_FOUND, "INTEREST_NOT_FOUND", "관심 등록되지 않은 식당입니다."),
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND", "리뷰를 찾을 수 없습니다."),
    PARTY_NOT_FOUND(HttpStatus.NOT_FOUND, "PARTY_NOT_FOUND", "식사팟을 찾을 수 없습니다."),
    PARTY_DELETED(HttpStatus.NOT_FOUND, "PARTY_DELETED", "삭제된 식사팟입니다."),

    // ===== 409 =====
    LOGIN_ID_ALREADY_EXISTS(HttpStatus.CONFLICT, "LOGIN_ID_ALREADY_EXISTS", "이미 사용 중인 아이디입니다."),
    NICKNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "NICKNAME_ALREADY_EXISTS", "이미 사용 중인 닉네임입니다."),
    RESTAURANT_ALREADY_EXISTS(HttpStatus.CONFLICT, "RESTAURANT_ALREADY_EXISTS", "이미 등록된 식당입니다."),
    ALREADY_INTERESTED(HttpStatus.CONFLICT, "ALREADY_INTERESTED", "이미 관심 등록한 식당입니다."),
    ALREADY_JOINED(HttpStatus.CONFLICT, "ALREADY_JOINED", "이미 참여 중인 식사팟입니다."),
    ALREADY_JOINED_SAME_MEAL(HttpStatus.CONFLICT, "ALREADY_JOINED_SAME_MEAL", "같은 시간대에 이미 참여 중인 식사팟이 있습니다."),
    NOT_JOINED(HttpStatus.CONFLICT, "NOT_JOINED", "참여하지 않은 식사팟입니다."),
    PARTY_FULL(HttpStatus.CONFLICT, "PARTY_FULL", "이미 마감된 식사팟입니다."),
    PARTY_FINISHED(HttpStatus.CONFLICT, "PARTY_FINISHED", "식사가 완료된 식사팟입니다."),
    PARTY_NOT_FINISHED(HttpStatus.CONFLICT, "PARTY_NOT_FINISHED", "식사가 끝난 뒤에 따봉을 남길 수 있습니다."),
    ALREADY_THUMBED(HttpStatus.CONFLICT, "ALREADY_THUMBED", "이미 따봉을 남겼습니다."),
    LEAVE_DEADLINE_PASSED(HttpStatus.CONFLICT, "LEAVE_DEADLINE_PASSED", "식사 30분 전부터는 나갈 수 없습니다."),
    SCHEDULE_NOT_SET(HttpStatus.CONFLICT, "SCHEDULE_NOT_SET", "방장이 아직 일정을 확정하지 않았습니다."),
    ALREADY_CONFIRMED(HttpStatus.CONFLICT, "ALREADY_CONFIRMED", "이미 참여를 확정했습니다."),
    NOT_CONFIRMED(HttpStatus.CONFLICT, "NOT_CONFIRMED", "참여를 확정하지 않았습니다."),
    CANCEL_DEADLINE_PASSED(HttpStatus.CONFLICT, "CANCEL_DEADLINE_PASSED", "식사 30분 전부터는 확정을 취소할 수 없습니다."),
    CHAT_READ_ONLY(HttpStatus.CONFLICT, "CHAT_READ_ONLY", "식사가 완료되어 메시지를 보낼 수 없습니다."),
    INVALID_STATUS(HttpStatus.CONFLICT, "INVALID_STATUS", "현재 상태에서는 변경할 수 없습니다."),
    NOT_ENOUGH_CANDIDATES(HttpStatus.CONFLICT, "NOT_ENOUGH_CANDIDATES", "룰렛을 돌릴 식당이 부족합니다."),

    // ===== 500, 502 =====
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."),
    MAP_API_ERROR(HttpStatus.BAD_GATEWAY, "MAP_API_ERROR", "지도 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
