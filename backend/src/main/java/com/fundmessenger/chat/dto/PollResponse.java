package com.fundmessenger.chat.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class PollResponse {
    private Long id;
    private Long chatRoomId;
    private String question;
    private List<String> options;
    private boolean multipleChoice;
    private boolean anonymous;
    private boolean closed;
    private LocalDateTime deadline;
    private LocalDateTime createdAt;
    private Long creatorId;
    private String creatorName;
    private List<OptionResult> results;
    private long totalVotes;
    private List<Integer> myVote; // null if not voted
    private int participantCount;
    private int totalMembers;

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class OptionResult {
        private int index;
        private String text;
        private long count;
        private double percentage;
    }
}
