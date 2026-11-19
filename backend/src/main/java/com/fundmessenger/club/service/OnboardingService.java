package com.fundmessenger.club.service;

import com.fundmessenger.club.dto.ApplyRequest;
import com.fundmessenger.club.dto.ClubResponse;
import com.fundmessenger.club.dto.SchoolResponse;
import com.fundmessenger.club.entity.Club;
import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.club.entity.MemberStatus;
import com.fundmessenger.club.repository.ClubMembershipRepository;
import com.fundmessenger.club.repository.ClubRepository;
import com.fundmessenger.club.repository.SchoolRepository;
import com.fundmessenger.user.entity.User;
import com.fundmessenger.user.repository.UserRepository;
import com.fundmessenger.config.exception.BadRequestException;
import com.fundmessenger.config.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OnboardingService {

    private final SchoolRepository schoolRepository;
    private final ClubRepository clubRepository;
    private final ClubMembershipRepository membershipRepository;
    private final UserRepository userRepository;

    public List<SchoolResponse> getSchools() {
        return schoolRepository.findAll().stream()
                .map(SchoolResponse::from)
                .toList();
    }

    public List<ClubResponse> getClubsBySchool(Long schoolId) {
        return clubRepository.findBySchoolId(schoolId).stream()
                .map(ClubResponse::from)
                .toList();
    }

    @Transactional
    public void applyToClub(Long userId, ApplyRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다"));

        Club club = clubRepository.findById(request.getClubId())
                .orElseThrow(() -> new NotFoundException("동아리를 찾을 수 없습니다"));

        // 중복 신청 체크 (같은 유저가 같은 동아리에 중복 신청)
        Optional<ClubMembership> existing = membershipRepository.findByUserIdAndClubId(userId, club.getId());
        if (existing.isPresent()) {
            ClubMembership m = existing.get();
            if (m.getStatus() == MemberStatus.REJECTED) {
                // 거절된 사용자 재신청: 기존 레코드를 PENDING으로 되돌림
                m.setStatus(MemberStatus.PENDING);
                m.setName(request.getName());
                m.setStudentId(request.getStudentId());
                m.setPhone(request.getPhone());
                log.info("재신청: user={}, club={}, name={}", user.getEmail(), club.getName(), request.getName());
                return;
            }
            throw new BadRequestException("이미 해당 동아리에 신청한 기록이 있습니다 (상태: " + m.getStatus() + ")");
        }

        // 1인 1계정 체크: 같은 학교 내 동일 학번으로 가입된 다른 계정이 있는지
        membershipRepository.findByStudentIdInSameSchoolByOtherUser(
                request.getStudentId(), club.getSchool().getId(), userId
        ).ifPresent(m -> {
            throw new BadRequestException("이미 해당 학번으로 가입된 계정이 있습니다. 기존 계정으로 로그인해주세요.");
        });

        ClubMembership membership = ClubMembership.builder()
                .user(user)
                .club(club)
                .name(request.getName())
                .studentId(request.getStudentId())
                .phone(request.getPhone())
                .status(MemberStatus.PENDING)
                .build();

        membershipRepository.save(membership);
        log.info("가입 신청: user={}, club={}, name={}", user.getEmail(), club.getName(), request.getName());
    }
}
