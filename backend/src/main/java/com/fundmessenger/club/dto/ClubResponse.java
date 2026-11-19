package com.fundmessenger.club.dto;

import com.fundmessenger.club.entity.Club;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ClubResponse {
    private Long id;
    private String name;
    private Long schoolId;

    public static ClubResponse from(Club club) {
        return new ClubResponse(club.getId(), club.getName(), club.getSchool().getId());
    }
}
