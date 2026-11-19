package com.fundmessenger.fund.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AiDecisionResponse {

    private List<NoteInfo> notes;
    private int creditsUsed;

    @Getter
    @Builder
    public static class NoteInfo {
        private Long noteId;
        private String title;
        private String content;
        private Integer sessionIndex;
    }
}
