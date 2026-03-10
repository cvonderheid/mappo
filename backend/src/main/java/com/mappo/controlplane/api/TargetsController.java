package com.mappo.controlplane.api;

import com.mappo.controlplane.api.query.TargetPageParameters;
import com.mappo.controlplane.model.TargetPageRecord;
import com.mappo.controlplane.service.TargetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/targets")
@RequiredArgsConstructor
@Tag(name = "Targets", description = "Fleet target queries and selection endpoints.")
public class TargetsController {

    private final TargetService targetService;

    @GetMapping("/page")
    @Operation(summary = "List fleet targets", description = "Primary paginated fleet endpoint for operator views.")
    public TargetPageRecord listTargetsPage(
        @Valid
        @ParameterObject
        @ModelAttribute
        TargetPageParameters parameters
    ) {
        return targetService.listTargetsPage(parameters.toQuery());
    }
}
