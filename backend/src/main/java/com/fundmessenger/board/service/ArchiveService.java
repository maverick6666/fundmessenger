package com.fundmessenger.board.service;

import com.fundmessenger.board.entity.SemiCategory;
import com.fundmessenger.board.repository.PostRepository;
import com.fundmessenger.board.repository.SemiCategoryRepository;
import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.club.entity.MemberStatus;
import com.fundmessenger.club.entity.Team;
import com.fundmessenger.club.repository.ClubMembershipRepository;
import com.fundmessenger.club.repository.TeamRepository;
import com.fundmessenger.club.service.ClubAuthorizationService;
import com.fundmessenger.drive.repository.DriveFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fundmessenger.board.dto.PostResponse;
import com.fundmessenger.drive.dto.DriveFileResponse;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ArchiveService {

    private final SemiCategoryRepository categoryRepository;
    private final ClubMembershipRepository membershipRepository;
    private final TeamRepository teamRepository;
    private final PostRepository postRepository;
    private final DriveFileRepository driveFileRepository;
    private final ClubAuthorizationService authService;

    /** 아카이브 요약 (특정 기수) */
    @Transactional(readOnly = true)
    public Map<String, Object> getArchiveSummary(Long clubId, Long generationId, Long userId) {
        authService.validateClubMember(userId, clubId);

        SemiCategory gen = validateGeneration(clubId, generationId);

        // 해당 기수 태그를 가진 팀
        List<Team> teams = teamRepository.findByClubIdAndDeletedFalseOrderByCreatedAtAsc(clubId).stream()
                .filter(t -> t.getGenerations().contains(gen))
                .toList();

        // 해당 기수 태그를 가진 멤버
        List<ClubMembership> members = membershipRepository.findByClubIdAndStatus(clubId, MemberStatus.APPROVED).stream()
                .filter(m -> m.getGenerations().contains(gen))
                .toList();

        // 해당 기수 멤버가 작성한 게시글 수 (작성자 기수 기반)
        Page<?> posts = postRepository.findByAuthorGenerationId(clubId, generationId, PageRequest.of(0, 1));
        // 해당 기수 멤버가 업로드한 파일 수 (업로더 기수 기반)
        long fileCount = driveFileRepository.countByUploaderGenerationId(clubId, generationId);

        return Map.of(
                "generation", Map.of("id", gen.getId(), "name", gen.getName()),
                "teamCount", teams.size(),
                "memberCount", members.size(),
                "postCount", posts.getTotalElements(),
                "fileCount", fileCount
        );
    }

    /** 아카이브 팀 목록 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getArchiveTeams(Long clubId, Long generationId, Long userId) {
        authService.validateClubMember(userId, clubId);

        SemiCategory gen = validateGeneration(clubId, generationId);

        return teamRepository.findByClubIdAndDeletedFalseOrderByCreatedAtAsc(clubId).stream()
                .filter(t -> t.getGenerations().contains(gen))
                .map(t -> {
                    long memberCount = membershipRepository.findByClubIdAndStatus(clubId, MemberStatus.APPROVED).stream()
                            .filter(m -> m.getGenerations().contains(gen))
                            .count();
                    return Map.<String, Object>of(
                            "id", t.getId(),
                            "name", t.getName(),
                            "description", t.getDescription() != null ? t.getDescription() : "",
                            "memberCount", memberCount
                    );
                })
                .toList();
    }

    /** 아카이브 멤버 목록 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getArchiveMembers(Long clubId, Long generationId, Long userId) {
        authService.validateClubMember(userId, clubId);

        SemiCategory gen = validateGeneration(clubId, generationId);

        return membershipRepository.findByClubIdAndStatus(clubId, MemberStatus.APPROVED).stream()
                .filter(m -> m.getGenerations().contains(gen))
                .map(m -> {
                    // 최초 기수 = displayOrder 가장 낮은 것
                    String firstGen = m.getGenerations().stream()
                            .min(Comparator.comparingInt(SemiCategory::getDisplayOrder))
                            .map(SemiCategory::getName)
                            .orElse(null);
                    return Map.<String, Object>of(
                            "id", m.getId(),
                            "name", m.getName(),
                            "studentId", m.getStudentId(),
                            "firstGeneration", firstGen != null ? firstGen : ""
                    );
                })
                .toList();
    }

    /** 아카이브 게시글 — 작성자 기수 기반 */
    @Transactional(readOnly = true)
    public Page<PostResponse> getArchivePosts(Long clubId, Long generationId, int page, int size, Long userId) {
        authService.validateClubMember(userId, clubId);
        validateGeneration(clubId, generationId);
        return postRepository.findByAuthorGenerationId(clubId, generationId, PageRequest.of(page, size))
                .map(PostResponse::from);
    }

    /** 아카이브 파일 — 업로더 기수 기반 */
    @Transactional(readOnly = true)
    public Page<DriveFileResponse> getArchiveFiles(Long clubId, Long generationId, int page, int size, Long userId) {
        authService.validateClubMember(userId, clubId);
        validateGeneration(clubId, generationId);
        return driveFileRepository.findByUploaderGenerationId(clubId, generationId, PageRequest.of(page, size))
                .map(DriveFileResponse::from);
    }

    private SemiCategory validateGeneration(Long clubId, Long generationId) {
        SemiCategory gen = categoryRepository.findById(generationId)
                .orElseThrow(() -> new IllegalArgumentException("기수를 찾을 수 없습니다: " + generationId));
        if (!gen.getClub().getId().equals(clubId) || !gen.isGeneration()) {
            throw new SecurityException("해당 동아리의 기수 태그가 아닙니다");
        }
        return gen;
    }
}
