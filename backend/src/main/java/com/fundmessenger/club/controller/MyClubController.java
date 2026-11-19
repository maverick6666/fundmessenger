package com.fundmessenger.club.controller;

import com.fundmessenger.club.dto.MyClubInfoResponse;
import com.fundmessenger.club.service.ClubMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/my")
@RequiredArgsConstructor
public class MyClubController {

    private final ClubMemberService clubMemberService;

    /** 내 동아리 정보 */
    @GetMapping("/club")
    public ResponseEntity<MyClubInfoResponse> getMyClub(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(clubMemberService.getMyClubInfo(userId));
    }
}
