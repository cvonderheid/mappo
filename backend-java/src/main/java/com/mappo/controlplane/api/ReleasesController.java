package com.mappo.controlplane.api;

import com.mappo.controlplane.service.ReleaseService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/releases")
public class ReleasesController {

    private final ReleaseService releaseService;

    public ReleasesController(ReleaseService releaseService) {
        this.releaseService = releaseService;
    }

    @GetMapping
    public List<Map<String, Object>> listReleases() {
        return releaseService.listReleases();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createRelease(@RequestBody Map<String, Object> request) {
        return releaseService.createRelease(request);
    }
}
