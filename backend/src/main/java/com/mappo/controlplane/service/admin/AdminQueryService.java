package com.mappo.controlplane.service.admin;

import com.mappo.controlplane.model.ForwarderLogPageRecord;
import com.mappo.controlplane.model.MarketplaceEventPageRecord;
import com.mappo.controlplane.model.ReleaseWebhookDeliveryPageRecord;
import com.mappo.controlplane.model.TargetRegistrationPageRecord;
import com.mappo.controlplane.model.query.ForwarderLogPageQuery;
import com.mappo.controlplane.model.query.MarketplaceEventPageQuery;
import com.mappo.controlplane.model.query.ReleaseWebhookDeliveryPageQuery;
import com.mappo.controlplane.model.query.TargetRegistrationPageQuery;
import com.mappo.controlplane.persistence.admin.ForwarderLogPageRepository;
import com.mappo.controlplane.persistence.admin.MarketplaceEventPageRepository;
import com.mappo.controlplane.persistence.release.ReleaseWebhookRepository;
import com.mappo.controlplane.persistence.target.TargetRegistrationPageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminQueryService {

    private final TargetRegistrationPageRepository targetRegistrationPageRepository;
    private final MarketplaceEventPageRepository marketplaceEventPageRepository;
    private final ForwarderLogPageRepository forwarderLogPageRepository;
    private final ReleaseWebhookRepository releaseWebhookRepository;

    public TargetRegistrationPageRecord listRegistrationsPage(TargetRegistrationPageQuery query) {
        return targetRegistrationPageRepository.listRegistrationsPage(query);
    }

    public MarketplaceEventPageRecord listMarketplaceEventsPage(MarketplaceEventPageQuery query) {
        return marketplaceEventPageRepository.listMarketplaceEventsPage(query);
    }

    public ForwarderLogPageRecord listForwarderLogsPage(ForwarderLogPageQuery query) {
        return forwarderLogPageRepository.listForwarderLogsPage(query);
    }

    public ReleaseWebhookDeliveryPageRecord listReleaseWebhookDeliveriesPage(
        ReleaseWebhookDeliveryPageQuery query
    ) {
        return releaseWebhookRepository.listReleaseWebhookDeliveriesPage(query);
    }
}
