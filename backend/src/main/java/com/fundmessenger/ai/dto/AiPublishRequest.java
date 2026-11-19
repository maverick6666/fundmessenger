package com.fundmessenger.ai.dto;

import lombok.Data;

import java.util.List;

@Data
public class AiPublishRequest {
    private List<Long> teamIds;
    private List<Long> boardIds;
}
