package com.mappo.controlplane.api.query;

import com.mappo.controlplane.jooq.enums.MappoMarketplaceEventStatus;
import com.mappo.controlplane.model.query.MarketplaceEventPageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MarketplaceEventPageParameters extends PageQueryParameters {

    @Schema(description = "Filter by onboarding event identifier.", example = "evt-20260308-001")
    private String eventId;

    @Schema(description = "Filter by onboarding event status.")
    private MappoMarketplaceEventStatus status;

    public MarketplaceEventPageQuery toQuery() {
        return new MarketplaceEventPageQuery(getPage(), getSize(), eventId, status);
    }
}
