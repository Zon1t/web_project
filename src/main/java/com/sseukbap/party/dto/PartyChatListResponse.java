package com.sseukbap.party.dto;

import java.util.List;

/**
 * GET /api/parties/{partyId}/chats 응답 data
 * readOnly: 식사 완료(FINISHED)된 팟이면 true → FE는 입력창을 비활성화한다.
 * 새 메시지가 없으면 chats는 빈 배열.
 */
public record PartyChatListResponse(
        boolean readOnly,
        List<PartyChatResponse> chats
) {
}
