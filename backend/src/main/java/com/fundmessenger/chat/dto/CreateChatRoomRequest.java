package com.fundmessenger.chat.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter @Setter
public class CreateChatRoomRequest {
    @NotBlank(message = "채팅방 유형은 필수입니다")
    private String type; // DM, GROUP, LIGHTNING

    @Size(max = 100, message = "채팅방 이름은 100자 이내여야 합니다")
    private String name;

    @Size(max = 500, message = "설명은 500자 이내여야 합니다")
    private String description;

    @Min(value = 2, message = "최소 인원은 2명 이상이어야 합니다")
    @Max(value = 100, message = "최대 인원은 100명 이하여야 합니다")
    private Integer maxMembers;

    private String joinMode; // FREE, APPROVAL
    private List<Long> memberIds; // ClubMembership IDs
}
