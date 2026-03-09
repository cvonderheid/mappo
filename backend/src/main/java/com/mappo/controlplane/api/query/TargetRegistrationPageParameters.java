package com.mappo.controlplane.api.query;

import com.mappo.controlplane.model.query.TargetRegistrationPageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TargetRegistrationPageParameters extends PageQueryParameters {

    @Schema(description = "Filter by target identifier.", example = "demo-target-01")
    private String targetId;

    @Schema(description = "Filter by target ring/group.", example = "canary")
    private String ring;

    @Schema(description = "Filter by target region.", example = "centralus")
    private String region;

    @Schema(description = "Filter by target tier.", example = "gold")
    private String tier;

    public TargetRegistrationPageQuery toQuery() {
        return new TargetRegistrationPageQuery(getPage(), getSize(), targetId, ring, region, tier);
    }
}
