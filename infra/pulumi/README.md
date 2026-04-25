# MAPPO Pulumi IaC (Java)

This Pulumi project provisions only the shared control-plane infrastructure that still belongs in IaC:

- control-plane PostgreSQL

It does not provision target workloads. The target fleet lives in
[`./infra/demo-fleet`](./infra/demo-fleet).

## Runtime

- Pulumi runtime: `java`
- Entry point: `com.mappo.pulumi.Main`
- Build file: `infra/pulumi/pom.xml`

## What it creates

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
- [`./infra/demo-fleet/README.md`](./infra/demo-fleet/README.md)
- [`./scripts/demo_fleet_up.sh`](./scripts/demo_fleet_up.sh)
