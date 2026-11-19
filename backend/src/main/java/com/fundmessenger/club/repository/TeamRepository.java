package com.fundmessenger.club.repository;

import com.fundmessenger.club.entity.Team;
import com.fundmessenger.club.entity.TeamType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {

    /** 크레딧 분배/회수 시 비관적 락 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Team t WHERE t.id = :id")
    Optional<Team> findByIdForUpdate(Long id);

    List<Team> findByClubIdAndDeletedFalseOrderByCreatedAtAsc(Long clubId);

    /** 팀 목록 + generations 한번에 로딩 (N+1 방지) */
    @Query("SELECT DISTINCT t FROM Team t LEFT JOIN FETCH t.generations WHERE t.club.id = :clubId AND t.deleted = false ORDER BY t.createdAt ASC")
    List<Team> findByClubIdWithGenerations(Long clubId);

    Optional<Team> findByClubIdAndName(Long clubId, String name);

    Optional<Team> findByClubIdAndNameAndDeletedFalse(Long clubId, String name);

    long countByClubIdAndDeletedFalse(Long clubId);

    /** deleted 포함 전체 — 팀 이름 중복 검사용 */
    List<Team> findByClubIdOrderByCreatedAtAsc(Long clubId);

    /** 시스템 팀 (회장단) 조회 */
    Optional<Team> findByClubIdAndSystemTeamTrue(Long clubId);
}
