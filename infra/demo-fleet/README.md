# MAPPO Demo Fleet IaC (Java)

This Pulumi project provisions a two-or-more target fleet across subscriptions/tenants for marketplace event simulation.

## Runtime

- Pulumi runtime: `java`
- Entry point: `com.mappo.demofleet.Main`
- Build file: `infra/demo-fleet/pom.xml`

## Outputs

- `targetCount`
- `mappoTargetInventory`
- `mappoTargetInventoryJson`
- `tenantBySubscription`

## Commands

Compile only:

```bash
./mvnw -pl infra/demo-fleet -DskipTests compile
```

Preview:

```bash
cd infra/demo-fleet
pulumi login --local
pulumi stack select <stack> || pulumi stack init <stack>
pulumi preview --stack <stack>
```

Apply:

```bash
cd infra/demo-fleet
pulumi up --stack <stack> --yes
```

Destroy:

```bash
cd infra/demo-fleet
pulumi destroy --stack <stack> --yes
```
