import type { Locator, Page } from "@playwright/test";

export class DeploymentsPage {
  constructor(private readonly page: Page) {}

  get releaseVersionDropdown(): Locator {
    return this.page.locator("#release-version");
  }

  get filteredMemberRows(): Locator {
    return this.page.locator('[data-testid="filtered-member-row"]');
  }

  filteredMemberCheckbox(targetId: string): Locator {
    return this.page.locator(`[data-testid="filtered-member-checkbox-${targetId}"]`);
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

  async selectTargetScope(scope: "filtered" | "specific"): Promise<void> {
    await this.page.locator("#target-scope").selectOption(scope);
  }

  async setSpecificTargetChecked(targetId: string, checked: boolean): Promise<void> {
    const checkbox = this.specificTargetCheckbox(targetId);
    if ((await checkbox.isChecked()) !== checked) {
      await checkbox.click();
    }
  }

  async startRun(): Promise<void> {
    await this.page.getByRole("button", { name: "Start Run" }).click();
  }

  async openRun(runId: string): Promise<void> {
    await this.selectRunButton(runId).click();
  }
}
