package com.mappo.controlplane.api.query;

import com.mappo.controlplane.jooq.enums.MappoReleaseWebhookStatus;
import com.mappo.controlplane.model.query.ReleaseWebhookDeliveryPageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReleaseWebhookDeliveryPageParameters extends PageQueryParameters {

    @Schema(description = "Filter by GitHub delivery identifier.", example = "4b85fdb0-1d72-11ef-8c1c-0242ac120002")
    private String deliveryId;

    @Schema(description = "Filter by MAPPO webhook processing status.")
    private MappoReleaseWebhookStatus status;

    public ReleaseWebhookDeliveryPageQuery toQuery() {
        return new ReleaseWebhookDeliveryPageQuery(getPage(), getSize(), deliveryId, status);
    }
}
