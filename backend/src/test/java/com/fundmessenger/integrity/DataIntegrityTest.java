package com.fundmessenger.integrity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundmessenger.admin.scheduler.SubscriptionScheduler;
import com.fundmessenger.ai.service.AiCommentBotScheduler;
import com.fundmessenger.board.entity.*;
import com.fundmessenger.board.repository.BoardRepository;
import com.fundmessenger.board.repository.PostCommentRepository;
import com.fundmessenger.board.repository.PostLikeRepository;
import com.fundmessenger.board.repository.PostRepository;
import com.fundmessenger.club.entity.*;
import com.fundmessenger.club.repository.ClubMembershipRepository;
import com.fundmessenger.club.repository.MemberTagRepository;
import com.fundmessenger.club.repository.TeamRepository;
import com.fundmessenger.config.CerebrasClient;
import com.fundmessenger.config.GeminiClient;
import com.fundmessenger.drive.entity.DriveFile;
import com.fundmessenger.drive.repository.DriveFileRepository;
import com.fundmessenger.jongtobang.service.NewsCollectorService;
import com.fundmessenger.jongtobang.service.NewsPipelineService;
import com.fundmessenger.jongtobang.service.StockMasterService;
import com.fundmessenger.jongtobang.service.StockPriceService;
import com.fundmessenger.support.TestHelper;
import com.fundmessenger.user.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 데이터 정합성 테스트.
 *
 * 부모 엔티티 삭제/수정 시 자식 데이터가 올바르게 cascade/정리되는지 검증한다.
 *
 * 주의: @Transactional 테스트에서 MockMvc를 사용할 때 persistence context 관리가 핵심.
 * - 테스트 데이터 생성 후 em.flush() → DB에 확실히 반영
 * - MockMvc 호출 (같은 트랜잭션에서 실행)
 * - em.flush() + em.clear() → 남은 변경사항 반영 + 1차 캐시 비움
 * - 이후 repository.findById()로 DB 상태 직접 검증
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DataIntegrityTest {

    @Autowired MockMvc mockMvc;
    @Autowired TestHelper h;
    @Autowired ObjectMapper objectMapper;
    @Autowired EntityManager em;

    // ── Repositories for direct verification ──
    @Autowired PostRepository postRepository;
    @Autowired PostCommentRepository postCommentRepository;
    @Autowired PostLikeRepository postLikeRepository;
    @Autowired BoardRepository boardRepository;
    @Autowired TeamRepository teamRepository;
    @Autowired MemberTagRepository memberTagRepository;
    @Autowired ClubMembershipRepository clubMembershipRepository;
    @Autowired DriveFileRepository driveFileRepository;

    // ── External service mocks ──
    @MockitoBean CerebrasClient cerebrasClient;
    @MockitoBean GeminiClient geminiClient;
    @MockitoBean StockPriceService stockPriceService;
    @MockitoBean StockMasterService stockMasterService;
    @MockitoBean NewsCollectorService newsCollectorService;
    @MockitoBean NewsPipelineService newsPipelineService;
    @MockitoBean AiCommentBotScheduler aiCommentBotScheduler;
    @MockitoBean SubscriptionScheduler subscriptionScheduler;

    private User execUser, regUser;
    private Club club;
    private ClubMembership execMembership, regMembership;
    private Board board;
    private SemiCategory tag;

    @BeforeEach
    void setUp() {
        execUser = h.createUser("integrity-exec@test.dev");
        regUser = h.createUser("integrity-reg@test.dev");
        club = h.createClubWithSchool("IntegrityClub", "IntegritySchool");
        execMembership = h.createApprovedMembership(execUser, club, "임원");
        regMembership = h.createApprovedMembership(regUser, club, "부원");
        h.createExecutiveTag(execMembership, club);
        h.createRegularTag(regMembership, club);
        board = h.createEveryoneBoard(club);
        tag = h.createSemiCategory(club, "정합성태그");
        // Flush all setup data to DB so MockMvc controllers can see it
        em.flush();
    }

    // ================================================================
    // T2.1: 팀 해체 후 팀 게시판 필터링 확인
    // ================================================================
    @Test
    @DisplayName("T2.1: 팀 해체 -> 팀이 soft delete 처리됨")
    void 팀해체후_소프트삭제확인() throws Exception {
        Team team = h.createTeam(club, "해체예정팀");
        Long teamId = team.getId();
        em.flush();

        mockMvc.perform(delete("/api/clubs/{clubId}/admin/teams/{teamId}",
                        club.getId(), teamId)
                        .header("Authorization", h.authHeader(execUser)))
                .andExpect(status().isOk());

        flushAndClear();
        Team afterTeam = teamRepository.findById(teamId).orElse(null);
        assertThat(afterTeam).isNotNull();
        assertThat(afterTeam.isDeleted()).isTrue();
    }

    // ================================================================
    // T2.2: 팀 해체 후 팀 태그 제거 확인
    // ================================================================
    @Test
    @DisplayName("T2.2: 팀 해체 -> 멤버의 팀 태그(TEAM_LEADER/TEAM_MEMBER)가 삭제됨")
    void 팀해체후_팀태그_제거확인() throws Exception {
        Team team = h.createTeam(club, "태그삭제팀");
        h.createTeamLeaderTag(execMembership, club, team);
        h.createTeamMemberTag(regMembership, club, team);
        em.flush();

        long tagCountBefore = memberTagRepository.findAll().stream()
                .filter(t -> t.getTeam() != null && t.getTeam().getId().equals(team.getId()))
                .count();
        assertThat(tagCountBefore).isEqualTo(2);

        mockMvc.perform(delete("/api/clubs/{clubId}/admin/teams/{teamId}",
                        club.getId(), team.getId())
                        .header("Authorization", h.authHeader(execUser)))
                .andExpect(status().isOk());

        flushAndClear();
        long tagCountAfter = memberTagRepository.findAll().stream()
                .filter(t -> t.getTeam() != null && t.getTeam().getId().equals(team.getId()))
                .count();
        assertThat(tagCountAfter).isZero();
    }

    // ================================================================
    // T2.3: 글 삭제 후 댓글 cascade
    // ================================================================
    @Test
    @DisplayName("T2.3: 글 삭제 -> 댓글이 cascade로 함께 삭제됨")
    void 글삭제후_댓글_cascade() throws Exception {
        Post post = createTestPost(execMembership, "댓글테스트글");
        PostComment comment = PostComment.builder()
                .post(post)
                .author(regMembership)
                .content("테스트 댓글입니다")
                .build();
        postCommentRepository.save(comment);
        Long commentId = comment.getId();
        Long postId = post.getId();
        em.flush();

        assertThat(postCommentRepository.findById(commentId)).isPresent();

        mockMvc.perform(delete("/api/clubs/{clubId}/posts/{postId}",
                        club.getId(), postId)
                        .header("Authorization", h.authHeader(execUser)))
                .andExpect(status().isNoContent());

        flushAndClear();
        assertThat(postRepository.findById(postId)).isEmpty();
        assertThat(postCommentRepository.findById(commentId)).isEmpty();
        // If comment still exists -> BUG-T2.3: CascadeType.ALL/orphanRemoval not working for comments
    }

    // ================================================================
    // T2.4: 글 삭제 후 좋아요 cascade
    // ================================================================
    @Test
    @DisplayName("T2.4: 글 삭제 -> PostLike가 cascade로 함께 삭제됨")
    void 글삭제후_좋아요_cascade() throws Exception {
        Post post = createTestPost(execMembership, "좋아요테스트글");
        PostLike like = PostLike.builder()
                .post(post)
                .member(regMembership)
                .build();
        postLikeRepository.save(like);
        Long likeId = like.getId();
        Long postId = post.getId();
        // Flush to DB and clear context so controller gets a clean state
        flushAndClear();

        assertThat(postLikeRepository.findById(likeId)).isPresent();

        mockMvc.perform(delete("/api/clubs/{clubId}/posts/{postId}",
                        club.getId(), postId)
                        .header("Authorization", h.authHeader(execUser)))
                .andExpect(status().isNoContent());

        // Flush controller's pending changes (delete) to DB, then clear for fresh reads
        flushAndClear();
        assertThat(postRepository.findById(postId)).isEmpty();
        assertThat(postLikeRepository.findById(likeId)).isEmpty();
        // If like still exists -> BUG-T2.4: CascadeType.ALL/orphanRemoval not working for likes
    }

    // ================================================================
    // T2.5: 글 삭제 후 DriveFile FK 해제
    // ================================================================
    @Test
    @DisplayName("T2.5: 글 삭제 -> 연동 DriveFile의 linkedPost가 null로 해제됨")
    void 글삭제후_DriveFile_FK해제() throws Exception {
        Post post = createTestPost(execMembership, "드라이브연동글");

        DriveFile driveFile = DriveFile.builder()
                .club(club)
                .uploader(execMembership)
                .originalName("test.pdf")
                .storedName("stored-uuid-" + System.nanoTime() + ".pdf")
                .fileSize(1024L)
                .mimeType("application/pdf")
                .linkedPost(post)
                .build();
        driveFile.getSemiCategories().add(tag);
        driveFile = driveFileRepository.save(driveFile);
        Long driveFileId = driveFile.getId();

        post.setLinkedDriveFile(driveFile);
        postRepository.save(post);
        Long postId = post.getId();
        em.flush();

        mockMvc.perform(delete("/api/clubs/{clubId}/posts/{postId}",
                        club.getId(), postId)
                        .header("Authorization", h.authHeader(execUser)))
                .andExpect(status().isNoContent());

        flushAndClear();
        DriveFile afterFile = driveFileRepository.findById(driveFileId).orElse(null);
        assertThat(afterFile).isNotNull(); // DriveFile must survive
        assertThat(afterFile.getLinkedPost()).isNull(); // FK must be nullified
        // If linkedPost is still set -> BUG-T2.5: Post deletion doesn't unlink DriveFile
        // If DriveFile is deleted -> BUG-T2.5: Post deletion cascades to DriveFile (should not)
    }

    // ================================================================
    // T2.6: 멤버 비활성화 후 글은 남아있음
    // ================================================================
    @Test
    @DisplayName("T2.6: 멤버 비활성화 -> 해당 멤버의 글은 여전히 조회 가능")
    void 멤버비활성화후_글_남아있음() throws Exception {
        Post post = createTestPost(regMembership, "비활성화테스트글");
        Long postId = post.getId();
        em.flush();

        mockMvc.perform(put("/api/clubs/{clubId}/admin/members/{membershipId}/deactivate",
                        club.getId(), regMembership.getId())
                        .header("Authorization", h.authHeader(execUser)))
                .andExpect(status().isOk());

        flushAndClear();
        Post afterPost = postRepository.findById(postId).orElse(null);
        assertThat(afterPost).isNotNull();
        assertThat(afterPost.getTitle()).isEqualTo("비활성화테스트글");

        ClubMembership afterMembership = clubMembershipRepository.findById(regMembership.getId()).orElse(null);
        assertThat(afterMembership).isNotNull();
        assertThat(afterMembership.getStatus()).isEqualTo(MemberStatus.INACTIVE);
        // If post is deleted -> BUG-T2.6: Member deactivation cascades to posts (should not)
    }

    // ================================================================
    // T2.7: 게시판 삭제 후 글 orphan 검증 (M:N)
    // ================================================================
    @Test
    @DisplayName("T2.7: 글이 2개 게시판에 속함 -> 1개 게시판 삭제 -> 글은 남은 1개 게시판에서 조회됨")
    void 게시판삭제후_글_orphan검증() throws Exception {
        Board board2 = h.createBoard(club, "두번째게시판", WritePermission.EVERYONE);

        Post post = Post.builder()
                .author(execMembership)
                .title("듀얼게시판글")
                .content("두 게시판에 속한 글")
                .build();
        post.getBoards().add(board);
        post.getBoards().add(board2);
        post.getSemiCategories().add(tag);
        post = postRepository.save(post);
        Long postId = post.getId();
        Long board2Id = board2.getId();
        em.flush();

        assertThat(post.getBoards()).hasSize(2);

        mockMvc.perform(delete("/api/clubs/{clubId}/admin/boards/{boardId}",
                        club.getId(), board2Id)
                        .header("Authorization", h.authHeader(execUser)))
                .andExpect(status().isNoContent());

        // Clear only (no flush) — deleted board reference is still in persistence context
        em.clear();
        Post afterPost = postRepository.findById(postId).orElse(null);
        assertThat(afterPost).isNotNull();
        assertThat(afterPost.getBoards()).isNotEmpty();
        assertThat(afterPost.getBoards().stream().noneMatch(b -> b.getId().equals(board2Id))).isTrue();
        // If post is deleted -> BUG-T2.7: Board deletion cascades to posts (should only unlink)
    }

    // ================================================================
    // T2.8 (Bonus): 글 삭제 후 댓글의 대댓글도 cascade
    // ================================================================
    @Test
    @DisplayName("T2.8: 글 삭제 -> 댓글 + 대댓글 모두 cascade 삭제")
    void 글삭제후_대댓글_cascade() throws Exception {
        Post post = createTestPost(execMembership, "대댓글테스트글");
        PostComment parentComment = PostComment.builder()
                .post(post)
                .author(execMembership)
                .content("부모 댓글")
                .build();
        parentComment = postCommentRepository.save(parentComment);

        PostComment reply = PostComment.builder()
                .post(post)
                .author(regMembership)
                .parentComment(parentComment)
                .content("대댓글")
                .build();
        reply = postCommentRepository.save(reply);
        Long parentId = parentComment.getId();
        Long replyId = reply.getId();
        Long postId = post.getId();
        em.flush();

        mockMvc.perform(delete("/api/clubs/{clubId}/posts/{postId}",
                        club.getId(), postId)
                        .header("Authorization", h.authHeader(execUser)))
                .andExpect(status().isNoContent());

        flushAndClear();
        assertThat(postCommentRepository.findById(parentId)).isEmpty();
        assertThat(postCommentRepository.findById(replyId)).isEmpty();
    }

    // ================================================================
    // Helpers
    // ================================================================

    private Post createTestPost(ClubMembership author, String title) {
        Post post = Post.builder()
                .author(author)
                .title(title)
                .content("테스트 내용")
                .build();
        post.getBoards().add(board);
        post.getSemiCategories().add(tag);
        return postRepository.save(post);
    }

    /**
     * Flush pending changes to DB and clear persistence context.
     * This ensures subsequent repository reads hit the actual DB state
     * rather than returning stale cached entities.
     */
    private void flushAndClear() {
        em.flush();
        em.clear();
    }
}
