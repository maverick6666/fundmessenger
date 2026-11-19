package com.fundmessenger.club.service;

import com.fundmessenger.calendar.entity.CalendarEvent;
import com.fundmessenger.calendar.repository.CalendarEventRepository;
import com.fundmessenger.club.dto.AnnouncementResponse;
import com.fundmessenger.club.dto.CreateAnnouncementRequest;
import com.fundmessenger.club.entity.Announcement;
import com.fundmessenger.club.entity.Club;
import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.club.entity.MemberStatus;
import com.fundmessenger.club.repository.AnnouncementRepository;
import com.fundmessenger.club.repository.ClubMembershipRepository;
import com.fundmessenger.club.repository.ClubRepository;
import com.fundmessenger.notification.entity.NotificationType;
import com.fundmessenger.notification.event.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final CalendarEventRepository calendarEventRepository;
    private final ClubRepository clubRepository;
    private final ClubMembershipRepository clubMembershipRepository;
    private final ClubAuthorizationService authService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<AnnouncementResponse> getAnnouncements(Long clubId, Long userId) {
        authService.validateClubMember(userId, clubId);
        return announcementRepository.findByClubIdOrderByIsPinnedDescCreatedAtDesc(clubId)
                .stream().map(AnnouncementResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public AnnouncementResponse getAnnouncement(Long clubId, Long announcementId, Long userId) {
        authService.validateClubMember(userId, clubId);
        Announcement a = getAnnouncementOrThrow(announcementId, clubId);
        return AnnouncementResponse.from(a);
    }

    @Transactional
    public AnnouncementResponse createAnnouncement(Long clubId, CreateAnnouncementRequest request, Long userId) {
        ClubMembership membership = authService.validateClubMember(userId, clubId);
        if (!authService.isExecutive(membership)) {
            throw new SecurityException("임원급만 공지를 작성할 수 있습니다");
        }

        Club club = clubRepository.findById(clubId).orElseThrow();

        Announcement announcement = Announcement.builder()
                .club(club)
                .title(request.getTitle())
                .content(request.getContent())
                .eventDate(request.getEventDate())
                .isPinned(request.isPinned())
                .isBanner(request.isBanner())
                .reminderDaysBefore(toReminderString(request.getReminderDaysBefore()))
                .createdBy(membership)
                .build();

        // 날짜 → CalendarEvent 자동 생성
        if (request.getEventDate() != null) {
            CalendarEvent event = CalendarEvent.builder()
                    .club(club)
                    .title(request.getTitle())
                    .eventDate(request.getEventDate().toLocalDate())
                    .createdBy(membership)
                    .build();
            event = calendarEventRepository.save(event);
            announcement.setLinkedEvent(event);
        }

        Announcement saved = announcementRepository.save(announcement);
        log.info("공지 생성: clubId={}, id={}", clubId, saved.getId());

        // 알림: 전 멤버에게 공지 알림
        List<Long> targetUserIds = clubMembershipRepository.findByClubIdAndStatus(clubId, MemberStatus.APPROVED)
                .stream()
                .filter(m -> !m.isBot() && !m.getUser().getId().equals(userId))
                .map(m -> m.getUser().getId())
                .toList();
        if (!targetUserIds.isEmpty()) {
            eventPublisher.publishEvent(NotificationEvent.ofMany(
                    clubId, targetUserIds, NotificationType.NEW_NOTICE,
                    "새 공지: " + request.getTitle(),
                    request.getContent().length() > 50 ? request.getContent().substring(0, 50) + "…" : request.getContent(),
                    "/club"
            ));
        }

        return AnnouncementResponse.from(saved);
    }

    @Transactional
    public AnnouncementResponse updateAnnouncement(Long clubId, Long announcementId,
                                                    CreateAnnouncementRequest request, Long userId) {
        ClubMembership membership = authService.validateClubMember(userId, clubId);
        if (!authService.isExecutive(membership)) {
            throw new SecurityException("임원급만 공지를 수정할 수 있습니다");
        }

        Announcement a = getAnnouncementOrThrow(announcementId, clubId);
        a.setTitle(request.getTitle());
        a.setContent(request.getContent());
        a.setPinned(request.isPinned());
        a.setBanner(request.isBanner());
        a.setReminderDaysBefore(toReminderString(request.getReminderDaysBefore()));

        // 날짜 변경 → CalendarEvent 동기화
        if (request.getEventDate() != null) {
            if (a.getLinkedEvent() != null) {
                a.getLinkedEvent().setTitle(request.getTitle());
                a.getLinkedEvent().setEventDate(request.getEventDate().toLocalDate());
            } else {
                Club club = clubRepository.findById(clubId).orElseThrow();
                CalendarEvent event = CalendarEvent.builder()
                        .club(club).title(request.getTitle())
                        .eventDate(request.getEventDate().toLocalDate())
                        .createdBy(membership).build();
                event = calendarEventRepository.save(event);
                a.setLinkedEvent(event);
            }
        } else if (a.getLinkedEvent() != null) {
            calendarEventRepository.delete(a.getLinkedEvent());
            a.setLinkedEvent(null);
        }

        a.setEventDate(request.getEventDate());
        return AnnouncementResponse.from(a);
    }

    @Transactional
    public void deleteAnnouncement(Long clubId, Long announcementId, Long userId) {
        ClubMembership membership = authService.validateClubMember(userId, clubId);
        if (!authService.isExecutive(membership)) {
            throw new SecurityException("임원급만 공지를 삭제할 수 있습니다");
        }

        Announcement a = getAnnouncementOrThrow(announcementId, clubId);

        // 연동 삭제
        if (a.getLinkedEvent() != null) {
            calendarEventRepository.delete(a.getLinkedEvent());
        }

        announcementRepository.delete(a);
        log.info("공지 삭제: clubId={}, id={}", clubId, announcementId);
    }

    @Transactional
    public void togglePin(Long clubId, Long announcementId, Long userId) {
        ClubMembership membership = authService.validateClubMember(userId, clubId);
        if (!authService.isExecutive(membership)) throw new SecurityException("권한 없음");
        Announcement a = getAnnouncementOrThrow(announcementId, clubId);
        a.setPinned(!a.isPinned());
    }

    @Transactional
    public void toggleBanner(Long clubId, Long announcementId, Long userId) {
        ClubMembership membership = authService.validateClubMember(userId, clubId);
        if (!authService.isExecutive(membership)) throw new SecurityException("권한 없음");
        Announcement a = getAnnouncementOrThrow(announcementId, clubId);
        a.setBanner(!a.isBanner());
    }

    private Announcement getAnnouncementOrThrow(Long id, Long clubId) {
        Announcement a = announcementRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("공지를 찾을 수 없습니다: " + id));
        if (!a.getClub().getId().equals(clubId)) {
            throw new SecurityException("해당 동아리의 공지가 아닙니다");
        }
        return a;
    }

    private String toReminderString(List<Integer> days) {
        if (days == null || days.isEmpty()) return null;
        return days.stream().map(String::valueOf).collect(Collectors.joining(","));
    }
}
