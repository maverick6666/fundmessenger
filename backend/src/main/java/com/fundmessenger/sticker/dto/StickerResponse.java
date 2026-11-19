package com.fundmessenger.sticker.dto;

import com.fundmessenger.sticker.entity.Sticker;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StickerResponse {
    private Long id;
    private Long packId;
    private String imageUrl;
    private String emoji;
    private int displayOrder;

    public static StickerResponse from(Sticker sticker) {
        return StickerResponse.builder()
                .id(sticker.getId())
                .packId(sticker.getPack().getId())
                .imageUrl(sticker.getImageUrl())
                .emoji(sticker.getEmoji())
                .displayOrder(sticker.getDisplayOrder())
                .build();
    }
}
