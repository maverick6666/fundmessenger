package com.fundmessenger.drive.repository;

import com.fundmessenger.drive.entity.DriveFile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface DriveFileRepository extends JpaRepository<DriveFile, Long> {

    /** 동아리 전체 파일 (최신순) — FETCH JOIN으로 N+1 방지 */
    @Query(value = "SELECT DISTINCT f FROM DriveFile f " +
           "LEFT JOIN FETCH f.teams " +
           "LEFT JOIN FETCH f.semiCategories " +
           "LEFT JOIN FETCH f.folders " +
           "LEFT JOIN FETCH f.uploader u LEFT JOIN FETCH u.user " +
           "WHERE f.club.id = :clubId ORDER BY f.createdAt DESC",
           countQuery = "SELECT COUNT(f) FROM DriveFile f WHERE f.club.id = :clubId")
    Page<DriveFile> findByClubIdWithAssociations(@Param("clubId") Long clubId, Pageable pageable);

    /** 팀 + 태그 교차 필터 (M:N teams) — FETCH JOIN으로 N+1 방지 */
    @Query(value = "SELECT DISTINCT f FROM DriveFile f " +
           "LEFT JOIN FETCH f.teams t2 " +
           "LEFT JOIN FETCH f.semiCategories " +
           "LEFT JOIN FETCH f.folders " +
           "LEFT JOIN FETCH f.uploader u LEFT JOIN FETCH u.user " +
           "LEFT JOIN f.teams t " +
           "LEFT JOIN f.semiCategories sc " +
           "WHERE f.club.id = :clubId " +
           "AND (:teamId IS NULL OR t.id = :teamId) " +
           "AND (:tagId IS NULL OR sc.id = :tagId) " +
           "ORDER BY f.createdAt DESC",
           countQuery = "SELECT COUNT(DISTINCT f) FROM DriveFile f " +
           "LEFT JOIN f.teams t LEFT JOIN f.semiCategories sc " +
           "WHERE f.club.id = :clubId " +
           "AND (:teamId IS NULL OR t.id = :teamId) " +
           "AND (:tagId IS NULL OR sc.id = :tagId)")
    Page<DriveFile> findWithFilters(@Param("clubId") Long clubId,
                                    @Param("teamId") Long teamId,
                                    @Param("tagId") Long tagId,
                                    Pageable pageable);

    /** 다중 팀 + 다중 태그 교차 필터 */
    @Query(value = "SELECT DISTINCT f FROM DriveFile f " +
           "LEFT JOIN FETCH f.teams LEFT JOIN FETCH f.semiCategories LEFT JOIN FETCH f.folders " +
           "LEFT JOIN FETCH f.uploader u LEFT JOIN FETCH u.user " +
           "JOIN f.teams t JOIN f.semiCategories sc " +
           "WHERE f.club.id = :clubId AND t.id IN :teamIds AND sc.id IN :tagIds " +
           "ORDER BY f.createdAt DESC",
           countQuery = "SELECT COUNT(DISTINCT f) FROM DriveFile f JOIN f.teams t JOIN f.semiCategories sc WHERE f.club.id = :clubId AND t.id IN :teamIds AND sc.id IN :tagIds")
    Page<DriveFile> findByTeamIdsAndTagIds(@Param("clubId") Long clubId,
                                           @Param("teamIds") List<Long> teamIds,
                                           @Param("tagIds") List<Long> tagIds,
                                           Pageable pageable);

    /** 다중 팀 필터만 */
    @Query(value = "SELECT DISTINCT f FROM DriveFile f " +
           "LEFT JOIN FETCH f.teams LEFT JOIN FETCH f.semiCategories LEFT JOIN FETCH f.folders " +
           "LEFT JOIN FETCH f.uploader u LEFT JOIN FETCH u.user " +
           "JOIN f.teams t " +
           "WHERE f.club.id = :clubId AND t.id IN :teamIds " +
           "ORDER BY f.createdAt DESC",
           countQuery = "SELECT COUNT(DISTINCT f) FROM DriveFile f JOIN f.teams t WHERE f.club.id = :clubId AND t.id IN :teamIds")
    Page<DriveFile> findByTeamIds(@Param("clubId") Long clubId,
                                   @Param("teamIds") List<Long> teamIds,
                                   Pageable pageable);

    /** 다중 태그 필터만 */
    @Query(value = "SELECT DISTINCT f FROM DriveFile f " +
           "LEFT JOIN FETCH f.teams LEFT JOIN FETCH f.semiCategories LEFT JOIN FETCH f.folders " +
           "LEFT JOIN FETCH f.uploader u LEFT JOIN FETCH u.user " +
           "JOIN f.semiCategories sc " +
           "WHERE f.club.id = :clubId AND sc.id IN :tagIds " +
           "ORDER BY f.createdAt DESC",
           countQuery = "SELECT COUNT(DISTINCT f) FROM DriveFile f JOIN f.semiCategories sc WHERE f.club.id = :clubId AND sc.id IN :tagIds")
    Page<DriveFile> findByTagIds(@Param("clubId") Long clubId,
                                  @Param("tagIds") List<Long> tagIds,
                                  Pageable pageable);

    /** 무소속 파일 (teams 비어있음) + 태그 교차 필터 — FETCH JOIN으로 N+1 방지 */
    @Query(value = "SELECT DISTINCT f FROM DriveFile f " +
           "LEFT JOIN FETCH f.semiCategories " +
           "LEFT JOIN FETCH f.folders " +
           "LEFT JOIN FETCH f.uploader u LEFT JOIN FETCH u.user " +
           "LEFT JOIN f.semiCategories sc " +
           "WHERE f.club.id = :clubId " +
           "AND f.teams IS EMPTY " +
           "AND (:tagId IS NULL OR sc.id = :tagId) " +
           "ORDER BY f.createdAt DESC",
           countQuery = "SELECT COUNT(DISTINCT f) FROM DriveFile f " +
           "LEFT JOIN f.semiCategories sc " +
           "WHERE f.club.id = :clubId AND f.teams IS EMPTY " +
           "AND (:tagId IS NULL OR sc.id = :tagId)")
    Page<DriveFile> findUnassignedWithTag(@Param("clubId") Long clubId,
                                          @Param("tagId") Long tagId,
                                          Pageable pageable);

    /** 동아리 전체 파일 수 */
    long countByClubId(Long clubId);

    /** 무소속 파일 수 (teams 비어있음) */
    @Query("SELECT COUNT(f) FROM DriveFile f WHERE f.club.id = :clubId AND f.teams IS EMPTY")
    long countByClubIdAndTeamsEmpty(@Param("clubId") Long clubId);

    /** 팀별 파일 수 (M:N) */
    @Query("SELECT t.id, COUNT(DISTINCT f) FROM DriveFile f JOIN f.teams t WHERE f.club.id = :clubId GROUP BY t.id")
    List<Object[]> countByTeamGrouped(@Param("clubId") Long clubId);

    /** 내 파일 (업로더 기준, 최신순) — FETCH JOIN으로 N+1 방지 */
    @Query(value = "SELECT DISTINCT f FROM DriveFile f " +
           "LEFT JOIN FETCH f.teams " +
           "LEFT JOIN FETCH f.semiCategories " +
           "LEFT JOIN FETCH f.folders " +
           "LEFT JOIN FETCH f.uploader u LEFT JOIN FETCH u.user " +
           "WHERE f.club.id = :clubId AND f.uploader.id = :uploaderId " +
           "ORDER BY f.createdAt DESC",
           countQuery = "SELECT COUNT(f) FROM DriveFile f WHERE f.club.id = :clubId AND f.uploader.id = :uploaderId")
    Page<DriveFile> findByClubIdAndUploaderIdWithAssociations(@Param("clubId") Long clubId,
                                                              @Param("uploaderId") Long uploaderId,
                                                              Pageable pageable);

    /** 내 파일 + 태그 필터 — FETCH JOIN으로 N+1 방지 */
    @Query(value = "SELECT DISTINCT f FROM DriveFile f " +
           "LEFT JOIN FETCH f.teams " +
           "LEFT JOIN FETCH f.semiCategories " +
           "LEFT JOIN FETCH f.folders " +
           "LEFT JOIN FETCH f.uploader u LEFT JOIN FETCH u.user " +
           "JOIN f.semiCategories sc " +
           "WHERE f.club.id = :clubId AND f.uploader.id = :uploaderId " +
           "AND sc.id = :tagId ORDER BY f.createdAt DESC",
           countQuery = "SELECT COUNT(DISTINCT f) FROM DriveFile f JOIN f.semiCategories sc " +
           "WHERE f.club.id = :clubId AND f.uploader.id = :uploaderId AND sc.id = :tagId")
    Page<DriveFile> findByClubIdAndUploaderIdAndTag(@Param("clubId") Long clubId,
                                                     @Param("uploaderId") Long uploaderId,
                                                     @Param("tagId") Long tagId,
                                                     Pageable pageable);

    /** 내 파일 수 (동아리 범위) */
    long countByClubIdAndUploaderId(Long clubId, Long uploaderId);

    /** 아카이브: 업로더의 기수(membership_generations)로 조회 — FETCH JOIN으로 N+1 방지 */
    @Query(value = "SELECT DISTINCT f FROM DriveFile f " +
           "LEFT JOIN FETCH f.teams " +
           "LEFT JOIN FETCH f.semiCategories " +
           "LEFT JOIN FETCH f.folders " +
           "LEFT JOIN FETCH f.uploader u LEFT JOIN FETCH u.user " +
           "JOIN u.generations g " +
           "WHERE f.club.id = :clubId " +
           "AND g.id = :generationId " +
           "ORDER BY f.createdAt DESC",
           countQuery = "SELECT COUNT(DISTINCT f) FROM DriveFile f " +
           "JOIN f.uploader m JOIN m.generations g " +
           "WHERE f.club.id = :clubId AND g.id = :generationId")
    Page<DriveFile> findByUploaderGenerationId(
            @Param("clubId") Long clubId,
            @Param("generationId") Long generationId,
            Pageable pageable);

    /** 아카이브: 업로더의 기수로 파일 수 카운트 */
    @Query("SELECT COUNT(DISTINCT f) FROM DriveFile f " +
           "JOIN f.uploader m " +
           "JOIN m.generations g " +
           "WHERE f.club.id = :clubId AND g.id = :generationId")
    long countByUploaderGenerationId(@Param("clubId") Long clubId, @Param("generationId") Long generationId);

    /** 대시보드: 동아리 내 기간 파일 수 */
    @Query("SELECT COUNT(f) FROM DriveFile f WHERE f.club.id = :clubId AND f.createdAt >= :since")
    long countByClubIdAndCreatedAtAfter(@Param("clubId") Long clubId, @Param("since") LocalDateTime since);

    /** 폴더별 파일 수 (M:N) */
    @Query("SELECT fo.id, COUNT(DISTINCT f) FROM DriveFile f JOIN f.folders fo WHERE f.club.id = :clubId GROUP BY fo.id")
    List<Object[]> countByFolderGrouped(@Param("clubId") Long clubId);

    /** 폴더 필터 (M:N folders) — FETCH JOIN으로 N+1 방지 */
    @Query(value = "SELECT DISTINCT f FROM DriveFile f " +
           "LEFT JOIN FETCH f.teams " +
           "LEFT JOIN FETCH f.semiCategories " +
           "LEFT JOIN FETCH f.folders " +
           "LEFT JOIN FETCH f.uploader u LEFT JOIN FETCH u.user " +
           "JOIN f.folders fo " +
           "WHERE f.club.id = :clubId " +
           "AND fo.id = :folderId " +
           "ORDER BY f.createdAt DESC",
           countQuery = "SELECT COUNT(DISTINCT f) FROM DriveFile f " +
           "JOIN f.folders fo " +
           "WHERE f.club.id = :clubId AND fo.id = :folderId")
    Page<DriveFile> findByClubIdAndFolderId(@Param("clubId") Long clubId,
                                             @Param("folderId") Long folderId,
                                             Pageable pageable);
}
