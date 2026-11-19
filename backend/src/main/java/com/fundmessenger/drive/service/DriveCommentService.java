package com.fundmessenger.drive.service;

import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.club.service.ClubAuthorizationService;
import com.fundmessenger.config.InputSanitizer;
import com.fundmessenger.drive.dto.DriveCommentResponse;
import com.fundmessenger.drive.entity.DriveComment;
import com.fundmessenger.drive.entity.DriveFile;
import com.fundmessenger.drive.repository.DriveCommentRepository;
import com.fundmessenger.drive.repository.DriveFileRepository;
import com.fundmessenger.notification.entity.NotificationType;
import com.fundmessenger.notification.event.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DriveCommentService {

    private final DriveCommentRepository commentRepository;
    private final DriveFileRepository fileRepository;
    private final ClubAuthorizationService authService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public Page<DriveCommentResponse> getComments(Long clubId, Long fileId, Integer pageNumber, int page, int size, Long userId) {
        authService.validateClubMember(userId, clubId);
        validateFileInClub(fileId, clubId);

        Page<DriveComment> comments;
        if (pageNumber != null) {
            comments = commentRepository.findByFileIdAndPageNumberOrderByCreatedAtDesc(fileId, pageNumber, PageRequest.of(page, size));
        } else {
            comments = commentRepository.findByFileIdOrderByCreatedAtDesc(fileId, PageRequest.of(page, size));
        }
        return comments.map(DriveCommentResponse::from);
    }

    @Transactional(readOnly = true)
    public List<Integer> getCommentedPages(Long clubId, Long fileId, Long userId) {
        authService.validateClubMember(userId, clubId);
        validateFileInClub(fileId, clubId);
        return commentRepository.findDistinctPageNumbersByFileId(fileId);
    }

    @Transactional
    public DriveCommentResponse createComment(Long clubId, Long fileId, String content, Integer pageNumber, Long userId) {
        ClubMembership membership = authService.validateClubMember(userId, clubId);
        DriveFile file = validateFileInClub(fileId, clubId);

        DriveComment comment = DriveComment.builder()
                .file(file)
                .author(membership)
                .content(InputSanitizer.sanitizePlainText(content))
                .pageNumber(pageNumber)
                .build();

        DriveComment saved = commentRepository.save(comment);
        log.info("드라이브 코멘트 작성: fileId={}, commentId={}, page={}", fileId, saved.getId(), pageNumber);

        // 알림: 파일 업로더에게 댓글 알림
        Long uploaderUserId = file.getUploader() != null ? file.getUploader().getUser().getId() : null;
        if (uploaderUserId != null && !uploaderUserId.equals(userId)) {
            eventPublisher.publishEvent(NotificationEvent.of(
                    clubId, uploaderUserId, NotificationType.DRIVE_COMMENT,
                    membership.getName() + "님이 코멘트를 달았습니다",
                    file.getOriginalName() + " — " + (content.length() > 40 ? content.substring(0, 40) + "…" : content),
                    "/club/drive/" + fileId
            ));
        }

        return DriveCommentResponse.from(saved);
    }

    @Transactional
    public void deleteComment(Long clubId, Long fileId, Long commentId, Long userId) {
        ClubMembership membership = authService.validateClubMember(userId, clubId);
        validateFileInClub(fileId, clubId);

        DriveComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("코멘트를 찾을 수 없습니다: " + commentId));

        if (!comment.getFile().getId().equals(fileId)) {
            throw new IllegalArgumentException("해당 파일의 코멘트가 아닙니다");
        }

        boolean isAuthor = comment.getAuthor().getId().equals(membership.getId());
        boolean isAdmin = authService.isExecutive(membership);

        if (!isAuthor && !isAdmin) {
            throw new SecurityException("코멘트를 삭제할 권한이 없습니다");
        }

        commentRepository.delete(comment);
        log.info("드라이브 코멘트 삭제: fileId={}, commentId={}", fileId, commentId);
    }

    private DriveFile validateFileInClub(Long fileId, Long clubId) {
        DriveFile file = fileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("파일을 찾을 수 없습니다: " + fileId));
        if (!file.getClub().getId().equals(clubId)) {
            throw new SecurityException("해당 동아리의 파일이 아닙니다");
        }
        return file;
    }
}
