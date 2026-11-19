package com.sseukbap.party.service;

import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import com.sseukbap.party.dto.PartyChatCreateRequest;
import com.sseukbap.party.dto.PartyChatCreateResponse;
import com.sseukbap.party.dto.PartyChatListResponse;
import com.sseukbap.party.dto.PartyChatResponse;
import com.sseukbap.party.entity.MealParty;
import com.sseukbap.party.entity.PartyChat;
import com.sseukbap.party.entity.PartyMember;
import com.sseukbap.party.repository.PartyChatRepository;
import com.sseukbap.party.repository.PartyMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 식사팟 대화: 조회(폴링)·전송. 참여자만 쓸 수 있다.
 * 기준: docs/API_SPEC.md 6장 (각 메서드의 번호는 명세의 "시스템 처리" 순서)
 *
 * 참여자가 아닐 때는 403 NOT_JOINED (응답 error 값은 "NOT_JOINED").
 * 참여·나가기의 NOT_JOINED(409)와 상태 코드가 달라서 ErrorCode.NOT_JOINED_FORBIDDEN을 쓴다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PartyChatService {

    /** 한 번에 돌려주는 최대 메시지 수 */
    private static final int MAX_CHATS = 200;

    private final PartyFinder partyFinder;
    private final PartyMemberRepository partyMemberRepository;
    private final PartyChatRepository partyChatRepository;

    /**
     * 대화 조회 (폴링). 처음엔 afterId 없이 전체, 이후 3초마다 마지막 chatId를 afterId로 보내 새 메시지만 받는다.
     */
    public PartyChatListResponse getChatList(Long partyId, Long afterId, Long memberId) {
        // 1. 팟 조회. 없거나 DELETED면 중단 (폴링 중 삭제된 경우 포함 → FE는 폴링을 멈춘다)
        MealParty party = partyFinder.getParty(partyId);

        // 2. 참여자 확인 (나간 경우 포함)
        if (!partyMemberRepository.existsByPartyIdAndMemberId(partyId, memberId)) {
            throw new BusinessException(ErrorCode.NOT_JOINED_FORBIDDEN, "참여자만 대화를 볼 수 있습니다.");
        }

        // 3. chatId > afterId, 오래된 순, 최대 200개 (afterId가 없으면 처음부터)
        List<PartyChat> chats = partyChatRepository.findAllAfter(
                partyId, afterId == null ? 0L : afterId, PageRequest.of(0, MAX_CHATS));

        // 4~5. 닉네임·내 메시지 여부, 식사 완료면 읽기 전용
        return new PartyChatListResponse(
                party.isFinished(),
                chats.stream().map(chat -> PartyChatResponse.of(chat, memberId)).toList());
    }

    /** 대화 전송 (TEXT). 식사 완료 후에는 보낼 수 없다. */
    @Transactional
    public PartyChatCreateResponse createChat(Long partyId, PartyChatCreateRequest request, Long memberId) {
        // 1. 내용 검증은 PartyChatCreateRequest의 @Valid에서 끝났다. (1~200자, 공백만 불가)

        // 2. 팟 조회. 없거나 DELETED면 중단
        MealParty party = partyFinder.getParty(partyId);

        // 3. 참여자 확인
        PartyMember partyMember = partyMemberRepository.findByPartyIdAndMemberId(partyId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_JOINED_FORBIDDEN, "참여자만 대화할 수 있습니다."));

        // 4. 식사 완료 → 읽기 전용
        if (party.isFinished()) {
            throw new BusinessException(ErrorCode.CHAT_READ_ONLY);
        }

        // 5~6. 저장 후 chatId 반환
        PartyChat chat = partyChatRepository.save(
                PartyChat.createText(party, partyMember.getMember(), request.content()));
        return new PartyChatCreateResponse(chat.getChatId(), chat.getCreatedAt());
    }
}
