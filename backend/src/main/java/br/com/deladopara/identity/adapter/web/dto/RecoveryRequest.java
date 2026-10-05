package br.com.deladopara.identity.adapter.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RecoveryRequest(
        @NotBlank @Email @Size(max = 254) String email) {}
