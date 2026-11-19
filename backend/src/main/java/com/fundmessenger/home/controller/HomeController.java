package com.fundmessenger.home.controller;

import com.fundmessenger.board.dto.PostResponse;
import com.fundmessenger.club.dto.AnnouncementResponse;
import com.fundmessenger.drive.dto.DriveFileResponse;
import com.fundmessenger.home.dto.ExecutiveResponse;
import com.fundmessenger.club.service.CarouselService;
import com.fundmessenger.home.service.HomeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clubs/{clubId}/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;
    private final CarouselService carouselService;

    /** 공지사항 배너 — 고정 공지 또는 최신 1건 */
    @GetMapping("/notices")
    public ResponseEntity<List<AnnouncementResponse>> getNotices(
            @PathVariable Long clubId,
            @RequestParam(defaultValue = "1") int size,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(homeService.getNotices(clubId, size, userId));
    }

    /** 게시판 패널 — 팀/태그 이중 필터 + 최신 N개 */
    @GetMapping("/posts")
    public ResponseEntity<List<PostResponse>> getHomePosts(
            @PathVariable Long clubId,
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false) Long tagId,
            @RequestParam(defaultValue = "5") int size,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(homeService.getHomePosts(clubId, teamId, tagId, size, userId));
    }

    /** 드라이브 패널 — 팀/태그 이중 필터 + 최신 N개 */
    @GetMapping("/files")
    public ResponseEntity<List<DriveFileResponse>> getHomeFiles(
            @PathVariable Long clubId,
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false) Long tagId,
            @RequestParam(defaultValue = "5") int size,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(homeService.getHomeFiles(clubId, teamId, tagId, size, userId));
    }

    /** 임원 캐러셀 */
    @GetMapping("/executives")
    public ResponseEntity<List<ExecutiveResponse>> getExecutives(
            @PathVariable Long clubId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(homeService.getExecutives(clubId, userId));
    }

    /** 캐러셀 본인 소개글 수정 */
    @PutMapping("/carousel/{carouselId}/bio")
    public ResponseEntity<ExecutiveResponse> updateCarouselBio(
            @PathVariable Long clubId,
            @PathVariable Long carouselId,
            @RequestBody java.util.Map<String, String> body,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(carouselService.updateCarouselBioByMember(clubId, carouselId, body.get("bio"), userId));
    }
}
