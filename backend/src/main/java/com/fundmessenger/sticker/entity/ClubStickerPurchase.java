package com.fundmessenger.sticker.entity;

import com.fundmessenger.club.entity.Club;
import com.fundmessenger.club.entity.ClubMembership;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "club_sticker_purchases", uniqueConstraints = {
        @UniqueConstraint(name = "uk_club_pack", columnNames = {"club_id", "pack_id"})
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ClubStickerPurchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pack_id", nullable = false)
    private StickerPack pack;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchased_by", nullable = false)
    private ClubMembership purchasedBy;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime purchasedAt = LocalDateTime.now();
}
