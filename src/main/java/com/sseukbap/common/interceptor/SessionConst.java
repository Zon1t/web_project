package com.sseukbap.common.interceptor;

/**
 * 세션에 저장하는 값의 이름.
 *
 * 로그인 성공 시 (auth 담당 C)
 *   session.setAttribute(SessionConst.LOGIN_MEMBER_ID, member.getMemberId());
 *
 * 저장하는 값은 회원 ID(Long) 하나뿐이다. 회원 정보가 더 필요하면 ID로 DB에서 조회한다.
 */
public final class SessionConst {

    public static final String LOGIN_MEMBER_ID = "LOGIN_MEMBER_ID";

    private SessionConst() {
    }
}
