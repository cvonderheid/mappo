package com.mappo.controlplane.api.query;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class PageQueryParameters {

    @Schema(description = "Zero-based page index.", example = "0", defaultValue = "0")
    @Min(0)
    private Integer page = 0;

    @Schema(description = "Page size.", example = "25", defaultValue = "25")
    @Min(1)
    @Max(200)
    private Integer size = 25;
}
