package com.fundmessenger.drive.entity;

import com.fundmessenger.club.entity.Club;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "drive_folders",
       uniqueConstraints = @UniqueConstraint(columnNames = {"club_id", "name"}),
       indexes = {
           @Index(name = "idx_drive_folder_club_order", columnList = "club_id, display_order")
       })
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class DriveFolder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    /** 폴더 설명 (선택) */
    @Column(length = 200)
    private String description;

    /** 표시 순서 (0부터, 낮을수록 위) */
    @Column(nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
