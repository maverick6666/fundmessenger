package com.fundmessenger.sticker.dto;

import com.fundmessenger.sticker.entity.StickerPack;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class StickerPackResponse {
    private Long id;
    private String name;
    private String thumbnailUrl;
    private int stickerCount;
    private int price;
    private boolean active;
    private boolean purchased;
    private LocalDateTime createdAt;

    public static StickerPackResponse from(StickerPack pack, boolean purchased) {
        return StickerPackResponse.builder()
                .id(pack.getId())
                .name(pack.getName())
                .thumbnailUrl(pack.getThumbnailUrl())
                .stickerCount(pack.getStickerCount())
                .price(pack.getPrice())
                .active(pack.isActive())
                .purchased(purchased)
                .createdAt(pack.getCreatedAt())
                .build();
    }
}
