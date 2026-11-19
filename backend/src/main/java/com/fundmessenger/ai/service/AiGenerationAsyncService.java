package com.fundmessenger.ai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiGenerationAsyncService {

    private final AiGenerationService generationService;

    @Async
    public void processGenerationAsync(Long jobId) {
        generationService.processGeneration(jobId);
    }
}
