package com.fundmessenger.club.repository;

import com.fundmessenger.club.entity.Club;
import com.fundmessenger.club.entity.SubscriptionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ClubRepository extends JpaRepository<Club, Long> {

    List<Club> findBySchoolId(Long schoolId);

    long countBySchoolId(Long schoolId);

    /** 크레딧 분배/회수 시 비관적 락 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Club c WHERE c.id = :id")
    Optional<Club> findByIdForUpdate(Long id);

    List<Club> findBySubscriptionStatusAndSubscriptionEndDateBefore(
            SubscriptionStatus status, LocalDate date);

    /** 대시보드: 구독 상태별 동아리 수 */
    long countBySubscriptionStatus(SubscriptionStatus status);
}
