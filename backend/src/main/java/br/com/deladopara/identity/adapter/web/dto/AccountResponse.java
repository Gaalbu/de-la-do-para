package br.com.deladopara.identity.adapter.web.dto;

import java.util.UUID;

public record AccountResponse(UUID id, String email, boolean emailVerified, String role, boolean cartMergeRequired) {
    public AccountResponse(UUID id, String email, boolean emailVerified, String role) {
        this(id, email, emailVerified, role, false);
    }
}
