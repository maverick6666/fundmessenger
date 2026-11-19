package com.fundmessenger.club.entity;

public enum MemberStatus {
    PENDING,   // 승인 대기
    APPROVED,  // 승인 완료
    REJECTED,  // 거절됨
    ALUMNI,    // 졸업 (서비스 접근 유지, 졸업자 팀 소속)
    INACTIVE   // 탈퇴 (서비스 접근 불가)
}
