package com.fundmessenger.config;

import com.fundmessenger.config.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ===== 도메인 예외 계층 (BusinessException 하위) =====

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, String>> handleBusiness(BusinessException e) {
        log.warn("Business error [{}]: {}", e.getCode(), e.getMessage());
        return ResponseEntity
                .status(e.getStatus())
                .body(Map.of("error", e.getMessage(), "code", e.getCode()));
    }

    // ===== 비즈니스 로직 예외 (안전한 메시지) =====

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, String>> handleSecurity(SecurityException e) {
        log.warn("Access denied: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException e) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> errors = e.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "유효하지 않은 값",
                        (a, b) -> a
                ));

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "입력값이 올바르지 않습니다", "fields", errors));
    }

    // ===== DB 예외 — 절대 raw SQL/스택트레이스 노출 금지 =====

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrity(DataIntegrityViolationException e) {
        log.error("Data integrity violation: {}", e.getMessage(), e);
        // 중복 데이터인지 판별
        String msg = e.getMessage();
        if (msg != null && (msg.contains("unique") || msg.contains("duplicate") || msg.contains("already exists"))) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "이미 존재하는 데이터입니다"));
        }
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "데이터 처리 중 오류가 발생했습니다"));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> handleDataAccess(DataAccessException e) {
        log.error("Database error: {}", e.getMessage(), e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요."));
    }

    // ===== RuntimeException — 비즈니스 메시지만 반환, DB 에러는 위에서 처리됨 =====

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntime(RuntimeException e) {
        log.error("Runtime error: {}", e.getMessage(), e);
        // 안전한 비즈니스 메시지만 반환 (SQL 키워드 포함 시 일반 메시지로 대체)
        String message = e.getMessage();
        if (message != null && isSqlRelated(message)) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "서버 오류가 발생했습니다"));
        }
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", message != null ? message : "요청 처리 중 오류가 발생했습니다"));
    }

    // ===== 최종 안전망 — 어떤 예외든 raw 메시지 노출 방지 =====

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneral(Exception e) {
        log.error("Unhandled exception: {}", e.getClass().getSimpleName(), e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요."));
    }

    // ===== 유틸리티 =====

    /** SQL 관련 키워드가 포함된 에러 메시지인지 판별 */
    private boolean isSqlRelated(String message) {
        String lower = message.toLowerCase();
        return lower.contains("sql") || lower.contains("jdbc") || lower.contains("constraint")
                || lower.contains("relation") || lower.contains("column") || lower.contains("table")
                || lower.contains("insert") || lower.contains("update") || lower.contains("delete")
                || lower.contains("select") || lower.contains("hibernate") || lower.contains("jpa")
                || lower.contains("postgresql") || lower.contains("transaction");
    }
}
