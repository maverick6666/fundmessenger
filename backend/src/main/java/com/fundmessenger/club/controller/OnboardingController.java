package com.fundmessenger.club.controller;

import com.fundmessenger.club.dto.ApplyRequest;
import com.fundmessenger.club.dto.ClubResponse;
import com.fundmessenger.club.dto.SchoolResponse;
import com.fundmessenger.club.service.OnboardingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingService onboardingService;

    /** 학교 목록 조회 */
    @GetMapping("/schools")
    public ResponseEntity<List<SchoolResponse>> getSchools() {
        return ResponseEntity.ok(onboardingService.getSchools());
    }

    /** 특정 학교의 동아리 목록 조회 */
    @GetMapping("/schools/{schoolId}/clubs")
    public ResponseEntity<List<ClubResponse>> getClubs(@PathVariable Long schoolId) {
        return ResponseEntity.ok(onboardingService.getClubsBySchool(schoolId));
    }

    /** 동아리 가입 신청 */
    @PostMapping("/apply")
    public ResponseEntity<?> apply(
            Authentication auth,
            @Valid @RequestBody ApplyRequest request
    ) {
        Long userId = (Long) auth.getPrincipal();
        onboardingService.applyToClub(userId, request);
        return ResponseEntity.ok().body(java.util.Map.of("message", "가입 신청이 완료되었습니다"));
    }
}
