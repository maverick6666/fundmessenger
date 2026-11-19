package com.fundmessenger.club.service;

import com.fundmessenger.board.entity.Board;
import com.fundmessenger.board.entity.SemiCategory;
import com.fundmessenger.board.repository.BoardRepository;
import com.fundmessenger.board.repository.SemiCategoryRepository;
import com.fundmessenger.club.dto.AdminFolderResponse;
import com.fundmessenger.club.dto.CreateAdminFolderRequest;
import com.fundmessenger.club.entity.*;
import com.fundmessenger.club.repository.AdminFolderRepository;
import com.fundmessenger.club.repository.TagDefinitionRepository;
import com.fundmessenger.config.exception.ForbiddenException;
import com.fundmessenger.config.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminFolderService {

    private final AdminFolderRepository folderRepo;
    private final TagDefinitionRepository tagDefRepo;
    private final BoardRepository boardRepo;
    private final SemiCategoryRepository semiCatRepo;
    private final ClubAuthorizationService authService;

    /** 폴더 목록 조회 (타입별) */
    public List<AdminFolderResponse> getFolders(Long clubId, AdminFolderType type, Long userId) {
        authService.validateClubAdmin(userId, clubId);
        return folderRepo.findByClubIdAndFolderTypeOrderByDisplayOrderAsc(clubId, type)
                .stream().map(AdminFolderResponse::from).toList();
    }

    /** 폴더 생성 (아이템 합체 시 사용) */
    @Transactional
    public AdminFolderResponse createFolder(Long clubId, CreateAdminFolderRequest req, Long userId) {
        ClubMembership membership = authService.validateClubAdmin(userId, clubId);
        AdminFolderType type = AdminFolderType.valueOf(req.getFolderType());

        AdminFolder folder = AdminFolder.builder()
                .club(membership.getClub())
                .name(req.getName())
                .folderType(type)
                .displayOrder(0)
                .collapsed(false)
                .build();
        folder = folderRepo.save(folder);

        // 아이템 ID가 있으면 해당 아이템들을 폴더에 배정
        if (req.getItemIds() != null && !req.getItemIds().isEmpty()) {
            assignItemsToFolder(folder, type, req.getItemIds());
        }

        return AdminFolderResponse.from(folder);
    }

    /** 폴더명 수정 */
    @Transactional
    public AdminFolderResponse renameFolder(Long clubId, Long folderId, String name, Long userId) {
        authService.validateClubAdmin(userId, clubId);
        AdminFolder folder = folderRepo.findById(folderId)
                .orElseThrow(() -> new NotFoundException("폴더를 찾을 수 없습니다"));
        if (!folder.getClub().getId().equals(clubId)) throw new ForbiddenException("권한 없음");
        folder.setName(name);
        return AdminFolderResponse.from(folderRepo.save(folder));
    }

    /** 폴더 접기/펼치기 토글 */
    @Transactional
    public AdminFolderResponse toggleCollapsed(Long clubId, Long folderId, Long userId) {
        authService.validateClubAdmin(userId, clubId);
        AdminFolder folder = folderRepo.findById(folderId)
                .orElseThrow(() -> new NotFoundException("폴더를 찾을 수 없습니다"));
        if (!folder.getClub().getId().equals(clubId)) throw new ForbiddenException("권한 없음");
        folder.setCollapsed(!folder.isCollapsed());
        return AdminFolderResponse.from(folderRepo.save(folder));
    }

    /** 폴더 삭제 (소속 아이템은 최상위로 이동) */
    @Transactional
    public void deleteFolder(Long clubId, Long folderId, Long userId) {
        authService.validateClubAdmin(userId, clubId);
        AdminFolder folder = folderRepo.findById(folderId)
                .orElseThrow(() -> new NotFoundException("폴더를 찾을 수 없습니다"));
        if (!folder.getClub().getId().equals(clubId)) throw new ForbiddenException("권한 없음");

        // 소속 아이템의 폴더를 null로 해제
        unassignAllItems(folder);
        folderRepo.delete(folder);
    }

    /** 아이템을 폴더에 배정 */
    @Transactional
    public void assignItems(Long clubId, Long folderId, List<Long> itemIds, Long userId) {
        authService.validateClubAdmin(userId, clubId);
        AdminFolder folder = folderRepo.findById(folderId)
                .orElseThrow(() -> new NotFoundException("폴더를 찾을 수 없습니다"));
        if (!folder.getClub().getId().equals(clubId)) throw new ForbiddenException("권한 없음");
        assignItemsToFolder(folder, folder.getFolderType(), itemIds);
    }

    /** 아이템을 폴더에서 해제 (최상위로) */
    @Transactional
    public void unassignItems(Long clubId, List<Long> itemIds, String folderType, Long userId) {
        authService.validateClubAdmin(userId, clubId);
        AdminFolderType type = AdminFolderType.valueOf(folderType);
        switch (type) {
            case TAG -> {
                List<TagDefinition> tags = tagDefRepo.findAllById(itemIds);
                tags.forEach(t -> t.setAdminFolder(null));
                tagDefRepo.saveAll(tags);
            }
            case BOARD -> {
                List<Board> boards = boardRepo.findAllById(itemIds);
                boards.forEach(b -> b.setAdminFolder(null));
                boardRepo.saveAll(boards);
            }
            case CATEGORY, GENERATION -> {
                List<SemiCategory> cats = semiCatRepo.findAllById(itemIds);
                cats.forEach(c -> c.setAdminFolder(null));
                semiCatRepo.saveAll(cats);
            }
        }
    }

    // ── 내부 헬퍼 ──

    private void assignItemsToFolder(AdminFolder folder, AdminFolderType type, List<Long> itemIds) {
        Long clubId = folder.getClub().getId();
        switch (type) {
            case TAG -> {
                List<TagDefinition> tags = tagDefRepo.findAllById(itemIds);
                tags.forEach(t -> {
                    if (!t.getClub().getId().equals(clubId)) {
                        throw new SecurityException("다른 동아리의 태그를 배정할 수 없습니다: id=" + t.getId());
                    }
                    t.setAdminFolder(folder);
                });
                tagDefRepo.saveAll(tags);
            }
            case BOARD -> {
                List<Board> boards = boardRepo.findAllById(itemIds);
                boards.forEach(b -> {
                    if (!b.getClub().getId().equals(clubId)) {
                        throw new SecurityException("다른 동아리의 게시판을 배정할 수 없습니다: id=" + b.getId());
                    }
                    b.setAdminFolder(folder);
                });
                boardRepo.saveAll(boards);
            }
            case CATEGORY, GENERATION -> {
                List<SemiCategory> cats = semiCatRepo.findAllById(itemIds);
                cats.forEach(c -> {
                    if (!c.getClub().getId().equals(clubId)) {
                        throw new SecurityException("다른 동아리의 카테고리를 배정할 수 없습니다: id=" + c.getId());
                    }
                    c.setAdminFolder(folder);
                });
                semiCatRepo.saveAll(cats);
            }
        }
    }

    private void unassignAllItems(AdminFolder folder) {
        switch (folder.getFolderType()) {
            case TAG -> {
                List<TagDefinition> tags = tagDefRepo.findByAdminFolderId(folder.getId());
                tags.forEach(t -> t.setAdminFolder(null));
                tagDefRepo.saveAll(tags);
            }
            case BOARD -> {
                List<Board> boards = boardRepo.findByAdminFolderId(folder.getId());
                boards.forEach(b -> b.setAdminFolder(null));
                boardRepo.saveAll(boards);
            }
            case CATEGORY, GENERATION -> {
                List<SemiCategory> cats = semiCatRepo.findByAdminFolderId(folder.getId());
                cats.forEach(c -> c.setAdminFolder(null));
                semiCatRepo.saveAll(cats);
            }
        }
    }
}
