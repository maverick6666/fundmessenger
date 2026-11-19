package com.fundmessenger.chat.repository;

import com.fundmessenger.chat.entity.Poll;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PollRepository extends JpaRepository<Poll, Long> {
    List<Poll> findByChatRoomIdOrderByCreatedAtDesc(Long chatRoomId);
    List<Poll> findByClubIdAndClosedOrderByCreatedAtDesc(Long clubId, boolean closed);
    List<Poll> findByClubIdOrderByCreatedAtDesc(Long clubId);
}
