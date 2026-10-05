package br.com.deladopara.identity.adapter.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetRequest(
        @NotBlank @Size(min = 40, max = 64) String token,
        @NotBlank @Size(min = 8, max = 72) String newPassword) {}
