package com.fundmessenger.sticker.repository;

import com.fundmessenger.sticker.entity.Sticker;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StickerRepository extends JpaRepository<Sticker, Long> {

    List<Sticker> findByPackIdOrderByDisplayOrderAsc(Long packId);

    int countByPackId(Long packId);
}
