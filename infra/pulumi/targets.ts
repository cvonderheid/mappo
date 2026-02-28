import { DEMO10_TARGET_DEFINITIONS } from "./targets.demo10";

export interface TargetConfig {
  id: string;
  tenantId?: string;
  subscriptionId: string;
  targetGroup?: string;
  region?: string;
  tier?: string;
  environment?: string;
  managedApplicationName?: string;
  managedResourceGroupName?: string;
  containerAppName?: string;
  environmentVariables?: Record<string, string>;
  tags?: Record<string, string>;
}

export interface TargetProfileOptions {
  defaultLocation: string;
  defaultSubscriptionId: string;
}

export type TargetProfileName = "empty" | "demo10";

export function targetsFromProfile(
  profile: TargetProfileName,
  options: TargetProfileOptions,
): TargetConfig[] {
  if (profile === "empty") {
    return [];
  }
  if (profile === "demo10") {
    return DEMO10_TARGET_DEFINITIONS.map((definition) => ({
      id: definition.id,
      tenantId: definition.tenantId,
      subscriptionId: options.defaultSubscriptionId,
      targetGroup: definition.targetGroup,
      region: definition.region || options.defaultLocation,
      tier: definition.tier,
      environment: "demo",
      tags: {
        tier: definition.tier,
        environment: "demo",
      },
    }));
  }
  return assertNever(profile);
}

export function parseProfileName(value: string): TargetProfileName {
  if (value === "empty" || value === "demo10") {
    return value;
  }
  throw new Error(`Unknown mappo:targetProfile '${value}'. Expected one of: empty, demo10.`);
}

function assertNever(value: never): never {
  throw new Error(`Unhandled profile value: ${String(value)}`);
}
