import type { Page } from "@playwright/test";

export class AppShellPage {
  constructor(private readonly page: Page) {}

  async goto(): Promise<void> {
    await this.page.goto("/");
  }

  async openTargets(): Promise<void> {
    await this.page.getByRole("link", { name: "Targets" }).click();
  }

  async openDeployments(): Promise<void> {
    await this.page.getByRole("link", { name: "Deployments" }).click();
  }

  async selectTargetGroup(group: "all" | "canary" | "prod"): Promise<void> {
    const labelByGroup = {
      all: "All groups",
      canary: "Canary group",
      prod: "Production group",
    } as const;
    await this.page.locator("#target-group-filter").click();
    await this.page
      .getByRole("option", { name: labelByGroup[group], exact: true })
      .click();
  }
}
