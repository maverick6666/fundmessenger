package com.fundmessenger.admin.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 시스템 전역 설정 (슈퍼어드민 관리)
 * 단일 행 패턴 — id=1 고정
 */
@Entity
@Table(name = "global_settings")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class GlobalSettings {

    @Id
    private Long id;  // 항상 1

    // ─── 드라이브 정책 ───
    @Column(nullable = false)
    @Builder.Default
    private String driveAllowedFormats = "pdf";  // 쉼표 구분

    @Column(nullable = false)
    @Builder.Default
    private Integer driveMaxFileSizeMb = 50;

    // ─── AI 정책 ───
    /** AI 글생성 1회당 크레딧 비용 */
    @Column(nullable = false)
    @Builder.Default
    private Integer aiCreditCost = 1;

    /** 동아리당 일일 AI 생성 기본 제한 (0=무제한) */
    @Column(nullable = false)
    @Builder.Default
    private Integer defaultDailyAiLimit = 10;

    // ─── AI 댓글봇 정책 ───
    /** 동아리당 일일 댓글봇 최소 횟수 */
    @Column(nullable = false)
    @Builder.Default
    private Integer dailyCommentBotMin = 5;

    /** 동아리당 일일 댓글봇 최대 횟수 */
    @Column(nullable = false)
    @Builder.Default
    private Integer dailyCommentBotMax = 10;
}
