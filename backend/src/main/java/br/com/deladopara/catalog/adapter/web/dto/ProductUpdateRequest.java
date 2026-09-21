package br.com.deladopara.catalog.adapter.web.dto;

import br.com.deladopara.catalog.domain.Product;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record ProductUpdateRequest(
        @NotBlank @Size(max = 80) @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*")
        String slug,

        @NotBlank @Size(max = 160) String displayName,
        @NotBlank @Size(max = 2000) String description,
        @NotNull Product.Category category,
        @NotNull UUID producerId,
        @NotNull Boolean active,
        @NotEmpty @Size(max = 50) List<@Valid ProductSkuWriteRequest> skus) {}
