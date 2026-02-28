import { expect, test } from "@playwright/test";

import { AppShellPage } from "../pages/app-shell.page";
import { DeploymentsPage } from "../pages/deployments.page";
import { createMockApiState, installMockApi } from "../support/mock-api";

test("shows release dropdown and target-group members in filtered scope", async ({ page }) => {
  const state = createMockApiState();
  await installMockApi(page, state);

  const app = new AppShellPage(page);
  const deployments = new DeploymentsPage(page);

  await app.goto();
  await app.openDeployments();

  await expect(deployments.releaseVersionDropdown).toBeVisible();
  await expect(deployments.releaseVersionDropdown).toHaveValue("rel-2026-02-25");

  await app.selectTargetGroup("canary");
  await expect(deployments.filteredMemberRows).toHaveCount(2);
  await expect(deployments.filteredMemberCheckbox("target-01")).toBeChecked();
  await expect(deployments.filteredMemberCheckbox("target-01")).toBeDisabled();
});

test("creates a run using selected release and specific targets", async ({ page }) => {
  const state = createMockApiState();
  await installMockApi(page, state);

  const app = new AppShellPage(page);
  const deployments = new DeploymentsPage(page);

  await app.goto();
  await app.openDeployments();
  await app.selectTargetGroup("canary");

  await deployments.selectReleaseVersion("2026.02.20.1");
  await deployments.selectTargetScope("specific");
  await deployments.setSpecificTargetChecked("target-01", true);
  await deployments.setSpecificTargetChecked("target-02", true);
  await deployments.startRun();

  await expect.poll(() => state.createRunRequests.length).toBe(1);
  const request = state.createRunRequests[0];
  expect(request.release_id).toBe("rel-2026-02-20");
  expect(request.target_ids).toEqual(["target-01", "target-02"]);

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

  await expect(page.getByTestId("stage-error-code-target-03-DEPLOYING")).toBeVisible();
  await expect(page.getByRole("link", { name: "Open in Azure Portal" }).first()).toBeVisible();

  await page.getByText("Azure error details").first().click();
  await expect(page.getByText(/MANIFEST_UNKNOWN/)).toBeVisible();
});
