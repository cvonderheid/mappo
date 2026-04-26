# MAPPO Azure Delivery Demo Targets IaC (Java)

This Pulumi project provisions two or more demo targets across subscriptions/tenants for marketplace event simulation.

## Runtime

- Pulumi runtime: `java`
- Entry point: `com.mappo.demo.azuredelivery.Main`
- Build file: `infra/demo/targets-azure-delivery/pom.xml`

## Outputs

- `targetCount`
- `mappoTargetInventory`
- `mappoTargetInventoryJson`
- `tenantBySubscription`

## Commands

Compile only:

```bash
./mvnw -pl infra/demo/targets-azure-delivery -DskipTests compile
```

Preview:

```bash
cd infra/demo/targets-azure-delivery
pulumi login --local
pulumi stack select <stack> || pulumi stack init <stack>
pulumi preview --stack <stack>
```

Apply:

```bash
cd infra/demo/targets-azure-delivery
pulumi up --stack <stack> --yes
```

Destroy:

```bash
cd infra/demo/targets-azure-delivery
pulumi destroy --stack <stack> --yes
```
