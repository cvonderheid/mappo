export type SidebarNavigationItem = {
  label: string;
  to: string;
};

export type SidebarNavigationGroup = {
  label: string;
  items: SidebarNavigationItem[];
};

export type BreadcrumbEntry = {
  label: string;
  to?: string;
};

export const SIDEBAR_NAVIGATION: SidebarNavigationGroup[] = [
  {
    label: "Project",
    items: [
      { label: "Config", to: "/projects" },
      { label: "Targets", to: "/targets" },
      { label: "Deployments", to: "/deployments" },
      { label: "Releases", to: "/releases" },
      { label: "Registration Events", to: "/onboarding" },
    ],
  },
  {
    label: "Admin",
    items: [
      { label: "Secret Inventory", to: "/secret-references" },
      { label: "Deployment Connections", to: "/deployment-connections" },
      { label: "Release Sources", to: "/release-sources" },
      { label: "Forwarder Logs", to: "/forwarder-logs" },
    ],
  },
  {
    label: "Demo",
    items: [{ label: "Demo", to: "/demo" }],
  },
];

export function isDeploymentPath(pathname: string): boolean {
  return pathname.startsWith("/deployments");
}

export function isTargetsPath(pathname: string): boolean {
  return pathname === "/targets";
}

export function isAdminPath(pathname: string): boolean {
  return (
    pathname === "/demo"
    || pathname === "/onboarding"
    || pathname === "/targets"
    || pathname === "/secret-references"
    || pathname === "/deployment-connections"
    || pathname === "/release-sources"
    || pathname === "/forwarder-logs"
  );
}

export function buildBreadcrumbEntries(pathname: string): BreadcrumbEntry[] {
  const items: BreadcrumbEntry[] = [];
  if (pathname.startsWith("/deployments/")) {
    const runId = decodeURIComponent(pathname.split("/")[2] ?? "");
    items.push(
      { label: "Project", to: "/deployments" },
      { label: "Deployments", to: "/deployments" },
      { label: runId || "Run Detail" }
    );
    return items;
  }
  if (pathname.startsWith("/deployments")) {
    items.push({ label: "Project", to: "/deployments" }, { label: "Deployments" });
    return items;
  }
  if (pathname.startsWith("/releases")) {
    items.push({ label: "Project", to: "/releases" }, { label: "Releases" });
    return items;
  }
  if (pathname.startsWith("/projects")) {
    items.push({ label: "Project" }, { label: "Config" });
    return items;
  }
  if (pathname.startsWith("/release-sources")) {
    items.push({ label: "Admin" }, { label: "Release Sources" });
    return items;
  }
  if (pathname.startsWith("/secret-references")) {
    items.push({ label: "Admin" }, { label: "Secret Inventory" });
    return items;
  }
  if (pathname.startsWith("/deployment-connections")) {
    items.push({ label: "Admin" }, { label: "Deployment Connections" });
    return items;
  }
  if (pathname.startsWith("/targets")) {
    items.push({ label: "Project", to: "/projects" }, { label: "Targets" });
    return items;
  }
  if (pathname.startsWith("/onboarding")) {
    items.push({ label: "Project", to: "/projects" }, { label: "Registration Events" });
    return items;
  }
  if (pathname.startsWith("/forwarder-logs")) {
    items.push({ label: "Admin" }, { label: "Forwarder Logs" });
    return items;
  }
  if (pathname.startsWith("/demo")) {
    items.push({ label: "Demo" });
  }
  return items;
}
