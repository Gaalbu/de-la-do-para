package br.com.deladopara.catalog.adapter.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProducerWriteRequest(
        @NotBlank @Size(max = 80) @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*")
        String slug,

        @NotBlank @Size(max = 120) String displayName,
        @NotBlank @Size(max = 300) String originLabel,
        @NotBlank @Size(max = 2000) String description) {}
