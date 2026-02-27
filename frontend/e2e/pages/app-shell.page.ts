import type { Page } from "@playwright/test";

export class AppShellPage {
  constructor(private readonly page: Page) {}

  async goto(): Promise<void> {
    await this.page.goto("/");
  }

  async openFleet(): Promise<void> {
    await this.page.getByRole("button", { name: "Fleet" }).click();
  }

  async openDeployments(): Promise<void> {
    await this.page.getByRole("button", { name: "Deployments" }).click();
  }

  async selectTargetGroup(group: "all" | "canary" | "prod"): Promise<void> {
    await this.page.locator("#target-group-filter").selectOption(group);
  }
}
