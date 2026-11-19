package com.fundmessenger.config;

import com.fundmessenger.file.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final FileStorageService fileStorageService;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // GCS 모드에서는 파일을 GCS URL에서 직접 서빙하므로 로컬 핸들러 불필요
        if (fileStorageService.isGcsMode()) {
            return;
        }

        // 로컬 모드: 업로드 디렉토리에서 정적 서빙
        String uploadPath = fileStorageService.getUploadDir().toUri().toString();
        registry.addResourceHandler("/api/files/images/**")
                .addResourceLocations(uploadPath);
        registry.addResourceHandler("/api/files/pdfs/**")
                .addResourceLocations(uploadPath);
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadPath);
    }
}
