export type DemoTargetDefinition = {
  id: string;
  tenantId: string;
  targetGroup: string;
  region: string;
  tier: string;
};

export const DEMO10_TARGET_DEFINITIONS: DemoTargetDefinition[] = [
  { id: "target-01", tenantId: "tenant-001", targetGroup: "canary", region: "eastus", tier: "gold" },
  { id: "target-02", tenantId: "tenant-002", targetGroup: "canary", region: "eastus", tier: "gold" },
  { id: "target-03", tenantId: "tenant-003", targetGroup: "prod", region: "eastus", tier: "gold" },
  { id: "target-04", tenantId: "tenant-004", targetGroup: "prod", region: "eastus", tier: "gold" },
  { id: "target-05", tenantId: "tenant-005", targetGroup: "prod", region: "eastus", tier: "silver" },
  { id: "target-06", tenantId: "tenant-006", targetGroup: "prod", region: "eastus", tier: "silver" },
  { id: "target-07", tenantId: "tenant-007", targetGroup: "prod", region: "eastus", tier: "silver" },
  { id: "target-08", tenantId: "tenant-008", targetGroup: "prod", region: "eastus", tier: "silver" },
  { id: "target-09", tenantId: "tenant-009", targetGroup: "prod", region: "eastus", tier: "bronze" },
  { id: "target-10", tenantId: "tenant-010", targetGroup: "prod", region: "eastus", tier: "bronze" },
];
