package com.fundmessenger.sticker.controller;

import com.fundmessenger.sticker.dto.*;
import com.fundmessenger.sticker.service.StickerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 슈퍼어드민 스티커 관리 API (EMJ-2)
 */
@RestController
@RequestMapping("/api/admin/sticker-packs")
@RequiredArgsConstructor
public class StickerAdminController {

    private final StickerService stickerService;

    @GetMapping
    public ResponseEntity<List<StickerPackResponse>> getAllPacks() {
        return ResponseEntity.ok(stickerService.getAllPacks());
    }

    @PostMapping
    public ResponseEntity<StickerPackResponse> createPack(
            @Valid @RequestBody CreateStickerPackRequest request,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(stickerService.createPack(request, userId));
    }

    @PutMapping("/{packId}")
    public ResponseEntity<StickerPackResponse> updatePack(
            @PathVariable Long packId,
            @Valid @RequestBody CreateStickerPackRequest request,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(stickerService.updatePack(packId, request, userId));
    }

    @PatchMapping("/{packId}/active")
    public ResponseEntity<Void> toggleActive(@PathVariable Long packId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        stickerService.togglePackActive(packId, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{packId}/stickers")
    public ResponseEntity<List<StickerResponse>> getStickers(@PathVariable Long packId) {
        return ResponseEntity.ok(stickerService.getPackStickers(packId));
    }

    @PostMapping("/{packId}/stickers")
    public ResponseEntity<StickerResponse> addSticker(
            @PathVariable Long packId,
            @RequestParam("file") MultipartFile file,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(stickerService.addSticker(packId, file, userId));
    }

    @DeleteMapping("/{packId}/stickers/{stickerId}")
    public ResponseEntity<Void> deleteSticker(
            @PathVariable Long packId,
            @PathVariable Long stickerId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        stickerService.deleteSticker(packId, stickerId, userId);
        return ResponseEntity.ok().build();
    }
}
