package com.fundmessenger.board.service;

import com.fundmessenger.ai.entity.AiGenerationJob;
import com.fundmessenger.ai.repository.AiGenerationJobRepository;
import com.fundmessenger.ai.service.AiGenerationService;
import com.fundmessenger.board.dto.*;
import com.fundmessenger.board.entity.*;
import com.fundmessenger.board.repository.BoardRepository;
import com.fundmessenger.board.repository.PostCommentRepository;
import com.fundmessenger.board.repository.PostLikeRepository;
import com.fundmessenger.board.repository.PostRepository;
import com.fundmessenger.board.repository.SemiCategoryRepository;
import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.club.entity.MemberTag;
import com.fundmessenger.club.entity.TagType;
import com.fundmessenger.club.entity.Team;
import com.fundmessenger.config.InputSanitizer;
import com.fundmessenger.club.repository.MemberTagRepository;
import com.fundmessenger.club.repository.TeamRepository;
import com.fundmessenger.club.service.ClubAuthorizationService;
import com.fundmessenger.drive.entity.DriveFile;
import com.fundmessenger.drive.repository.DriveFileRepository;
import com.fundmessenger.file.service.FileStorageService;
import com.fundmessenger.notification.entity.NotificationType;
import com.fundmessenger.config.exception.BadRequestException;
import com.fundmessenger.config.exception.ForbiddenException;
import com.fundmessenger.config.exception.NotFoundException;
import com.fundmessenger.notification.event.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BoardService {

    private final BoardRepository boardRepository;
    private final PostRepository postRepository;
    private final PostCommentRepository postCommentRepository;
    private final PostLikeRepository postLikeRepository;
    private final TeamRepository teamRepository;
    private final MemberTagRepository memberTagRepository;
    private final SemiCategoryRepository semiCategoryRepository;
    private final AiGenerationJobRepository aiGenerationJobRepository;
    private final AiGenerationService aiGenerationService;
    private final DriveFileRepository driveFileRepository;
    private final ClubAuthorizationService authService;
    private final FileStorageService fileStorageService;
    private final ApplicationEventPublisher eventPublisher;

    private String normalizeContentJson(String contentJson) {
        if (contentJson == null) return null;
        String trimmed = contentJson.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    // ========== 게시판 CRUD ==========

    @Transactional(readOnly = true)
    public List<BoardResponse> getBoards(Long clubId, Long userId) {
        ClubMembership membership = authService.validateClubMember(userId, clubId);

        // 배치 카운트로 N+1 방지
        Map<Long, Long> postCountMap = buildPostCountMap(clubId);

        return boardRepository.findByClubIdOrderByDisplayOrderAscCreatedAtAsc(clubId).stream()
                .map(board -> {
                    long postCount = postCountMap.getOrDefault(board.getId(), 0L);
                    boolean canWrite = checkCanWrite(board, membership);
                    return BoardResponse.from(board, postCount, canWrite);
                })
                .toList();
    }

    /** 게시판 생성 (임원급) — boardType=CUSTOM 고정 */
    @Transactional
    public BoardResponse createBoard(Long clubId, CreateBoardRequest request, Long userId) {
        ClubMembership membership = authService.validateClubAdmin(userId, clubId);

        boardRepository.findByClubIdAndName(clubId, request.getName().trim())
                .ifPresent(b -> {
                    throw new BadRequestException("이미 같은 이름의 게시판이 존재합니다: " + request.getName());
                });

        WritePermission wp = request.getWritePermission() != null
                ? request.getWritePermission() : WritePermission.EVERYONE;

        Board board = boardRepository.save(Board.builder()
                .name(request.getName().trim())
                .club(membership.getClub())
                .boardType(BoardType.CUSTOM)
                .writePermission(wp)
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
                .description(request.getDescription())
                .build());

        log.info("게시판 생성: clubId={}, boardId={}, name={}, boardType=CUSTOM", clubId, board.getId(), board.getName());
        return BoardResponse.from(board, 0, true);
    }

    @Transactional
    public BoardResponse updateBoard(Long clubId, Long boardId, UpdateBoardRequest request, Long userId) {
        authService.validateClubAdmin(userId, clubId);

        Board board = getBoardOrThrow(boardId, clubId);

        String newName = request.getName().trim();
        if (!board.getName().equals(newName)) {
            boardRepository.findByClubIdAndName(clubId, newName)
                    .ifPresent(b -> {
                        throw new BadRequestException("이미 같은 이름의 게시판이 존재합니다: " + newName);
                    });
            board.setName(newName);
        }

        if (request.getWritePermission() != null) {
            board.setWritePermission(request.getWritePermission());
        }

        if (request.getDisplayOrder() != null) {
            board.setDisplayOrder(request.getDisplayOrder());
        }
        board.setDescription(request.getDescription());

        long postCount = postRepository.countByBoardId(boardId);
        return BoardResponse.from(board, postCount, true);
    }

    /** 게시판 일괄 순서 변경 */
    @Transactional
    public void reorderBoards(Long clubId, List<Long> boardIds, Long userId) {
        authService.validateClubAdmin(userId, clubId);

        List<Board> boards = boardRepository.findByClubIdOrderByDisplayOrderAscCreatedAtAsc(clubId);
        var boardMap = boards.stream().collect(Collectors.toMap(Board::getId, b -> b));

        for (int i = 0; i < boardIds.size(); i++) {
            Board board = boardMap.get(boardIds.get(i));
            if (board != null) {
                board.setDisplayOrder(i);
            }
        }
        log.info("게시판 순서 변경: clubId={}, count={}", clubId, boardIds.size());
    }

    /** 게시판 삭제 — AI 게시판은 삭제 불가. M:N: post_boards만 해제 */
    @Transactional
    public void deleteBoard(Long clubId, Long boardId, Long userId) {
        authService.validateClubAdmin(userId, clubId);

        Board board = getBoardOrThrow(boardId, clubId);

        if (board.getBoardType() == BoardType.AI) {
            throw new BadRequestException("AI 게시판은 삭제할 수 없습니다");
        }

        // M:N: post_boards 연결만 해제 (Post는 유지, 게시판 0개 상태 허용)
        postRepository.unlinkAllFromBoard(boardId);
        boardRepository.delete(board);
        log.info("게시판 삭제: clubId={}, boardId={}, name={}", clubId, boardId, board.getName());
    }

    // ========== 사이드바 ==========

    /** 사이드바 데이터: 게시판 + 팀 + 태그 한 번에 */
    @Transactional(readOnly = true)
    public BoardSidebarResponse getSidebarData(Long clubId, Long userId) {
        ClubMembership membership = authService.validateClubMember(userId, clubId);

        List<Board> boards = boardRepository.findByClubIdOrderByDisplayOrderAscCreatedAtAsc(clubId);

        // 배치 카운트로 N+1 방지
        Map<Long, Long> postCountMap = buildPostCountMap(clubId);

        List<BoardSidebarResponse.BoardSidebarItem> boardItems = boards.stream()
                .map(b -> BoardSidebarResponse.BoardSidebarItem.builder()
                        .id(b.getId())
                        .name(b.getName())
                        .boardType(b.getBoardType().name())
                        .postCount(postCountMap.getOrDefault(b.getId(), 0L))
                        .canWrite(checkCanWrite(b, membership))
                        .build())
                .toList();

        List<Team> teams = teamRepository.findByClubIdAndDeletedFalseOrderByCreatedAtAsc(clubId);
        List<Long> teamIds = teams.stream().map(Team::getId).toList();
        List<MemberTag> allTeamTags = teamIds.isEmpty() ? List.of() : memberTagRepository.findByTeamIdIn(teamIds);
        Map<Long, Long> teamMemberCounts = allTeamTags.stream()
                .collect(Collectors.groupingBy(mt -> mt.getTeam().getId(), Collectors.counting()));

        // 팀별 게시글 수 배치 조회
        Map<Long, Long> teamPostCounts = postRepository.countByTeamGrouped(clubId).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]
                ));

        List<BoardSidebarResponse.TeamSidebarItem> teamItems = teams.stream()
                .map(t -> BoardSidebarResponse.TeamSidebarItem.builder()
                        .id(t.getId())
                        .name(t.getName())
                        .description(t.getDescription())
                        .memberCount(teamMemberCounts.getOrDefault(t.getId(), 0L))
                        .postCount(teamPostCounts.getOrDefault(t.getId(), 0L))
                        .teamType(t.getTeamType() != null ? t.getTeamType().name() : "CUSTOM")
                        .systemTeam(t.isSystemTeam())
                        .build())
                .toList();

        List<SemiCategoryResponse> tags = semiCategoryRepository
                .findByClubIdOrderByDisplayOrderAscCreatedAtAsc(clubId).stream()
                .filter(sc -> !sc.isGeneration())
                .filter(sc -> sc.isEnabled())
                .map(SemiCategoryResponse::from)
                .toList();

        return BoardSidebarResponse.builder()
                .boards(boardItems)
                .teams(teamItems)
                .tags(tags)
                .build();
    }

    // ========== 글 CRUD ==========

    /** 글 목록 (게시판별 + 태그 필터) */
    @Transactional(readOnly = true)
    public Page<PostResponse> getPosts(Long clubId, Long boardId, Long tagId, int page, int size, Long userId) {
        authService.validateClubMember(userId, clubId);
        getBoardOrThrow(boardId, clubId);

        return postRepository.findByBoardWithTagFilter(boardId, tagId, PageRequest.of(page, size))
                .map(PostResponse::from);
    }

    /** Cross-board: 팀별 + 태그 교차 필터 (다중 선택 지원) */
    @Transactional(readOnly = true)
    public Page<PostResponse> getClubPosts(Long clubId, List<Long> teamIds, List<Long> tagIds, int page, int size, Long userId) {
        authService.validateClubMember(userId, clubId);

        boolean hasTeams = teamIds != null && !teamIds.isEmpty();
        boolean hasTags = tagIds != null && !tagIds.isEmpty();
        PageRequest pageable = PageRequest.of(page, size);

        if (hasTeams && hasTags) {
            return postRepository.findByTeamIdsAndTagIds(clubId, teamIds, tagIds, pageable).map(PostResponse::from);
        } else if (hasTeams) {
            return postRepository.findByTeamIds(clubId, teamIds, pageable).map(PostResponse::from);
        } else if (hasTags) {
            return postRepository.findByTagIds(clubId, tagIds, pageable).map(PostResponse::from);
        } else {
            return postRepository.findWithFilters(clubId, null, null, pageable).map(PostResponse::from);
        }
    }

    /** 글 상세 — postId만으로 조회 */
    @Transactional(readOnly = true)
    public PostDetailResponse getPostById(Long clubId, Long postId, Long userId) {
        ClubMembership membership = authService.validateClubMember(userId, clubId);

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException("글을 찾을 수 없습니다: " + postId));

        validatePostBelongsToClub(post, clubId);

        boolean liked = postLikeRepository.existsByPostIdAndMemberId(postId, membership.getId());
        return withDriveFileUrl(PostDetailResponse.from(post, liked));
    }

    @Transactional(readOnly = true)
    public PostDetailResponse getPost(Long clubId, Long boardId, Long postId, Long userId) {
        ClubMembership membership = authService.validateClubMember(userId, clubId);
        getBoardOrThrow(boardId, clubId);

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException("글을 찾을 수 없습니다: " + postId));
        // M:N: 해당 게시판에 속하는지 확인
        boolean inBoard = post.getBoards().stream().anyMatch(b -> b.getId().equals(boardId));
        if (!inBoard) {
            throw new BadRequestException("해당 게시판의 글이 아닙니다");
        }
        boolean liked = postLikeRepository.existsByPostIdAndMemberId(postId, membership.getId());
        return withDriveFileUrl(PostDetailResponse.from(post, liked));
    }

    /** 글 작성 — M:N 게시판 + M:N 태그 (최소 1개씩) */
    @Transactional
    public PostDetailResponse createPost(Long clubId, CreatePostRequest request, Long userId) {
        ClubMembership membership = authService.validateClubMember(userId, clubId);

        // 게시판 M:N 해석
        Set<Board> boards = resolveBoards(clubId, request.getBoardIds(), membership);

        Set<SemiCategory> categories = resolveCategories(clubId, request.getSemiCategoryIds());
        Set<Team> teams = resolveTeams(clubId, request.getTeamIds());
        String sanitizedTitle = InputSanitizer.sanitizePlainText(request.getTitle().trim());
        String sanitizedContent = InputSanitizer.sanitizeMarkdown(request.getContent());
        String normalizedContentJson = normalizeContentJson(request.getContentJson());

        AiGenerationJob aiJob = null;
        DriveFile linkedDriveFile = null;
        boolean aiGenerated = false;
        boolean aiEditedOnCreate = false;

        if (request.getAiJobId() != null) {
            aiJob = aiGenerationJobRepository.findDetailedById(request.getAiJobId())
                    .orElseThrow(() -> new BadRequestException("AI 생성 작업을 찾을 수 없습니다: " + request.getAiJobId()));

            if (!aiJob.getRequestedBy().getId().equals(membership.getId())) {
                throw new ForbiddenException("본인의 AI 생성 작업만 게시할 수 있습니다");
            }
            if (!aiJob.getClub().getId().equals(clubId)) {
                throw new BadRequestException("해당 동아리의 AI 생성 작업이 아닙니다");
            }
            if (aiJob.getStatus() != AiGenerationJob.JobStatus.COMPLETED) {
                throw new BadRequestException("완료된 AI 생성 작업만 게시할 수 있습니다");
            }

            linkedDriveFile = aiJob.getDriveFile();
            /*
            if (linkedDriveFile.getLinkedPost() != null) {
                throw new BadRequestException("이미 게시된 AI 글입니다");
            }

            */
            if (linkedDriveFile.getLinkedPost() != null) {
                throw new BadRequestException("이미 게시된 AI 글입니다");
            }

            aiGenerated = true;
            aiEditedOnCreate = !Objects.equals(
                    InputSanitizer.sanitizeMarkdown(aiJob.getResultMarkdown()),
                    sanitizedContent
            );
        }

        Post post = Post.builder()
                .boards(boards)
                .teams(teams)
                .author(membership)
                .title(sanitizedTitle)
                .content(sanitizedContent)
                .contentJson(normalizedContentJson)
                .semiCategories(categories)
                .linkedDriveFile(linkedDriveFile)
                .aiGenerated(aiGenerated)
                .aiEdited(aiEditedOnCreate)
                .build();

        post = postRepository.save(post);
        if (linkedDriveFile != null) {
            linkedDriveFile.setLinkedPost(post);
            driveFileRepository.save(linkedDriveFile);
        }
        log.info("글 작성: boards={}, postId={}, author={}", request.getBoardIds(), post.getId(), membership.getName());

        // 알림: 팀 게시판에 글 작성 시 해당 팀 멤버들에게 TEAM_ACTIVITY 알림
        publishTeamActivityForPost(clubId, post, membership);

        return withDriveFileUrl(PostDetailResponse.from(post));
    }

    /** 글 수정 — M:N 게시판 + 태그 교체 */
    @Transactional
    public PostDetailResponse updatePost(Long clubId, Long postId, CreatePostRequest request, Long userId) {
        ClubMembership membership = authService.validateClubMember(userId, clubId);

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException("글을 찾을 수 없습니다: " + postId));
        validatePostBelongsToClub(post, clubId);
        validatePostAuthorOrAdmin(post, membership);

        post.setTitle(InputSanitizer.sanitizePlainText(request.getTitle().trim()));
        post.setContent(InputSanitizer.sanitizeMarkdown(request.getContent()));
        post.setContentJson(normalizeContentJson(request.getContentJson()));

        // 게시판 M:N 교체
        Set<Board> boards = resolveBoards(clubId, request.getBoardIds(), membership);
        post.getBoards().clear();
        post.getBoards().addAll(boards);

        // 태그 교체
        Set<SemiCategory> categories = resolveCategories(clubId, request.getSemiCategoryIds());
        post.getSemiCategories().clear();
        post.getSemiCategories().addAll(categories);

        // 팀 교체
        Set<Team> teams = resolveTeams(clubId, request.getTeamIds());
        post.getTeams().clear();
        post.getTeams().addAll(teams);

        if (post.isAiGenerated()) {
            post.setAiEdited(true);
        }

        return withDriveFileUrl(PostDetailResponse.from(post));
    }

    @Transactional
    public void deletePost(Long clubId, Long postId, Long userId) {
        ClubMembership membership = authService.validateClubMember(userId, clubId);

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException("글을 찾을 수 없습니다: " + postId));
        validatePostBelongsToClub(post, clubId);
        validatePostAuthorOrAdmin(post, membership);

        // 연동된 DriveFile FK 해제
        DriveFile linkedFile = post.getLinkedDriveFile();
        if (linkedFile != null) {
            linkedFile.setLinkedPost(null);
            log.info("Post 삭제 → DriveFile FK 해제: driveFileId={}", linkedFile.getId());
        }

        // M:N 연결 해제
        post.getBoards().clear();
        post.getSemiCategories().clear();
        post.getTeams().clear();

        postCommentRepository.deleteByPostId(postId);
        postRepository.delete(post);
        log.info("글 삭제: postId={}", postId);
    }

    @Transactional(readOnly = true)
    public Page<PostResponse> getMyPosts(Long clubId, int page, int size, Long tagId, Long userId) {
        ClubMembership membership = authService.validateClubMember(userId, clubId);
        if (tagId != null) {
            return postRepository.findByAuthorIdAndClubIdAndSemiCategoryId(
                    membership.getId(), clubId, tagId, PageRequest.of(page, size))
                    .map(PostResponse::from);
        }
        return postRepository.findByAuthorIdAndClubId(membership.getId(), clubId, PageRequest.of(page, size))
                .map(PostResponse::from);
    }

    // ========== 추천(좋아요) ==========

    @Transactional
    public Map<String, Object> toggleLike(Long clubId, Long postId, Long userId) {
        ClubMembership membership = authService.validateClubMember(userId, clubId);
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException("게시글을 찾을 수 없습니다"));

        // clubId 검증 (M:N)
        boolean belongsToClub = post.getBoards().stream()
                .anyMatch(b -> b.getClub().getId().equals(clubId));
        if (!belongsToClub && !post.getAuthor().getClub().getId().equals(clubId)) {
            throw new NotFoundException("게시글을 찾을 수 없습니다");
        }

        Optional<PostLike> existing = postLikeRepository.findByPostIdAndMemberId(postId, membership.getId());
        boolean liked;

        if (existing.isPresent()) {
            postLikeRepository.delete(existing.get());
            postRepository.decrementLikeCount(postId);
            liked = false;
        } else {
            PostLike like = PostLike.builder()
                    .post(post)
                    .member(membership)
                    .build();
            postLikeRepository.save(like);
            postRepository.incrementLikeCount(postId);
            liked = true;
        }

        // 알림: 좋아요 (좋아요 취소 시에는 알림 안 보냄)
        if (liked) {
            Long authorUserId = post.getAuthor().getUser().getId();
            if (!authorUserId.equals(userId)) {
                eventPublisher.publishEvent(NotificationEvent.of(
                        clubId, authorUserId, NotificationType.CONTENT_LIKED,
                        membership.getName() + "님이 게시글을 추천했습니다",
                        post.getTitle(),
                        "/club/boards/posts/" + postId
                ));
            }
        }

        // Re-read to get the atomically updated count
        int updatedCount = postRepository.findById(postId)
                .map(Post::getLikeCount).orElse(0);
        return Map.of("liked", liked, "likeCount", updatedCount);
    }

    // ========== Private ==========

    private Set<Board> resolveBoards(Long clubId, List<Long> boardIds, ClubMembership membership) {
        if (boardIds == null || boardIds.isEmpty()) {
            throw new BadRequestException("게시판을 최소 1개 선택해야 합니다");
        }
        Set<Board> boards = new HashSet<>();
        for (Long boardId : boardIds) {
            Board board = boardRepository.findById(boardId)
                    .orElseThrow(() -> new NotFoundException("게시판을 찾을 수 없습니다: " + boardId));
            if (!board.getClub().getId().equals(clubId)) {
                throw new ForbiddenException("해당 동아리의 게시판이 아닙니다");
            }
            validateWritePermission(board, membership);
            boards.add(board);
        }
        return boards;
    }

    private Set<Team> resolveTeams(Long clubId, List<Long> teamIds) {
        if (teamIds == null || teamIds.isEmpty()) {
            return new HashSet<>(); // 무소속
        }
        List<Team> teams = teamRepository.findAllById(teamIds);
        if (teams.size() != teamIds.size()) {
            throw new BadRequestException("존재하지 않는 팀이 포함되어 있습니다");
        }
        for (Team team : teams) {
            if (!team.getClub().getId().equals(clubId)) {
                throw new ForbiddenException("해당 동아리의 팀이 아닙니다");
            }
        }
        return new HashSet<>(teams);
    }

    private Set<SemiCategory> resolveCategories(Long clubId, List<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            throw new BadRequestException("태그를 최소 1개 선택해야 합니다");
        }

        Set<SemiCategory> categories = new HashSet<>();
        for (Long catId : categoryIds) {
            SemiCategory cat = semiCategoryRepository.findById(catId)
                    .orElseThrow(() -> new NotFoundException("태그를 찾을 수 없습니다: " + catId));
            if (!cat.getClub().getId().equals(clubId)) {
                throw new ForbiddenException("해당 동아리의 태그가 아닙니다");
            }
            categories.add(cat);
        }
        return categories;
    }

    private Board getBoardOrThrow(Long boardId, Long clubId) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new NotFoundException("게시판을 찾을 수 없습니다: " + boardId));
        if (!board.getClub().getId().equals(clubId)) {
            throw new ForbiddenException("해당 동아리의 게시판이 아닙니다");
        }
        return board;
    }

    /** SEC-2/3: 게시글이 해당 동아리 소속인지 검증 (boards M:N 또는 author 기준) */
    private void validatePostBelongsToClub(Post post, Long clubId) {
        boolean belongsToClub = post.getBoards().stream()
                .anyMatch(b -> b.getClub().getId().equals(clubId));
        if (!belongsToClub && !post.getAuthor().getClub().getId().equals(clubId)) {
            throw new ForbiddenException("해당 동아리의 글이 아닙니다");
        }
    }

    private void validatePostAuthorOrAdmin(Post post, ClubMembership membership) {
        boolean isAuthor = post.getAuthor().getId().equals(membership.getId());
        boolean isAdmin = authService.isExecutive(membership);
        if (!isAuthor && !isAdmin) {
            throw new ForbiddenException("글을 수정/삭제할 권한이 없습니다");
        }
    }

    private void validateWritePermission(Board board, ClubMembership membership) {
        switch (board.getWritePermission()) {
            case EVERYONE -> {}
            case EXECUTIVE_ONLY -> {
                if (!authService.isExecutive(membership)) {
                    throw new ForbiddenException("임원급만 글을 작성할 수 있습니다");
                }
            }
        }
    }

    /** 동아리 내 모든 게시판의 글 수를 배치로 조회 (N+1 방지) */
    private Map<Long, Long> buildPostCountMap(Long clubId) {
        return postRepository.countPostsByClubBoards(clubId).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]
                ));
    }

    private boolean checkCanWrite(Board board, ClubMembership membership) {
        try {
            validateWritePermission(board, membership);
            return true;
        } catch (ForbiddenException | IllegalStateException e) {
            return false;
        }
    }

    /** 글 작성 시 Post.teams의 팀 멤버들에게 TEAM_ACTIVITY 알림 발행 */
    private void publishTeamActivityForPost(Long clubId, Post post, ClubMembership author) {
        Long authorUserId = author.getUser().getId();

        Set<Long> teamIds = post.getTeams().stream()
                .map(Team::getId)
                .collect(Collectors.toSet());

        if (teamIds.isEmpty()) return;

        // 배치 로드로 N+1 방지
        List<MemberTag> allTags = memberTagRepository.findByTeamIdIn(new ArrayList<>(teamIds));
        Map<Long, List<Long>> teamToUserIds = allTags.stream()
                .collect(Collectors.groupingBy(
                        mt -> mt.getTeam().getId(),
                        Collectors.mapping(mt -> mt.getClubMembership().getUser().getId(),
                                Collectors.collectingAndThen(Collectors.toSet(), ArrayList::new))
                ));

        for (Long teamId : teamIds) {
            List<Long> targetUserIds = teamToUserIds.getOrDefault(teamId, List.of()).stream()
                    .filter(uid -> !uid.equals(authorUserId))
                    .toList();

            if (!targetUserIds.isEmpty()) {
                eventPublisher.publishEvent(NotificationEvent.ofTeamActivity(
                        clubId, targetUserIds, teamId,
                        author.getName() + "님이 새 글을 작성했습니다",
                        post.getTitle(),
                        "/club/boards/posts/" + post.getId()
                ));
            }
        }
    }

    /** PostDetailResponse에 linkedDriveFileUrl 세팅 (GCS 호환) */
    private PostDetailResponse withDriveFileUrl(PostDetailResponse resp) {
        if (resp.getLinkedDriveFileStoredName() != null) {
            return PostDetailResponse.builder()
                    .id(resp.getId())
                    .boards(resp.getBoards())
                    .title(resp.getTitle())
                    .content(resp.getContent())
                    .contentJson(resp.getContentJson())
                    .authorName(resp.getAuthorName())
                    .authorMembershipId(resp.getAuthorMembershipId())
                    .semiCategories(resp.getSemiCategories())
                    .teams(resp.getTeams())
                    .linkedDriveFileId(resp.getLinkedDriveFileId())
                    .linkedDriveFileName(resp.getLinkedDriveFileName())
                    .linkedDriveFileStoredName(resp.getLinkedDriveFileStoredName())
                    .linkedDriveFileUrl(fileStorageService.getPublicUrl(resp.getLinkedDriveFileStoredName()))
                    .aiGenerated(resp.isAiGenerated())
                    .aiEdited(resp.isAiEdited())
                    .likeCount(resp.getLikeCount())
                    .liked(resp.isLiked())
                    .createdAt(resp.getCreatedAt())
                    .updatedAt(resp.getUpdatedAt())
                    .build();
        }
        return resp;
    }
}
