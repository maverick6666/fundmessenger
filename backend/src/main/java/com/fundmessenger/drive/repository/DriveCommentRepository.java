package com.fundmessenger.drive.repository;

import com.fundmessenger.drive.entity.DriveComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DriveCommentRepository extends JpaRepository<DriveComment, Long> {

    /** 파일의 전체 코멘트 (최신순) */
    Page<DriveComment> findByFileIdOrderByCreatedAtDesc(Long fileId, Pageable pageable);

    /** 파일의 특정 페이지 코멘트 (최신순) */
    Page<DriveComment> findByFileIdAndPageNumberOrderByCreatedAtDesc(Long fileId, Integer pageNumber, Pageable pageable);

    /** 코멘트가 있는 페이지 번호 목록 (distinct, 오름차순) */
    @Query("SELECT DISTINCT c.pageNumber FROM DriveComment c WHERE c.file.id = :fileId AND c.pageNumber IS NOT NULL ORDER BY c.pageNumber ASC")
    List<Integer> findDistinctPageNumbersByFileId(@Param("fileId") Long fileId);

    /** 파일의 전체 코멘트 수 */
    long countByFileId(Long fileId);

    /** 파일의 특정 페이지 코멘트 수 */
    long countByFileIdAndPageNumber(Long fileId, Integer pageNumber);

    /** 파일 삭제 시 코멘트 일괄 삭제 */
    void deleteByFileId(Long fileId);

    /** 여러 파일의 코멘트 수 일괄 조회 */
    @Query("SELECT c.file.id, COUNT(c) FROM DriveComment c WHERE c.file.id IN :fileIds GROUP BY c.file.id")
    List<Object[]> countByFileIdIn(@Param("fileIds") List<Long> fileIds);
}
