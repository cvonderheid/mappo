import type { Release } from "@/lib/types";

const SOURCE_TYPE_PRIORITY: Record<string, number> = {
  deployment_stack: 0,
  bicep: 1,
  template_spec: 2,
};

function parseReleaseVersionParts(version: string | null | undefined): number[] {
  const normalized = String(version ?? "").trim();
  if (normalized === "") {
    return [];
  }
  return normalized
    .split(".")
    .map((part) => Number.parseInt(part, 10))
    .filter((part) => Number.isFinite(part));
}

export function compareReleaseVersionsDesc(left: string | null | undefined, right: string | null | undefined): number {
  const leftParts = parseReleaseVersionParts(left);
  const rightParts = parseReleaseVersionParts(right);
  const maxLength = Math.max(leftParts.length, rightParts.length);

  for (let index = 0; index < maxLength; index += 1) {
    const leftPart = leftParts[index] ?? 0;
    const rightPart = rightParts[index] ?? 0;
    if (leftPart !== rightPart) {
      return rightPart - leftPart;
    }
  }

  return String(right ?? "").localeCompare(String(left ?? ""));
}

function sourceTypePriority(sourceType: string | null | undefined): number {
  return SOURCE_TYPE_PRIORITY[sourceType ?? ""] ?? Number.MAX_SAFE_INTEGER;
}

function compareReleasePreference(left: Release, right: Release): number {
  const versionOrder = compareReleaseVersionsDesc(left.sourceVersion, right.sourceVersion);
  if (versionOrder !== 0) {
    return versionOrder;
  }

  const sourceTypeOrder = sourceTypePriority(left.sourceType) - sourceTypePriority(right.sourceType);
  if (sourceTypeOrder !== 0) {
    return sourceTypeOrder;
  }

  const leftCreatedAt = new Date(left.createdAt ?? 0).getTime();
  const rightCreatedAt = new Date(right.createdAt ?? 0).getTime();
  return rightCreatedAt - leftCreatedAt;
}

export function canonicalizeReleases(releases: Release[]): Release[] {
  const preferredByVersion = new Map<string, Release>();

  for (const release of releases) {
    const versionKey = String(release.sourceVersion ?? "").trim();
    if (versionKey === "") {
      continue;
    }
    const current = preferredByVersion.get(versionKey);
    if (!current || compareReleasePreference(release, current) < 0) {
      preferredByVersion.set(versionKey, release);
    }
  }

  return Array.from(preferredByVersion.values()).sort(compareReleasePreference);
}
