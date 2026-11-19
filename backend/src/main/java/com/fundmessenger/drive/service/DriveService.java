package com.fundmessenger.drive.service;

import com.fundmessenger.board.dto.SemiCategoryResponse;
import com.fundmessenger.board.entity.Post;
import com.fundmessenger.board.entity.SemiCategory;
import com.fundmessenger.board.repository.SemiCategoryRepository;
import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.club.entity.MemberTag;
import com.fundmessenger.club.entity.Team;
import com.fundmessenger.club.repository.MemberTagRepository;
import com.fundmessenger.club.repository.TeamRepository;
import com.fundmessenger.club.service.ClubAuthorizationService;
import com.fundmessenger.drive.dto.DriveFileResponse;
import com.fundmessenger.drive.dto.DriveSidebarResponse;
import com.fundmessenger.drive.entity.DriveFile;
import com.fundmessenger.drive.entity.DriveFolder;
import com.fundmessenger.config.exception.BadRequestException;
import com.fundmessenger.config.exception.NotFoundException;
import com.fundmessenger.ai.repository.AiGenerationJobRepository;
import com.fundmessenger.drive.repository.DriveCommentRepository;
import com.fundmessenger.drive.repository.DriveFileRepository;
import com.fundmessenger.drive.repository.DriveFolderRepository;
import com.fundmessenger.file.service.FileStorageService;
import com.fundmessenger.notification.event.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DriveService {

    private final DriveFileRepository driveFileRepository;
    private final DriveFolderRepository driveFolderRepository;
    private final DriveCommentRepository driveCommentRepository;
    private final AiGenerationJobRepository aiGenerationJobRepository;
    private final TeamRepository teamRepository;
    private final MemberTagRepository memberTagRepository;
    private final SemiCategoryRepository semiCategoryRepository;
    private final ClubAuthorizationService authService;
    private final FileStorageService fileStorageService;
    private final ApplicationEventPublisher eventPublisher;

    /** 파일 목록 — 팀 + 태그 + 폴더 교차 필터, 다중 선택 지원 */
    @Transactional(readOnly = true)
    public Page<DriveFileResponse> getFiles(Long clubId, List<Long> teamIds, List<Long> tagIds, Long folderId,
                                            boolean unassigned, boolean myOnly, int page, int size, Long userId) {
        ClubMembership membership = authService.validateClubMember(userId, clubId);

        Page<DriveFile> files;
        PageRequest pageable = PageRequest.of(page, size);

        List<Long> effectiveTeamIds = (teamIds == null || teamIds.isEmpty()) ? null : teamIds;
        List<Long> effectiveTagIds = (tagIds == null || tagIds.isEmpty()) ? null : tagIds;

        if (myOnly) {
            Long uploaderId = membership.getId();
            Long singleTagId = effectiveTagIds != null ? effectiveTagIds.get(0) : null;
            if (singleTagId != null) {
                files = driveFileRepository.findByClubIdAndUploaderIdAndTag(clubId, uploaderId, singleTagId, pageable);
            } else {
                files = driveFileRepository.findByClubIdAndUploaderIdWithAssociations(clubId, uploaderId, pageable);
            }
        } else if (folderId != null) {
            files = driveFileRepository.findByClubIdAndFolderId(clubId, folderId, pageable);
        } else if (unassigned) {
            Long singleTagId = effectiveTagIds != null ? effectiveTagIds.get(0) : null;
            files = driveFileRepository.findUnassignedWithTag(clubId, singleTagId, pageable);
        } else if (effectiveTeamIds != null && effectiveTagIds != null) {
            files = driveFileRepository.findByTeamIdsAndTagIds(clubId, effectiveTeamIds, effectiveTagIds, pageable);
        } else if (effectiveTeamIds != null) {
            files = driveFileRepository.findByTeamIds(clubId, effectiveTeamIds, pageable);
        } else if (effectiveTagIds != null) {
            files = driveFileRepository.findByTagIds(clubId, effectiveTagIds, pageable);
        } else {
            files = driveFileRepository.findByClubIdWithAssociations(clubId, pageable);
        }

        return enrichWithCommentCounts(files);
    }

    /** PDF 업로드 — teamIds M:N + folderIds M:N */
    @Transactional
    public DriveFileResponse uploadFile(Long clubId, MultipartFile file, List<Long> teamIds,
                                         List<Long> folderIds, List<Long> semiCategoryIds,
                                         String description, String displayName, Long userId) {
        ClubMembership membership = authService.validateClubMember(userId, clubId);

        // 태그 최소 1개 필수
        if (semiCategoryIds == null || semiCategoryIds.isEmpty()) {
            throw new IllegalArgumentException("태그를 최소 1개 선택해야 합니다");
        }

        // MIME 타입 검증
        String contentType = file.getContentType();
        if (contentType == null || !contentType.equals("application/pdf")) {
            throw new IllegalArgumentException("PDF 파일만 업로드 가능합니다");
        }

        // 파일 크기 검증 (50MB)
        long maxSize = 50L * 1024 * 1024;
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException("파일 크기는 50MB를 초과할 수 없습니다");
        }

        String storedName = fileStorageService.storePdf(file);

        // displayName 처리
        String finalName = file.getOriginalFilename();
        if (displayName != null && !displayName.isBlank()) {
            String ext = "";
            String orig = file.getOriginalFilename();
            if (orig != null && orig.contains(".")) {
                ext = orig.substring(orig.lastIndexOf("."));
            }
            finalName = displayName.trim() + ext;
        }

        DriveFile.DriveFileBuilder builder = DriveFile.builder()
                .club(membership.getClub())
                .uploader(membership)
                .originalName(finalName)
                .storedName(storedName)
                .fileSize(file.getSize())
                .mimeType(file.getContentType())
                .description(description);

        // 팀 M:N 설정
        Set<Team> teams = resolveTeams(clubId, teamIds);
        builder.teams(teams);

        // 폴더 M:N 설정
        Set<DriveFolder> driveFolders = resolveFolders(clubId, folderIds);
        builder.folders(driveFolders);

        // 태그 설정
        Set<SemiCategory> categories = resolveCategories(clubId, semiCategoryIds);
        builder.semiCategories(categories);

        DriveFile saved = driveFileRepository.save(builder.build());
        log.info("드라이브 파일 업로드: clubId={}, fileId={}, name={}, teams={}, folders={}", clubId, saved.getId(), saved.getOriginalName(), teamIds, folderIds);

        // 알림: 팀에 파일 업로드 시 해당 팀 멤버들에게 TEAM_ACTIVITY 알림
        publishTeamActivityForFile(clubId, saved, membership);

        return withFileUrl(DriveFileResponse.from(saved));
    }

    /** 파일 상세 */
    @Transactional(readOnly = true)
    public DriveFileResponse getFile(Long clubId, Long fileId, Long userId) {
        authService.validateClubMember(userId, clubId);
        DriveFile file = getFileOrThrow(fileId, clubId);
        long commentCount = driveCommentRepository.countByFileId(fileId);
        return withFileUrl(DriveFileResponse.from(file, commentCount));
    }

    /** 파일 속성 수정 (업로더 또는 임원급) — 팀 M:N 변경 + 폴더 M:N 변경 + 태그 변경 */
    @Transactional
    public DriveFileResponse updateFile(Long clubId, Long fileId, List<Long> teamIds, List<Long> folderIds, List<Long> semiCategoryIds, Long userId) {
        ClubMembership membership = authService.validateClubMember(userId, clubId);
        DriveFile file = getFileOrThrow(fileId, clubId);

        boolean isUploader = file.getUploader().getId().equals(membership.getId());
        boolean isAdmin = authService.isExecutive(membership);
        if (!isUploader && !isAdmin) {
            throw new SecurityException("파일 속성을 수정할 권한이 없습니다");
        }

        // 태그 최소 1개 필수
        if (semiCategoryIds == null || semiCategoryIds.isEmpty()) {
            throw new IllegalArgumentException("태그를 최소 1개 선택해야 합니다");
        }

        // 팀 M:N 변경
        Set<Team> teams = resolveTeams(clubId, teamIds);
        file.getTeams().clear();
        file.getTeams().addAll(teams);

        // 폴더 M:N 변경
        Set<DriveFolder> driveFolders = resolveFolders(clubId, folderIds);
        file.getFolders().clear();
        file.getFolders().addAll(driveFolders);

        // 태그 변경
        Set<SemiCategory> categories = resolveCategories(clubId, semiCategoryIds);
        file.getSemiCategories().clear();
        file.getSemiCategories().addAll(categories);

        DriveFile saved = driveFileRepository.save(file);
        long commentCount = driveCommentRepository.countByFileId(fileId);
        log.info("드라이브 파일 속성 수정: clubId={}, fileId={}", clubId, fileId);
        return withFileUrl(DriveFileResponse.from(saved, commentCount));
    }

    /** 파일 삭제 (업로더 또는 임원급) */
    @Transactional
    public void deleteFile(Long clubId, Long fileId, Long userId) {
        ClubMembership membership = authService.validateClubMember(userId, clubId);
        DriveFile file = getFileOrThrow(fileId, clubId);

        boolean isUploader = file.getUploader().getId().equals(membership.getId());
        boolean isAdmin = authService.isExecutive(membership);

        if (!isUploader && !isAdmin) {
            throw new SecurityException("파일을 삭제할 권한이 없습니다");
        }

        // 연동된 Post FK 해제 (양방향)
        Post linkedPost = file.getLinkedPost();
        if (linkedPost != null) {
            linkedPost.setLinkedDriveFile(null);
            file.setLinkedPost(null);
            log.info("DriveFile 삭제 → Post FK 해제: postId={}", linkedPost.getId());
        }

        // 연동된 AI 생성 작업 삭제
        aiGenerationJobRepository.deleteByDriveFileId(fileId);

        // 코멘트 삭제
        driveCommentRepository.deleteByFileId(fileId);

        // 연결 해제
        file.getTeams().clear();
        file.getSemiCategories().clear();
        file.getFolders().clear();

        // DB 삭제 먼저 (트랜잭션으로 보호) → 파일 삭제 나중 (실패해도 고아 파일만 남음)
        String storedName = file.getStoredName();
        driveFileRepository.delete(file);
        driveFileRepository.flush();
        fileStorageService.deleteFile(storedName);
        log.info("드라이브 파일 삭제: clubId={}, fileId={}", clubId, fileId);
    }

    /** 파일 다운로드 */
    @Transactional(readOnly = true)
    public DownloadResult downloadFile(Long clubId, Long fileId, Long userId) {
        authService.validateClubMember(userId, clubId);
        DriveFile file = getFileOrThrow(fileId, clubId);

        try {
            byte[] content = fileStorageService.downloadAsBytes(file.getStoredName());
            Resource resource = new org.springframework.core.io.ByteArrayResource(content);
            return new DownloadResult(resource, file.getOriginalName());
        } catch (Exception e) {
            throw new BadRequestException("파일 다운로드 실패: " + e.getMessage());
        }
    }

    /** 사이드바 데이터 — 팀 목록(+무소속) + 태그 목록 */
    @Transactional(readOnly = true)
    public DriveSidebarResponse getSidebarData(Long clubId, Long userId) {
        authService.validateClubMember(userId, clubId);

        List<Team> teams = teamRepository.findByClubIdAndDeletedFalseOrderByCreatedAtAsc(clubId);

        // 팀별 파일 수 일괄 조회
        Map<Long, Long> teamFileCounts = driveFileRepository.countByTeamGrouped(clubId).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]
                ));
        long unassignedCount = driveFileRepository.countByClubIdAndTeamsEmpty(clubId);

        // 팀별 멤버 수 배치 조회
        List<Long> teamIds = teams.stream().map(Team::getId).toList();
        List<MemberTag> allTeamTags = teamIds.isEmpty() ? List.of() : memberTagRepository.findByTeamIdInWithTeam(teamIds);
        Map<Long, Long> teamMemberCounts = allTeamTags.stream()
                .collect(Collectors.groupingBy(mt -> mt.getTeam().getId(), Collectors.counting()));

        List<DriveSidebarResponse.TeamSidebarItem> teamItems = new ArrayList<>();
        for (Team team : teams) {
            teamItems.add(DriveSidebarResponse.TeamSidebarItem.builder()
                    .id(team.getId())
                    .name(team.getName())
                    .description(team.getDescription())
                    .memberCount(teamMemberCounts.getOrDefault(team.getId(), 0L))
                    .fileCount(teamFileCounts.getOrDefault(team.getId(), 0L))
                    .systemTeam(team.isSystemTeam())
                    .build());
        }
        teamItems.add(DriveSidebarResponse.TeamSidebarItem.builder()
                .id(null)
                .name("자유")
                .fileCount(unassignedCount)
                .build());

        // 폴더 목록 + 파일 수
        List<DriveFolder> driveFolders = driveFolderRepository.findByClubIdOrderByDisplayOrderAsc(clubId);
        Map<Long, Long> folderFileCounts = driveFileRepository.countByFolderGrouped(clubId).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]
                ));
        List<DriveSidebarResponse.FolderSidebarItem> folderItems = driveFolders.stream()
                .map(f -> DriveSidebarResponse.FolderSidebarItem.builder()
                        .id(f.getId())
                        .name(f.getName())
                        .description(f.getDescription())
                        .fileCount(folderFileCounts.getOrDefault(f.getId(), 0L))
                        .build())
                .collect(Collectors.toList());

        List<SemiCategoryResponse> tags = semiCategoryRepository
                .findByClubIdOrderByDisplayOrderAscCreatedAtAsc(clubId).stream()
                .filter(sc -> !sc.isGeneration())
                .filter(sc -> sc.isEnabled())
                .map(SemiCategoryResponse::from)
                .collect(Collectors.toList());

        return DriveSidebarResponse.builder()
                .teams(teamItems)
                .folders(folderItems)
                .tags(tags)
                .build();
    }

    // ─── 폴더 CRUD ───

    /** 폴더 목록 */
    @Transactional(readOnly = true)
    public List<DriveSidebarResponse.FolderSidebarItem> getFolders(Long clubId, Long userId) {
        authService.validateClubMember(userId, clubId);
        List<DriveFolder> folders = driveFolderRepository.findByClubIdOrderByDisplayOrderAsc(clubId);
        Map<Long, Long> fileCounts = driveFileRepository.countByFolderGrouped(clubId).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
        return folders.stream()
                .map(f -> DriveSidebarResponse.FolderSidebarItem.builder()
                        .id(f.getId())
                        .name(f.getName())
                        .description(f.getDescription())
                        .fileCount(fileCounts.getOrDefault(f.getId(), 0L))
                        .build())
                .collect(Collectors.toList());
    }

    /** 폴더 생성 (관리자만) */
    @Transactional
    public DriveFolder createFolder(Long clubId, String name, String description, Integer displayOrder, Long userId) {
        ClubMembership membership = authService.validateClubMember(userId, clubId);
        if (!authService.isExecutive(membership)) {
            throw new SecurityException("폴더를 생성할 권한이 없습니다");
        }
        if (driveFolderRepository.findByClubIdAndName(clubId, name).isPresent()) {
            throw new BadRequestException("동일한 이름의 폴더가 이미 존재합니다: " + name);
        }
        DriveFolder folder = DriveFolder.builder()
                .name(name)
                .club(membership.getClub())
                .description(description)
                .displayOrder(displayOrder != null ? displayOrder : 0)
                .build();
        return driveFolderRepository.save(folder);
    }

    /** 폴더 수정 (관리자만) */
    @Transactional
    public DriveFolder updateFolder(Long clubId, Long folderId, String name, String description, Integer displayOrder, Long userId) {
        ClubMembership membership = authService.validateClubMember(userId, clubId);
        if (!authService.isExecutive(membership)) {
            throw new SecurityException("폴더를 수정할 권한이 없습니다");
        }
        DriveFolder folder = driveFolderRepository.findById(folderId)
                .orElseThrow(() -> new NotFoundException("폴더를 찾을 수 없습니다: " + folderId));
        if (!folder.getClub().getId().equals(clubId)) {
            throw new SecurityException("해당 동아리의 폴더가 아닙니다");
        }
        // 이름 중복 체크 (자기 자신 제외)
        Optional<DriveFolder> existing = driveFolderRepository.findByClubIdAndName(clubId, name);
        if (existing.isPresent() && !existing.get().getId().equals(folderId)) {
            throw new BadRequestException("동일한 이름의 폴더가 이미 존재합니다: " + name);
        }
        folder.setName(name);
        folder.setDescription(description);
        if (displayOrder != null) folder.setDisplayOrder(displayOrder);
        return driveFolderRepository.save(folder);
    }

    /** 폴더 삭제 (관리자만) — 파일의 폴더 연결만 해제, 파일은 유지 */
    @Transactional
    public void deleteFolder(Long clubId, Long folderId, Long userId) {
        ClubMembership membership = authService.validateClubMember(userId, clubId);
        if (!authService.isExecutive(membership)) {
            throw new SecurityException("폴더를 삭제할 권한이 없습니다");
        }
        DriveFolder folder = driveFolderRepository.findById(folderId)
                .orElseThrow(() -> new NotFoundException("폴더를 찾을 수 없습니다: " + folderId));
        if (!folder.getClub().getId().equals(clubId)) {
            throw new SecurityException("해당 동아리의 폴더가 아닙니다");
        }
        driveFolderRepository.delete(folder);
        log.info("드라이브 폴더 삭제: clubId={}, folderId={}, name={}", clubId, folderId, folder.getName());
    }

    public record DownloadResult(Resource resource, String originalName) {}

    // ─── 내부 헬퍼 ───

    private DriveFile getFileOrThrow(Long fileId, Long clubId) {
        DriveFile file = driveFileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("파일을 찾을 수 없습니다: " + fileId));
        if (!file.getClub().getId().equals(clubId)) {
            throw new SecurityException("해당 동아리의 파일이 아닙니다");
        }
        return file;
    }

    private Set<Team> resolveTeams(Long clubId, List<Long> teamIds) {
        if (teamIds == null || teamIds.isEmpty()) {
            return new HashSet<>(); // 무소속
        }
        List<Team> teams = teamRepository.findAllById(teamIds);
        if (teams.size() != teamIds.size()) {
            throw new IllegalArgumentException("존재하지 않는 팀이 포함되어 있습니다");
        }
        for (Team team : teams) {
            if (!team.getClub().getId().equals(clubId)) {
                throw new SecurityException("해당 동아리의 팀이 아닙니다");
            }
        }
        return new HashSet<>(teams);
    }

    private Set<DriveFolder> resolveFolders(Long clubId, List<Long> folderIds) {
        if (folderIds == null || folderIds.isEmpty()) {
            return new HashSet<>();
        }
        List<DriveFolder> folders = driveFolderRepository.findAllById(folderIds);
        if (folders.size() != folderIds.size()) {
            throw new IllegalArgumentException("존재하지 않는 폴더가 포함되어 있습니다");
        }
        for (DriveFolder folder : folders) {
            if (!folder.getClub().getId().equals(clubId)) {
                throw new SecurityException("해당 동아리의 폴더가 아닙니다");
            }
        }
        return new HashSet<>(folders);
    }

    private Set<SemiCategory> resolveCategories(Long clubId, List<Long> categoryIds) {
        List<SemiCategory> categories = semiCategoryRepository.findAllById(categoryIds);
        if (categories.size() != categoryIds.size()) {
            throw new IllegalArgumentException("존재하지 않는 태그가 포함되어 있습니다");
        }
        for (SemiCategory cat : categories) {
            if (!cat.getClub().getId().equals(clubId)) {
                throw new SecurityException("해당 동아리의 태그가 아닙니다");
            }
        }
        return new HashSet<>(categories);
    }

    /** 파일 업로드 시 팀 멤버들에게 TEAM_ACTIVITY 알림 발행 */
    private void publishTeamActivityForFile(Long clubId, DriveFile file, ClubMembership uploader) {
        Long uploaderUserId = uploader.getUser().getId();

        List<Long> teamIds = file.getTeams().stream().map(Team::getId).toList();
        if (teamIds.isEmpty()) return;

        // 배치 로드로 N+1 방지
        List<MemberTag> allTags = memberTagRepository.findByTeamIdIn(teamIds);
        Map<Long, List<Long>> teamToUserIds = allTags.stream()
                .collect(Collectors.groupingBy(
                        mt -> mt.getTeam().getId(),
                        Collectors.mapping(mt -> mt.getClubMembership().getUser().getId(),
                                Collectors.collectingAndThen(Collectors.toSet(), ArrayList::new))
                ));

        for (Team team : file.getTeams()) {
            List<Long> targetUserIds = teamToUserIds.getOrDefault(team.getId(), List.of()).stream()
                    .filter(uid -> !uid.equals(uploaderUserId))
                    .toList();

            if (!targetUserIds.isEmpty()) {
                eventPublisher.publishEvent(NotificationEvent.ofTeamActivity(
                        clubId, targetUserIds, team.getId(),
                        uploader.getName() + "님이 새 파일을 업로드했습니다",
                        file.getOriginalName(),
                        "/club/drive/" + file.getId()
                ));
            }
        }
    }

    private Page<DriveFileResponse> enrichWithCommentCounts(Page<DriveFile> files) {
        List<Long> fileIds = files.getContent().stream().map(DriveFile::getId).collect(Collectors.toList());
        Map<Long, Long> commentCounts = Map.of();
        if (!fileIds.isEmpty()) {
            commentCounts = driveCommentRepository.countByFileIdIn(fileIds).stream()
                    .collect(Collectors.toMap(
                            row -> (Long) row[0],
                            row -> (Long) row[1]
                    ));
        }
        final Map<Long, Long> counts = commentCounts;
        return files.map(f -> withFileUrl(DriveFileResponse.from(f, counts.getOrDefault(f.getId(), 0L))));
    }

    /** DriveFileResponse에 fileUrl 세팅 */
    private DriveFileResponse withFileUrl(DriveFileResponse resp) {
        return DriveFileResponse.builder()
                .id(resp.getId())
                .originalName(resp.getOriginalName())
                .storedName(resp.getStoredName())
                .fileUrl(fileStorageService.getPublicUrl(resp.getStoredName()))
                .fileSize(resp.getFileSize())
                .mimeType(resp.getMimeType())
                .description(resp.getDescription())
                .teams(resp.getTeams())
                .uploaderName(resp.getUploaderName())
                .uploaderMembershipId(resp.getUploaderMembershipId())
                .semiCategories(resp.getSemiCategories())
                .folders(resp.getFolders())
                .linkedPostId(resp.getLinkedPostId())
                .linkedPostTitle(resp.getLinkedPostTitle())
                .createdAt(resp.getCreatedAt())
                .commentCount(resp.getCommentCount())
                .build();
    }
}
