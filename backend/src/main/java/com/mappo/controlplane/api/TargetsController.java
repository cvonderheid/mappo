package com.mappo.controlplane.api;

import com.mappo.controlplane.model.TargetPageRecord;
import com.mappo.controlplane.model.TargetRecord;
import com.mappo.controlplane.model.query.TargetPageQuery;
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

    @GetMapping("/page")
    public TargetPageRecord listTargetsPage(
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "25") int size,
        @RequestParam(value = "targetId", required = false) String targetId,
        @RequestParam(value = "customerName", required = false) String customerName,
        @RequestParam(value = "tenantId", required = false) String tenantId,
        @RequestParam(value = "subscriptionId", required = false) String subscriptionId,
        @RequestParam(value = "ring", required = false) String ring,
        @RequestParam(value = "region", required = false) String region,
        @RequestParam(value = "tier", required = false) String tier,
        @RequestParam(value = "version", required = false) String version,
        @RequestParam(value = "runtimeStatus", required = false) String runtimeStatus,
        @RequestParam(value = "lastDeploymentStatus", required = false) String lastDeploymentStatus
    ) {
        return targetService.listTargetsPage(
            new TargetPageQuery(
                page,
                size,
                targetId,
                customerName,
                tenantId,
                subscriptionId,
                ring,
                region,
                tier,
                version,
                runtimeStatus,
                lastDeploymentStatus
            )
        );
    }
}
