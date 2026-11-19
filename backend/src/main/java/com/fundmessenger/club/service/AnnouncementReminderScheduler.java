package com.fundmessenger.club.service;

import com.fundmessenger.club.entity.Announcement;
import com.fundmessenger.club.entity.MemberStatus;
import com.fundmessenger.club.repository.AnnouncementRepository;
import com.fundmessenger.club.repository.ClubMembershipRepository;
import com.fundmessenger.notification.entity.NotificationType;
import com.fundmessenger.notification.event.NotificationEvent;
import com.fundmessenger.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnnouncementReminderScheduler {

    private final AnnouncementRepository announcementRepository;
    private final ClubMembershipRepository membershipRepository;
    private final NotificationRepository notificationRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** 매일 오전 9시에 공지 리마인더 발행 */
    @Scheduled(cron = "0 0 9 * * *")
    public void sendReminders() {
        LocalDate today = LocalDate.now();

        List<Announcement> announcements = announcementRepository
                .findByEventDateGreaterThanEqualAndReminderDaysBeforeIsNotNull(today.atStartOfDay());

        int sent = 0;
        for (Announcement a : announcements) {
            int[] days = parseDays(a.getReminderDaysBefore());
            for (int d : days) {
                if (a.getEventDate().toLocalDate().minusDays(d).equals(today)) {
                    if (sendReminderIfNotSent(a, d)) {
                        sent++;
                    }
                }
            }
        }

        if (sent > 0) {
            log.info("공지 리마인더 {} 건 발행 완료", sent);
        }
    }

    private int[] parseDays(String csv) {
        if (csv == null || csv.isBlank()) return new int[0];
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .mapToInt(Integer::parseInt)
                .toArray();
    }

    private boolean sendReminderIfNotSent(Announcement a, int daysBefore) {
        String targetUrl = "/club?announcement=" + a.getId() + "&reminder=" + daysBefore;

        // 중복 체크: 같은 clubId + targetUrl로 이미 발행되었으면 스킵
        if (notificationRepository.existsByClubIdAndTargetUrl(a.getClub().getId(), targetUrl)) {
            return false;
        }

        List<Long> memberUserIds = membershipRepository
                .findByClubIdAndStatus(a.getClub().getId(), MemberStatus.APPROVED)
                .stream()
                .map(m -> m.getUser().getId())
                .toList();

        if (memberUserIds.isEmpty()) return false;

        eventPublisher.publishEvent(NotificationEvent.ofMany(
                a.getClub().getId(),
                memberUserIds,
                NotificationType.NOTICE_REMINDER,
                "[공지 리마인더] " + a.getTitle() + " — D-" + daysBefore,
                a.getEventDate().toString(),
                targetUrl
        ));

        log.info("공지 리마인더 발행: clubId={}, announcementId={}, D-{}",
                a.getClub().getId(), a.getId(), daysBefore);
        return true;
    }
}
