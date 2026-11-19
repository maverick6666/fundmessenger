package com.fundmessenger.file.service;

import com.fundmessenger.config.exception.BadRequestException;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class FileStorageService {

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp"
    );

    private static final long MAX_IMAGE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final long MAX_PDF_SIZE = 50 * 1024 * 1024; // 50MB

    private final String bucketName;
    private final Storage gcsStorage;
    private final Path uploadDir;
    private final boolean useGcs;

    public FileStorageService(
            @Value("${gcs.bucket-name:}") String bucketName,
            @Value("${app.upload-dir:uploads}") String uploadDirPath) {

        this.bucketName = bucketName;
        this.useGcs = bucketName != null && !bucketName.isBlank();

        if (useGcs) {
            this.gcsStorage = StorageOptions.getDefaultInstance().getService();
            this.uploadDir = null;
            log.info("FileStorage: GCS 모드 (bucket={})", bucketName);
        } else {
            this.gcsStorage = null;
            this.uploadDir = Paths.get(uploadDirPath).toAbsolutePath().normalize();
            try {
                Files.createDirectories(this.uploadDir);
            } catch (IOException e) {
                throw new BadRequestException("업로드 디렉토리를 생성할 수 없습니다: " + this.uploadDir);
            }
            log.info("FileStorage: 로컬 모드 (dir={})", this.uploadDir);
        }
    }

    /** 이미지 저장 → storedName 반환 */
    public String storeImage(MultipartFile file) {
        validateImage(file);
        String ext = getExtension(file.getOriginalFilename());
        String storedName = UUID.randomUUID() + ext;

        if (useGcs) {
            uploadToGcs(storedName, file);
        } else {
            saveToLocal(storedName, file);
        }
        log.info("이미지 저장: {} → {}", file.getOriginalFilename(), storedName);
        return storedName;
    }

    /** PDF 저장 → storedName 반환 */
    public String storePdf(MultipartFile file) {
        validatePdf(file);
        String storedName = UUID.randomUUID() + ".pdf";

        if (useGcs) {
            uploadToGcs(storedName, file);
        } else {
            saveToLocal(storedName, file);
        }
        log.info("PDF 저장: {} → {} ({}KB)", file.getOriginalFilename(), storedName, file.getSize() / 1024);
        return storedName;
    }

    /** 파일 삭제 */
    public void deleteFile(String storedName) {
        if (useGcs) {
            try {
                gcsStorage.delete(BlobId.of(bucketName, storedName));
            } catch (Exception e) {
                log.warn("GCS 파일 삭제 실패: {}", storedName, e);
            }
        } else {
            try {
                Path target = this.uploadDir.resolve(storedName);
                Files.deleteIfExists(target);
            } catch (IOException e) {
                log.warn("로컬 파일 삭제 실패: {}", storedName, e);
            }
        }
    }

    /** 파일을 byte[]로 다운로드 (AI 서비스, 다운로드 등에서 사용) */
    public byte[] downloadAsBytes(String storedName) {
        if (useGcs) {
            byte[] content = gcsStorage.readAllBytes(BlobId.of(bucketName, storedName));
            if (content == null) {
                throw new BadRequestException("GCS에서 파일을 찾을 수 없습니다: " + storedName);
            }
            return content;
        } else {
            try {
                Path filePath = this.uploadDir.resolve(storedName);
                return Files.readAllBytes(filePath);
            } catch (IOException e) {
                throw new BadRequestException("로컬 파일 읽기 실패: " + storedName);
            }
        }
    }

    /** 파일의 공개 URL 반환 (GCS: 공개 URL, 로컬: 상대 경로) */
    public String getPublicUrl(String storedName) {
        if (useGcs) {
            return String.format("https://storage.googleapis.com/%s/%s", bucketName, storedName);
        } else {
            return "/api/files/images/" + storedName;
        }
    }

    /** 로컬 모드에서만 사용 — 업로드 디렉토리 경로 (정적 리소스 서빙용) */
    public Path getUploadDir() {
        if (useGcs) {
            throw new UnsupportedOperationException("GCS 모드에서는 getUploadDir()을 사용할 수 없습니다");
        }
        return this.uploadDir;
    }

    public boolean isGcsMode() {
        return useGcs;
    }

    // ─── GCS 업로드 ───

    private void uploadToGcs(String storedName, MultipartFile file) {
        try {
            BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucketName, storedName))
                    .setContentType(file.getContentType())
                    .build();
            gcsStorage.createFrom(blobInfo, file.getInputStream());
        } catch (IOException e) {
            throw new BadRequestException("GCS 업로드 실패: " + file.getOriginalFilename());
        }
    }

    // ─── 로컬 저장 ───

    private void saveToLocal(String storedName, MultipartFile file) {
        try {
            Path target = this.uploadDir.resolve(storedName);
            file.transferTo(target.toFile());
        } catch (IOException e) {
            throw new BadRequestException("파일 저장 실패: " + file.getOriginalFilename());
        }
    }

    // ─── 검증 ───

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("파일이 비어있습니다");
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new IllegalArgumentException("파일 크기는 10MB를 초과할 수 없습니다");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("허용되지 않는 파일 형식입니다. (jpeg, png, gif, webp만 허용)");
        }
    }

    private void validatePdf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("파일이 비어있습니다");
        }
        if (file.getSize() > MAX_PDF_SIZE) {
            throw new IllegalArgumentException("PDF 파일 크기는 50MB를 초과할 수 없습니다");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.equals("application/pdf")) {
            throw new IllegalArgumentException("PDF 파일만 업로드할 수 있습니다");
        }
    }

    private String getExtension(String filename) {
        if (filename == null) return ".bin";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : ".bin";
    }
}
