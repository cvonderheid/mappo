import { describe, expect, it } from "vitest";

import type { Release } from "@/lib/types";
import { canonicalizeReleases, compareReleaseVersionsDesc } from "@/lib/releases";

function release(overrides: Partial<Release>): Release {
  return {
    id: overrides.id ?? "rel-test",
    sourceRef: overrides.sourceRef ?? "github://example/release",
    sourceVersion: overrides.sourceVersion ?? "2026.03.07.1",
    sourceType: overrides.sourceType ?? "deployment_stack",
    sourceVersionRef: overrides.sourceVersionRef ?? "https://example.invalid/release.json",
    deploymentScope: overrides.deploymentScope ?? "resource_group",
    executionSettings: overrides.executionSettings ?? {
      armMode: "incremental",
      whatIfOnCanary: false,
      verifyAfterDeploy: true,
    },
    parameterDefaults: overrides.parameterDefaults ?? {},
    releaseNotes: overrides.releaseNotes ?? "",
    verificationHints: overrides.verificationHints ?? [],
    createdAt: overrides.createdAt ?? "2026-03-08T00:00:00Z",
  };
}

describe("compareReleaseVersionsDesc", () => {
  it("sorts newer semantic date versions first", () => {
    expect(compareReleaseVersionsDesc("2026.03.07.2", "2026.03.07.1")).toBeLessThan(0);
    expect(compareReleaseVersionsDesc("2026.03.08.1", "2026.03.07.9")).toBeLessThan(0);
    expect(compareReleaseVersionsDesc("2026.03.04.1", "2026.03.07.1")).toBeGreaterThan(0);
  });
});

describe("canonicalizeReleases", () => {
  it("deduplicates by sourceVersion and prefers deployment_stack rows", () => {
    const releases = canonicalizeReleases([
      release({
        id: "rel-template",
        sourceVersion: "2026.03.07.1",
        sourceType: "template_spec",
        createdAt: "2026-03-08T10:00:00Z",
      }),
      release({
        id: "rel-deployment",
        sourceVersion: "2026.03.07.1",
        sourceType: "deployment_stack",
        createdAt: "2026-03-07T10:00:00Z",
      }),
      release({
        id: "rel-newer",
        sourceVersion: "2026.03.07.2",
        sourceType: "deployment_stack",
        createdAt: "2026-03-08T11:00:00Z",
      }),
      release({
        id: "rel-older",
        sourceVersion: "2026.03.04.1",
        sourceType: "template_spec",
        createdAt: "2026-03-08T09:00:00Z",
      }),
    ]);

    expect(releases.map((item) => item.id)).toEqual([
      "rel-newer",
      "rel-deployment",
      "rel-older",
    ]);
  });
});
