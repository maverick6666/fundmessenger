package com.fundmessenger.club.repository;

import com.fundmessenger.club.entity.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    List<Announcement> findByClubIdOrderByIsPinnedDescCreatedAtDesc(Long clubId);

    List<Announcement> findByClubIdAndIsPinnedTrueOrderByPinOrderAsc(Long clubId);

    List<Announcement> findByClubIdAndIsBannerTrue(Long clubId);

    List<Announcement> findByClubIdAndEventDateBetweenAndReminderDaysBeforeIsNotNull(Long clubId, LocalDateTime from, LocalDateTime to);

    /** 리마인더 스케줄러용: eventDate가 현재 이후이고 reminderDaysBefore가 설정된 모든 공지 */
    List<Announcement> findByEventDateGreaterThanEqualAndReminderDaysBeforeIsNotNull(LocalDateTime now);
}
