package com.mappo.controlplane.api;

import com.mappo.controlplane.api.request.SecretReferenceCreateRequest;
import com.mappo.controlplane.api.request.SecretReferencePatchRequest;
import com.mappo.controlplane.model.SecretReferenceRecord;
import com.mappo.controlplane.service.secretreference.SecretReferenceCatalogService;
import com.mappo.controlplane.service.secretreference.SecretReferenceCommandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/secret-references")
@RequiredArgsConstructor
@Tag(name = "Secret References", description = "Named secret references backed by MAPPO runtime defaults, environment variables, or Azure Key Vault.")
public class SecretReferencesController {

    private final SecretReferenceCatalogService secretReferenceCatalogService;
    private final SecretReferenceCommandService secretReferenceCommandService;

    @GetMapping
    @Operation(summary = "List secret references")
    public List<SecretReferenceRecord> listSecretReferences() {
        return secretReferenceCatalogService.listSecretReferences();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create secret reference")
    public SecretReferenceRecord createSecretReference(@Valid @RequestBody SecretReferenceCreateRequest request) {
        return secretReferenceCommandService.createSecretReference(request);
    }

    @PatchMapping("/{secretReferenceId}")
    @Operation(summary = "Patch secret reference")
    public SecretReferenceRecord patchSecretReference(
        @PathVariable("secretReferenceId") String secretReferenceId,
        @RequestBody(required = false) SecretReferencePatchRequest request
    ) {
        return secretReferenceCommandService.patchSecretReference(secretReferenceId, request);
    }

    @DeleteMapping("/{secretReferenceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete secret reference")
    public void deleteSecretReference(@PathVariable("secretReferenceId") String secretReferenceId) {
        secretReferenceCommandService.deleteSecretReference(secretReferenceId);
    }
}
