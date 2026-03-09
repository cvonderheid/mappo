import { compareReleaseVersionsDesc } from "@/lib/releases";
import type { Release, Target } from "@/lib/types";

export type LatestReleaseStatus = "current" | "outdated" | "unknown";
export type RuntimeStatus = "healthy" | "degraded" | "registered" | "unknown";
export type LastDeploymentStatusTone = "succeeded" | "failed" | "running" | "queued" | "unknown";

export function targetLatestReleaseStatus(
  target: Pick<Target, "lastDeployedRelease">,
  latestVersion: string
): LatestReleaseStatus {
  if (latestVersion.trim() === "") {
    return "unknown";
  }
  if (!target.lastDeployedRelease) {
    return "unknown";
  }
  return compareReleaseVersionsDesc(target.lastDeployedRelease, latestVersion) === 0
    ? "current"
    : "outdated";
}

export function releaseAvailabilitySummary(targets: Target[], latestRelease: Release | null) {
  const latestVersion = latestRelease?.sourceVersion?.trim() ?? "";
  let outdatedCount = 0;
  let unknownCount = 0;

  for (const target of targets) {
    const status = targetLatestReleaseStatus(target, latestVersion);
    if (status === "outdated") {
      outdatedCount += 1;
    } else if (status === "unknown") {
      unknownCount += 1;
    }
  }

  return {
    latestVersion,
    hasBanner: latestVersion !== "" && (outdatedCount > 0 || unknownCount > 0),
    outdatedCount,
    unknownCount,
  };
}

export function targetRuntimeStatus(target: Pick<Target, "healthStatus">): RuntimeStatus {
  const value = String(target.healthStatus ?? "").trim().toLowerCase();
  if (value === "healthy" || value === "degraded" || value === "registered") {
    return value as RuntimeStatus;
  }
  return "unknown";
}

export function targetLastDeploymentTone(
  target: Pick<Target, "lastDeploymentStatus">
): LastDeploymentStatusTone {
  const value = String(target.lastDeploymentStatus ?? "").trim().toUpperCase();
  if (value === "SUCCEEDED") {
    return "succeeded";
  }
  if (value === "FAILED") {
    return "failed";
  }
  if (value === "DEPLOYING" || value === "VERIFYING" || value === "VALIDATING") {
    return "running";
  }
  if (value === "QUEUED") {
    return "queued";
  }
  return "unknown";
}
