package com.fundmessenger.support.repository;

import com.fundmessenger.support.entity.SupportTicket;
import com.fundmessenger.support.entity.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

    List<SupportTicket> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<SupportTicket> findAllByOrderByCreatedAtDesc();

    List<SupportTicket> findByStatusOrderByCreatedAtDesc(TicketStatus status);

    long countByStatus(TicketStatus status);
}
