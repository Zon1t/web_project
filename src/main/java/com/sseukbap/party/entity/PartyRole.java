package com.sseukbap.party.entity;

/**
 * 식사팟에서 로그인 회원의 역할 (상세 응답의 myRole). DB에 저장하지 않는다.
 */
public enum PartyRole {
    HOST,     // 방장
    MEMBER,   // 참여자
    NONE      // 미참여
}
