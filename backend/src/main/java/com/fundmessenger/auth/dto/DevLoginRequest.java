package com.fundmessenger.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DevLoginRequest {
    @NotBlank
    private String role;  // "SUPER_ADMIN", "PRESIDENT", "VICE_PRESIDENT", etc.
}
