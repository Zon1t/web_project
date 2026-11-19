package com.sseukbap.common.interceptor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 로그인한 회원 ID를 Controller 파라미터로 바로 받는다.
 *
 * 사용 예
 *   @PostMapping("/{partyId}/members")
 *   public ApiResponse<PartyJoinResponse> join(@PathVariable Long partyId, @LoginMember Long memberId) { ... }
 *
 * 타입은 반드시 Long. (세션에는 회원 ID만 저장한다)
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface LoginMember {
}
