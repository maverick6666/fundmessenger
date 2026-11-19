package com.fundmessenger.drive.entity;

import com.fundmessenger.club.entity.ClubMembership;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "drive_comments", indexes = {
    @Index(name = "idx_drive_comment_file", columnList = "file_id, created_at DESC")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class DriveComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    private DriveFile file;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private ClubMembership author;

    @Column(nullable = false, length = 2000)
    private String content;

    /** null이면 전체 코멘트, 숫자면 해당 페이지 코멘트 */
    private Integer pageNumber;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
