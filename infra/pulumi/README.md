# MAPPO Pulumi IaC (Java)

This Pulumi project provisions a marketplace-accurate managed application demo surface for MAPPO.

## Runtime

- Pulumi runtime: `java`
- Entry point: `com.mappo.pulumi.Main`
- Build file: `infra/pulumi/pom.xml`

## What it creates

- Optional stable ingress:
- Azure Front Door profile, endpoint, route, and optional custom domain for the MAPPO webhook/API edge
- Per target subscription:
- Managed app definition resource group (`Microsoft.Solutions/applicationDefinitions`)
- Managed app instances resource group (`Microsoft.Solutions/applications`)
- Per target:
- Managed app instance + managed resource group
- Managed Environment + Container App inside the managed resource group
- Optional control-plane persistence:
- Azure Database for PostgreSQL Flexible Server + `mappo` database

## Stack config contract

Pulumi config namespace is `mappo`.

Required:
- Either `mappo:publisherPrincipalObjectId` or `mappo:publisherPrincipalObjectIds`

Required when `mappo:controlPlanePostgresEnabled=true`:
- `mappo:controlPlanePostgresAdminPassword` (secret)

Common optional keys:
- `mappo:targetProfile` (`empty` or `demo10`; default `empty`)
- `mappo:targets` (explicit array override)
- `mappo:defaultLocation` (`eastus`)
- `mappo:controlPlanePostgresEnabled` (`false`)
- `mappo:controlPlaneSubscriptionId`
- `mappo:controlPlaneLocation`
- `mappo:frontDoorEnabled` (`false`)
- `mappo:frontDoorOriginHost` (required when Front Door is enabled)
- `mappo:frontDoorCustomDomainHostName`
- `mappo:frontDoorDnsZoneSubscriptionId`
- `mappo:frontDoorDnsZoneResourceGroupName`
- `mappo:frontDoorDnsZoneName`

Detailed Front Door setup:
- [frontdoor-webhook-runbook.md](/Users/cvonderheid/workspace/mappo/docs/frontdoor-webhook-runbook.md)

## Commands

Compile only:

```bash
./mvnw -pl infra/pulumi -DskipTests compile
```

Preview:

```bash
cd infra/pulumi
pulumi login --local
pulumi stack select <stack> || pulumi stack init <stack>
pulumi preview --stack <stack>
```

Apply:

```bash
cd infra/pulumi
pulumi up --stack <stack> --yes
```

Destroy:

```bash
cd infra/pulumi
pulumi destroy --stack <stack> --yes
```
