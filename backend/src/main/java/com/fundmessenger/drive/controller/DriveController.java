package com.fundmessenger.drive.controller;

import com.fundmessenger.drive.dto.DriveFileResponse;
import com.fundmessenger.drive.dto.DriveSidebarResponse;
import com.fundmessenger.drive.dto.UpdateDriveFileRequest;
import com.fundmessenger.drive.entity.DriveFolder;
import com.fundmessenger.drive.service.DriveService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/clubs/{clubId}/drive")
@RequiredArgsConstructor
public class DriveController {

    private final DriveService driveService;

    /** 사이드바 데이터 (팀 목록 + 태그 목록) */
    @GetMapping("/sidebar")
    public ResponseEntity<DriveSidebarResponse> getSidebar(
            @PathVariable Long clubId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(driveService.getSidebarData(clubId, userId));
    }

    /** 파일 목록 (팀 + 태그 + 폴더 교차 필터, 다중 선택 지원) */
    @GetMapping("/files")
    public ResponseEntity<Page<DriveFileResponse>> getFiles(
            @PathVariable Long clubId,
            @RequestParam(required = false) List<Long> teamIds,
            @RequestParam(required = false) List<Long> tagIds,
            @RequestParam(required = false) Long folderId,
            @RequestParam(defaultValue = "false") boolean unassigned,
            @RequestParam(defaultValue = "false") boolean myOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(driveService.getFiles(clubId, teamIds, tagIds, folderId, unassigned, myOnly, page, size, userId));
    }

    /** PDF 업로드 (팀 M:N + 폴더 M:N + 태그 선택) */
    @PostMapping("/files")
    public ResponseEntity<DriveFileResponse> uploadFile(
            @PathVariable Long clubId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) List<Long> teamIds,
            @RequestParam(required = false) List<Long> folderIds,
            @RequestParam("semiCategoryIds") List<Long> semiCategoryIds,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String displayName,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(driveService.uploadFile(clubId, file, teamIds, folderIds, semiCategoryIds, description, displayName, userId));
    }

    /** 파일 속성 수정 (팀 M:N + 폴더 M:N + 태그 변경) */
    @PutMapping("/files/{fileId}")
    public ResponseEntity<DriveFileResponse> updateFile(
            @PathVariable Long clubId,
            @PathVariable Long fileId,
            @Valid @RequestBody UpdateDriveFileRequest request,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        List<Long> teamIds = request.getTeamIds() != null ? request.getTeamIds() : List.of();
        List<Long> folderIds = request.getFolderIds() != null ? request.getFolderIds() : List.of();
        return ResponseEntity.ok(driveService.updateFile(clubId, fileId, teamIds, folderIds, request.getSemiCategoryIds(), userId));
    }

    /** 파일 상세 */
    @GetMapping("/files/{fileId}")
    public ResponseEntity<DriveFileResponse> getFile(
            @PathVariable Long clubId,
            @PathVariable Long fileId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(driveService.getFile(clubId, fileId, userId));
    }

    /** 파일 다운로드 */
    @GetMapping("/files/{fileId}/download")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable Long clubId,
            @PathVariable Long fileId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        DriveService.DownloadResult result = driveService.downloadFile(clubId, fileId, userId);

        String encodedName = URLEncoder.encode(result.originalName(), StandardCharsets.UTF_8)
                .replace("+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + encodedName + "\"; filename*=UTF-8''" + encodedName)
                .body(result.resource());
    }

    /** 파일 삭제 */
    @DeleteMapping("/files/{fileId}")
    public ResponseEntity<Void> deleteFile(
            @PathVariable Long clubId,
            @PathVariable Long fileId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        driveService.deleteFile(clubId, fileId, userId);
        return ResponseEntity.noContent().build();
    }

    // ─── 폴더 CRUD ───

    /** 폴더 목록 */
    @GetMapping("/folders")
    public ResponseEntity<List<DriveSidebarResponse.FolderSidebarItem>> getFolders(
            @PathVariable Long clubId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(driveService.getFolders(clubId, userId));
    }

    /** 폴더 생성 (관리자만) */
    @PostMapping("/folders")
    public ResponseEntity<Map<String, Object>> createFolder(
            @PathVariable Long clubId,
            @RequestBody Map<String, Object> body,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        String name = (String) body.get("name");
        String description = (String) body.get("description");
        Integer displayOrder = body.get("displayOrder") != null ? ((Number) body.get("displayOrder")).intValue() : null;
        DriveFolder folder = driveService.createFolder(clubId, name, description, displayOrder, userId);
        return ResponseEntity.ok(Map.of("id", folder.getId(), "name", folder.getName(), "description", folder.getDescription() != null ? folder.getDescription() : "", "displayOrder", folder.getDisplayOrder()));
    }

    /** 폴더 수정 (관리자만) */
    @PutMapping("/folders/{folderId}")
    public ResponseEntity<Map<String, Object>> updateFolder(
            @PathVariable Long clubId,
            @PathVariable Long folderId,
            @RequestBody Map<String, Object> body,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        String name = (String) body.get("name");
        String description = (String) body.get("description");
        Integer displayOrder = body.get("displayOrder") != null ? ((Number) body.get("displayOrder")).intValue() : null;
        DriveFolder folder = driveService.updateFolder(clubId, folderId, name, description, displayOrder, userId);
        return ResponseEntity.ok(Map.of("id", folder.getId(), "name", folder.getName(), "description", folder.getDescription() != null ? folder.getDescription() : "", "displayOrder", folder.getDisplayOrder()));
    }

    /** 폴더 삭제 (관리자만) */
    @DeleteMapping("/folders/{folderId}")
    public ResponseEntity<Void> deleteFolder(
            @PathVariable Long clubId,
            @PathVariable Long folderId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        driveService.deleteFolder(clubId, folderId, userId);
        return ResponseEntity.noContent().build();
    }
}
