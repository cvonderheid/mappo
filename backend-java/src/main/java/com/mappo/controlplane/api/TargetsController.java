package com.mappo.controlplane.api;

import com.mappo.controlplane.model.TargetRecord;
import com.mappo.controlplane.service.TargetService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/targets")
@RequiredArgsConstructor
public class TargetsController {

    private final TargetService targetService;

    @GetMapping
    public List<TargetRecord> listTargets(
        @RequestParam(value = "ring", required = false) String ring,
        @RequestParam(value = "region", required = false) String region,
        @RequestParam(value = "tier", required = false) String tier,
        @RequestParam(value = "environment", required = false) String environment
    ) {
        return targetService.listTargets(ring, region, tier, environment);
    }
}
