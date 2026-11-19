package com.fundmessenger.chat.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class SendMessageRequest {
    @Size(max = 10000, message = "메시지는 10000자 이내여야 합니다")
    private String content;

    @Size(max = 500, message = "이미지 URL은 500자 이내여야 합니다")
    private String imageUrl;

    private Long sharedPostId;
    private Long sharedNewsId;
}
