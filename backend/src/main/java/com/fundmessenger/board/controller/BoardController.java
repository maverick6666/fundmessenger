package com.fundmessenger.board.controller;

import com.fundmessenger.board.dto.*;
import com.fundmessenger.board.service.BoardService;
import com.fundmessenger.board.service.PostCommentService;
import com.fundmessenger.board.service.SemiCategoryService;
import com.fundmessenger.club.service.ClubAuthorizationService;
import com.fundmessenger.config.exception.BadRequestException;
import com.fundmessenger.config.exception.ForbiddenException;
import com.fundmessenger.config.exception.NotFoundException;
import com.fundmessenger.fund.dto.PositionResponse;
import com.fundmessenger.fund.entity.TeamSettings;
import com.fundmessenger.fund.repository.PositionRepository;
import com.fundmessenger.fund.service.PositionService;
import com.fundmessenger.fund.service.TeamSettingsService;
import com.fundmessenger.club.entity.Team;
import com.fundmessenger.club.entity.TeamType;
import com.fundmessenger.club.repository.TeamRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/clubs/{clubId}")
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;
    private final PostCommentService postCommentService;
    private final SemiCategoryService semiCategoryService;
    private final PositionService positionService;
    private final TeamSettingsService teamSettingsService;
    private final ClubAuthorizationService authService;
    private final TeamRepository teamRepository;
    private final PositionRepository positionRepository;

    // ========== 게시판 (읽기 — 멤버 누구나) ==========

    /** 게시판 목록 */
    @GetMapping("/boards")
    public ResponseEntity<List<BoardResponse>> getBoards(
            @PathVariable Long clubId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(boardService.getBoards(clubId, userId));
    }

    // ========== 게시판 (관리 — 임원급) ==========

    /** 게시판 생성 */
    @PostMapping("/admin/boards")
    public ResponseEntity<BoardResponse> createBoard(
            @PathVariable Long clubId,
            @RequestBody @Valid CreateBoardRequest request,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(boardService.createBoard(clubId, request, userId));
    }

    /** 게시판 수정 */
    @PutMapping("/admin/boards/{boardId}")
    public ResponseEntity<BoardResponse> updateBoard(
            @PathVariable Long clubId,
            @PathVariable Long boardId,
            @RequestBody @Valid UpdateBoardRequest request,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(boardService.updateBoard(clubId, boardId, request, userId));
    }

    /** 게시판 일괄 순서 변경 */
    @PatchMapping("/admin/boards/reorder")
    public ResponseEntity<Void> reorderBoards(
            @PathVariable Long clubId,
            @RequestBody java.util.Map<String, List<Long>> body,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        boardService.reorderBoards(clubId, body.get("boardIds"), userId);
        return ResponseEntity.noContent().build();
    }

    /** 게시판 삭제 */
    @DeleteMapping("/admin/boards/{boardId}")
    public ResponseEntity<Void> deleteBoard(
            @PathVariable Long clubId,
            @PathVariable Long boardId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        boardService.deleteBoard(clubId, boardId, userId);
        return ResponseEntity.noContent().build();
    }

    // ========== 사이드바 ==========

    /** 사이드바 데이터 (게시판 목록 + 팀 목록 + 태그 목록) */
    @GetMapping("/boards/sidebar")
    public ResponseEntity<BoardSidebarResponse> getSidebar(
            @PathVariable Long clubId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(boardService.getSidebarData(clubId, userId));
    }

    // ========== 글 ==========

    /** Cross-board 글 목록 (팀별 + 태그 교차 필터, 다중 선택 지원) */
    @GetMapping("/posts")
    public ResponseEntity<Page<PostResponse>> getClubPosts(
            @PathVariable Long clubId,
            @RequestParam(required = false) List<Long> teamIds,
            @RequestParam(required = false) List<Long> tagIds,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(boardService.getClubPosts(clubId, teamIds, tagIds, page, size, userId));
    }

    /** 내가 쓴 글 목록 (동아리 전체, 태그 필터 선택) */
    @GetMapping("/posts/mine")
    public ResponseEntity<Page<PostResponse>> getMyPosts(
            @PathVariable Long clubId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long tagId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(boardService.getMyPosts(clubId, page, size, tagId, userId));
    }

    /** 게시판별 글 목록 (페이지네이션 + 태그 필터) */
    @GetMapping("/boards/{boardId}/posts")
    public ResponseEntity<Page<PostResponse>> getPosts(
            @PathVariable Long clubId,
            @PathVariable Long boardId,
            @RequestParam(required = false) Long tagId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(boardService.getPosts(clubId, boardId, tagId, page, size, userId));
    }

    /** 글 상세 (postId만으로 조회) */
    @GetMapping("/posts/{postId}")
    public ResponseEntity<PostDetailResponse> getPostById(
            @PathVariable Long clubId,
            @PathVariable Long postId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(boardService.getPostById(clubId, postId, userId));
    }

    /** 글 상세 (boardId + postId) */
    @GetMapping("/boards/{boardId}/posts/{postId}")
    public ResponseEntity<PostDetailResponse> getPost(
            @PathVariable Long clubId,
            @PathVariable Long boardId,
            @PathVariable Long postId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(boardService.getPost(clubId, boardId, postId, userId));
    }

    /** 글 작성 (M:N — boardIds는 request body) */
    @PostMapping("/posts")
    public ResponseEntity<PostDetailResponse> createPost(
            @PathVariable Long clubId,
            @RequestBody @Valid CreatePostRequest request,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(boardService.createPost(clubId, request, userId));
    }

    /** 글 수정 (M:N — boardIds는 request body) */
    @PutMapping("/posts/{postId}")
    public ResponseEntity<PostDetailResponse> updatePost(
            @PathVariable Long clubId,
            @PathVariable Long postId,
            @RequestBody @Valid CreatePostRequest request,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(boardService.updatePost(clubId, postId, request, userId));
    }

    /** 글 삭제 */
    @DeleteMapping("/posts/{postId}")
    public ResponseEntity<Void> deletePost(
            @PathVariable Long clubId,
            @PathVariable Long postId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        boardService.deletePost(clubId, postId, userId);
        return ResponseEntity.noContent().build();
    }

    // ========== 추천(좋아요) ==========

    /** 게시글 추천 토글 */
    @PostMapping("/posts/{postId}/like")
    public ResponseEntity<Map<String, Object>> toggleLike(
            @PathVariable Long clubId,
            @PathVariable Long postId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(boardService.toggleLike(clubId, postId, userId));
    }

    // ========== 댓글 ==========

    /** 댓글 목록 */
    @GetMapping("/posts/{postId}/comments")
    public ResponseEntity<List<PostCommentResponse>> getComments(
            @PathVariable Long clubId,
            @PathVariable Long postId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(postCommentService.getComments(clubId, postId, userId));
    }

    /** 댓글 작성 */
    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<PostCommentResponse> createComment(
            @PathVariable Long clubId,
            @PathVariable Long postId,
            @RequestBody @Valid CreatePostCommentRequest request,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(postCommentService.createComment(clubId, postId, request.getContent(), request.getParentCommentId(), userId));
    }

    /** 댓글 삭제 */
    @DeleteMapping("/posts/{postId}/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable Long clubId,
            @PathVariable Long postId,
            @PathVariable Long commentId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        postCommentService.deleteComment(clubId, postId, commentId, userId);
        return ResponseEntity.noContent().build();
    }

    // ========== 세미카테고리 (읽기 — 멤버 누구나) ==========

    /** 세미카테고리 목록 */
    @GetMapping("/semi-categories")
    public ResponseEntity<List<SemiCategoryResponse>> getCategories(
            @PathVariable Long clubId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(semiCategoryService.getCategories(clubId, userId));
    }

    // ========== 세미카테고리 (관리 — 임원급) ==========

    /** 세미카테고리 생성 */
    @PostMapping("/admin/semi-categories")
    public ResponseEntity<SemiCategoryResponse> createCategory(
            @PathVariable Long clubId,
            @RequestBody @Valid CreateSemiCategoryRequest request,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(semiCategoryService.createCategory(clubId, request, userId));
    }

    /** 세미카테고리 수정 */
    @PutMapping("/admin/semi-categories/{categoryId}")
    public ResponseEntity<SemiCategoryResponse> updateCategory(
            @PathVariable Long clubId,
            @PathVariable Long categoryId,
            @RequestBody @Valid CreateSemiCategoryRequest request,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(semiCategoryService.updateCategory(clubId, categoryId, request, userId));
    }

    /** 세미카테고리 일괄 순서 변경 */
    @PatchMapping("/admin/semi-categories/reorder")
    public ResponseEntity<Void> reorderCategories(
            @PathVariable Long clubId,
            @RequestBody java.util.Map<String, List<Long>> body,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        semiCategoryService.reorderCategories(clubId, body.get("categoryIds"), userId);
        return ResponseEntity.noContent().build();
    }

    /** 세미카테고리 삭제 */
    @DeleteMapping("/admin/semi-categories/{categoryId}")
    public ResponseEntity<Void> deleteCategory(
            @PathVariable Long clubId,
            @PathVariable Long categoryId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        semiCategoryService.deleteCategory(clubId, categoryId, userId);
        return ResponseEntity.noContent().build();
    }

    // ========== 펀드팀 뷰 (공개용 — 동아리 멤버 누구나) ==========

    /** 펀드팀 포지션 목록 (공개용 — 게시판 펀드팀 뷰) */
    @GetMapping("/boards/fund-teams/{teamId}/positions")
    public ResponseEntity<List<PositionResponse>> getFundTeamPositions(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            @RequestParam(required = false) String status,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        authService.validateClubMember(userId, clubId);

        // 해당 팀이 FUND 타입인지 검증
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("팀", teamId));
        if (team.getTeamType() != TeamType.FUND) {
            throw new BadRequestException("펀드팀이 아닙니다");
        }
        if (!team.getClub().getId().equals(clubId)) {
            throw new ForbiddenException("해당 동아리의 팀이 아닙니다");
        }

        // 공개된 종목만 조회 (lastExportedAt 최신순)
        var positions = (status != null)
                ? positionRepository.findByTeamIdAndPublishedTrueAndStatusOrderByLastExportedAtDesc(
                        teamId, com.fundmessenger.fund.entity.PositionStatus.valueOf(status.toUpperCase()))
                : positionRepository.findByTeamIdAndPublishedTrueOrderByLastExportedAtDesc(teamId);
        var responses = positions.stream().map(positionService::toResponse).toList();
        return ResponseEntity.ok(responses);
    }

    /** 펀드팀 공개 종목 상세 (position + exported notes + executed requests) */
    @GetMapping("/boards/fund-teams/{teamId}/positions/{positionId}/detail")
    public ResponseEntity<Map<String, Object>> getFundTeamPositionDetail(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            @PathVariable Long positionId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        authService.validateClubMember(userId, clubId);

        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("팀", teamId));
        if (team.getTeamType() != TeamType.FUND) {
            throw new BadRequestException("펀드팀이 아닙니다");
        }
        if (!team.getClub().getId().equals(clubId)) {
            throw new ForbiddenException("해당 동아리의 팀이 아닙니다");
        }

        com.fundmessenger.fund.entity.Position position = positionRepository.findById(positionId)
                .orElseThrow(() -> new NotFoundException("포지션", positionId));
        if (!position.getTeam().getId().equals(teamId)) {
            throw new ForbiddenException("해당 팀의 포지션이 아닙니다");
        }
        if (!position.isPublished()) {
            throw new ForbiddenException("비공개 종목입니다");
        }

        var positionResponse = positionService.toResponse(position);
        var exportedNotes = positionService.getExportedNotesByPosition(positionId);
        var executedRequests = positionService.getExecutedRequestsByPosition(positionId);

        return ResponseEntity.ok(Map.of(
                "position", positionResponse,
                "notes", exportedNotes,
                "tradeHistory", executedRequests
        ));
    }

    /** 펀드팀 공개 설정 (수익률 공개 여부 등 — 동아리 멤버 누구나) */
    @GetMapping("/boards/fund-teams/{teamId}/settings")
    public ResponseEntity<Map<String, Boolean>> getFundTeamPublicSettings(
            @PathVariable Long clubId,
            @PathVariable Long teamId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        authService.validateClubMember(userId, clubId);

        // 팀이 해당 동아리 소속인지 검증 (IDOR 방지)
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("팀", teamId));
        if (!team.getClub().getId().equals(clubId)) {
            throw new ForbiddenException("해당 동아리의 팀이 아닙니다");
        }

        TeamSettings settings = teamSettingsService.getOrCreateSettings(teamId);
        return ResponseEntity.ok(Map.of("profitPublic", settings.isProfitPublic()));
    }

    // ========== 시스템 태그 관리 (임원급) ==========

    /** 시스템 태그 목록 */
    @GetMapping("/admin/semi-categories/system")
    public ResponseEntity<List<SemiCategoryResponse>> getSystemCategories(
            @PathVariable Long clubId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(semiCategoryService.getSystemCategories(clubId, userId));
    }

    /** 시스템 태그 활성화/비활성화 토글 */
    @PatchMapping("/admin/semi-categories/{categoryId}/toggle")
    public ResponseEntity<SemiCategoryResponse> toggleCategoryEnabled(
            @PathVariable Long clubId,
            @PathVariable Long categoryId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(semiCategoryService.toggleCategoryEnabled(clubId, categoryId, userId));
    }
}
