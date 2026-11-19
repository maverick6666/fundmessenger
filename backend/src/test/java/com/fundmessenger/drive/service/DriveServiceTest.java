package com.fundmessenger.drive.service;

import com.fundmessenger.ai.repository.AiGenerationJobRepository;
import com.fundmessenger.board.entity.SemiCategory;
import com.fundmessenger.board.repository.SemiCategoryRepository;
import com.fundmessenger.club.entity.Club;
import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.club.entity.Team;
import com.fundmessenger.club.repository.MemberTagRepository;
import com.fundmessenger.club.repository.TeamRepository;
import com.fundmessenger.club.service.ClubAuthorizationService;
import com.fundmessenger.drive.dto.DriveFileResponse;
import com.fundmessenger.drive.entity.DriveFile;
import com.fundmessenger.drive.repository.DriveCommentRepository;
import com.fundmessenger.drive.repository.DriveFileRepository;
import com.fundmessenger.drive.repository.DriveFolderRepository;
import com.fundmessenger.file.service.FileStorageService;
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
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class DriveServiceTest {

    @Mock DriveFileRepository driveFileRepository;
    @Mock DriveFolderRepository driveFolderRepository;
    @Mock DriveCommentRepository driveCommentRepository;
    @Mock AiGenerationJobRepository aiGenerationJobRepository;
    @Mock TeamRepository teamRepository;
    @Mock MemberTagRepository memberTagRepository;
    @Mock SemiCategoryRepository semiCategoryRepository;
    @Mock ClubAuthorizationService authService;
    @Mock FileStorageService fileStorageService;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks DriveService sut;

    User user;
    Club club;
    ClubMembership membership;
    ClubMembership otherMembership;

    @BeforeEach
    void setUp() {
        user = TestFixtures.user(1L, "uploader@test.dev");
        club = TestFixtures.club(1L, "테스트동아리");
        membership = TestFixtures.approvedMembership(1L, user, club);

        User otherUser = TestFixtures.user(2L, "other@test.dev");
        otherMembership = TestFixtures.approvedMembership(2L, otherUser, club);
    }

    @Test
    @DisplayName("파일 업로드 — PDF 파일이 정상 업로드된다")
    void uploadFile_success() {
        // given
        MultipartFile file = mock(MultipartFile.class);
        given(file.getContentType()).willReturn("application/pdf");
        given(file.getOriginalFilename()).willReturn("report.pdf");
        given(file.getSize()).willReturn(1024L);

        SemiCategory tag = TestFixtures.semiCategory(1L, club, "분석자료", false);

        given(authService.validateClubMember(1L, 1L)).willReturn(membership);
        given(fileStorageService.storePdf(file)).willReturn("uuid-stored.pdf");
        given(semiCategoryRepository.findAllById(List.of(1L))).willReturn(List.of(tag));
        given(driveFileRepository.save(any(DriveFile.class))).willAnswer(inv -> {
            DriveFile f = inv.getArgument(0);
            f.setId(100L);
            f.setCreatedAt(LocalDateTime.now());
            return f;
        });

        // when
        DriveFileResponse result = sut.uploadFile(1L, file, List.of(), List.of(), List.of(1L),
                "설명", null, 1L);

        // then
        assertThat(result).isNotNull();
        then(fileStorageService).should().storePdf(file);
        then(driveFileRepository).should().save(any(DriveFile.class));
    }

    @Test
    @DisplayName("파일 목록 조회 — 동아리의 파일 목록을 반환한다")
    void getFiles_returnsList() {
        // given
        DriveFile driveFile = DriveFile.builder()
                .club(club).uploader(membership).originalName("test.pdf")
                .storedName("uuid.pdf").fileSize(1024L).mimeType("application/pdf")
                .teams(new HashSet<>()).semiCategories(new HashSet<>())
                .build();
        driveFile.setId(1L);
        driveFile.setCreatedAt(LocalDateTime.now());

        Page<DriveFile> page = new PageImpl<>(List.of(driveFile));

        given(authService.validateClubMember(1L, 1L)).willReturn(membership);
        given(driveFileRepository.findByClubIdWithAssociations(eq(1L), any(PageRequest.class)))
                .willReturn(page);
        given(driveCommentRepository.countByFileIdIn(anyList())).willReturn(List.of());

        // when
        Page<DriveFileResponse> result = sut.getFiles(1L, null, null, null, false, false, 0, 10, 1L);

        // then
        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("파일 삭제 — 업로더가 삭제하면 성공한다")
    void deleteFile_byUploader_succeeds() {
        // given
        DriveFile driveFile = DriveFile.builder()
                .club(club).uploader(membership).originalName("test.pdf")
                .storedName("uuid.pdf").fileSize(1024L)
                .teams(new HashSet<>()).semiCategories(new HashSet<>())
                .build();
        driveFile.setId(10L);

        given(authService.validateClubMember(1L, 1L)).willReturn(membership);
        given(driveFileRepository.findById(10L)).willReturn(Optional.of(driveFile));

        // when
        sut.deleteFile(1L, 10L, 1L);

        // then
        then(driveFileRepository).should().delete(driveFile);
        then(driveFileRepository).should().flush();
        then(fileStorageService).should().deleteFile("uuid.pdf");
    }

    @Test
    @DisplayName("파일 삭제 — 업로더도 아니고 임원도 아니면 SecurityException")
    void deleteFile_byNonUploaderNonAdmin_fails() {
        // given
        DriveFile driveFile = DriveFile.builder()
                .club(club).uploader(membership).originalName("test.pdf")
                .storedName("uuid.pdf").fileSize(1024L)
                .teams(new HashSet<>()).semiCategories(new HashSet<>())
                .build();
        driveFile.setId(10L);

        given(authService.validateClubMember(2L, 1L)).willReturn(otherMembership);
        given(driveFileRepository.findById(10L)).willReturn(Optional.of(driveFile));
        given(authService.isExecutive(otherMembership)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> sut.deleteFile(1L, 10L, 2L))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("권한");
    }

    @Test
    @DisplayName("파일 속성 수정 — 팀/태그가 정상 변경된다")
    void updateFile_changesTeamsAndTags() {
        // given
        Team team = TestFixtures.team(1L, club, "A팀");
        SemiCategory tag = TestFixtures.semiCategory(2L, club, "리서치", false);

        DriveFile driveFile = DriveFile.builder()
                .club(club).uploader(membership).originalName("test.pdf")
                .storedName("uuid.pdf").fileSize(1024L)
                .teams(new HashSet<>()).semiCategories(new HashSet<>())
                .build();
        driveFile.setId(10L);
        driveFile.setCreatedAt(LocalDateTime.now());

        given(authService.validateClubMember(1L, 1L)).willReturn(membership);
        given(driveFileRepository.findById(10L)).willReturn(Optional.of(driveFile));
        given(teamRepository.findAllById(List.of(1L))).willReturn(List.of(team));
        given(semiCategoryRepository.findAllById(List.of(2L))).willReturn(List.of(tag));
        given(driveFileRepository.save(any(DriveFile.class))).willAnswer(inv -> inv.getArgument(0));
        given(driveCommentRepository.countByFileId(10L)).willReturn(0L);

        // when
        DriveFileResponse result = sut.updateFile(1L, 10L, List.of(1L), List.of(), List.of(2L), 1L);

        // then
        assertThat(result).isNotNull();
        then(driveFileRepository).should().save(any(DriveFile.class));
    }
}
