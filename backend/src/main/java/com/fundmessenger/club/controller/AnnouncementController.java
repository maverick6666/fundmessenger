package com.fundmessenger.club.controller;

import com.fundmessenger.club.dto.AnnouncementResponse;
import com.fundmessenger.club.dto.CreateAnnouncementRequest;
import com.fundmessenger.club.service.AnnouncementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clubs/{clubId}/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    @GetMapping
    public ResponseEntity<List<AnnouncementResponse>> getAnnouncements(
            @PathVariable Long clubId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(announcementService.getAnnouncements(clubId, userId));
    }

    @GetMapping("/{announcementId}")
    public ResponseEntity<AnnouncementResponse> getAnnouncement(
            @PathVariable Long clubId,
            @PathVariable Long announcementId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(announcementService.getAnnouncement(clubId, announcementId, userId));
    }

    @PostMapping
    public ResponseEntity<AnnouncementResponse> createAnnouncement(
            @PathVariable Long clubId,
            @RequestBody CreateAnnouncementRequest request,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(announcementService.createAnnouncement(clubId, request, userId));
    }

    @PutMapping("/{announcementId}")
    public ResponseEntity<AnnouncementResponse> updateAnnouncement(
            @PathVariable Long clubId,
            @PathVariable Long announcementId,
            @RequestBody CreateAnnouncementRequest request,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(announcementService.updateAnnouncement(clubId, announcementId, request, userId));
    }

    @DeleteMapping("/{announcementId}")
    public ResponseEntity<Void> deleteAnnouncement(
            @PathVariable Long clubId,
            @PathVariable Long announcementId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        announcementService.deleteAnnouncement(clubId, announcementId, userId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{announcementId}/pin")
    public ResponseEntity<Void> togglePin(
            @PathVariable Long clubId,
            @PathVariable Long announcementId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        announcementService.togglePin(clubId, announcementId, userId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{announcementId}/banner")
    public ResponseEntity<Void> toggleBanner(
            @PathVariable Long clubId,
            @PathVariable Long announcementId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        announcementService.toggleBanner(clubId, announcementId, userId);
        return ResponseEntity.ok().build();
    }
}
