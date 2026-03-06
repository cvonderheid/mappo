package com.mappo.controlplane.api;

import com.mappo.controlplane.api.request.ReleaseCreateRequest;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.service.ReleaseService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/releases")
@RequiredArgsConstructor
public class ReleasesController {

    private final ReleaseService releaseService;

    @GetMapping
    public List<ReleaseRecord> listReleases() {
        return releaseService.listReleases();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReleaseRecord createRelease(@Valid @RequestBody ReleaseCreateRequest request) {
        return releaseService.createRelease(request);
    }
}
