package com.fundmessenger.support.service;

import com.fundmessenger.support.dto.*;
import com.fundmessenger.support.entity.*;
import com.fundmessenger.support.repository.SupportTicketRepository;
import com.fundmessenger.support.repository.TicketReplyRepository;
import com.fundmessenger.user.entity.User;
import com.fundmessenger.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupportService {

    private final SupportTicketRepository ticketRepository;
    private final TicketReplyRepository replyRepository;
    private final UserRepository userRepository;

    // ===== 사용자용 =====

    @Transactional
    public TicketResponse createTicket(Long userId, CreateTicketRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다"));

        TicketCategory category = TicketCategory.GENERAL;
        if (request.getCategory() != null && !request.getCategory().isBlank()) {
            try {
                category = TicketCategory.valueOf(request.getCategory());
            } catch (IllegalArgumentException ignored) {
                // 잘못된 카테고리면 GENERAL로
            }
        }

        SupportTicket ticket = ticketRepository.save(SupportTicket.builder()
                .user(user)
                .title(request.getTitle().trim())
                .content(request.getContent().trim())
                .category(category)
                .build());

        log.info("문의 생성: id={}, user={}, category={}", ticket.getId(), user.getEmail(), category);
        return TicketResponse.from(ticket);
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> getMyTickets(Long userId) {
        return ticketRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(TicketResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TicketDetailResponse getMyTicketDetail(Long ticketId, Long userId) {
        SupportTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("문의를 찾을 수 없습니다: " + ticketId));
        if (!ticket.getUser().getId().equals(userId)) {
            throw new SecurityException("본인의 문의만 조회할 수 있습니다");
        }

        List<ReplyResponse> replies = replyRepository.findByTicketIdOrderByCreatedAtAsc(ticketId).stream()
                .map(ReplyResponse::from)
                .toList();

        return TicketDetailResponse.from(ticket, replies);
    }

    @Transactional
    public ReplyResponse createUserReply(Long ticketId, CreateReplyRequest request, Long userId) {
        SupportTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("문의를 찾을 수 없습니다: " + ticketId));
        if (!ticket.getUser().getId().equals(userId)) {
            throw new SecurityException("본인의 문의에만 답변할 수 있습니다");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다"));

        TicketReply reply = replyRepository.save(TicketReply.builder()
                .ticket(ticket)
                .author(user)
                .content(request.getContent().trim())
                .adminReply(false)
                .build());

        // 사용자가 추가 답변하면 다시 OPEN으로
        if (ticket.getStatus() == TicketStatus.ANSWERED) {
            ticket.setStatus(TicketStatus.OPEN);
        }

        log.info("사용자 추가 답변: ticketId={}, user={}", ticketId, user.getEmail());
        return ReplyResponse.from(reply);
    }

    // ===== SUPER_ADMIN용 =====

    @Transactional(readOnly = true)
    public List<TicketResponse> getAllTickets(Long adminUserId) {
        validateSuperAdmin(adminUserId);
        return ticketRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(TicketResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> getTicketsByStatus(TicketStatus status, Long adminUserId) {
        validateSuperAdmin(adminUserId);
        return ticketRepository.findByStatusOrderByCreatedAtDesc(status).stream()
                .map(TicketResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TicketDetailResponse getTicketDetail(Long ticketId, Long adminUserId) {
        validateSuperAdmin(adminUserId);

        SupportTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("문의를 찾을 수 없습니다: " + ticketId));

        List<ReplyResponse> replies = replyRepository.findByTicketIdOrderByCreatedAtAsc(ticketId).stream()
                .map(ReplyResponse::from)
                .toList();

        return TicketDetailResponse.from(ticket, replies);
    }

    @Transactional
    public ReplyResponse createAdminReply(Long ticketId, CreateReplyRequest request, Long adminUserId) {
        validateSuperAdmin(adminUserId);

        SupportTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("문의를 찾을 수 없습니다: " + ticketId));

        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다"));

        TicketReply reply = replyRepository.save(TicketReply.builder()
                .ticket(ticket)
                .author(admin)
                .content(request.getContent().trim())
                .adminReply(true)
                .build());

        ticket.setStatus(TicketStatus.ANSWERED);

        log.info("어드민 답변: ticketId={}, admin={}", ticketId, admin.getEmail());
        return ReplyResponse.from(reply);
    }

    @Transactional
    public TicketResponse closeTicket(Long ticketId, Long adminUserId) {
        validateSuperAdmin(adminUserId);

        SupportTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("문의를 찾을 수 없습니다: " + ticketId));

        ticket.setStatus(TicketStatus.CLOSED);
        log.info("티켓 종료: ticketId={}", ticketId);
        return TicketResponse.from(ticket);
    }

    private void validateSuperAdmin(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다"));
        if (!user.isSuperAdmin()) {
            throw new SecurityException("슈퍼어드민 권한이 필요합니다");
        }
    }
}
