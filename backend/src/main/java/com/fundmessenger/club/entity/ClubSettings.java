package com.fundmessenger.club.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "club_settings")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ClubSettings {

    @Id
    private Long id;  // Club ID와 동일 (1:1 매핑)

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "id")
    private Club club;

    // ─── 동아리 정보 ───
    @Column(columnDefinition = "TEXT")
    private String clubDescription;

    private String clubLogoUrl;  // /api/files/images/xxx 형태 (파일 업로드)

    // ─── 캐러셀: CarouselMember 엔티티로 이동 (carousel_team_id 폐기) ───

    // ─── AI 댓글봇 설정 ───
    @Column(nullable = false)
    @Builder.Default
    private boolean aiCommentBotEnabled = false;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String aiCommentBotScope = "BOTH";  // JONGTOBANG_ONLY / BOARD_ONLY / BOTH

    @Column(length = 50)
    private String weeklyReviewBotName;  // 주간 리뷰 봇 발행 이름 (null이면 동아리명 사용)

    // ─── AI 글작성 권한 설정 ───
    @Column(length = 20)
    @Builder.Default
    private String aiAccessMode = "ALL";  // EXECUTIVE_ONLY / ALL / NONE (null → ALL)

    // ─── 향후 확장 필드 ───
    // 졸업자 권한: [5] 기수 관리 구현 시 추가
    // 드라이브 정책: GlobalSettings(슈퍼어드민)로 이동됨
}
