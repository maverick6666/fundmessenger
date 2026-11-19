package com.fundmessenger.club.dto;

import com.fundmessenger.club.entity.School;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SchoolResponse {
    private Long id;
    private String name;

    public static SchoolResponse from(School school) {
        return new SchoolResponse(school.getId(), school.getName());
    }
}
