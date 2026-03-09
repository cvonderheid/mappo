package com.mappo.controlplane.api.query;

import com.mappo.controlplane.jooq.enums.MappoForwarderLogLevel;
import com.mappo.controlplane.model.query.ForwarderLogPageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ForwarderLogPageParameters extends PageQueryParameters {

    @Schema(description = "Filter by forwarder log identifier.", example = "log-20260308-001")
    private String logId;

    @Schema(description = "Filter by forwarder log level.")
    private MappoForwarderLogLevel level;

    public ForwarderLogPageQuery toQuery() {
        return new ForwarderLogPageQuery(getPage(), getSize(), logId, level);
    }
}
