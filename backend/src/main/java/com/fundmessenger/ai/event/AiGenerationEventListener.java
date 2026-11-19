package com.fundmessenger.ai.event;

import com.fundmessenger.ai.service.AiGenerationAsyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiGenerationEventListener {

    private final AiGenerationAsyncService asyncService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleGenerationRequested(AiGenerationRequestedEvent event) {
        log.info("AI generation queued after commit: jobId={}", event.jobId());
        asyncService.processGenerationAsync(event.jobId());
    }
}
