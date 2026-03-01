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

  specificTargetCheckbox(targetId: string): Locator {
    return this.page.locator(`[data-testid="specific-target-checkbox-${targetId}"]`);
  }

  runCard(runId: string): Locator {
    return this.page.locator(`[data-testid="run-card-${runId}"]`);
  }

  resumeButton(runId: string): Locator {
    return this.page.locator(`[data-testid="resume-${runId}"]`);
  }

  retryFailedButton(runId: string): Locator {
    return this.page.locator(`[data-testid="retry-failed-${runId}"]`);
  }

  selectRunButton(runId: string): Locator {
    return this.page.locator(`[data-testid="select-run-${runId}"]`);
  }

  async selectReleaseVersion(versionLabel: string): Promise<void> {
    await this.releaseVersionDropdown.selectOption({ label: versionLabel });
  }

  async openControls(): Promise<void> {
    await this.openControlsButton.click();
  }

  async selectTargetGroup(group: "all" | "canary" | "prod"): Promise<void> {
    await this.targetGroupFilterDropdown.selectOption(group);
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
}
