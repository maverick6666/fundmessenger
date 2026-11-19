package com.fundmessenger.fund.dto;

import com.fundmessenger.fund.entity.DecisionNote;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class DecisionNoteResponse {

    private Long id;
    private Long teamId;
    private Long positionId;
    private String positionTicker;
    private String positionTickerName;
    private String title;
    private String content;
    private String noteType;
    private boolean exportEnabled;
    private Long linkedPostId;
    private boolean aiGenerated;
    private String aiSessionId;
    private Integer aiSessionIndex;
    private Long sourceChatRoomId;
    private Long authorId;
    private String authorName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static DecisionNoteResponse from(DecisionNote note) {
        return DecisionNoteResponse.builder()
                .id(note.getId())
                .teamId(note.getTeam().getId())
                .positionId(note.getPosition() != null ? note.getPosition().getId() : null)
                .positionTicker(note.getPosition() != null ? note.getPosition().getTicker() : null)
                .positionTickerName(note.getPosition() != null ? note.getPosition().getTickerName() : null)
                .title(note.getTitle())
                .content(note.getContent())
                .noteType(note.getNoteType().name())
                .exportEnabled(note.isExportEnabled())
                .linkedPostId(note.getLinkedPost() != null ? note.getLinkedPost().getId() : null)
                .aiGenerated(note.isAiGenerated())
                .aiSessionId(note.getAiSessionId())
                .aiSessionIndex(note.getAiSessionIndex())
                .sourceChatRoomId(note.getSourceChatRoomId())
                .authorId(note.getAuthor().getId())
                .authorName(note.getAuthor().getName())
                .createdAt(note.getCreatedAt())
                .updatedAt(note.getUpdatedAt())
                .build();
    }
}
