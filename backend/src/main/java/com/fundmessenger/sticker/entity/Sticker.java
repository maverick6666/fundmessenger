package com.fundmessenger.sticker.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "stickers")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Sticker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pack_id", nullable = false)
    private StickerPack pack;

    @Column(nullable = false)
    private String imageUrl;

    /** 대표 유니코드 이모지 (선택) */
    @Column(length = 10)
    private String emoji;

    @Column(nullable = false)
    @Builder.Default
    private int displayOrder = 0;
}
