package com.fundmessenger.chat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundmessenger.chat.dto.ChatMessageResponse;
import com.fundmessenger.chat.dto.CreatePollRequest;
import com.fundmessenger.chat.dto.PollResponse;
import com.fundmessenger.chat.dto.VoteRequest;
import com.fundmessenger.chat.entity.ChatMessage;
import com.fundmessenger.chat.entity.ChatRoom;
import com.fundmessenger.chat.entity.ChatRoomType;
import com.fundmessenger.chat.entity.Poll;
import com.fundmessenger.chat.entity.PollVote;
import com.fundmessenger.chat.event.ChatMessageSentEvent;
import com.fundmessenger.chat.repository.ChatMemberRepository;
import com.fundmessenger.chat.repository.ChatMessageRepository;
import com.fundmessenger.chat.repository.ChatRoomRepository;
import com.fundmessenger.chat.repository.PollRepository;
import com.fundmessenger.chat.repository.PollVoteRepository;
import com.fundmessenger.club.entity.Club;
import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.club.repository.ClubMembershipRepository;
import com.fundmessenger.club.repository.ClubRepository;
import com.fundmessenger.club.service.ClubAuthorizationService;
import com.fundmessenger.notification.entity.NotificationType;
import com.fundmessenger.notification.event.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PollService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final PollRepository pollRepository;
    private final PollVoteRepository pollVoteRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatMemberRepository chatMemberRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ClubMembershipRepository clubMembershipRepository;
    private final ClubRepository clubRepository;
    private final ClubAuthorizationService authService;
    private final ChatMemberService chatMemberService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public PollResponse createPoll(Long clubId, CreatePollRequest request, Long userId) {
        ClubMembership membership = authService.validateClubMember(userId, clubId);
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new IllegalArgumentException("동아리를 찾을 수 없습니다"));
        ChatRoom room = chatRoomRepository.findById(request.getChatRoomId())
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다"));
        chatMemberService.validateRoomMember(room.getId(), membership.getId());

        String optionsJson = toJson(request.getOptions());

        Poll poll = Poll.builder()
                .chatRoom(room)
                .club(club)
                .question(request.getQuestion())
                .options(optionsJson)
                .multipleChoice(request.isMultipleChoice())
                .anonymous(request.isAnonymous())
                .deadline(request.getDeadline())
                .createdBy(membership)
                .build();

        Poll saved = pollRepository.save(poll);

        ChatMessage message = ChatMessage.builder()
                .chatRoom(room)
                .sender(membership)
                .content("📊 " + request.getQuestion())
                .messageType(ChatMessage.MessageType.TEXT)
                .poll(saved)
                .build();
        ChatMessage savedMessage = chatMessageRepository.save(message);

        eventPublisher.publishEvent(new ChatMessageSentEvent(room.getId(), ChatMessageResponse.from(savedMessage)));
        notifyPollCreated(clubId, room, membership, request.getQuestion());

        return buildPollResponse(saved, membership);
    }

    @Transactional
    public PollResponse vote(Long clubId, Long pollId, VoteRequest request, Long userId) {
        Poll poll = pollRepository.findById(pollId)
                .orElseThrow(() -> new IllegalArgumentException("투표를 찾을 수 없습니다"));

        if (poll.isClosed()) {
            throw new IllegalStateException("이미 마감된 투표입니다.");
        }

        if (poll.getDeadline() != null && LocalDateTime.now().isAfter(poll.getDeadline())) {
            throw new IllegalStateException("투표 마감 시간이 지났습니다");
        }

        ClubMembership membership = authService.validateClubMember(userId, clubId);
        chatMemberService.validateRoomMember(poll.getChatRoom().getId(), membership.getId());

        List<String> options = fromJson(poll.getOptions());
        for (Integer index : request.getSelectedOptions()) {
            if (index < 0 || index >= options.size()) {
                throw new IllegalArgumentException("유효하지 않은 선택지입니다: " + index);
            }
        }

        if (!poll.isMultipleChoice() && request.getSelectedOptions().size() > 1) {
            throw new IllegalArgumentException("단일 선택 투표입니다. 하나만 선택해 주세요.");
        }

        String selectedJson = toJson(request.getSelectedOptions());

        Optional<PollVote> existing = pollVoteRepository.findByPollIdAndVoterId(pollId, membership.getId());
        if (existing.isPresent()) {
            PollVote vote = existing.get();
            vote.setSelectedOptions(selectedJson);
            vote.setVotedAt(LocalDateTime.now());
            pollVoteRepository.save(vote);
        } else {
            PollVote vote = PollVote.builder()
                    .poll(poll)
                    .voter(membership)
                    .selectedOptions(selectedJson)
                    .build();
            pollVoteRepository.save(vote);
        }

        PollResponse response = buildPollResponse(poll, membership);

        messagingTemplate.convertAndSend(
                "/topic/chat/" + poll.getChatRoom().getId(),
                Map.of("type", "POLL_UPDATE", "pollId", pollId, "pollData", response)
        );

        return response;
    }

    @Transactional
    public PollResponse closePoll(Long clubId, Long pollId, Long userId) {
        Poll poll = pollRepository.findById(pollId)
                .orElseThrow(() -> new IllegalArgumentException("투표를 찾을 수 없습니다"));

        ClubMembership membership = authService.validateClubMember(userId, clubId);

        if (!poll.getCreatedBy().getId().equals(membership.getId())) {
            throw new SecurityException("투표 생성자만 마감할 수 있습니다");
        }

        poll.setClosed(true);
        pollRepository.save(poll);

        PollResponse response = buildPollResponse(poll, membership);

        messagingTemplate.convertAndSend(
                "/topic/chat/" + poll.getChatRoom().getId(),
                Map.of("type", "POLL_UPDATE", "pollId", pollId, "pollData", response)
        );

        return response;
    }

    @Transactional(readOnly = true)
    public PollResponse getPoll(Long clubId, Long pollId, Long userId) {
        Poll poll = pollRepository.findById(pollId)
                .orElseThrow(() -> new IllegalArgumentException("투표를 찾을 수 없습니다"));

        ClubMembership membership = authService.validateClubMember(userId, clubId);
        return buildPollResponse(poll, membership);
    }

    @Transactional(readOnly = true)
    public List<PollResponse> getPolls(Long clubId, String status) {
        List<Poll> polls;
        if ("OPEN".equalsIgnoreCase(status)) {
            polls = pollRepository.findByClubIdAndClosedOrderByCreatedAtDesc(clubId, false);
        } else if ("CLOSED".equalsIgnoreCase(status)) {
            polls = pollRepository.findByClubIdAndClosedOrderByCreatedAtDesc(clubId, true);
        } else {
            polls = pollRepository.findByClubIdOrderByCreatedAtDesc(clubId);
        }

        return polls.stream()
                .map(poll -> buildPollResponse(poll, null))
                .collect(Collectors.toList());
    }

    private PollResponse buildPollResponse(Poll poll, ClubMembership viewer) {
        List<String> options = fromJson(poll.getOptions());
        List<PollVote> votes = pollVoteRepository.findByPollId(poll.getId());

        Map<Integer, Long> countByOption = new HashMap<>();
        for (int index = 0; index < options.size(); index += 1) {
            countByOption.put(index, 0L);
        }

        for (PollVote vote : votes) {
            List<Integer> selected = fromJsonIntegers(vote.getSelectedOptions());
            for (Integer index : selected) {
                countByOption.merge(index, 1L, Long::sum);
            }
        }

        long totalVotes = countByOption.values().stream().mapToLong(Long::longValue).sum();

        List<PollResponse.OptionResult> results = new ArrayList<>();
        for (int index = 0; index < options.size(); index += 1) {
            long count = countByOption.getOrDefault(index, 0L);
            double percentage = totalVotes > 0 ? (double) count / totalVotes * 100.0 : 0.0;
            results.add(PollResponse.OptionResult.builder()
                    .index(index)
                    .text(options.get(index))
                    .count(count)
                    .percentage(Math.round(percentage * 10.0) / 10.0)
                    .build());
        }

        List<Integer> myVote = null;
        if (viewer != null) {
            Optional<PollVote> myPollVote = pollVoteRepository.findByPollIdAndVoterId(poll.getId(), viewer.getId());
            if (myPollVote.isPresent()) {
                myVote = fromJsonIntegers(myPollVote.get().getSelectedOptions());
            }
        }

        int totalMembers = (int) chatMemberRepository.countByChatRoomIdAndIsLeftFalse(poll.getChatRoom().getId());

        return PollResponse.builder()
                .id(poll.getId())
                .chatRoomId(poll.getChatRoom().getId())
                .question(poll.getQuestion())
                .options(options)
                .multipleChoice(poll.isMultipleChoice())
                .anonymous(poll.isAnonymous())
                .closed(poll.isClosed())
                .deadline(poll.getDeadline())
                .createdAt(poll.getCreatedAt())
                .creatorId(poll.getCreatedBy().getId())
                .creatorName(poll.getCreatedBy().getName())
                .results(results)
                .totalVotes(totalVotes)
                .myVote(myVote)
                .participantCount(votes.size())
                .totalMembers(totalMembers)
                .build();
    }

    private void notifyPollCreated(Long clubId, ChatRoom room, ClubMembership sender, String question) {
        if (room.getType() != ChatRoomType.DM) {
            return;
        }

        chatMemberRepository.findByChatRoomIdAndIsLeftFalse(room.getId()).stream()
                .filter(chatMember -> !chatMember.getMember().getId().equals(sender.getId()))
                .findFirst()
                .ifPresent(chatMember -> eventPublisher.publishEvent(NotificationEvent.of(
                        clubId,
                        chatMember.getMember().getUser().getId(),
                        NotificationType.DM_RECEIVED,
                        sender.getName() + "님이 투표를 보냈습니다",
                        truncate(question, 50),
                        "/club/chat/" + room.getId()
                )));
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "새 투표가 도착했습니다";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new RuntimeException("JSON 직렬화 실패", exception);
        }
    }

    private List<String> fromJson(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException exception) {
            throw new RuntimeException("JSON 역직렬화 실패", exception);
        }
    }

    private List<Integer> fromJsonIntegers(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<List<Integer>>() {});
        } catch (JsonProcessingException exception) {
            throw new RuntimeException("JSON 역직렬화 실패", exception);
        }
    }
}
