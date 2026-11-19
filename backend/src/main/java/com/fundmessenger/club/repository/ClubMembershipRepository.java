package com.fundmessenger.club.repository;

import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.club.entity.MemberStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ClubMembershipRepository extends JpaRepository<ClubMembership, Long> {

    /** 사용자의 모든 멤버십 (승인된 것만) */
    List<ClubMembership> findByUserIdAndStatus(Long userId, MemberStatus status);

    /** 사용자의 모든 멤버십 (상태 무관) */
    List<ClubMembership> findByUserId(Long userId);

    /** 특정 동아리 + 사용자 중복 체크 */
    Optional<ClubMembership> findByUserIdAndClubId(Long userId, Long clubId);

    /** 특정 동아리의 상태별 멤버십 목록 */
    List<ClubMembership> findByClubIdAndStatus(Long clubId, MemberStatus status);

    /** 상태별 전체 멤버십 조회 (SUPER_ADMIN 관리용) */
    List<ClubMembership> findAllByStatus(MemberStatus status);

    /** 대시보드: 상태별 멤버십 수 */
    long countByStatus(MemberStatus status);

    /** 특정 동아리의 승인된 전체 멤버 목록 (이름순) */
    List<ClubMembership> findByClubIdAndStatusOrderByNameAsc(Long clubId, MemberStatus status);

    /** 1인 1계정: 같은 학교 내 동일 학번으로 가입된 다른 계정 존재 여부 */
    @Query("SELECT m FROM ClubMembership m WHERE m.studentId = :studentId " +
           "AND m.club.school.id = :schoolId AND m.user.id <> :userId")
    Optional<ClubMembership> findByStudentIdInSameSchoolByOtherUser(
            @Param("studentId") String studentId,
            @Param("schoolId") Long schoolId,
            @Param("userId") Long userId
    );

    /** 대시보드: 동아리별 상태별 멤버 수 */
    long countByClubIdAndStatus(Long clubId, MemberStatus status);

    /** 대시보드: 기간 내 신규 승인 멤버 수 */
    @Query("SELECT COUNT(m) FROM ClubMembership m WHERE m.club.id = :clubId " +
           "AND m.status = com.fundmessenger.club.entity.MemberStatus.APPROVED " +
           "AND m.approvedAt >= :since")
    long countNewApprovedSince(@Param("clubId") Long clubId, @Param("since") LocalDateTime since);

    /** 대시보드: 월별 가입자 수 (가입 추이) */
    @Query("SELECT FUNCTION('TO_CHAR', m.approvedAt, 'YYYY-MM'), COUNT(m) FROM ClubMembership m " +
           "WHERE m.club.id = :clubId AND m.status = com.fundmessenger.club.entity.MemberStatus.APPROVED " +
           "AND m.approvedAt >= :since " +
           "GROUP BY FUNCTION('TO_CHAR', m.approvedAt, 'YYYY-MM') " +
           "ORDER BY FUNCTION('TO_CHAR', m.approvedAt, 'YYYY-MM') ASC")
    List<Object[]> findMonthlySignups(@Param("clubId") Long clubId, @Param("since") LocalDateTime since);

    /** 특정 동아리에서 아무 팀에도 속하지 않은 APPROVED 멤버 조회 (태그 기반) */
    @Query("SELECT cm FROM ClubMembership cm WHERE cm.club.id = :clubId " +
           "AND cm.status = com.fundmessenger.club.entity.MemberStatus.APPROVED " +
           "AND cm.id NOT IN (" +
           "  SELECT mt.clubMembership.id FROM MemberTag mt " +
           "  WHERE mt.club.id = :clubId AND mt.tagType IN (com.fundmessenger.club.entity.TagType.TEAM_LEADER, com.fundmessenger.club.entity.TagType.TEAM_MEMBER)" +
           ")")
    List<ClubMembership> findUnassignedMembers(@Param("clubId") Long clubId);
}
