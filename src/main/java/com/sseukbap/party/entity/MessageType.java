package com.sseukbap.party.entity;

/**
 * 대화 메시지 유형 (PARTY_CHAT.MSG_TYPE)
 */
public enum MessageType {
    TEXT,    // 참여자가 보낸 메시지
    SYSTEM   // 참여·나가기·일정·확정 등 서버가 남기는 메시지 (작성자 없음)
}
