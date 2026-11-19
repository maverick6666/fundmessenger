package com.fundmessenger.ai.controller;

import com.fundmessenger.ai.dto.AiEditorContentResponse;
import com.fundmessenger.ai.dto.AiGenerateRequest;
import com.fundmessenger.ai.dto.AiGenerationJobResponse;
import com.fundmessenger.ai.dto.AiPublishRequest;
import com.fundmessenger.ai.entity.AiGenerationJob;
import com.fundmessenger.ai.entity.AiGenerationJob.JobStatus;
import com.fundmessenger.ai.service.AiGenerationService;
import com.fundmessenger.ai.service.AiPermissionService;
import com.fundmessenger.auth.service.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/clubs/{clubId}/ai")
@RequiredArgsConstructor
public class AiGenerationController {

    private final AiGenerationService generationService;
    private final AiPermissionService permissionService;
    private final JwtService jwtService;

    /** AI 글작성 권한 확인 */
    @GetMapping("/permission")
    public ResponseEntity<Map<String, Boolean>> checkPermission(
            @PathVariable Long clubId,
            @RequestHeader("Authorization") String token) {
        Long userId = jwtService.getUserId(token.replace("Bearer ", ""));
        boolean hasPermission = permissionService.hasAiPermission(userId, clubId);
        return ResponseEntity.ok(Map.of("hasPermission", hasPermission));
    }

    /** AI 글 생성 요청 */
    @PostMapping("/generate")
    public ResponseEntity<AiGenerationJobResponse> generate(
            @PathVariable Long clubId,
            @Valid @RequestBody AiGenerateRequest request,
            @RequestHeader("Authorization") String token) {
        Long userId = jwtService.getUserId(token.replace("Bearer ", ""));
        AiGenerationJob job = generationService.requestGeneration(
                clubId, request.getDriveFileId(), request.getBoardId(), request.getTeamId(), request.getSemiCategoryIds(), userId);
        return ResponseEntity.ok(AiGenerationJobResponse.from(job));
    }

    /** 작업 상태 조회 */
    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<AiGenerationJobResponse> getJob(
            @PathVariable Long clubId,
            @PathVariable Long jobId,
            @RequestHeader("Authorization") String token) {
        Long userId = jwtService.getUserId(token.replace("Bearer ", ""));
        AiGenerationJob job = generationService.getJob(jobId, userId);
        return ResponseEntity.ok(AiGenerationJobResponse.from(job));
    }

    /** 내 작업 목록 */
    @GetMapping("/jobs")
    public ResponseEntity<List<AiGenerationJobResponse>> getMyJobs(
            @PathVariable Long clubId,
            @RequestParam(required = false) String status,
            @RequestHeader("Authorization") String token) {
        Long userId = jwtService.getUserId(token.replace("Bearer ", ""));
        JobStatus jobStatus = status != null ? JobStatus.valueOf(status) : null;
        return ResponseEntity.ok(
                generationService.getMyJobs(clubId, userId, jobStatus).stream()
                        .map(AiGenerationJobResponse::from)
                        .toList());
    }

    /** 바로 발행 */
    @PostMapping("/jobs/{jobId}/publish")
    public ResponseEntity<Map<String, Long>> publish(
            @PathVariable Long clubId,
            @PathVariable Long jobId,
            @RequestBody(required = false) AiPublishRequest request,
            @RequestHeader("Authorization") String token) {
        Long userId = jwtService.getUserId(token.replace("Bearer ", ""));
        List<Long> teamIds = request != null ? request.getTeamIds() : null;
        List<Long> boardIds = request != null ? request.getBoardIds() : null;
        Long postId = generationService.publishResult(jobId, userId, teamIds, boardIds);
        return ResponseEntity.ok(Map.of("postId", postId));
    }

    /** 에디터로 전달 */
    @PostMapping("/jobs/{jobId}/to-editor")
    public ResponseEntity<AiEditorContentResponse> toEditor(
            @PathVariable Long clubId,
            @PathVariable Long jobId,
            @RequestHeader("Authorization") String token) {
        Long userId = jwtService.getUserId(token.replace("Bearer ", ""));
        AiGenerationJob job = generationService.getEditorContent(jobId, userId);
        return ResponseEntity.ok(AiEditorContentResponse.from(job));
    }

    /** 기존 AI 결과를 바탕으로 재생성 */
    @PostMapping("/jobs/{jobId}/regenerate")
    public ResponseEntity<AiGenerationJobResponse> regenerate(
            @PathVariable Long clubId,
            @PathVariable Long jobId,
            @RequestHeader("Authorization") String token) {
        Long userId = jwtService.getUserId(token.replace("Bearer ", ""));
        AiGenerationJob regenerated = generationService.regenerate(jobId, userId);
        return ResponseEntity.ok(AiGenerationJobResponse.from(regenerated));
    }
}
