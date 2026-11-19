package com.fundmessenger.club.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class AddTeamMembersRequest {
    @NotEmpty(message = "추가할 멤버를 선택해주세요")
    private List<Long> clubMembershipIds;
}
