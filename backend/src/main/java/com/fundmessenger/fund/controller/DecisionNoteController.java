package com.fundmessenger.fund.controller;

import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.club.repository.MemberTagRepository;
import com.fundmessenger.club.service.ClubAuthorizationService;
import com.fundmessenger.config.exception.ForbiddenException;
import com.fundmessenger.fund.dto.CreateDecisionNoteRequest;
import com.fundmessenger.fund.dto.DecisionNoteResponse;
import com.fundmessenger.fund.dto.UpdateDecisionNoteRequest;
import com.fundmessenger.fund.entity.DecisionNote;
import com.fundmessenger.fund.entity.NoteType;
import com.fundmessenger.fund.service.DecisionNoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clubs/{clubId}/fund/teams/{teamId}/notes")
@RequiredArgsConstructor
public class DecisionNoteController {

    private final DecisionNoteService noteService;
    private final ClubAuthorizationService authService;
    private final MemberTagRepository memberTagRepository;

    /** 노트 목록 (noteType 필터) */
    @GetMapping
    public ResponseEntity<List<DecisionNoteResponse>> getNotes(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            @RequestParam(required = false) String noteType,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        validateTeamMember(userId, clubId, teamId);

        NoteType type = noteType != null ? NoteType.valueOf(noteType.toUpperCase()) : null;
        List<DecisionNote> notes = noteService.getNotesByTeam(teamId, type);

        List<DecisionNoteResponse> responses = notes.stream()
                .map(DecisionNoteResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    /** 포지션별 노트 목록 */
    @GetMapping("/position/{positionId}")
    public ResponseEntity<List<DecisionNoteResponse>> getNotesByPosition(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            @PathVariable Long positionId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        validateTeamMember(userId, clubId, teamId);

        List<DecisionNote> notes = noteService.getNotesByPosition(positionId);

        List<DecisionNoteResponse> responses = notes.stream()
                .map(DecisionNoteResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    /** 노트 상세 */
    @GetMapping("/{noteId}")
    public ResponseEntity<DecisionNoteResponse> getNote(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            @PathVariable Long noteId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        validateTeamMember(userId, clubId, teamId);

        DecisionNote note = noteService.getNoteById(noteId);
        validateNoteBelongsToTeam(note, teamId);
        return ResponseEntity.ok(DecisionNoteResponse.from(note));
    }

    /** 노트 생성 (매니저) */
    @PostMapping
    public ResponseEntity<DecisionNoteResponse> createNote(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            @RequestBody @Valid CreateDecisionNoteRequest request,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        ClubMembership membership = authService.validateTeamManager(userId, clubId, teamId);

        DecisionNote note = noteService.createNote(teamId, request, membership);
        return ResponseEntity.ok(DecisionNoteResponse.from(note));
    }

    /** 노트 수정 (작성자 본인 또는 매니저) */
    @PatchMapping("/{noteId}")
    public ResponseEntity<DecisionNoteResponse> updateNote(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            @PathVariable Long noteId,
            @RequestBody UpdateDecisionNoteRequest request,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        ClubMembership membership = validateAuthorOrManager(userId, clubId, teamId, noteId);

        DecisionNote updated = noteService.updateNote(noteId, request, membership);
        return ResponseEntity.ok(DecisionNoteResponse.from(updated));
    }

    /** 노트 삭제 (작성자 본인 또는 매니저) */
    @DeleteMapping("/{noteId}")
    public ResponseEntity<Void> deleteNote(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            @PathVariable Long noteId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        validateAuthorOrManager(userId, clubId, teamId, noteId);

        noteService.deleteNote(noteId, null);
        return ResponseEntity.noContent().build();
    }

    /** 내보내기 토글 (매니저) */
    @PatchMapping("/{noteId}/export")
    public ResponseEntity<DecisionNoteResponse> toggleExport(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            @PathVariable Long noteId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        ClubMembership membership = authService.validateTeamManager(userId, clubId, teamId);

        DecisionNote note = noteService.getNoteById(noteId);
        validateNoteBelongsToTeam(note, teamId);

        DecisionNote toggled = noteService.toggleExport(noteId, membership);
        return ResponseEntity.ok(DecisionNoteResponse.from(toggled));
    }

    // ── Helpers ──

    private void validateTeamMember(Long userId, Long clubId, Long teamId) {
        ClubMembership membership = authService.validateClubMember(userId, clubId);
        if (authService.isExecutive(membership)) return;
        if (!memberTagRepository.existsByClubMembershipIdAndTeamId(membership.getId(), teamId)) {
            throw new SecurityException("해당 팀의 멤버가 아닙니다");
        }
    }

    /** 작성자 본인이거나 매니저이면 통과 — 둘 다 아니면 예외 */
    private ClubMembership validateAuthorOrManager(Long userId, Long clubId, Long teamId, Long noteId) {
        ClubMembership membership = authService.validateActiveMember(userId, clubId);
        DecisionNote note = noteService.getNoteById(noteId);
        validateNoteBelongsToTeam(note, teamId);

        // 작성자 본인이면 OK
        if (note.getAuthor().getId().equals(membership.getId())) {
            return membership;
        }
        // 매니저/팀장이면 OK
        try {
            authService.validateTeamManager(userId, clubId, teamId);
            return membership;
        } catch (Exception e) {
            throw new SecurityException("작성자 본인이거나 매니저만 수정/삭제할 수 있습니다");
        }
    }

    private void validateNoteBelongsToTeam(DecisionNote note, Long teamId) {
        if (!note.getTeam().getId().equals(teamId)) {
            throw new ForbiddenException("해당 팀의 노트가 아닙니다");
        }
    }
}
