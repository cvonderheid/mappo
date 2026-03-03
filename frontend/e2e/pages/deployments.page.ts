import type { Locator, Page } from "@playwright/test";

export class DeploymentsPage {
  constructor(private readonly page: Page) {}

  get openControlsButton(): Locator {
    return this.page.getByTestId("open-deployment-controls");
  }

  get targetGroupFilterDropdown(): Locator {
    return this.page.locator("#target-group-filter");
  }

  get releaseVersionDropdown(): Locator {
    return this.page.locator("#release-version");
  }

  get specificTargetRows(): Locator {
    return this.page.locator('[data-testid^="specific-target-row-"]');
  }

  get deploymentControlsDrawer(): Locator {
    return this.page.getByRole("heading", { name: "Deployment Controls" });
  }

  specificTargetCheckbox(targetId: string): Locator {
    return this.page.locator(`[data-testid="specific-target-checkbox-${targetId}"]`);
  }

  runRow(runId: string): Locator {
    return this.page.locator(`[data-testid="run-row-${runId}"]`);
  }

  runActionsTrigger(runId: string): Locator {
    return this.page.locator(`[data-testid="run-actions-trigger-${runId}"]`);
  }

  runActionView(runId: string): Locator {
    return this.page.locator(`[data-testid="run-action-view-${runId}"]`);
  }

  runActionClone(runId: string): Locator {
    return this.page.locator(`[data-testid="run-action-clone-${runId}"]`);
  }

  runActionResume(runId: string): Locator {
    return this.page.locator(`[data-testid="run-action-resume-${runId}"]`);
  }

  runActionRetryFailed(runId: string): Locator {
    return this.page.locator(`[data-testid="run-action-retry-failed-${runId}"]`);
  }

  selectRunButton(runId: string): Locator {
    return this.page.locator(`[data-testid="select-run-${runId}"]`);
  }

  async selectReleaseVersion(versionLabel: string): Promise<void> {
    await this.selectFromShadcnSelect(this.releaseVersionDropdown, versionLabel);
  }

  async openControls(): Promise<void> {
    await this.openControlsButton.click();
  }

  async selectTargetGroup(group: "all" | "canary" | "prod"): Promise<void> {
    const labelByGroup = {
      all: "All groups",
      canary: "Canary group",
      prod: "Production group",
    } as const;
    await this.selectFromShadcnSelect(this.targetGroupFilterDropdown, labelByGroup[group]);
  }

  async setSpecificTargetChecked(targetId: string, checked: boolean): Promise<void> {
    const checkbox = this.specificTargetCheckbox(targetId);
    if ((await checkbox.isChecked()) !== checked) {
      if (checked) {
        await checkbox.check({ force: true });
      } else {
        await checkbox.uncheck({ force: true });
      }
    }
  }

  async startRun(): Promise<void> {
    await this.page.getByRole("button", { name: "Start Run" }).click({ force: true });
  }

  async openRun(runId: string): Promise<void> {
    await this.selectRunButton(runId).click();
  }

  async openRunActions(runId: string): Promise<void> {
    await this.runActionsTrigger(runId).click();
  }

  private async selectFromShadcnSelect(trigger: Locator, optionLabel: string): Promise<void> {
    await trigger.click();
    await this.page.getByRole("option", { name: optionLabel, exact: true }).click();
  }
}
