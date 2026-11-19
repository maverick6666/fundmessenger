package com.fundmessenger.sticker.controller;

import com.fundmessenger.sticker.dto.StickerPackResponse;
import com.fundmessenger.sticker.dto.StickerResponse;
import com.fundmessenger.sticker.service.StickerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 스티커 스토어 + 구매 API (EMJ-3)
 */
@RestController
@RequestMapping("/api/clubs/{clubId}/stickers")
@RequiredArgsConstructor
public class StickerStoreController {

    private final StickerService stickerService;

    /** 스토어 목록 (활성 팩 + 구매 여부) */
    @GetMapping("/store")
    public ResponseEntity<List<StickerPackResponse>> getStore(
            @PathVariable Long clubId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(stickerService.getStoreList(clubId, userId));
    }

    /** 팩 구매 */
    @PostMapping("/store/purchase/{packId}")
    public ResponseEntity<Void> purchasePack(
            @PathVariable Long clubId,
            @PathVariable Long packId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        stickerService.purchasePack(clubId, packId, userId);
        return ResponseEntity.ok().build();
    }

    /** 구매한 팩 목록 */
    @GetMapping("/purchased")
    public ResponseEntity<List<StickerPackResponse>> getPurchasedPacks(
            @PathVariable Long clubId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(stickerService.getPurchasedPacks(clubId, userId));
    }

    /** 구매한 팩의 스티커 목록 */
    @GetMapping("/purchased/{packId}")
    public ResponseEntity<List<StickerResponse>> getPurchasedStickers(
            @PathVariable Long clubId,
            @PathVariable Long packId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(stickerService.getPurchasedPackStickers(clubId, packId, userId));
    }
}
