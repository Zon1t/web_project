package com.sseukbap.party.dto;

import java.util.Map;

/**
 * POST /api/parties/my/unread-chats 요청 (2026-11-26)
 * seen: { 팟번호: 그 팟에서 마지막으로 본 메시지 번호 } — 브라우저가 기억한 값. 없는 팟은 참여한 뒤의 메시지를 센다.
 */
public record PartyUnreadChatRequest(Map<Long, Long> seen) {
}
