package com.fundmessenger.ai.service;

import com.fundmessenger.ai.entity.AiGenerationJob;
import com.fundmessenger.ai.entity.AiGenerationJob.JobStatus;
import com.fundmessenger.ai.event.AiGenerationRequestedEvent;
import com.fundmessenger.ai.repository.AiGenerationJobRepository;
import com.fundmessenger.ai.util.AiMarkdownTitleExtractor;
import com.fundmessenger.board.entity.Board;
import com.fundmessenger.board.entity.Post;
import com.fundmessenger.board.entity.SemiCategory;
import com.fundmessenger.board.repository.BoardRepository;
import com.fundmessenger.board.repository.PostRepository;
import com.fundmessenger.club.entity.*;
import com.fundmessenger.club.repository.MemberTagRepository;
import com.fundmessenger.club.repository.TeamRepository;
import com.fundmessenger.config.CerebrasClient;
import com.fundmessenger.drive.entity.DriveFile;
import com.fundmessenger.drive.repository.DriveFileRepository;
import com.fundmessenger.board.repository.SemiCategoryRepository;
import com.fundmessenger.file.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiGenerationService {

    private final AiGenerationJobRepository jobRepository;
    private final DriveFileRepository driveFileRepository;
    private final BoardRepository boardRepository;
    private final PostRepository postRepository;
    private final TeamRepository teamRepository;
    private final MemberTagRepository memberTagRepository;
    private final FileStorageService fileStorageService;
    private final PdfToImageService pdfToImageService;
    private final GeminiService geminiService;
    private final AiCerebrasService cerebrasService;
    private final SemiCategoryRepository semiCategoryRepository;
    private final AiPermissionService permissionService;
    private final CerebrasClient cerebrasClient;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * AI 글 생성 요청
     */
    @Transactional
    public AiGenerationJob requestGeneration(Long clubId, Long driveFileId, Long boardId, Long teamId, List<Long> semiCategoryIds, Long userId) {
        // 권한 검증
        ClubMembership membership = permissionService.validateAiPermission(userId, clubId);

        // 진행중 작업 확인 — 1건만 허용
        boolean hasActiveJob = jobRepository.existsByRequestedByIdAndStatusIn(
                membership.getId(), List.of(JobStatus.QUEUED, JobStatus.PROCESSING));
        if (hasActiveJob) {
            throw new IllegalStateException("요청 처리중입니다. 잠시 후 다시 시도하세요.");
        }

        // DriveFile 검증
        DriveFile driveFile = driveFileRepository.findById(driveFileId)
                .orElseThrow(() -> new IllegalArgumentException("파일을 찾을 수 없습니다"));

        if (!driveFile.getClub().getId().equals(clubId)) {
            throw new IllegalArgumentException("해당 동아리의 파일이 아닙니다");
        }

        if (driveFile.getMimeType() == null || !driveFile.getMimeType().contains("pdf")) {
            throw new IllegalArgumentException("PDF 파일만 AI 글 생성이 가능합니다");
        }

        // 이미 연동된 Post가 있으면 거부
        if (driveFile.getLinkedPost() != null) {
            throw new IllegalArgumentException("이미 생성된 글이 있습니다");
        }

        // Board 검증 (선택 — null이면 게시판 없이 생성)
        Board board = null;
        if (boardId != null) {
            board = boardRepository.findById(boardId)
                    .orElseThrow(() -> new IllegalArgumentException("게시판을 찾을 수 없습니다"));
        }

        // 팀 검증
        if (teamId == null) {
            throw new IllegalArgumentException("팀을 선택해야 합니다");
        }
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("팀을 찾을 수 없습니다: " + teamId));
        if (!team.getClub().getId().equals(clubId)) {
            throw new SecurityException("해당 동아리의 팀이 아닙니다");
        }

        // 페이지 수 검증
        byte[] pdfBytes = fileStorageService.downloadAsBytes(driveFile.getStoredName());
        int pageCount = pdfToImageService.getPageCount(pdfBytes);
        if (pageCount > 50) {
            throw new IllegalArgumentException("PDF 페이지 수 초과: " + pageCount + "페이지 (최대 50페이지)");
        }

        // Job 생성
        AiGenerationJob job = AiGenerationJob.builder()
                .club(driveFile.getClub())
                .team(team)
                .driveFile(driveFile)
                .board(board)
                .requestedBy(membership)
                .status(JobStatus.QUEUED)
                .build();
        job.setSemiCategoryIds(semiCategoryIds);
        job = jobRepository.save(job);

        // 커밋 후 비동기 처리 시작
        eventPublisher.publishEvent(new AiGenerationRequestedEvent(job.getId()));

        log.info("AI 글 생성 요청: jobId={}, file={}", job.getId(), driveFile.getOriginalName());
        return job;
    }

    /**
     * 비동기 2-hop 파이프라인
     */
    public void processGeneration(Long jobId) {
        AiGenerationJob job = jobRepository.findDetailedById(jobId).orElse(null);
        if (job == null) return;

        try {
            // PROCESSING 전환
            job.setStatus(JobStatus.PROCESSING);
            job.setProcessingStep("PDF 텍스트 추출 중...");
            jobRepository.saveAndFlush(job);

            DriveFile driveFile = job.getDriveFile();

            // Step 1: Gemini — PDF 직접 전송 → 텍스트 추출
            log.info("Job {}: Step 1 — Gemini PDF 텍스트 추출", jobId);
            byte[] pdfBytes = fileStorageService.downloadAsBytes(driveFile.getStoredName());
            String extractedText = geminiService.extractText(
                    pdfBytes,
                    driveFile.getOriginalName(),
                    job.getRequestedBy().getName());

            // Step 2: Cerebras — 텍스트 → 분석 글
            log.info("Job {}: Step 2 — Cerebras 글 생성", jobId);
            job.setProcessingStep("AI 글 생성 중...");
            jobRepository.saveAndFlush(job);
            String article = cerebrasService.generateArticle(extractedText);

            job.setResultMarkdown(article);
            job.setProcessingStep(null);
            job.setStatus(JobStatus.COMPLETED);
            job.setCompletedAt(LocalDateTime.now());
            jobRepository.saveAndFlush(job);

            log.info("Job {} 완료: {}자 생성", jobId, article.length());

        } catch (Exception e) {
            log.error("Job {} 실패: {}", jobId, e.getMessage(), e);
            job.setErrorMessage(e.getMessage() != null ? e.getMessage().substring(0, Math.min(e.getMessage().length(), 500)) : "알 수 없는 오류");
            job.setStatus(JobStatus.FAILED);
            job.setCompletedAt(LocalDateTime.now());
            jobRepository.save(job);
        }
    }

    /**
     * 작업 상태 조회
     */
    @Transactional(readOnly = true)
    public AiGenerationJob getJob(Long jobId, Long userId) {
        AiGenerationJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("작업을 찾을 수 없습니다"));
        if (!job.getRequestedBy().getUser().getId().equals(userId)) {
            throw new SecurityException("본인의 작업만 조회할 수 있습니다");
        }
        return job;
    }

    /**
     * 내 작업 목록
     */
    @Transactional(readOnly = true)
    public List<AiGenerationJob> getMyJobs(Long clubId, Long userId, JobStatus status) {
        // membership 찾기
        ClubMembership membership = permissionService.validateAiPermission(userId, clubId);
        if (status != null) {
            return jobRepository.findByRequestedByIdAndStatusOrderByCreatedAtDesc(membership.getId(), status);
        }
        return jobRepository.findByRequestedByIdOrderByCreatedAtDesc(membership.getId());
    }

    @Transactional
    public AiGenerationJob regenerate(Long jobId, Long userId) {
        AiGenerationJob original = getJob(jobId, userId);

        if (original.getStatus() != JobStatus.COMPLETED && original.getStatus() != JobStatus.FAILED) {
            throw new IllegalArgumentException("완료되거나 실패한 작업만 재생성할 수 있습니다");
        }

        ClubMembership membership = permissionService.validateAiPermission(userId, original.getClub().getId());
        boolean hasActiveJob = jobRepository.existsByRequestedByIdAndStatusIn(
                membership.getId(), List.of(JobStatus.QUEUED, JobStatus.PROCESSING));
        if (hasActiveJob) {
            throw new IllegalStateException("요청 처리중입니다. 잠시 후 다시 시도해주세요");
        }

        DriveFile driveFile = driveFileRepository.findById(original.getDriveFile().getId())
                .orElseThrow(() -> new IllegalArgumentException("파일을 찾을 수 없습니다"));

        // 이미 발행된 DriveFile이면 재생성은 가능하지만 경고 로깅 (발행은 publishable 플래그로 차단)
        if (driveFile.getLinkedPost() != null) {
            log.info("이미 발행된 DriveFile로 재생성 요청: fileId={}, postId={}", driveFile.getId(), driveFile.getLinkedPost().getId());
        }

        Board board = boardRepository.findById(original.getBoard().getId())
                .orElseThrow(() -> new IllegalArgumentException("게시판을 찾을 수 없습니다"));
        Team team = teamRepository.findById(original.getTeam().getId())
                .orElseThrow(() -> new IllegalArgumentException("팀을 찾을 수 없습니다: " + original.getTeam().getId()));

        byte[] rPdfBytes = fileStorageService.downloadAsBytes(driveFile.getStoredName());
        int pageCount = pdfToImageService.getPageCount(rPdfBytes);
        if (pageCount > 50) {
            throw new IllegalArgumentException("PDF 페이지 수 초과: " + pageCount + "페이지 (최대 50페이지)");
        }

        AiGenerationJob regenerated = AiGenerationJob.builder()
                .club(original.getClub())
                .team(team)
                .driveFile(driveFile)
                .board(board)
                .requestedBy(membership)
                .status(JobStatus.QUEUED)
                .build();
        regenerated.setSemiCategoryIds(original.getSemiCategoryIds());
        regenerated = jobRepository.save(regenerated);

        eventPublisher.publishEvent(new AiGenerationRequestedEvent(regenerated.getId()));
        log.info("AI regenerate requested: sourceJobId={}, newJobId={}, file={}",
                original.getId(), regenerated.getId(), driveFile.getOriginalName());
        return regenerated;
    }

    /**
     * 바로 발행 — Post 생성 + 크레딧 차감
     * teamIds/boardIds가 제공되면 해당 값 사용, 아니면 기존 로직(DriveFile.teams / job.getBoard()) 유지
     */
    @Transactional
    public Long publishResult(Long jobId, Long userId, List<Long> teamIds, List<Long> boardIds) {
        AiGenerationJob job = getJob(jobId, userId);

        if (job.getStatus() != JobStatus.COMPLETED) {
            throw new IllegalArgumentException("완료된 작업만 발행할 수 있습니다");
        }

        DriveFile driveFile = job.getDriveFile();

        // 이중 발행 방지
        if (driveFile.getLinkedPost() != null) {
            throw new IllegalArgumentException("이미 발행된 글이 있습니다");
        }

        // Post 생성
        // 모달에서 선택한 태그가 있으면 사용, 없으면 DriveFile 태그 승계
        List<Long> selectedIds = job.getSemiCategoryIds();
        Set<SemiCategory> tags;
        if (selectedIds != null && !selectedIds.isEmpty()) {
            tags = new HashSet<>(semiCategoryRepository.findAllById(selectedIds));
        } else {
            tags = driveFile.getSemiCategories().stream()
                    .filter(sc -> !sc.isGeneration())
                    .collect(Collectors.toSet());
        }

        // 팀 결정: teamIds 제공 시 해당 팀, 아니면 DriveFile.teams 승계
        Set<Team> postTeams;
        if (teamIds != null && !teamIds.isEmpty()) {
            postTeams = new HashSet<>(teamRepository.findAllById(teamIds));
        } else {
            postTeams = new HashSet<>(driveFile.getTeams());
        }

        // 게시판 결정: boardIds 제공 시 해당 게시판들, 아니면 job.getBoard() (null이면 빈 set)
        Set<Board> postBoards;
        if (boardIds != null && !boardIds.isEmpty()) {
            postBoards = new HashSet<>(boardRepository.findAllById(boardIds));
        } else if (job.getBoard() != null) {
            postBoards = new HashSet<>(Set.of(job.getBoard()));
        } else {
            postBoards = new HashSet<>();
        }

        Post post = Post.builder()
                .boards(postBoards)
                .teams(postTeams)
                .author(job.getRequestedBy())
                .title(AiMarkdownTitleExtractor.extractTitle(job.getResultMarkdown()))
                .content(job.getResultMarkdown())
                .semiCategories(tags.isEmpty() ? new HashSet<>() : tags)
                .linkedDriveFile(driveFile)
                .aiGenerated(true)
                .build();
        post = postRepository.save(post);

        // 양방향 FK 설정
        driveFile.setLinkedPost(post);
        driveFileRepository.save(driveFile);

        log.info("AI 글 발행: postId={}, jobId={}, teams={}, boards={}",
                post.getId(), jobId,
                postTeams.stream().map(Team::getId).toList(),
                postBoards.stream().map(Board::getId).toList());
        return post.getId();
    }

    /**
     * 에디터로 전달
     */
    @Transactional(readOnly = true)
    public AiGenerationJob getEditorContent(Long jobId, Long userId) {
        AiGenerationJob job = getJob(jobId, userId);
        if (job.getStatus() != JobStatus.COMPLETED) {
            throw new IllegalArgumentException("완료된 작업만 에디터로 전달할 수 있습니다");
        }
        return job;
    }


}
