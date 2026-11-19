package com.fundmessenger.config.exception;

import org.springframework.http.HttpStatus;

public class ExternalServiceException extends BusinessException {
    public ExternalServiceException(String service, String detail) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "EXTERNAL_SERVICE_ERROR",
                service + " 서비스 오류: " + detail);
    }
}
