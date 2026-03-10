# MAPPO Pulumi IaC (Java)

This Pulumi project provisions only the shared control-plane infrastructure that still belongs in IaC:

- control-plane PostgreSQL
- Front Door edge / custom-domain ingress

It does not provision target workloads. The target fleet lives in
[`/Users/cvonderheid/workspace/mappo/infra/demo-fleet`](/Users/cvonderheid/workspace/mappo/infra/demo-fleet).

## Runtime

- Pulumi runtime: `java`
- Entry point: `com.mappo.pulumi.Main`
- Build file: `infra/pulumi/pom.xml`

## What it creates

- Optional stable ingress:
  - Azure Front Door profile, endpoint, route, and optional custom domain for the MAPPO webhook/API edge
- Optional control-plane persistence:
  - Azure Database for PostgreSQL Flexible Server + `mappo` database

## Stack config contract

Pulumi config namespace is `mappo`.

Required when `mappo:controlPlanePostgresEnabled=true`:
- `mappo:controlPlanePostgresAdminPassword` (secret)

Common optional keys:
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

Target fleet provisioning:
- [`/Users/cvonderheid/workspace/mappo/infra/demo-fleet/README.md`](/Users/cvonderheid/workspace/mappo/infra/demo-fleet/README.md)
- [`/Users/cvonderheid/workspace/mappo/scripts/demo_fleet_up.sh`](/Users/cvonderheid/workspace/mappo/scripts/demo_fleet_up.sh)
