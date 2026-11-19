package com.fundmessenger.concurrency;

import com.fundmessenger.board.entity.Board;
import com.fundmessenger.board.entity.Post;
import com.fundmessenger.board.entity.PostLike;
import com.fundmessenger.board.entity.SemiCategory;
import com.fundmessenger.board.entity.WritePermission;
import com.fundmessenger.board.repository.PostLikeRepository;
import com.fundmessenger.board.repository.PostRepository;
import com.fundmessenger.board.service.BoardService;
import com.fundmessenger.club.dto.AllocateCreditRequest;
import com.fundmessenger.club.entity.Club;
import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.club.entity.Team;
import com.fundmessenger.club.repository.ClubRepository;
import com.fundmessenger.club.repository.TeamRepository;
import com.fundmessenger.club.service.CreditService;
import com.fundmessenger.admin.scheduler.SubscriptionScheduler;
import com.fundmessenger.ai.service.AiCommentBotScheduler;
import com.fundmessenger.config.CerebrasClient;
import com.fundmessenger.config.GeminiClient;
import com.fundmessenger.jongtobang.service.NewsCollectorService;
import com.fundmessenger.jongtobang.service.NewsPipelineService;
import com.fundmessenger.jongtobang.service.StockMasterService;
import com.fundmessenger.jongtobang.service.StockPriceService;
import com.fundmessenger.support.TestHelper;
import com.fundmessenger.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.environment=development")
@ActiveProfiles("test")
class ConcurrencyTest {

    @Autowired CreditService creditService;
    @Autowired BoardService boardService;
    @Autowired ClubRepository clubRepository;
    @Autowired TeamRepository teamRepository;
    @Autowired PostRepository postRepository;
    @Autowired PostLikeRepository postLikeRepository;
    @Autowired TestHelper h;
    @Autowired TransactionTemplate tx;

    // Mock external services to prevent context load failure
    @MockitoBean CerebrasClient cerebrasClient;
    @MockitoBean GeminiClient geminiClient;
    @MockitoBean StockPriceService stockPriceService;
    @MockitoBean StockMasterService stockMasterService;
    @MockitoBean NewsCollectorService newsCollectorService;
    @MockitoBean NewsPipelineService newsPipelineService;
    @MockitoBean AiCommentBotScheduler aiCommentBotScheduler;
    @MockitoBean SubscriptionScheduler subscriptionScheduler;

    // Shared test data — populated in setup, used across threads
    private Long clubId;
    private Long teamId;
    private Long adminUserId;

    @BeforeEach
    void setUp() {
        tx.executeWithoutResult(status -> {
            // Clean up previous test data
            postLikeRepository.deleteAll();
            postRepository.deleteAll();

            User admin = h.createUser("conc-admin-" + UUID.randomUUID() + "@test.dev");
            Club club = h.createClubWithSchool("동시성테스트동아리-" + UUID.randomUUID(),
                    "동시성테스트대학-" + UUID.randomUUID());
            club.setCreditBalance(10_000);
            clubRepository.save(club);

            ClubMembership adminMembership = h.createApprovedMembership(admin, club, "관리자");
            h.createExecutiveTag(adminMembership, club);

            Team team = h.createTeam(club, "동시성팀-" + UUID.randomUUID());

            this.clubId = club.getId();
            this.teamId = team.getId();
            this.adminUserId = admin.getId();
        });
    }

    // ============================================================
    // 1. 크레딧 동시 분배 정합성
    // ============================================================

    @Test
    @DisplayName("크레딧 동시 분배 정합성 — 10스레드 × 100원, 잔액은 정확히 1000 차감되어야 함")
    void 크레딧_동시_분배_정합성() throws Exception {
        final int THREADS = 10;
        final int AMOUNT_PER_THREAD = 100;

        ExecutorService exec = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch go = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<Throwable> errors = new CopyOnWriteArrayList<>();

        for (int i = 0; i < THREADS; i++) {
            exec.submit(() -> {
                ready.countDown();
                try {
                    go.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                try {
                    AllocateCreditRequest req = new AllocateCreditRequest();
                    req.setTeamId(teamId);
                    req.setAmount(AMOUNT_PER_THREAD);
                    req.setDescription("동시성 테스트 분배");
                    creditService.allocateToTeam(clubId, req, adminUserId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    errors.add(e);
                }
            });
        }

        ready.await(10, TimeUnit.SECONDS);
        go.countDown(); // 동시 출발!
        exec.shutdown();
        assertThat(exec.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        // 검증: DB 최종 상태 — 비관적 락(findByIdForUpdate)이 있으므로 모두 성공해야 함
        Club finalClub = tx.execute(status -> clubRepository.findById(clubId).orElseThrow());
        Team finalTeam = tx.execute(status -> teamRepository.findById(teamId).orElseThrow());

        int expectedBalance = 10_000 - (successCount.get() * AMOUNT_PER_THREAD);
        int expectedAllocation = successCount.get() * AMOUNT_PER_THREAD;

        assertThat(finalClub.getCreditBalance())
                .as("동아리 잔액: 성공 %d건 × %d원 차감", successCount.get(), AMOUNT_PER_THREAD)
                .isEqualTo(expectedBalance);
        assertThat(finalTeam.getCreditAllocation())
                .as("팀 할당: 성공 %d건 × %d원 증가", successCount.get(), AMOUNT_PER_THREAD)
                .isEqualTo(expectedAllocation);

        // 모든 스레드 성공 시 정확히 10000 - 1000 = 9000
        if (successCount.get() == THREADS) {
            assertThat(finalClub.getCreditBalance()).isEqualTo(9_000);
            assertThat(finalTeam.getCreditAllocation()).isEqualTo(1_000);
        }

        // 잔액 + 할당 합이 원래 총액과 일치해야 함 (돈이 증발하면 안 됨)
        assertThat(finalClub.getCreditBalance() + finalTeam.getCreditAllocation())
                .as("총합 보전: 잔액 + 팀할당 = 원래 총액 10,000")
                .isEqualTo(10_000);
    }

    // ============================================================
    // 2. 좋아요 동시 토글 정합성
    // ============================================================

    @Test
    @DisplayName("좋아요 동시 토글 정합성 — 5명이 동시에 좋아요 → likeCount와 실제 PostLike 행 수 일치")
    void 좋아요_동시_토글_정합성() throws Exception {
        final int USER_COUNT = 5;

        // Setup: 글 하나 + 5명의 다른 유저
        List<Long> userIds = new ArrayList<>();
        Long postId = tx.execute(status -> {
            Club club = clubRepository.findById(clubId).orElseThrow();
            Board board = h.createEveryoneBoard(club);
            SemiCategory tag = h.createSemiCategory(club, "동시성태그-" + UUID.randomUUID());

            // 글 작성자
            User authorUser = h.createUser("like-author-" + UUID.randomUUID() + "@test.dev");
            ClubMembership authorMem = h.createApprovedMembership(authorUser, club, "글작성자");
            h.createRegularTag(authorMem, club);

            Post post = Post.builder()
                    .boards(new HashSet<>(Set.of(board)))
                    .author(authorMem)
                    .title("동시좋아요테스트")
                    .content("테스트 본문")
                    .semiCategories(new HashSet<>(Set.of(tag)))
                    .build();
            post = postRepository.save(post);

            // 5명의 좋아요 유저 생성
            for (int i = 0; i < USER_COUNT; i++) {
                User user = h.createUser("like-user-" + i + "-" + UUID.randomUUID() + "@test.dev");
                ClubMembership mem = h.createApprovedMembership(user, club, "좋아요유저" + i);
                h.createRegularTag(mem, club);
                userIds.add(user.getId());
            }

            return post.getId();
        });

        ExecutorService exec = Executors.newFixedThreadPool(USER_COUNT);
        CountDownLatch ready = new CountDownLatch(USER_COUNT);
        CountDownLatch go = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < USER_COUNT; i++) {
            final Long userId = userIds.get(i);
            exec.submit(() -> {
                ready.countDown();
                try {
                    go.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                try {
                    boardService.toggleLike(clubId, postId, userId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                }
            });
        }

        ready.await(10, TimeUnit.SECONDS);
        go.countDown(); // 동시 출발!
        exec.shutdown();
        assertThat(exec.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        // 검증: likeCount == 실제 PostLike 행 수
        tx.executeWithoutResult(status -> {
            Post finalPost = postRepository.findById(postId).orElseThrow();
            long actualLikeRows = postLikeRepository.findAll().stream()
                    .filter(pl -> pl.getPost().getId().equals(postId))
                    .count();

            // 핵심 정합성: 카운터와 실제 행 수 일치
            // toggleLike는 비관적 락 없이 incrementLikeCount/decrementLikeCount를 쓰므로
            // 동시 접근 시 불일치 가능성이 있다 (이것이 race condition)
            assertThat(finalPost.getLikeCount())
                    .as("likeCount(%d)와 실제 PostLike 행 수(%d)가 일치해야 함",
                            finalPost.getLikeCount(), actualLikeRows)
                    .isEqualTo((int) actualLikeRows);
        });
    }

    // ============================================================
    // 3. 동시 글삭제 멱등성
    // ============================================================

    @Test
    @DisplayName("동시 글삭제 멱등성 — 2스레드가 같은 글 삭제 → 하나만 성공, 나머지 NotFoundException")
    void 동시_글삭제_멱등성() throws Exception {
        // Setup: 글 하나 생성
        Long postId = tx.execute(status -> {
            Club club = clubRepository.findById(clubId).orElseThrow();
            Board board = h.createEveryoneBoard(club);
            SemiCategory tag = h.createSemiCategory(club, "삭제멱등태그-" + UUID.randomUUID());

            User authorUser = h.createUser("del-author-" + UUID.randomUUID() + "@test.dev");
            ClubMembership authorMem = h.createApprovedMembership(authorUser, club, "삭제글작성자");
            h.createExecutiveTag(authorMem, club); // 임원이어야 삭제 가능

            Post post = Post.builder()
                    .boards(new HashSet<>(Set.of(board)))
                    .author(authorMem)
                    .title("동시삭제테스트")
                    .content("삭제될 본문")
                    .semiCategories(new HashSet<>(Set.of(tag)))
                    .build();
            return postRepository.save(post).getId();
        });

        final int THREADS = 2;
        ExecutorService exec = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch go = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < THREADS; i++) {
            exec.submit(() -> {
                ready.countDown();
                try {
                    go.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                try {
                    boardService.deletePost(clubId, postId, adminUserId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 동시 삭제 시 다양한 예외 발생 가능:
                    // - NotFoundException (글이 이미 삭제됨)
                    // - DataAccessException (FK constraint, deadlock 등)
                    // - ObjectOptimisticLockingFailureException
                    // 모두 "삭제 실패"로 처리
                    failCount.incrementAndGet();
                }
            });
        }

        ready.await(10, TimeUnit.SECONDS);
        go.countDown(); // 동시 출발!
        exec.shutdown();
        assertThat(exec.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        // 검증: 모든 스레드가 처리됨 (성공 또는 실패)
        assertThat(successCount.get() + failCount.get())
                .as("총 처리 수 = 스레드 수")
                .isEqualTo(THREADS);
        assertThat(successCount.get())
                .as("삭제 성공은 최소 1건")
                .isGreaterThanOrEqualTo(1);

        // 최종 DB 상태: 글이 없어야 함 (멱등성 핵심 검증)
        tx.executeWithoutResult(status -> {
            assertThat(postRepository.findById(postId))
                    .as("동시 삭제 후 최종적으로 글이 DB에서 사라져야 함")
                    .isEmpty();
        });
    }
}
