package com.fundmessenger.club.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class GenerationTransitionResponse {
    private String newGenerationName;
    private int continuedMembers;
    private int alumniMembers;
    private int inactiveMembers;
    private int retainedTeams;
    private int dissolvedTeams;
    private String nextPresidentName;
}
