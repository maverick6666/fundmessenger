package com.fundmessenger.jongtobang.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "raw_news", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"original_link"})
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class RawNews {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** NAVER / MARKETAUX */
    @Column(nullable = false, length = 30)
    private String sourceChannel;

    @Column(nullable = false, length = 500)
    private String originalTitle;

    @Column(columnDefinition = "TEXT")
    private String originalDescription;

    @Column(nullable = false, length = 1000)
    private String originalLink;

    @Column(length = 200)
    private String publisher;

    private LocalDateTime publishedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cluster_id")
    private NewsCluster cluster;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "canonical_news_id")
    private CanonicalNews canonicalNews;

    @Column(nullable = false)
    @Builder.Default
    private boolean processed = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
