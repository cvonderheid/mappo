import { expect, test } from "@playwright/test";

import { AppShellPage } from "../pages/app-shell.page";
import { DeploymentsPage } from "../pages/deployments.page";
import { createMockApiState, installMockApi } from "../support/mock-api";

test("shows release dropdown and optional specific-target list for selected group", async ({ page }) => {
  const state = createMockApiState();
  await installMockApi(page, state);

  const app = new AppShellPage(page);
  const deployments = new DeploymentsPage(page);

  await app.goto();
  await app.openDeployments();
  await deployments.openControls();

  await expect(deployments.releaseVersionDropdown).toBeVisible();
  await expect(deployments.releaseVersionDropdown).toContainText("2026.02.25.3");

  await deployments.selectTargetGroup("canary");
  await expect(deployments.specificTargetRows).toHaveCount(2);
  await expect(deployments.specificTargetCheckbox("target-01")).not.toBeChecked();
  await expect(deployments.specificTargetCheckbox("target-01")).toBeEnabled();
});

test("creates a run using selected release and specific targets within target-group filter", async ({ page }) => {
  const state = createMockApiState();
  await installMockApi(page, state);

  const app = new AppShellPage(page);
  const deployments = new DeploymentsPage(page);

  await app.goto();
  await app.openDeployments();
  await deployments.openControls();
  await deployments.selectTargetGroup("canary");

  await deployments.selectReleaseVersion("2026.02.20.1");
  await page.getByRole("button", { name: "Select all visible" }).click();
  await deployments.startRun();

  await expect.poll(() => state.createRunRequests.length).toBe(1);
  const request = state.createRunRequests[0];
  expect(request.release_id).toBe("rel-2026-02-20");
  expect(request.target_ids).toEqual(["target-01", "target-02"]);
  expect(request.target_tags).toEqual({ ring: "canary" });

  await expect(deployments.runRow("run-e2e-1")).toBeVisible();
});

test("enforces resume and retry button applicability by run status", async ({ page }) => {
  const state = createMockApiState();
  await installMockApi(page, state);

  const app = new AppShellPage(page);
  const deployments = new DeploymentsPage(page);

  await app.goto();
  await app.openDeployments();

  await expect(deployments.runRow("run-succeeded")).toBeVisible();
  await deployments.openRunActions("run-succeeded");
  await page.waitForTimeout(1600);
  await expect(deployments.runActionClone("run-succeeded")).toBeVisible();
  await expect(deployments.runActionResume("run-succeeded")).toHaveCount(0);
  await expect(deployments.runActionRetryFailed("run-succeeded")).toHaveCount(0);
  await page.keyboard.press("Escape");
  await expect(page.getByTestId("run-progress-run-succeeded-segment-succeeded")).toBeVisible();
  await expect(page.getByTestId("run-progress-run-succeeded-segment-failed")).toHaveCount(0);

  await expect(deployments.runRow("run-failed")).toBeVisible();
  await deployments.openRunActions("run-failed");
  await expect(deployments.runActionResume("run-failed")).toBeVisible();
  await expect(deployments.runActionRetryFailed("run-failed")).toBeVisible();
  await expect(page.getByTestId("run-progress-run-failed-segment-failed")).toBeVisible();
});

test("shows structured Azure stage errors in run detail", async ({ page }) => {
  const state = createMockApiState();
  await installMockApi(page, state);

  const app = new AppShellPage(page);
  const deployments = new DeploymentsPage(page);

  await app.goto();
  await app.openDeployments();
  await deployments.openRun("run-failed");

  await expect(page).toHaveURL(/\/deployments\/run-failed$/);
  await expect(page.getByTestId("stage-error-code-target-03-DEPLOYING")).toBeVisible();
  await expect(page.getByText("correlation-id: corr-e2e-deploying")).toHaveCount(0);

  await page.getByText("Azure error details").first().click();
  await expect(page.getByText(/MANIFEST_UNKNOWN/)).toBeVisible();
});

test("clone run opens controls and pre-populates release and targets", async ({ page }) => {
  const state = createMockApiState();
  await installMockApi(page, state);

  const app = new AppShellPage(page);
  const deployments = new DeploymentsPage(page);

  await app.goto();
  await app.openDeployments();

  await deployments.openRunActions("run-succeeded");
  await deployments.runActionClone("run-succeeded").click();

  await expect(deployments.deploymentControlsDrawer).toBeVisible();
  await expect(deployments.releaseVersionDropdown).toContainText("2026.02.25.3");
  await expect(deployments.targetGroupFilterDropdown).toContainText("Canary group");
  await expect(deployments.specificTargetCheckbox("target-01")).toBeChecked();
  await expect(deployments.specificTargetCheckbox("target-02")).toBeChecked();
});
