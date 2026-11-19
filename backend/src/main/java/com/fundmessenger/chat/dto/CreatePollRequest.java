package com.fundmessenger.chat.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class CreatePollRequest {
    @NotNull
    private Long chatRoomId;

    @NotBlank @Size(max = 200)
    private String question;

    @NotNull @Size(min = 2, max = 10)
    private List<@NotBlank @Size(max = 100) String> options;

    private boolean multipleChoice;
    private boolean anonymous;
    private LocalDateTime deadline;
}
