package com.fundmessenger.file.controller;

import com.fundmessenger.file.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;

    /** 이미지 업로드 (에디터용) */
    @PostMapping("/images")
    public ResponseEntity<Map<String, String>> uploadImage(
            @RequestParam("file") MultipartFile file,
            Authentication auth
    ) {
        // 인증된 사용자만 업로드 가능 (SecurityConfig에서 보장)
        String storedName = fileStorageService.storeImage(file);
        String url = "/api/files/images/" + storedName;
        return ResponseEntity.ok(Map.of("url", url));
    }
}
