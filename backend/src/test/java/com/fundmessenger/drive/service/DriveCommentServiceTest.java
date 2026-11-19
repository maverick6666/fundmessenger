package com.fundmessenger.drive.service;

import com.fundmessenger.club.entity.*;
import com.fundmessenger.club.service.ClubAuthorizationService;
import com.fundmessenger.drive.dto.DriveCommentResponse;
import com.fundmessenger.drive.entity.DriveComment;
import com.fundmessenger.drive.entity.DriveFile;
import com.fundmessenger.drive.repository.DriveCommentRepository;
import com.fundmessenger.drive.repository.DriveFileRepository;
import com.fundmessenger.support.TestFixtures;
import com.fundmessenger.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class DriveCommentServiceTest {

    @Mock private DriveCommentRepository commentRepository;
    @Mock private DriveFileRepository fileRepository;
    @Mock private ClubAuthorizationService authService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private DriveCommentService driveCommentService;

    private Club club;
    private ClubMembership member1, member2;
    private DriveFile driveFile;

    @BeforeEach
    void setUp() {
        club = TestFixtures.club(1L, "테스트동아리");
        User user1 = TestFixtures.user(10L, "uploader@test.dev");
        User user2 = TestFixtures.user(20L, "commenter@test.dev");
        member1 = TestFixtures.approvedMembership(100L, user1, club);
        member2 = TestFixtures.approvedMembership(200L, user2, club);

        driveFile = DriveFile.builder()
                .club(club)
                .uploader(member1)
                .originalName("report.pdf")
                .storedName("uuid-report.pdf")
                .fileSize(1024L)
                .mimeType("application/pdf")
                .build();
        driveFile.setId(1L);
        driveFile.setCreatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("코멘트 작성 성공 — 파일에 코멘트를 달면 저장된다")
    void createComment_success() {
        // given
        given(authService.validateClubMember(20L, 1L)).willReturn(member2);
        given(fileRepository.findById(1L)).willReturn(Optional.of(driveFile));

        DriveComment saved = DriveComment.builder()
                .file(driveFile)
                .author(member2)
                .content("좋은 자료입니다")
                .pageNumber(3)
                .build();
        saved.setId(1L);
        saved.setCreatedAt(LocalDateTime.now());

        given(commentRepository.save(any(DriveComment.class))).willReturn(saved);

        // when
        DriveCommentResponse response = driveCommentService.createComment(1L, 1L, "좋은 자료입니다", 3, 20L);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getContent()).isEqualTo("좋은 자료입니다");
        then(commentRepository).should().save(any(DriveComment.class));
    }

    @Test
    @DisplayName("코멘트 목록 조회 — 파일의 코멘트 페이지가 반환된다")
    void getComments_returnsList() {
        // given
        given(authService.validateClubMember(10L, 1L)).willReturn(member1);
        given(fileRepository.findById(1L)).willReturn(Optional.of(driveFile));

        DriveComment comment = DriveComment.builder()
                .file(driveFile)
                .author(member2)
                .content("코멘트 내용")
                .build();
        comment.setId(1L);
        comment.setCreatedAt(LocalDateTime.now());

        Page<DriveComment> page = new PageImpl<>(List.of(comment));
        given(commentRepository.findByFileIdOrderByCreatedAtDesc(eq(1L), any(PageRequest.class)))
                .willReturn(page);

        // when
        Page<DriveCommentResponse> result = driveCommentService.getComments(1L, 1L, null, 0, 20, 10L);

        // then
        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("코멘트 삭제 성공 — 작성자가 본인 코멘트를 삭제할 수 있다")
    void deleteComment_byAuthor_success() {
        // given
        given(authService.validateClubMember(20L, 1L)).willReturn(member2);
        given(fileRepository.findById(1L)).willReturn(Optional.of(driveFile));

        DriveComment comment = DriveComment.builder()
                .file(driveFile)
                .author(member2)
                .content("삭제할 코멘트")
                .build();
        comment.setId(10L);
        comment.setCreatedAt(LocalDateTime.now());

        given(commentRepository.findById(10L)).willReturn(Optional.of(comment));
        given(authService.isExecutive(member2)).willReturn(false);

        // when
        driveCommentService.deleteComment(1L, 1L, 10L, 20L);

        // then
        then(commentRepository).should().delete(comment);
    }
}
