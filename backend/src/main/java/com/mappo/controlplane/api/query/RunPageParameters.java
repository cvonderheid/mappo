package com.mappo.controlplane.api.query;

import com.mappo.controlplane.jooq.enums.MappoRunStatus;
import com.mappo.controlplane.model.query.RunPageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RunPageParameters extends PageQueryParameters {

    @Schema(description = "Filter by run identifier.", example = "run-2a8daf6089")
    private String runId;

    @Schema(description = "Filter by release identifier.", example = "rel-01fcd26e87")
    private String releaseId;

    @Schema(description = "Filter by run status.")
    private MappoRunStatus status;

    public RunPageQuery toQuery() {
        return new RunPageQuery(getPage(), getSize(), runId, releaseId, status);
    }
}
