package com.fundmessenger.ai.repository;

import com.fundmessenger.ai.entity.AiGenerationJob;
import com.fundmessenger.ai.entity.AiGenerationJob.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AiGenerationJobRepository extends JpaRepository<AiGenerationJob, Long> {

    /** 동아리별 AI 사용 횟수 (COMPLETED 기준) */
    @Query("SELECT j.club.id, COUNT(j) FROM AiGenerationJob j WHERE j.status = 'COMPLETED' GROUP BY j.club.id")
    List<Object[]> countCompletedByClub();

    /** 특정 동아리의 AI 사용 횟수 */
    long countByClubIdAndStatus(Long clubId, JobStatus status);

    List<AiGenerationJob> findByRequestedByIdOrderByCreatedAtDesc(Long membershipId);

    List<AiGenerationJob> findByRequestedByIdAndStatusOrderByCreatedAtDesc(Long membershipId, JobStatus status);

    void deleteByDriveFileId(Long driveFileId);

    /** 해당 사용자의 진행중(QUEUED/PROCESSING) 작업이 있는지 확인 */
    boolean existsByRequestedByIdAndStatusIn(Long membershipId, List<JobStatus> statuses);

    @EntityGraph(attributePaths = {"driveFile", "requestedBy"})
    Optional<AiGenerationJob> findDetailedById(Long jobId);
}
