package com.fundmessenger.chat.controller;

import com.fundmessenger.chat.dto.CreatePollRequest;
import com.fundmessenger.chat.dto.PollResponse;
import com.fundmessenger.chat.dto.VoteRequest;
import com.fundmessenger.chat.service.PollService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clubs/{clubId}/polls")
@RequiredArgsConstructor
public class PollController {

    private final PollService pollService;

    @PostMapping
    public ResponseEntity<PollResponse> createPoll(
            @PathVariable Long clubId,
            @Valid @RequestBody CreatePollRequest request,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(pollService.createPoll(clubId, request, userId));
    }

    @GetMapping("/{pollId}")
    public ResponseEntity<PollResponse> getPoll(
            @PathVariable Long clubId,
            @PathVariable Long pollId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(pollService.getPoll(clubId, pollId, userId));
    }

    @PostMapping("/{pollId}/vote")
    public ResponseEntity<PollResponse> vote(
            @PathVariable Long clubId,
            @PathVariable Long pollId,
            @Valid @RequestBody VoteRequest request,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(pollService.vote(clubId, pollId, request, userId));
    }

    @PostMapping("/{pollId}/close")
    public ResponseEntity<PollResponse> closePoll(
            @PathVariable Long clubId,
            @PathVariable Long pollId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(pollService.closePoll(clubId, pollId, userId));
    }

    @GetMapping
    public ResponseEntity<List<PollResponse>> getPolls(
            @PathVariable Long clubId,
            @RequestParam(required = false) String status,
            Authentication auth) {
        return ResponseEntity.ok(pollService.getPolls(clubId, status));
    }
}
