package com.fundmessenger.jongtobang.entity;

import com.fundmessenger.club.entity.ClubMembership;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "news_likes", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"coupling_id", "member_id"})
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class NewsLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupling_id", nullable = false)
    private StockNewsCoupling coupling;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private ClubMembership member;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
