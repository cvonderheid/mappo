package com.mappo.controlplane.api.query;

import com.mappo.controlplane.jooq.enums.MappoRuntimeProbeStatus;
import com.mappo.controlplane.jooq.enums.MappoTargetStage;
import com.mappo.controlplane.model.query.TargetPageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TargetPageParameters extends PageQueryParameters {

    @Schema(description = "Filter by project identifier.", example = "managed-app-demo")
    private String projectId;

    @Schema(description = "Filter by target identifier.", example = "demo-target-01")
    private String targetId;

    @Schema(description = "Filter by customer display name.", example = "Demo Customer A")
    private String customerName;

    @Schema(description = "Filter by tenant identifier.", example = "abe468b2-18bb-4dd2-90b9-5b8982337eb7")
    private String tenantId;

    @Schema(description = "Filter by subscription identifier.", example = "c0d51042-7d0a-41f7-b270-151e4c4ea263")
    private String subscriptionId;

    @Schema(description = "Filter by target ring/group.", example = "canary")
    private String ring;

    @Schema(description = "Filter by target region.", example = "centralus")
    private String region;

    @Schema(description = "Filter by target tier.", example = "gold")
    private String tier;

    @Schema(description = "Filter by deployed version.", example = "2026.03.08.1")
    private String version;

    @Schema(description = "Filter by runtime status.")
    private MappoRuntimeProbeStatus runtimeStatus;

    @Schema(description = "Filter by last deployment target stage.")
    private MappoTargetStage lastDeploymentStatus;

    public TargetPageQuery toQuery() {
        return new TargetPageQuery(
            getPage(),
            getSize(),
            projectId,
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
        );
    }
}
