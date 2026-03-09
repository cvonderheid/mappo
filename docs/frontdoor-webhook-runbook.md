# MAPPO Front Door Webhook Runbook

This runbook gives MAPPO a stable public webhook URL in front of the hosted backend Container App.

Use this when you want the GitHub webhook target to remain stable even if the backend Container App host changes.

## What Pulumi Creates

In a dedicated edge resource group:
- Azure Front Door Standard/Premium profile
- Azure Front Door endpoint
- Origin group pointing to the backend ACA hostname
- Route forwarding `/*` to the backend origin
- Optional custom domain
- Optional Azure DNS TXT/CNAME records when the DNS zone is hosted in Azure DNS

Pulumi output of interest:
- `managedAppReleaseWebhookUrl`
- `frontDoorDefaultWebhookUrl`
- `frontDoorDefaultHostname`
- `frontDoorDnsTxtRecordName`
- `frontDoorDnsTxtRecordValue`
- `frontDoorDnsCnameRecordName`
- `frontDoorDnsCnameRecordValue`

## Config Keys

Pulumi namespace: `mappo`

Required to enable Front Door:
- `mappo:frontDoorEnabled=true`
- `mappo:frontDoorOriginHost=<backend-aca-hostname>`

Optional:
- `mappo:frontDoorSubscriptionId`
- `mappo:frontDoorResourceGroupName`
- `mappo:frontDoorResourceGroupLocation`
- `mappo:frontDoorProfileSku` (`Standard_AzureFrontDoor` or `Premium_AzureFrontDoor`)
- `mappo:frontDoorProfileName`
- `mappo:frontDoorEndpointName`
- `mappo:frontDoorOriginGroupName`
- `mappo:frontDoorOriginName`
- `mappo:frontDoorRouteName`
- `mappo:frontDoorHealthProbePath`

Custom domain:
- `mappo:frontDoorCustomDomainHostName=api.<your-domain>`

Optional Azure DNS automation:
- `mappo:frontDoorDnsZoneSubscriptionId`
- `mappo:frontDoorDnsZoneResourceGroupName`
- `mappo:frontDoorDnsZoneName`

## Recommended Setup

Use a subdomain like:

```text
api.<your-domain>
```

Do not use the zone apex in this automation. The Pulumi path assumes a CNAME-based binding.

## Example Config

```bash
cd /Users/cvonderheid/workspace/mappo/infra/pulumi

pulumi config set --stack demo mappo:frontDoorEnabled true
pulumi config set --stack demo mappo:frontDoorOriginHost "ca-mappo-api-demo.victorioussmoke-7becae0e.centralus.azurecontainerapps.io"
pulumi config set --stack demo mappo:frontDoorCustomDomainHostName "api.example.com"
```

If the zone is hosted in Azure DNS:

```bash
pulumi config set --stack demo mappo:frontDoorDnsZoneSubscriptionId "<dns-subscription-id>"
pulumi config set --stack demo mappo:frontDoorDnsZoneResourceGroupName "<dns-zone-resource-group>"
pulumi config set --stack demo mappo:frontDoorDnsZoneName "example.com"
```

Then apply:

```bash
pulumi up --stack demo --yes
```

## DNS Behavior

### Azure DNS hosted zone

If `frontDoorDnsZone*` config is set, Pulumi creates:
- TXT: `_dnsauth.<subdomain>`
- CNAME: `<subdomain>` -> `<afd-endpoint-host>`

That is the preferred path. It removes the last manual DNS step.

### External DNS

If the zone is not hosted in Azure DNS:

1. Run `pulumi up`
2. Read the outputs:
   - `frontDoorDnsTxtRecordName`
   - `frontDoorDnsTxtRecordValue`
   - `frontDoorDnsCnameRecordName`
   - `frontDoorDnsCnameRecordValue`
3. Create those DNS records at your DNS provider
4. Wait for validation and certificate issuance to complete

## Final Webhook URL

Use the Pulumi output:

```bash
pulumi stack output managedAppReleaseWebhookUrl --stack demo
```

That resolves to:

- custom domain URL when `frontDoorCustomDomainHostName` is configured
- otherwise the Azure Front Door default hostname

Expected format:

```text
https://api.<your-domain>/api/v1/admin/releases/webhooks/github
```

## Notes

- The Front Door origin is the hosted MAPPO backend ACA hostname, not the frontend.
- Runtime ACA remains script-deployed today. Front Door treats that backend hostname as an input origin.
- This change does not yet replace the separate GitHub webhook bootstrap flow. It gives that flow a stable target URL.
