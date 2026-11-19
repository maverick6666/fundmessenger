package com.fundmessenger.home.service;

import com.fundmessenger.board.dto.PostResponse;
import com.fundmessenger.board.repository.PostRepository;
import com.fundmessenger.club.dto.AnnouncementResponse;
import com.fundmessenger.club.entity.CarouselMember;
import com.fundmessenger.club.repository.AnnouncementRepository;
import com.fundmessenger.club.repository.CarouselMemberRepository;
import com.fundmessenger.club.service.ClubAuthorizationService;
import com.fundmessenger.drive.dto.DriveFileResponse;
import com.fundmessenger.drive.repository.DriveFileRepository;
import com.fundmessenger.home.dto.ExecutiveResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeService {

    private final AnnouncementRepository announcementRepository;
    private final PostRepository postRepository;
    private final DriveFileRepository driveFileRepository;
    private final CarouselMemberRepository carouselMemberRepository;
    private final ClubAuthorizationService authService;

    /**
     * 공지사항 배너: 고정된 Announcement 반환 (고정 공지가 없으면 최신 N개)
     */
    public List<AnnouncementResponse> getNotices(Long clubId, int size, Long userId) {
        authService.validateClubMember(userId, clubId);

        List<AnnouncementResponse> pinned = announcementRepository
                .findByClubIdAndIsPinnedTrueOrderByPinOrderAsc(clubId)
                .stream()
                .limit(size)
                .map(AnnouncementResponse::from)
                .collect(Collectors.toList());

        if (!pinned.isEmpty()) return pinned;

        // 고정 공지가 없으면 최신 N개
        return announcementRepository
                .findByClubIdOrderByIsPinnedDescCreatedAtDesc(clubId)
                .stream()
                .limit(size)
                .map(AnnouncementResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 게시판 패널: 팀/태그 이중 필터 + 최신 N개
     */
    public List<PostResponse> getHomePosts(Long clubId, Long teamId, Long tagId, int size, Long userId) {
        authService.validateClubMember(userId, clubId);

        return postRepository.findWithFilters(clubId, teamId, tagId, PageRequest.of(0, size))
                .getContent()
                .stream()
                .map(PostResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 드라이브 패널: 팀/태그 이중 필터 + 최신 N개
     */
    public List<DriveFileResponse> getHomeFiles(Long clubId, Long teamId, Long tagId, int size, Long userId) {
        authService.validateClubMember(userId, clubId);

        return driveFileRepository.findWithFilters(clubId, teamId, tagId, PageRequest.of(0, size))
                .getContent()
                .stream()
                .map(DriveFileResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 임원 캐러셀: CarouselMember 기반
     */
    public List<ExecutiveResponse> getExecutives(Long clubId, Long userId) {
        authService.validateClubMember(userId, clubId);

        List<CarouselMember> members = carouselMemberRepository.findByClubIdWithMembershipDetails(clubId);
        if (members.isEmpty()) return List.of();

        return members.stream()
                .map(ExecutiveResponse::fromCarousel)
                .collect(Collectors.toList());
    }
}
