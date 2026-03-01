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
  await expect(deployments.releaseVersionDropdown).toHaveValue("rel-2026-02-25");

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

  await expect(deployments.runCard("run-e2e-1")).toBeVisible();
});

test("enforces resume and retry button applicability by run status", async ({ page }) => {
  const state = createMockApiState();
  await installMockApi(page, state);

  const app = new AppShellPage(page);
  const deployments = new DeploymentsPage(page);

  await app.goto();
  await app.openDeployments();

  await expect(deployments.runCard("run-succeeded")).toBeVisible();
  await expect(deployments.resumeButton("run-succeeded")).toBeDisabled();
  await expect(deployments.retryFailedButton("run-succeeded")).toBeDisabled();
  await expect(page.getByTestId("run-progress-run-succeeded-segment-succeeded")).toBeVisible();
  await expect(page.getByTestId("run-progress-run-succeeded-segment-failed")).toHaveCount(0);

  await expect(deployments.runCard("run-failed")).toBeVisible();
  await expect(deployments.resumeButton("run-failed")).toBeEnabled();
  await expect(deployments.retryFailedButton("run-failed")).toBeEnabled();
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
  await expect(page.getByText("correlation-id: corr-e2e-deploying")).toBeVisible();

  await page.getByText("Azure error details").first().click();
  await expect(page.getByText(/MANIFEST_UNKNOWN/)).toBeVisible();
});
