package com.fundmessenger.chat.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter @Setter
public class ContentShareRequest {
    private List<Long> targetRoomIds;
    private List<Long> targetMemberIds; // DM으로 보낼 멤버
    private Long sharedPostId;
    private Long sharedNewsId;
}
