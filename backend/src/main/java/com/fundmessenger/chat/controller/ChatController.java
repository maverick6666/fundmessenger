package com.fundmessenger.chat.controller;

import com.fundmessenger.chat.dto.*;
import com.fundmessenger.chat.service.ChatMemberService;
import com.fundmessenger.chat.service.ChatMessageService;
import com.fundmessenger.chat.service.ChatRoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/clubs/{clubId}/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatRoomService roomService;
    private final ChatMessageService messageService;
    private final ChatMemberService memberService;

    @GetMapping("/rooms")
    public ResponseEntity<List<ChatRoomResponse>> getMyRooms(
            @PathVariable Long clubId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(roomService.getMyRooms(clubId, userId));
    }

    @PostMapping("/rooms")
    public ResponseEntity<ChatRoomResponse> createRoom(
            @PathVariable Long clubId,
            @Valid @RequestBody CreateChatRoomRequest request,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(roomService.createRoom(clubId, request, userId));
    }

    @PostMapping("/rooms/{roomId}/join")
    public ResponseEntity<Void> joinLightning(
            @PathVariable Long clubId,
            @PathVariable Long roomId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        memberService.joinLightning(clubId, roomId, userId);
        return ResponseEntity.ok().build();
    }

    /** cursor 기반 페이징: beforeId 이전 메시지 (#9) */
    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<List<ChatMessageResponse>> getMessages(
            @PathVariable Long clubId,
            @PathVariable Long roomId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(defaultValue = "30") int size,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(messageService.getMessages(clubId, roomId, beforeId, size, userId));
    }

    @PostMapping("/rooms/{roomId}/messages")
    public ResponseEntity<ChatMessageResponse> sendMessage(
            @PathVariable Long clubId,
            @PathVariable Long roomId,
            @Valid @RequestBody SendMessageRequest request,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(messageService.sendMessage(clubId, roomId, request, userId));
    }

    @PostMapping("/share")
    public ResponseEntity<Void> shareContent(
            @PathVariable Long clubId,
            @RequestBody ContentShareRequest request,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        messageService.shareContent(clubId, request, userId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/rooms/{roomId}/read")
    public ResponseEntity<Void> markRead(
            @PathVariable Long clubId,
            @PathVariable Long roomId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        memberService.markRead(clubId, roomId, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/rooms/{roomId}/leave")
    public ResponseEntity<Void> leaveRoom(
            @PathVariable Long clubId,
            @PathVariable Long roomId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        memberService.leaveRoom(clubId, roomId, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/rooms/{roomId}/members")
    public ResponseEntity<List<ChatMemberResponse>> getRoomMembers(
            @PathVariable Long clubId,
            @PathVariable Long roomId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(memberService.getRoomMembers(clubId, roomId, userId));
    }

    @PutMapping("/rooms/{roomId}/mute")
    public ResponseEntity<Map<String, Boolean>> toggleMute(
            @PathVariable Long clubId,
            @PathVariable Long roomId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        boolean muted = memberService.toggleMute(clubId, roomId, userId);
        return ResponseEntity.ok(Map.of("muted", muted));
    }

    @GetMapping("/lightning")
    public ResponseEntity<List<ChatRoomResponse>> getLightningRooms(
            @PathVariable Long clubId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(roomService.getLightningRooms(clubId, userId));
    }
}
