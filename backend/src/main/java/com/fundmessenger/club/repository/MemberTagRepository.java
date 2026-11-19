package com.fundmessenger.club.repository;

import com.fundmessenger.club.entity.MemberTag;
import com.fundmessenger.club.entity.PermissionLevel;
import com.fundmessenger.club.entity.TagType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MemberTagRepository extends JpaRepository<MemberTag, Long> {

    List<MemberTag> findByClubMembershipId(Long clubMembershipId);

    /** 배치 조회: 여러 멤버십의 태그를 한 번에 로딩 (N+1 방지) */
    List<MemberTag> findByClubMembershipIdIn(List<Long> clubMembershipIds);

    List<MemberTag> findByClubIdOrderByDisplayOrderAsc(Long clubId);

    List<MemberTag> findByTeamId(Long teamId);

    /** 캐러셀용: 팀 태그 + membership + user + generations 한번에 로딩 (LazyInitializationException 방지) */
    @Query("SELECT mt FROM MemberTag mt " +
           "JOIN FETCH mt.clubMembership cm " +
           "JOIN FETCH cm.user " +
           "LEFT JOIN FETCH cm.generations " +
           "WHERE mt.team.id = :teamId")
    List<MemberTag> findByTeamIdWithMembershipDetails(@Param("teamId") Long teamId);

    /** 배치 조회 (N+1 방지) */
    List<MemberTag> findByTeamIdIn(List<Long> teamIds);

    /** 배치 조회 + team FETCH JOIN (groupingBy에서 team LAZY 로드 N+1 방지) */
    @Query("SELECT mt FROM MemberTag mt JOIN FETCH mt.team WHERE mt.team.id IN :teamIds")
    List<MemberTag> findByTeamIdInWithTeam(@Param("teamIds") List<Long> teamIds);

    List<MemberTag> findByTeamIdAndTagType(Long teamId, TagType tagType);

    boolean existsByClubMembershipIdAndPermissionLevel(Long clubMembershipId, PermissionLevel level);

    boolean existsByClubMembershipIdAndTeamId(Long clubMembershipId, Long teamId);

    boolean existsByClubMembershipIdAndTeamIdAndTagType(Long clubMembershipId, Long teamId, TagType tagType);

    Optional<MemberTag> findByClubMembershipIdAndTeamIdAndTagType(Long clubMembershipId, Long teamId, TagType tagType);

    /** 특정 멤버의 특정 팀 태그 (타입 무관) — 시스템 팀 제거용 */
    @Query("SELECT mt FROM MemberTag mt WHERE mt.clubMembership.id = :membershipId AND mt.team.id = :teamId")
    Optional<MemberTag> findByClubMembershipIdAndTeamId(Long membershipId, Long teamId);

    Optional<MemberTag> findByClubMembershipIdAndName(Long clubMembershipId, String name);

    void deleteAllByTeamId(Long teamId);

    long countByTeamId(Long teamId);

    /** 팀 고유 멤버 수 (태그 중복 제외) */
    @Query("SELECT COUNT(DISTINCT mt.clubMembership.id) FROM MemberTag mt WHERE mt.team.id = :teamId")
    long countDistinctMembersByTeamId(@Param("teamId") Long teamId);

    /** 팀 태그가 하나도 없는 멤버 (미배정) */
    @Query("SELECT cm FROM ClubMembership cm WHERE cm.club.id = :clubId " +
           "AND cm.status = 'APPROVED' " +
           "AND NOT EXISTS (SELECT 1 FROM MemberTag mt WHERE mt.clubMembership = cm " +
           "AND mt.tagType IN ('TEAM_LEADER', 'TEAM_MEMBER'))")
    List<com.fundmessenger.club.entity.ClubMembership> findUnassignedMembers(@Param("clubId") Long clubId);

    /** 특정 동아리의 고유 태그 정의 (중복 제거 - name+tagType+permissionLevel 기준) */
    @Query("SELECT DISTINCT mt.name, mt.tagType, mt.permissionLevel, mt.team.id, mt.displayOrder " +
           "FROM MemberTag mt WHERE mt.club.id = :clubId ORDER BY mt.displayOrder")
    List<Object[]> findDistinctTagDefinitions(@Param("clubId") Long clubId);

    /** 태그 이름별 멤버 수 */
    long countByClubIdAndName(Long clubId, String name);

    /** 특정 태그명을 가진 모든 MemberTag 조회 (일괄 업데이트/삭제용) */
    List<MemberTag> findByClubIdAndName(Long clubId, String name);

    /** 대시보드: 동아리 내 팀 소속 태그 (팀별 접속률 산출용) */
    List<MemberTag> findByClubIdAndTagTypeIn(Long clubId, List<TagType> tagTypes);
}
