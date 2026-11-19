package com.fundmessenger.fund.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundmessenger.board.entity.Post;
import com.fundmessenger.board.repository.PostRepository;
import com.fundmessenger.chat.entity.ChatMessage;
import com.fundmessenger.chat.entity.ChatRoom;
import com.fundmessenger.chat.entity.ChatRoomType;
import com.fundmessenger.chat.entity.Poll;
import com.fundmessenger.chat.entity.PollVote;
import com.fundmessenger.chat.repository.ChatMessageRepository;
import com.fundmessenger.chat.repository.ChatRoomRepository;
import com.fundmessenger.chat.repository.PollRepository;
import com.fundmessenger.chat.repository.PollVoteRepository;
import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.club.entity.Team;
import com.fundmessenger.club.repository.TeamRepository;
import com.fundmessenger.config.CerebrasClient;
import com.fundmessenger.fund.dto.AiDecisionResponse;
import com.fundmessenger.fund.entity.DecisionNote;
import com.fundmessenger.fund.entity.NoteType;
import com.fundmessenger.fund.entity.Position;
import com.fundmessenger.fund.repository.DecisionNoteRepository;
import com.fundmessenger.jongtobang.entity.CanonicalNews;
import com.fundmessenger.jongtobang.repository.CanonicalNewsRepository;
import com.fundmessenger.config.exception.ExternalServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiDecisionService {

    private static final int TOKEN_LIMIT = 60_000;
    private static final double CHARS_PER_TOKEN = 1.5;

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final DecisionNoteRepository noteRepository;
    private final TeamRepository teamRepository;
    private final CerebrasClient cerebrasClient;
    private final PositionService positionService;
    private final PollRepository pollRepository;
    private final PollVoteRepository pollVoteRepository;
    private final PostRepository postRepository;
    private final CanonicalNewsRepository canonicalNewsRepository;
    private final ObjectMapper objectMapper;

    /**
     * AI 의사결정서 생성 — 트랜잭션 분리: 검증(TX1) → AI호출(TX외부) → 저장+차감(TX2)
     */
    public AiDecisionResponse generateDecisionNote(Long clubId, Long teamId, Long chatRoomId, ClubMembership membership) {
        // ── Phase 1: 검증 + 데이터 수집 (읽기 트랜잭션) ──
        PreparedData data = prepareData(clubId, teamId, chatRoomId);

        // ── Phase 2: AI 호출 (트랜잭션 외부 — DB 락 점유 안 함) ──
        String sessionId = UUID.randomUUID().toString();
        List<String> chunks = splitText(data.messageStr, data.splits);
        List<GeneratedChunk> generatedChunks = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            String systemPrompt = buildSystemPrompt(data.tickerName, data.ticker, data.market, data.requestType);
            JsonNode result = cerebrasClient.generateJsonUser(systemPrompt, chunks.get(i));
            if (result == null) {
                throw new ExternalServiceException("AI", "생성에 실패했습니다 (청크 " + (i+1) + "/" + chunks.size() + "). 잠시 후 다시 시도하세요.");
            }

            String title = result.has("title") ? result.get("title").asText() : data.tickerName + " 의사결정서";
            String content = result.has("content") ? result.get("content").asText() : result.toString();
            if (chunks.size() > 1) {
                title = title + " (" + (i + 1) + "-" + chunks.size() + ")";
            }
            generatedChunks.add(new GeneratedChunk(title, content, i + 1));
        }

        // ── Phase 3: 저장 + 크레딧 차감 (쓰기 트랜잭션) ──
        return saveNotesAndDeductCredits(clubId, teamId, data, membership, sessionId, generatedChunks);
    }

    @Transactional(readOnly = true)
    protected PreparedData prepareData(Long clubId, Long teamId, Long chatRoomId) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다: " + chatRoomId));
        if (chatRoom.getType() != ChatRoomType.DISCUSSION) {
            throw new IllegalArgumentException("토론방(DISCUSSION)에서만 AI 의사결정서를 생성할 수 있습니다");
        }
        if (!chatRoom.getClub().getId().equals(clubId)) {
            throw new IllegalArgumentException("해당 동아리의 채팅방이 아닙니다");
        }

        Team team = positionService.validateFundTeam(teamId);

        // 마지막 AI 생성 이후의 메시지만 수집 (첫 생성이면 전체)
        Position linkedPosition = chatRoom.getPosition();
        List<ChatMessage> messages;
        if (linkedPosition != null) {
            List<DecisionNote> prevAiNotes = noteRepository.findByPositionIdOrderByCreatedAtDesc(linkedPosition.getId())
                    .stream().filter(DecisionNote::isAiGenerated).toList();
            if (!prevAiNotes.isEmpty()) {
                messages = chatMessageRepository.findTextMessagesSince(chatRoomId, prevAiNotes.get(0).getCreatedAt());
            } else {
                messages = chatMessageRepository.findAllTextMessagesByRoomId(chatRoomId);
            }
        } else {
            messages = chatMessageRepository.findAllTextMessagesByRoomId(chatRoomId);
        }
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("새로운 토론 메시지가 없습니다 (이전 AI 생성 이후 추가된 메시지 없음)");
        }

        StringBuilder messagesText = new StringBuilder();
        for (ChatMessage msg : messages) {
            String senderName = msg.getSender().getName();

            // 텍스트 메시지
            String content = msg.getContent();
            if (content != null && !content.isBlank()) {
                messagesText.append("[").append(senderName).append("] ").append(content).append("\n");
            }

            // 공유된 게시글 본문 추출
            if (msg.getSharedPostId() != null) {
                postRepository.findById(msg.getSharedPostId()).ifPresent(post -> {
                    messagesText.append("[").append(senderName).append(" — 공유 글] ")
                            .append(post.getTitle());
                    if (post.getContent() != null && !post.getContent().isBlank()) {
                        String postBody = post.getContent().length() > 500
                                ? post.getContent().substring(0, 500) + "..." : post.getContent();
                        messagesText.append("\n").append(postBody);
                    }
                    messagesText.append("\n");
                });
            }

            // 공유된 뉴스 본문 추출
            if (msg.getSharedNewsId() != null) {
                canonicalNewsRepository.findById(msg.getSharedNewsId()).ifPresent(news -> {
                    messagesText.append("[").append(senderName).append(" — 공유 뉴스] ")
                            .append(news.getTitle());
                    if (news.getBodyMarkdown() != null && !news.getBodyMarkdown().isBlank()) {
                        String newsBody = news.getBodyMarkdown().length() > 500
                                ? news.getBodyMarkdown().substring(0, 500) + "..." : news.getBodyMarkdown();
                        messagesText.append("\n").append(newsBody);
                    }
                    messagesText.append("\n");
                });
            }
        }

        // 투표 결과 추가
        List<Poll> polls = pollRepository.findByChatRoomIdOrderByCreatedAtDesc(chatRoomId);
        if (!polls.isEmpty()) {
            messagesText.append("\n--- 투표 결과 ---\n");
            for (Poll poll : polls) {
                messagesText.append("[투표] ").append(poll.getQuestion());
                if (poll.isClosed()) messagesText.append(" (마감)");
                messagesText.append("\n");

                List<String> optionNames = parseOptions(poll.getOptions());
                List<PollVote> votes = pollVoteRepository.findByPollId(poll.getId());
                int[] counts = new int[optionNames.size()];
                for (PollVote vote : votes) {
                    List<Integer> selected = parseSelectedOptions(vote.getSelectedOptions());
                    for (int idx : selected) {
                        if (idx >= 0 && idx < counts.length) counts[idx]++;
                    }
                }
                for (int i = 0; i < optionNames.size(); i++) {
                    messagesText.append("  - ").append(optionNames.get(i))
                            .append(": ").append(counts[i]).append("표\n");
                }
                messagesText.append("  참여자: ").append(votes.size()).append("명\n");
            }
        }

        String messageStr = messagesText.toString();
        if (messageStr.isBlank()) {
            throw new IllegalArgumentException("토론 메시지가 없습니다");
        }

        Position position = chatRoom.getPosition();
        String tickerName = position != null ? position.getTickerName() : "미지정";
        String ticker = position != null ? position.getTicker() : "";
        String market = position != null ? position.getMarket() : "";
        String requestType = chatRoom.getTradeRequest() != null
                ? chatRoom.getTradeRequest().getRequestType().name() : "분석";

        int estimatedTokens = estimateTokens(messageStr);
        int splits = estimatedTokens > TOKEN_LIMIT ? (int) Math.ceil((double) estimatedTokens / TOKEN_LIMIT) : 1;

        // 크레딧 확인 (락 없이 — 실제 차감은 Phase 3에서 락 획득)
        if (team.getCreditAllocation() < splits) {
            throw new IllegalArgumentException(
                    "팀 크레딧이 부족합니다 (잔액: " + team.getCreditAllocation() + ", 필요: " + splits + ")");
        }

        return new PreparedData(chatRoom.getClub().getId(), team, position, messageStr,
                tickerName, ticker, market, requestType, splits, chatRoomId);
    }

    @Transactional
    protected AiDecisionResponse saveNotesAndDeductCredits(
            Long clubId, Long teamId, PreparedData data, ClubMembership membership,
            String sessionId, List<GeneratedChunk> generatedChunks) {

        List<AiDecisionResponse.NoteInfo> noteInfos = new ArrayList<>();
        for (GeneratedChunk chunk : generatedChunks) {
            DecisionNote note = DecisionNote.builder()
                    .team(data.team)
                    .position(data.position)
                    .title(chunk.title)
                    .content(chunk.content)
                    .noteType(NoteType.DECISION)
                    .aiGenerated(true)
                    .aiSessionId(sessionId)
                    .aiSessionIndex(chunk.index)
                    .sourceChatRoomId(data.chatRoomId)
                    .author(membership)
                    .build();
            note = noteRepository.save(note);
            noteInfos.add(AiDecisionResponse.NoteInfo.builder()
                    .noteId(note.getId()).title(chunk.title).content(chunk.content).sessionIndex(chunk.index).build());
        }

        log.info("AI 의사결정서 완료: teamId={}, notes={}", teamId, generatedChunks.size());
        return AiDecisionResponse.builder().notes(noteInfos).creditsUsed(0).build();
    }

    // ── Inner records ──
    private record PreparedData(Long clubId, Team team, Position position, String messageStr,
                                 String tickerName, String ticker, String market, String requestType, int splits,
                                 Long chatRoomId) {}
    private record GeneratedChunk(String title, String content, int index) {}

    // ── Private helpers ──

    private String buildSystemPrompt(String tickerName, String ticker, String market, String requestType) {
        return """
                당신은 대학 투자동아리의 투자 분석가입니다.
                아래 종목 토론 내용을 바탕으로 투자 의사결정서를 작성하세요.

                ## 종목 정보
                - 종목: %s (%s)
                - 시장: %s
                - 매매 유형: %s

                ## 작성 형식
                1. 요약 (1-2문장)
                2. 투자 근거
                3. 리스크 요인
                4. 매매 전략 (진입/청산 기준)
                5. 팀원 의견 (투표 결과가 있으면 반영)
                6. 결론

                ## 추가 지침
                - [공유 글] / [공유 뉴스] 태그가 있으면 해당 내용을 근거로 활용하세요
                - [투표] 결과가 있으면 "팀원 의견" 섹션에 투표 결과와 함의를 포함하세요

                마크다운 형식으로 작성하세요.

                반드시 아래 JSON 형식으로 응답하세요:
                {"title": "의사결정서 제목", "content": "마크다운 본문"}
                """.formatted(tickerName, ticker, market, requestType);
    }

    private List<String> parseOptions(String optionsJson) {
        try {
            return objectMapper.readValue(optionsJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<Integer> parseSelectedOptions(String selectedJson) {
        try {
            return objectMapper.readValue(selectedJson, new TypeReference<List<Integer>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private int estimateTokens(String text) {
        return (int) Math.ceil(text.length() / CHARS_PER_TOKEN);
    }

    private List<String> splitText(String text, int splits) {
        if (splits <= 1) {
            return List.of(text);
        }

        List<String> chunks = new ArrayList<>();
        String[] lines = text.split("\n");
        int linesPerChunk = (int) Math.ceil((double) lines.length / splits);

        StringBuilder current = new StringBuilder();
        int lineCount = 0;

        for (String line : lines) {
            current.append(line).append("\n");
            lineCount++;

            if (lineCount >= linesPerChunk && chunks.size() < splits - 1) {
                chunks.add(current.toString());
                current = new StringBuilder();
                lineCount = 0;
            }
        }

        if (!current.isEmpty()) {
            chunks.add(current.toString());
        }

        return chunks;
    }
}
