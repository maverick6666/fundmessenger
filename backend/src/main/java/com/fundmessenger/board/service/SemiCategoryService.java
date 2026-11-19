package com.fundmessenger.board.service;

import com.fundmessenger.board.dto.CreateSemiCategoryRequest;
import com.fundmessenger.board.dto.SemiCategoryResponse;
import com.fundmessenger.board.entity.SemiCategory;
import com.fundmessenger.board.repository.PostRepository;
import com.fundmessenger.board.repository.SemiCategoryRepository;
import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.club.service.ClubAuthorizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SemiCategoryService {

    private final SemiCategoryRepository categoryRepository;
    private final PostRepository postRepository;
    private final ClubAuthorizationService authService;

    /** 카테고리 목록 (멤버 누구나) — enabled=false인 태그 제외 */
    @Transactional(readOnly = true)
    public List<SemiCategoryResponse> getCategories(Long clubId, Long userId) {
        authService.validateClubMember(userId, clubId);

        return categoryRepository.findByClubIdOrderByDisplayOrderAscCreatedAtAsc(clubId).stream()
                .filter(sc -> !sc.isGeneration())
                .filter(SemiCategory::isEnabled)
                .map(SemiCategoryResponse::from)
                .toList();
    }

    /** 카테고리 생성 (임원급) */
    @Transactional
    public SemiCategoryResponse createCategory(Long clubId, CreateSemiCategoryRequest request, Long userId) {
        ClubMembership membership = authService.validateClubAdmin(userId, clubId);

        String name = request.getName().trim();

        // 이름 중복 체크
        categoryRepository.findByClubIdAndName(clubId, name)
                .ifPresent(c -> {
                    throw new IllegalArgumentException("이미 같은 이름의 카테고리가 존재합니다: " + name);
                });

        SemiCategory category = categoryRepository.save(SemiCategory.builder()
                .club(membership.getClub())
                .name(name)
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
                .build());

        log.info("세미카테고리 생성: clubId={}, categoryId={}, name={}", clubId, category.getId(), name);
        return SemiCategoryResponse.from(category);
    }

    /** 카테고리 수정 (임원급) */
    @Transactional
    public SemiCategoryResponse updateCategory(Long clubId, Long categoryId, CreateSemiCategoryRequest request, Long userId) {
        authService.validateClubAdmin(userId, clubId);

        SemiCategory category = getCategoryOrThrow(categoryId, clubId);

        String newName = request.getName().trim();
        if (!category.getName().equals(newName)) {
            categoryRepository.findByClubIdAndName(clubId, newName)
                    .ifPresent(c -> {
                        throw new IllegalArgumentException("이미 같은 이름의 카테고리가 존재합니다: " + newName);
                    });
            category.setName(newName);
        }

        if (request.getDisplayOrder() != null) {
            category.setDisplayOrder(request.getDisplayOrder());
        }

        log.info("세미카테고리 수정: clubId={}, categoryId={}, name={}", clubId, categoryId, category.getName());
        return SemiCategoryResponse.from(category);
    }

    /** 카테고리 일괄 순서 변경 (임원급) */
    @Transactional
    public void reorderCategories(Long clubId, List<Long> categoryIds, Long userId) {
        authService.validateClubAdmin(userId, clubId);

        List<SemiCategory> categories = categoryRepository.findByClubIdOrderByDisplayOrderAscCreatedAtAsc(clubId);
        var categoryMap = categories.stream().collect(Collectors.toMap(SemiCategory::getId, c -> c));

        for (int i = 0; i < categoryIds.size(); i++) {
            SemiCategory category = categoryMap.get(categoryIds.get(i));
            if (category != null) {
                category.setDisplayOrder(i);
            }
        }
        log.info("세미카테고리 순서 변경: clubId={}, count={}", clubId, categoryIds.size());
    }

    /** 카테고리 삭제 (임원급) — 연관된 Post.semiCategory → null 처리 */
    @Transactional
    public void deleteCategory(Long clubId, Long categoryId, Long userId) {
        authService.validateClubAdmin(userId, clubId);

        SemiCategory category = getCategoryOrThrow(categoryId, clubId);

        // M:N 조인테이블에서 해당 태그 제거
        postRepository.removeTagFromAllPosts(categoryId);

        categoryRepository.delete(category);
        log.info("세미카테고리 삭제: clubId={}, categoryId={}, name={}", clubId, categoryId, category.getName());
    }

    /** 시스템 태그 목록 (임원급) */
    @Transactional(readOnly = true)
    public List<SemiCategoryResponse> getSystemCategories(Long clubId, Long userId) {
        authService.validateClubAdmin(userId, clubId);

        return categoryRepository.findByClubIdOrderByDisplayOrderAscCreatedAtAsc(clubId).stream()
                .filter(SemiCategory::isSystemTag)
                .map(SemiCategoryResponse::from)
                .toList();
    }

    /** 시스템 태그 활성화/비활성화 토글 (임원급) */
    @Transactional
    public SemiCategoryResponse toggleCategoryEnabled(Long clubId, Long categoryId, Long userId) {
        authService.validateClubAdmin(userId, clubId);

        SemiCategory category = getCategoryOrThrow(categoryId, clubId);
        category.setEnabled(!category.isEnabled());

        log.info("세미카테고리 활성화 토글: clubId={}, categoryId={}, name={}, enabled={}",
                clubId, categoryId, category.getName(), category.isEnabled());
        return SemiCategoryResponse.from(category);
    }

    // ========== Private ==========

    private SemiCategory getCategoryOrThrow(Long categoryId, Long clubId) {
        SemiCategory category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("카테고리를 찾을 수 없습니다: " + categoryId));
        if (!category.getClub().getId().equals(clubId)) {
            throw new SecurityException("해당 동아리의 카테고리가 아닙니다");
        }
        return category;
    }
}
