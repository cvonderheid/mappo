package com.mappo.pulumi;

import com.pulumi.Context;
import com.pulumi.azurenative.Provider;
import com.pulumi.azurenative.cdn.AFDCustomDomain;
import com.pulumi.azurenative.cdn.AFDCustomDomainArgs;
import com.pulumi.azurenative.cdn.AFDEndpoint;
import com.pulumi.azurenative.cdn.AFDEndpointArgs;
import com.pulumi.azurenative.cdn.AFDOrigin;
import com.pulumi.azurenative.cdn.AFDOriginArgs;
import com.pulumi.azurenative.cdn.AFDOriginGroup;
import com.pulumi.azurenative.cdn.AFDOriginGroupArgs;
import com.pulumi.azurenative.cdn.Profile;
import com.pulumi.azurenative.cdn.ProfileArgs;
import com.pulumi.azurenative.cdn.Route;
import com.pulumi.azurenative.cdn.RouteArgs;
import com.pulumi.azurenative.cdn.enums.AFDEndpointProtocols;
import com.pulumi.azurenative.cdn.enums.AfdCertificateType;
import com.pulumi.azurenative.cdn.enums.AfdMinimumTlsVersion;
import com.pulumi.azurenative.cdn.enums.EnabledState;
import com.pulumi.azurenative.cdn.enums.ForwardingProtocol;
import com.pulumi.azurenative.cdn.enums.HealthProbeRequestType;
import com.pulumi.azurenative.cdn.enums.HttpsRedirect;
import com.pulumi.azurenative.cdn.enums.LinkToDefaultDomain;
import com.pulumi.azurenative.cdn.enums.ProbeProtocol;
import com.pulumi.azurenative.cdn.enums.SkuName;
import com.pulumi.azurenative.cdn.inputs.AFDDomainHttpsParametersArgs;
import com.pulumi.azurenative.cdn.inputs.ActivatedResourceReferenceArgs;
import com.pulumi.azurenative.cdn.inputs.HealthProbeParametersArgs;
import com.pulumi.azurenative.cdn.inputs.LoadBalancingSettingsParametersArgs;
import com.pulumi.azurenative.cdn.inputs.ResourceReferenceArgs;
import com.pulumi.azurenative.dns.RecordSet;
import com.pulumi.azurenative.dns.RecordSetArgs;
import com.pulumi.azurenative.dns.inputs.CnameRecordArgs;
import com.pulumi.azurenative.dns.inputs.TxtRecordArgs;
import com.pulumi.azurenative.resources.ResourceGroup;
import com.pulumi.azurenative.resources.ResourceGroupArgs;
import com.pulumi.core.Either;
import com.pulumi.core.Output;
import com.pulumi.resources.CustomResourceOptions;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class FrontDoorResources {
    private final ResourceGroup resourceGroup;
    private final Profile profile;
    private final AFDEndpoint endpoint;
    private final AFDOriginGroup originGroup;
    private final AFDOrigin origin;
    private final AFDCustomDomain customDomain;
    private final Route route;
    private final RecordSet dnsTxtRecord;
    private final RecordSet dnsCnameRecord;

    private final Output<String> defaultHostname;
    private final Output<String> defaultWebhookUrl;
    private final Output<String> customDomainHostname;
    private final Output<String> customDomainValidationState;
    private final Output<String> customDomainValidationToken;
    private final Output<String> dnsTxtRecordName;
    private final Output<String> dnsTxtRecordValue;
    private final Output<String> dnsCnameRecordName;
    private final Output<String> dnsCnameRecordValue;
    private final Output<String> finalWebhookUrl;

    private FrontDoorResources(
        ResourceGroup resourceGroup,
        Profile profile,
        AFDEndpoint endpoint,
        AFDOriginGroup originGroup,
        AFDOrigin origin,
        AFDCustomDomain customDomain,
        Route route,
        RecordSet dnsTxtRecord,
        RecordSet dnsCnameRecord,
        Output<String> defaultHostname,
        Output<String> defaultWebhookUrl,
        Output<String> customDomainHostname,
        Output<String> customDomainValidationState,
        Output<String> customDomainValidationToken,
        Output<String> dnsTxtRecordName,
        Output<String> dnsTxtRecordValue,
        Output<String> dnsCnameRecordName,
        Output<String> dnsCnameRecordValue,
        Output<String> finalWebhookUrl
    ) {
        this.resourceGroup = resourceGroup;
        this.profile = profile;
        this.endpoint = endpoint;
        this.originGroup = originGroup;
        this.origin = origin;
        this.customDomain = customDomain;
        this.route = route;
        this.dnsTxtRecord = dnsTxtRecord;
        this.dnsCnameRecord = dnsCnameRecord;
        this.defaultHostname = defaultHostname;
        this.defaultWebhookUrl = defaultWebhookUrl;
        this.customDomainHostname = customDomainHostname;
        this.customDomainValidationState = customDomainValidationState;
        this.customDomainValidationToken = customDomainValidationToken;
        this.dnsTxtRecordName = dnsTxtRecordName;
        this.dnsTxtRecordValue = dnsTxtRecordValue;
        this.dnsCnameRecordName = dnsCnameRecordName;
        this.dnsCnameRecordValue = dnsCnameRecordValue;
        this.finalWebhookUrl = finalWebhookUrl;
    }

    static FrontDoorResources create(
        Context ctx,
        FrontDoorConfig config,
        Provider edgeProvider,
        Provider dnsProvider
    ) {
        validate(config);

        CustomResourceOptions edgeOpts = CustomResourceOptions.builder().provider(edgeProvider).build();
        CustomResourceOptions dnsOpts = dnsProvider == null ? null : CustomResourceOptions.builder().provider(dnsProvider).build();

        ResourceGroup resourceGroup = new ResourceGroup(
            "frontdoor-rg",
            ResourceGroupArgs.builder()
                .resourceGroupName(config.resourceGroupName())
                .location(config.resourceGroupLocation())
                .tags(Map.of(
                    "managedBy", "pulumi",
                    "system", "mappo",
                    "service", "frontdoor"
                ))
                .build(),
            edgeOpts
        );

        Profile profile = new Profile(
            "frontdoor-profile",
            ProfileArgs.builder()
                .resourceGroupName(resourceGroup.name())
                .profileName(config.profileName())
                .location("global")
                .sku(com.pulumi.azurenative.cdn.inputs.SkuArgs.builder()
                    .name(resolveSku(config.profileSku()))
                    .build())
                .tags(Map.of(
                    "managedBy", "pulumi",
                    "system", "mappo",
                    "service", "frontdoor"
                ))
                .build(),
            edgeOpts
        );

        AFDEndpoint endpoint = new AFDEndpoint(
            "frontdoor-endpoint",
            AFDEndpointArgs.builder()
                .resourceGroupName(resourceGroup.name())
                .profileName(profile.name())
                .endpointName(config.endpointName())
                .location("global")
                .enabledState(EnabledState.Enabled)
                .build(),
            edgeOpts
        );

        AFDOriginGroup originGroup = new AFDOriginGroup(
            "frontdoor-origin-group",
            AFDOriginGroupArgs.builder()
                .resourceGroupName(resourceGroup.name())
                .profileName(profile.name())
                .originGroupName(config.originGroupName())
                .sessionAffinityState(EnabledState.Disabled)
                .healthProbeSettings(HealthProbeParametersArgs.builder()
                    .probePath(config.healthProbePath())
                    .probeProtocol(ProbeProtocol.Https)
                    .probeRequestType(HealthProbeRequestType.HEAD)
                    .probeIntervalInSeconds(60)
                    .build())
                .loadBalancingSettings(LoadBalancingSettingsParametersArgs.builder()
                    .sampleSize(4)
                    .successfulSamplesRequired(3)
                    .additionalLatencyInMilliseconds(0)
                    .build())
                .build(),
            edgeOpts
        );

        AFDOrigin origin = new AFDOrigin(
            "frontdoor-origin",
            AFDOriginArgs.builder()
                .resourceGroupName(resourceGroup.name())
                .profileName(profile.name())
                .originGroupName(originGroup.name())
                .originName(config.originName())
                .hostName(config.originHost())
                .originHostHeader(config.originHost())
                .enabledState(EnabledState.Enabled)
                .httpsPort(443)
                .enforceCertificateNameCheck(true)
                .priority(1)
                .weight(1000)
                .build(),
            edgeOpts
        );

        AFDCustomDomain customDomain = null;
        Output<List<ActivatedResourceReferenceArgs>> customDomainRefs = null;
        Output<String> customDomainHostname = Output.ofNullable(null);
        Output<String> customDomainValidationState = Output.ofNullable(null);
        Output<String> customDomainValidationToken = Output.ofNullable(null);
        Output<String> dnsTxtRecordName = Output.ofNullable(null);
        Output<String> dnsTxtRecordValue = Output.ofNullable(null);
        Output<String> dnsCnameRecordName = Output.ofNullable(null);
        Output<String> dnsCnameRecordValue = Output.ofNullable(null);
        RecordSet txtRecord = null;
        RecordSet cnameRecord = null;

        if (config.hasCustomDomain()) {
            customDomain = new AFDCustomDomain(
                "frontdoor-custom-domain",
                AFDCustomDomainArgs.builder()
                    .resourceGroupName(resourceGroup.name())
                    .profileName(profile.name())
                    .customDomainName(normalizeResourceName(config.customDomainHostName()))
                    .hostName(config.customDomainHostName())
                    .tlsSettings(AFDDomainHttpsParametersArgs.builder()
                        .certificateType(AfdCertificateType.ManagedCertificate)
                        .minimumTlsVersion(AfdMinimumTlsVersion.TLS12)
                        .build())
                    .build(),
                edgeOpts
            );

            customDomainRefs = customDomain.id().applyValue(id ->
                List.of(ActivatedResourceReferenceArgs.builder().id(id).build())
            );
            customDomainHostname = customDomain.hostName();
            customDomainValidationState = customDomain.domainValidationState();
            customDomainValidationToken = customDomain.validationProperties().applyValue(value -> value.validationToken());

            String zoneRelativeName = zoneRelativeName(config.customDomainHostName(), config.dnsZoneName());
            String txtRelativeName = zoneRelativeName == null || zoneRelativeName.isBlank()
                ? null
                : "_dnsauth." + zoneRelativeName;

            dnsTxtRecordName = txtRelativeName == null ? Output.ofNullable(null) : Output.of(txtRelativeName);
            dnsTxtRecordValue = customDomainValidationToken;
            dnsCnameRecordName = zoneRelativeName == null || zoneRelativeName.isBlank() ? Output.ofNullable(null) : Output.of(zoneRelativeName);
            dnsCnameRecordValue = endpoint.hostName();

            if (config.hasAzureDnsZone()) {
                txtRecord = new RecordSet(
                    "frontdoor-custom-domain-txt",
                    RecordSetArgs.builder()
                        .resourceGroupName(config.dnsZoneResourceGroupName())
                        .zoneName(config.dnsZoneName())
                        .recordType("TXT")
                        .relativeRecordSetName(txtRelativeName)
                        .ttl(3600.0)
                        .txtRecords(customDomainValidationToken.applyValue(token ->
                            List.of(TxtRecordArgs.builder().value(List.of(token)).build())
                        ))
                        .build(),
                    dnsOpts
                );
                cnameRecord = new RecordSet(
                    "frontdoor-custom-domain-cname",
                    RecordSetArgs.builder()
                        .resourceGroupName(config.dnsZoneResourceGroupName())
                        .zoneName(config.dnsZoneName())
                        .recordType("CNAME")
                        .relativeRecordSetName(zoneRelativeName)
                        .ttl(3600.0)
                        .cnameRecord(CnameRecordArgs.builder().cname(endpoint.hostName()).build())
                        .build(),
                    dnsOpts
                );
            }
        }

        RouteArgs.Builder routeArgs = RouteArgs.builder()
            .resourceGroupName(resourceGroup.name())
            .profileName(profile.name())
            .endpointName(endpoint.name())
            .routeName(config.routeName())
            .originGroup(ResourceReferenceArgs.builder().id(originGroup.id()).build())
            .patternsToMatch(List.of("/*"))
            .supportedProtocols(
                Either.ofRight(AFDEndpointProtocols.Http),
                Either.ofRight(AFDEndpointProtocols.Https)
            )
            .httpsRedirect(HttpsRedirect.Enabled)
            .forwardingProtocol(ForwardingProtocol.HttpsOnly)
            .linkToDefaultDomain(LinkToDefaultDomain.Enabled)
            .enabledState(EnabledState.Enabled);
        if (customDomainRefs != null) {
            routeArgs.customDomains(customDomainRefs);
        }

        Route route = new Route(
            "frontdoor-route",
            routeArgs.build(),
            edgeOpts
        );

        Output<String> defaultHostname = endpoint.hostName();
        Output<String> defaultWebhookUrl = defaultHostname.applyValue(value ->
            "https://" + value + "/api/v1/admin/releases/webhooks/github"
        );
        Output<String> finalWebhookUrl = config.hasCustomDomain()
            ? customDomain.hostName().applyValue(value -> "https://" + value + "/api/v1/admin/releases/webhooks/github")
            : defaultWebhookUrl;

        ctx.log().info("Configured Azure Front Door endpoint " + config.endpointName() + " for host " + config.originHost() + ".");

        return new FrontDoorResources(
            resourceGroup,
            profile,
            endpoint,
            originGroup,
            origin,
            customDomain,
            route,
            txtRecord,
            cnameRecord,
            defaultHostname,
            defaultWebhookUrl,
            customDomainHostname,
            customDomainValidationState,
            customDomainValidationToken,
            dnsTxtRecordName,
            dnsTxtRecordValue,
            dnsCnameRecordName,
            dnsCnameRecordValue,
            finalWebhookUrl
        );
    }

    private static void validate(FrontDoorConfig config) {
        if (!config.enabled()) {
            return;
        }
        if (config.originHost() == null || config.originHost().isBlank()) {
            throw new IllegalStateException(
                "Front Door is enabled, but no backend origin host was configured. "
                    + "Set mappo:frontDoorOriginHost or MAPPO_RUNTIME_BACKEND_URL."
            );
        }
        if (config.hasAzureDnsZone() && !config.hasCustomDomain()) {
            throw new IllegalStateException(
                "Azure DNS zone settings were provided for Front Door, but no custom domain host name was configured."
            );
        }
        if (config.hasCustomDomain() && labelCount(config.customDomainHostName()) < 3) {
            throw new IllegalStateException(
                "Front Door custom domain must be a subdomain like api.example.com. Apex domains are not supported by this automation."
            );
        }
        if (config.hasAzureDnsZone()) {
            String relativeName = zoneRelativeName(config.customDomainHostName(), config.dnsZoneName());
            if (relativeName == null || relativeName.isBlank()) {
                throw new IllegalStateException(
                    "Front Door custom domain must be under the configured DNS zone and must not equal the zone apex."
                );
            }
        }
    }

    private static SkuName resolveSku(String skuName) {
        try {
            return SkuName.valueOf(skuName);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                "Unsupported Front Door SKU '" + skuName + "'. Use Standard_AzureFrontDoor or Premium_AzureFrontDoor.",
                ex
            );
        }
    }

    private static String normalizeResourceName(String hostName) {
        return hostName.toLowerCase(Locale.ROOT).replace('.', '-');
    }

    private static int labelCount(String hostName) {
        return (int) hostName.chars().filter(ch -> ch == '.').count() + 1;
    }

    private static String zoneRelativeName(String hostName, String zoneName) {
        if (zoneName == null || zoneName.isBlank()) {
            return null;
        }
        String normalizedHost = hostName.toLowerCase(Locale.ROOT);
        String normalizedZone = zoneName.toLowerCase(Locale.ROOT);
        if (!normalizedHost.endsWith("." + normalizedZone)) {
            return null;
        }
        String relative = normalizedHost.substring(0, normalizedHost.length() - normalizedZone.length() - 1);
        return relative.isBlank() ? null : relative;
    }

    static String extractHost(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        if (!trimmed.contains("://")) {
            return trimmed;
        }
        return URI.create(trimmed).getHost();
    }

    ResourceGroup resourceGroup() {
        return resourceGroup;
    }

    Profile profile() {
        return profile;
    }

    AFDEndpoint endpoint() {
        return endpoint;
    }

    AFDOriginGroup originGroup() {
        return originGroup;
    }

    AFDOrigin origin() {
        return origin;
    }

    AFDCustomDomain customDomain() {
        return customDomain;
    }

    Route route() {
        return route;
    }

    RecordSet dnsTxtRecord() {
        return dnsTxtRecord;
    }

    RecordSet dnsCnameRecord() {
        return dnsCnameRecord;
    }

    Output<String> defaultHostname() {
        return defaultHostname;
    }

    Output<String> defaultWebhookUrl() {
        return defaultWebhookUrl;
    }

    Output<String> customDomainHostname() {
        return customDomainHostname;
    }

    Output<String> customDomainValidationState() {
        return customDomainValidationState;
    }

    Output<String> customDomainValidationToken() {
        return customDomainValidationToken;
    }

    Output<String> dnsTxtRecordName() {
        return dnsTxtRecordName;
    }

    Output<String> dnsTxtRecordValue() {
        return dnsTxtRecordValue;
    }

    Output<String> dnsCnameRecordName() {
        return dnsCnameRecordName;
    }

    Output<String> dnsCnameRecordValue() {
        return dnsCnameRecordValue;
    }

    Output<String> finalWebhookUrl() {
        return finalWebhookUrl;
    }
}
