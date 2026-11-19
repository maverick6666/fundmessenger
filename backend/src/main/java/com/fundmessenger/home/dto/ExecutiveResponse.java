package com.fundmessenger.home.dto;

import com.fundmessenger.board.entity.SemiCategory;
import com.fundmessenger.club.entity.CarouselMember;
import com.fundmessenger.club.entity.ClubMembership;
import lombok.Builder;
import lombok.Getter;

import java.util.Comparator;

@Getter
@Builder
public class ExecutiveResponse {
    private Long id;
    private Long carouselId;
    private String name;
    private String roleName;
    private String firstGeneration;
    private String bio;
    private String phone;
    private String avatarUrl;

    public static ExecutiveResponse fromCarousel(CarouselMember cm) {
        ClubMembership membership = cm.getClubMembership();

        String firstGen = membership.getGenerations().stream()
                .min(Comparator.comparingInt(SemiCategory::getDisplayOrder))
                .map(SemiCategory::getName)
                .orElse(null);

        var user = membership.getUser();
        return ExecutiveResponse.builder()
                .id(membership.getId())
                .carouselId(cm.getId())
                .name(membership.getName())
                .roleName(cm.getRoleName())
                .firstGeneration(firstGen)
                .bio(cm.getBio())
                .phone(user != null ? user.getPhone() : null)
                .avatarUrl(user != null ? user.getAvatarUrl() : null)
                .build();
    }
}
