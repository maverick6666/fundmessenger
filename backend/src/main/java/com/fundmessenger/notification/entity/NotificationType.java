package com.fundmessenger.notification.entity;

public enum NotificationType {
    TEAM_CHANGE,             // 팀 배정/해제
    ROLE_CHANGE,             // 팀장/매니저 임명/해제
    GENERATION_TRANSITION,   // 기수 전환
    POST_COMMENT,            // 내 글에 댓글
    COMMENT_REPLY,           // 내 댓글에 대댓글
    DRIVE_COMMENT,           // 내 파일에 코멘트
    CONTENT_LIKED,           // 좋아요 (글/뉴스/스레드)
    TEAM_ACTIVITY,           // 팀 새 글/파일/일정
    NEW_NOTICE,              // 공지 등록
    NOTICE_REMINDER,         // 공지 리마인더 (D-7, D-3, D-1 등)
    AI_GENERATION,           // AI 글 생성 완료/실패
    DM_RECEIVED,             // DM 수신
    OPEN_CHAT_CREATED;       // 오픈채팅방 생성

    private static final java.util.Set<NotificationType> REQUIRED = java.util.Set.of(
            TEAM_CHANGE, ROLE_CHANGE, GENERATION_TRANSITION
    );

    public boolean isRequired() {
        return REQUIRED.contains(this);
    }
}
