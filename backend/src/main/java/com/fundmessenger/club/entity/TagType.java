package com.fundmessenger.club.entity;

public enum TagType {
    SYSTEM,       // 회장, 일반부원 (삭제 불가)
    TEAM_LEADER,  // 팀장 (팀 생성/임명 시 자동)
    TEAM_MANAGER, // 팀 매니저 (팀장이 임명, 일정/AI 권한)
    TEAM_MEMBER,  // 팀원 (팀 배정 시 자동)
    CUSTOM        // 부회장, 총무, 서기 등 (자유 CRUD)
}
