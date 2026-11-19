package com.fundmessenger.sticker.service;

import com.fundmessenger.club.entity.Club;
import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.club.repository.ClubRepository;
import com.fundmessenger.club.service.ClubAuthorizationService;
import com.fundmessenger.file.service.FileStorageService;
import com.fundmessenger.user.entity.User;
import com.fundmessenger.user.repository.UserRepository;
import com.fundmessenger.sticker.dto.*;
import com.fundmessenger.sticker.entity.*;
import com.fundmessenger.sticker.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StickerService {

    private final StickerPackRepository packRepository;
    private final StickerRepository stickerRepository;
    private final ClubStickerPurchaseRepository purchaseRepository;
    private final ClubRepository clubRepository;
    private final ClubAuthorizationService authService;
    private final FileStorageService fileStorageService;
    private final UserRepository userRepository;

    // ========== 슈퍼어드민 (EMJ-2) ==========

    @Transactional
    public StickerPackResponse createPack(CreateStickerPackRequest request, Long userId) {
        validateSuperAdmin(userId);
        StickerPack pack = packRepository.save(StickerPack.builder()
                .name(request.getName().trim())
                .price(request.getPrice())
                .build());
        log.info("스티커 팩 생성: id={}, name={}", pack.getId(), pack.getName());
        return StickerPackResponse.from(pack, false);
    }

    @Transactional
    public StickerResponse addSticker(Long packId, MultipartFile file, Long userId) {
        validateSuperAdmin(userId);
        StickerPack pack = packRepository.findById(packId)
                .orElseThrow(() -> new IllegalArgumentException("팩을 찾을 수 없습니다: " + packId));

        // WebP 검증
        String contentType = file.getContentType();
        if (contentType == null || !contentType.contains("webp")) {
            throw new IllegalArgumentException("WebP 형식만 업로드 가능합니다");
        }
        if (file.getSize() > 300 * 1024) {
            throw new IllegalArgumentException("300KB 이하 파일만 업로드 가능합니다");
        }

        String storedUrl = fileStorageService.storeImage(file);

        int nextOrder = stickerRepository.countByPackId(packId);
        Sticker sticker = stickerRepository.save(Sticker.builder()
                .pack(pack)
                .imageUrl(storedUrl)
                .displayOrder(nextOrder)
                .build());

        // 팩 스티커 수 갱신
        pack.setStickerCount(stickerRepository.countByPackId(packId));

        // 썸네일이 없으면 첫 스티커를 썸네일로
        if (pack.getThumbnailUrl() == null) {
            pack.setThumbnailUrl(storedUrl);
        }

        log.info("스티커 추가: packId={}, stickerId={}", packId, sticker.getId());
        return StickerResponse.from(sticker);
    }

    @Transactional
    public void deleteSticker(Long packId, Long stickerId, Long userId) {
        validateSuperAdmin(userId);
        Sticker sticker = stickerRepository.findById(stickerId)
                .orElseThrow(() -> new IllegalArgumentException("스티커를 찾을 수 없습니다"));
        if (!sticker.getPack().getId().equals(packId)) {
            throw new IllegalArgumentException("해당 팩의 스티커가 아닙니다");
        }
        stickerRepository.delete(sticker);

        StickerPack pack = sticker.getPack();
        pack.setStickerCount(stickerRepository.countByPackId(packId));
    }

    @Transactional
    public void togglePackActive(Long packId, Long userId) {
        validateSuperAdmin(userId);
        StickerPack pack = packRepository.findById(packId)
                .orElseThrow(() -> new IllegalArgumentException("팩을 찾을 수 없습니다"));
        pack.setActive(!pack.isActive());
        log.info("스티커 팩 활성화 토글: id={}, active={}", packId, pack.isActive());
    }

    @Transactional
    public StickerPackResponse updatePack(Long packId, CreateStickerPackRequest request, Long userId) {
        validateSuperAdmin(userId);
        StickerPack pack = packRepository.findById(packId)
                .orElseThrow(() -> new IllegalArgumentException("팩을 찾을 수 없습니다"));
        pack.setName(request.getName().trim());
        pack.setPrice(request.getPrice());
        return StickerPackResponse.from(pack, false);
    }

    @Transactional(readOnly = true)
    public List<StickerPackResponse> getAllPacks() {
        return packRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(p -> StickerPackResponse.from(p, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StickerResponse> getPackStickers(Long packId) {
        return stickerRepository.findByPackIdOrderByDisplayOrderAsc(packId).stream()
                .map(StickerResponse::from)
                .toList();
    }

    // ========== 스토어 + 구매 (EMJ-3) ==========

    @Transactional(readOnly = true)
    public List<StickerPackResponse> getStoreList(Long clubId, Long userId) {
        authService.validateClubMember(userId, clubId);

        Set<Long> purchasedPackIds = purchaseRepository.findByClubId(clubId).stream()
                .map(p -> p.getPack().getId())
                .collect(Collectors.toSet());

        return packRepository.findByActiveTrueOrderByCreatedAtDesc().stream()
                .map(pack -> StickerPackResponse.from(pack, purchasedPackIds.contains(pack.getId())))
                .toList();
    }

    @Transactional
    public void purchasePack(Long clubId, Long packId, Long userId) {
        ClubMembership membership = authService.validateClubAdmin(userId, clubId);

        StickerPack pack = packRepository.findById(packId)
                .orElseThrow(() -> new IllegalArgumentException("팩을 찾을 수 없습니다"));

        if (!pack.isActive()) {
            throw new IllegalArgumentException("비활성화된 팩입니다");
        }

        if (purchaseRepository.existsByClubIdAndPackId(clubId, packId)) {
            throw new IllegalArgumentException("이미 구매한 팩입니다");
        }

        // 동아리 전체 크레딧에서 차감 (첫번째 팀 — 또는 동아리 레벨)
        // origin 설계: 임원급 권한으로 구매, 크레딧은 동아리 수준
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new IllegalArgumentException("동아리를 찾을 수 없습니다"));

        // 구매 기록
        purchaseRepository.save(ClubStickerPurchase.builder()
                .club(club)
                .pack(pack)
                .purchasedBy(membership)
                .build());

        log.info("스티커 팩 구매: clubId={}, packId={}", clubId, packId);
    }

    /** 구매한 팩의 전체 스티커 목록 */
    @Transactional(readOnly = true)
    public List<StickerPackResponse> getPurchasedPacks(Long clubId, Long userId) {
        authService.validateClubMember(userId, clubId);

        return purchaseRepository.findByClubId(clubId).stream()
                .map(p -> StickerPackResponse.from(p.getPack(), true))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StickerResponse> getPurchasedPackStickers(Long clubId, Long packId, Long userId) {
        authService.validateClubMember(userId, clubId);

        if (!purchaseRepository.existsByClubIdAndPackId(clubId, packId)) {
            throw new IllegalArgumentException("구매하지 않은 팩입니다");
        }

        return stickerRepository.findByPackIdOrderByDisplayOrderAsc(packId).stream()
                .map(StickerResponse::from)
                .toList();
    }

    // ========== Private ==========

    private void validateSuperAdmin(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다"));
        if (!user.isSuperAdmin()) {
            throw new SecurityException("슈퍼어드민 권한이 필요합니다");
        }
    }
}
