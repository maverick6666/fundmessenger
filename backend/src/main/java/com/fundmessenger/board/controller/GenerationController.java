package com.fundmessenger.board.controller;

import com.fundmessenger.board.dto.SemiCategoryResponse;
import com.fundmessenger.board.service.GenerationService;
import com.fundmessenger.club.dto.GenerationTransitionRequest;
import com.fundmessenger.club.dto.GenerationTransitionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/clubs/{clubId}/generations")
@RequiredArgsConstructor
public class GenerationController {

    private final GenerationService generationService;

    /** 기수 태그 목록 */
    @GetMapping
    public ResponseEntity<List<SemiCategoryResponse>> getGenerations(
            @PathVariable Long clubId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(generationService.getGenerations(clubId, userId));
    }

    /** 기수 태그 단일 생성 */
    @PostMapping
    public ResponseEntity<SemiCategoryResponse> createGeneration(
            @PathVariable Long clubId,
            @RequestBody Map<String, String> body,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        String name = body.getOrDefault("name", "").trim();
        if (name.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(generationService.createGeneration(clubId, name, userId));
    }

    /** 기수 태그 일괄 생성 (범위) */
    @PostMapping("/batch")
    public ResponseEntity<List<SemiCategoryResponse>> batchCreateGenerations(
            @PathVariable Long clubId,
            @RequestBody Map<String, Integer> body,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Integer start = body.get("startNumber");
        Integer end = body.get("endNumber");
        if (start == null || end == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(generationService.batchCreateGenerations(clubId, start, end, userId));
    }

    /** 기수 태그 삭제 */
    @DeleteMapping("/{generationId}")
    public ResponseEntity<Void> deleteGeneration(
            @PathVariable Long clubId,
            @PathVariable Long generationId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        generationService.deleteGeneration(clubId, generationId, userId);
        return ResponseEntity.noContent().build();
    }

    // ========== 멤버 기수 태그 ==========

    /** 멤버 기수 목록 */
    @GetMapping("/members/{membershipId}")
    public ResponseEntity<List<SemiCategoryResponse>> getMemberGenerations(
            @PathVariable Long clubId,
            @PathVariable Long membershipId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(generationService.getMemberGenerations(clubId, membershipId, userId));
    }

    /** 멤버에 기수 태그 추가 */
    @PostMapping("/members/{membershipId}/{generationId}")
    public ResponseEntity<Void> addMemberGeneration(
            @PathVariable Long clubId,
            @PathVariable Long membershipId,
            @PathVariable Long generationId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        generationService.addMemberGeneration(clubId, membershipId, generationId, userId);
        return ResponseEntity.ok().build();
    }

    /** 멤버에서 기수 태그 제거 */
    @DeleteMapping("/members/{membershipId}/{generationId}")
    public ResponseEntity<Void> removeMemberGeneration(
            @PathVariable Long clubId,
            @PathVariable Long membershipId,
            @PathVariable Long generationId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        generationService.removeMemberGeneration(clubId, membershipId, generationId, userId);
        return ResponseEntity.noContent().build();
    }

    // ========== 기수 전환 ==========

    /** 기수 전환 실행 (회장단) */
    @PostMapping("/transition")
    public ResponseEntity<GenerationTransitionResponse> executeTransition(
            @PathVariable Long clubId,
            @RequestBody GenerationTransitionRequest request,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(generationService.executeTransition(clubId, request, userId));
    }
}
