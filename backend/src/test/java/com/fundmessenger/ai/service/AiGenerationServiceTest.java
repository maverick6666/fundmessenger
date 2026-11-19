package com.fundmessenger.ai.service;

import com.fundmessenger.admin.entity.GlobalSettings;
import com.fundmessenger.admin.repository.GlobalSettingsRepository;
import com.fundmessenger.ai.entity.AiGenerationJob;
import com.fundmessenger.ai.entity.AiGenerationJob.JobStatus;
import com.fundmessenger.ai.repository.AiGenerationJobRepository;
import com.fundmessenger.board.entity.*;
import com.fundmessenger.board.repository.BoardRepository;
import com.fundmessenger.board.repository.PostRepository;
import com.fundmessenger.board.repository.SemiCategoryRepository;
import com.fundmessenger.club.entity.*;
import com.fundmessenger.club.repository.CreditHistoryRepository;
import com.fundmessenger.club.repository.MemberTagRepository;
import com.fundmessenger.club.repository.TeamRepository;
import com.fundmessenger.config.CerebrasClient;
import com.fundmessenger.drive.entity.DriveFile;
import com.fundmessenger.drive.repository.DriveFileRepository;
import com.fundmessenger.file.service.FileStorageService;
import com.fundmessenger.support.TestFixtures;
import com.fundmessenger.user.entity.User;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class AiGenerationServiceTest {

    @Mock private AiGenerationJobRepository jobRepository;
    @Mock private DriveFileRepository driveFileRepository;
    @Mock private BoardRepository boardRepository;
    @Mock private PostRepository postRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private MemberTagRepository memberTagRepository;
    @Mock private CreditHistoryRepository creditHistoryRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private PdfToImageService pdfToImageService;
    @Mock private GeminiService geminiService;
    @Mock private AiCerebrasService cerebrasService;
    @Mock private SemiCategoryRepository semiCategoryRepository;
    @Mock private AiPermissionService permissionService;
    @Mock private GlobalSettingsRepository globalSettingsRepository;
    @Mock private CerebrasClient cerebrasClient;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private AiGenerationService aiGenerationService;

    private Club club;
    private ClubMembership member;
    private Team team;
    private Board board;
    private DriveFile driveFile;

    @BeforeEach
    void setUp() {
        club = TestFixtures.club(1L, "테스트동아리");
        User user = TestFixtures.user(10L, "member@test.dev");
        member = TestFixtures.approvedMembership(100L, user, club);

        team = TestFixtures.team(1L, club, "GrowthFund");
        team.setCreditAllocation(100);

        board = TestFixtures.everyoneBoard(1L, club);

        driveFile = DriveFile.builder()
                .club(club)
                .uploader(member)
                .originalName("analysis.pdf")
                .storedName("uuid-analysis.pdf")
                .fileSize(2048L)
                .mimeType("application/pdf")
                .build();
        driveFile.setId(1L);
        driveFile.setCreatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("AI 글 생성 요청 성공 — 권한/크레딧/파일 검증 후 Job이 생성된다")
    void requestGeneration_success() {
        // given
        given(permissionService.validateAiPermission(10L, 1L)).willReturn(member);
        given(jobRepository.existsByRequestedByIdAndStatusIn(eq(100L), anyList())).willReturn(false);
        given(driveFileRepository.findById(1L)).willReturn(Optional.of(driveFile));
        given(boardRepository.findById(1L)).willReturn(Optional.of(board));
        given(teamRepository.findById(1L)).willReturn(Optional.of(team));

        GlobalSettings settings = new GlobalSettings();
        settings.setAiCreditCost(1);
        given(globalSettingsRepository.findById(1L)).willReturn(Optional.of(settings));

        given(fileStorageService.getUploadDir()).willReturn(Path.of("/uploads"));
        given(pdfToImageService.getPageCount(any(Path.class))).willReturn(10);

        AiGenerationJob savedJob = AiGenerationJob.builder()
                .club(club)
                .team(team)
                .driveFile(driveFile)
                .board(board)
                .requestedBy(member)
                .status(JobStatus.QUEUED)
                .build();
        savedJob.setId(1L);
        savedJob.setCreatedAt(LocalDateTime.now());

        given(jobRepository.save(any(AiGenerationJob.class))).willReturn(savedJob);

        // when
        AiGenerationJob result = aiGenerationService.requestGeneration(1L, 1L, 1L, 1L, null, 10L);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(JobStatus.QUEUED);
        then(jobRepository).should().save(any(AiGenerationJob.class));
    }

    @Test
    @DisplayName("AI 글 생성 실패 — 팀 크레딧 부족 시 예외 발생")
    void requestGeneration_insufficientCredits_fails() {
        // given
        given(permissionService.validateAiPermission(10L, 1L)).willReturn(member);
        given(jobRepository.existsByRequestedByIdAndStatusIn(eq(100L), anyList())).willReturn(false);
        given(driveFileRepository.findById(1L)).willReturn(Optional.of(driveFile));
        given(boardRepository.findById(1L)).willReturn(Optional.of(board));

        Team poorTeam = TestFixtures.team(2L, club, "BrokeTeam");
        poorTeam.setCreditAllocation(0);  // 크레딧 없음
        given(teamRepository.findById(2L)).willReturn(Optional.of(poorTeam));

        GlobalSettings settings = new GlobalSettings();
        settings.setAiCreditCost(1);
        given(globalSettingsRepository.findById(1L)).willReturn(Optional.of(settings));

        // when & then
        assertThatThrownBy(() ->
                aiGenerationService.requestGeneration(1L, 1L, 1L, 2L, null, 10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("크레딧이 부족");
    }

    @Test
    @DisplayName("작업 상태 조회 — 본인 작업이면 Job이 반환된다")
    void getJobStatus_returnsJob() {
        // given
        AiGenerationJob job = AiGenerationJob.builder()
                .club(club)
                .team(team)
                .driveFile(driveFile)
                .board(board)
                .requestedBy(member)
                .status(JobStatus.COMPLETED)
                .resultMarkdown("# 분석 결과")
                .build();
        job.setId(1L);
        job.setCreatedAt(LocalDateTime.now());

        given(jobRepository.findById(1L)).willReturn(Optional.of(job));

        // when
        AiGenerationJob result = aiGenerationService.getJob(1L, 10L);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(result.getResultMarkdown()).isEqualTo("# 분석 결과");
    }
}
