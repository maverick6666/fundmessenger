package com.fundmessenger.board.controller;

import com.fundmessenger.board.dto.PostResponse;
import com.fundmessenger.board.service.ArchiveService;
import com.fundmessenger.drive.dto.DriveFileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/clubs/{clubId}/archive")
@RequiredArgsConstructor
public class ArchiveController {

    private final ArchiveService archiveService;

    /** 아카이브 요약 */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getArchiveSummary(
            @PathVariable Long clubId,
            @RequestParam Long generationId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(archiveService.getArchiveSummary(clubId, generationId, userId));
    }

    /** 아카이브 팀 목록 */
    @GetMapping("/teams")
    public ResponseEntity<List<Map<String, Object>>> getArchiveTeams(
            @PathVariable Long clubId,
            @RequestParam Long generationId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(archiveService.getArchiveTeams(clubId, generationId, userId));
    }

    /** 아카이브 게시글 — 작성자 기수 기반 조회 */
    @GetMapping("/posts")
    public ResponseEntity<Page<PostResponse>> getArchivePosts(
            @PathVariable Long clubId,
            @RequestParam Long generationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(archiveService.getArchivePosts(clubId, generationId, page, size, userId));
    }

    /** 아카이브 파일 — 업로더 기수 기반 조회 */
    @GetMapping("/files")
    public ResponseEntity<Page<DriveFileResponse>> getArchiveFiles(
            @PathVariable Long clubId,
            @RequestParam Long generationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(archiveService.getArchiveFiles(clubId, generationId, page, size, userId));
    }

    /** 아카이브 멤버 목록 */
    @GetMapping("/members")
    public ResponseEntity<List<Map<String, Object>>> getArchiveMembers(
            @PathVariable Long clubId,
            @RequestParam Long generationId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(archiveService.getArchiveMembers(clubId, generationId, userId));
    }
}
