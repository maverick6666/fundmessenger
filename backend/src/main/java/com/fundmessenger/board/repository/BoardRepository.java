package com.fundmessenger.board.repository;

import com.fundmessenger.board.entity.Board;
import com.fundmessenger.board.entity.BoardType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BoardRepository extends JpaRepository<Board, Long> {

    /** 동아리 전체 게시판 (displayOrder ASC → createdAt ASC) */
    List<Board> findByClubIdOrderByDisplayOrderAscCreatedAtAsc(Long clubId);

    Optional<Board> findByClubIdAndName(Long clubId, String name);

    long countByClubId(Long clubId);

    List<Board> findByAdminFolderId(Long adminFolderId);

    List<Board> findByClubIdAndBoardType(Long clubId, BoardType boardType);
}
