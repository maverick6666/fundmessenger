package com.fundmessenger.sticker.repository;

import com.fundmessenger.sticker.entity.ClubStickerPurchase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClubStickerPurchaseRepository extends JpaRepository<ClubStickerPurchase, Long> {

    boolean existsByClubIdAndPackId(Long clubId, Long packId);

    Optional<ClubStickerPurchase> findByClubIdAndPackId(Long clubId, Long packId);

    List<ClubStickerPurchase> findByClubId(Long clubId);
}
