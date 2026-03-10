package com.mappo.controlplane.service.admin;

import com.mappo.controlplane.model.ForwarderLogPageRecord;
import com.mappo.controlplane.model.MarketplaceEventPageRecord;
import com.mappo.controlplane.model.ReleaseWebhookDeliveryPageRecord;
import com.mappo.controlplane.model.TargetRegistrationPageRecord;
import com.mappo.controlplane.model.query.ForwarderLogPageQuery;
import com.mappo.controlplane.model.query.MarketplaceEventPageQuery;
import com.mappo.controlplane.model.query.ReleaseWebhookDeliveryPageQuery;
import com.mappo.controlplane.model.query.TargetRegistrationPageQuery;
import com.mappo.controlplane.repository.AdminPageRepository;
import com.mappo.controlplane.repository.ReleaseWebhookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminQueryService {

    private final AdminPageRepository adminPageRepository;
    private final ReleaseWebhookRepository releaseWebhookRepository;

    public TargetRegistrationPageRecord listRegistrationsPage(TargetRegistrationPageQuery query) {
        return adminPageRepository.listRegistrationsPage(query);
    }

    public MarketplaceEventPageRecord listMarketplaceEventsPage(MarketplaceEventPageQuery query) {
        return adminPageRepository.listMarketplaceEventsPage(query);
    }

    public ForwarderLogPageRecord listForwarderLogsPage(ForwarderLogPageQuery query) {
        return adminPageRepository.listForwarderLogsPage(query);
    }

    public ReleaseWebhookDeliveryPageRecord listReleaseWebhookDeliveriesPage(
        ReleaseWebhookDeliveryPageQuery query
    ) {
        return releaseWebhookRepository.listReleaseWebhookDeliveriesPage(query);
    }
}
