package com.fundmessenger.drive.controller;

import com.fundmessenger.drive.dto.CreateCommentRequest;
import com.fundmessenger.drive.dto.DriveCommentResponse;
import com.fundmessenger.drive.service.DriveCommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clubs/{clubId}/drive/files/{fileId}/comments")
@RequiredArgsConstructor
public class DriveCommentController {

    private final DriveCommentService commentService;

    /** 코멘트 목록 (전체 또는 페이지별 필터) */
    @GetMapping
    public ResponseEntity<Page<DriveCommentResponse>> getComments(
            @PathVariable Long clubId,
            @PathVariable Long fileId,
            @RequestParam(required = false) Integer pageNumber,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(commentService.getComments(clubId, fileId, pageNumber, page, size, userId));
    }

    /** 코멘트가 있는 페이지 번호 목록 */
    @GetMapping("/pages")
    public ResponseEntity<List<Integer>> getCommentedPages(
            @PathVariable Long clubId,
            @PathVariable Long fileId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(commentService.getCommentedPages(clubId, fileId, userId));
    }

    /** 코멘트 작성 */
    @PostMapping
    public ResponseEntity<DriveCommentResponse> createComment(
            @PathVariable Long clubId,
            @PathVariable Long fileId,
            @Valid @RequestBody CreateCommentRequest request,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(commentService.createComment(clubId, fileId, request.getContent(), request.getPageNumber(), userId));
    }

    /** 코멘트 삭제 */
    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable Long clubId,
            @PathVariable Long fileId,
            @PathVariable Long commentId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        commentService.deleteComment(clubId, fileId, commentId, userId);
        return ResponseEntity.noContent().build();
    }
}
