package com.fundmessenger.sticker.repository;

import com.fundmessenger.sticker.entity.StickerPack;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StickerPackRepository extends JpaRepository<StickerPack, Long> {

    List<StickerPack> findByActiveTrueOrderByCreatedAtDesc();

    List<StickerPack> findAllByOrderByCreatedAtDesc();
}
