import { Fragment, Suspense, type ReactNode } from "react";
import { NavLink } from "react-router-dom";

import AppRouteLoadingFallback from "@/features/app/AppRouteLoadingFallback";
import { ProjectSwitcherMenu } from "@/features/app/lazyRoutes";
import { SIDEBAR_NAVIGATION, type BreadcrumbEntry } from "@/features/app/navigation";
import { Button } from "@/components/ui/button";
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from "@/components/ui/breadcrumb";
import {
  Sidebar,
  SidebarContent,
  SidebarGroup,
  SidebarGroupLabel,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
} from "@/components/ui/sidebar";
import type { ProjectDefinition } from "@/lib/types";

type AppShellChromeProps = {
  projectThemeKey: string;
  pathname: string;
  projects: ProjectDefinition[];
  selectedProjectId: string;
  targetsCount: number;
  activeRunCount: number;
  projectReleaseCount: number;
  breadcrumbEntries: BreadcrumbEntry[];
  hasReleaseBanner: boolean;
  latestReleaseId: string;
  latestReleaseVersion: string;
  outdatedTargetCount: number;
  unknownTargetCount: number;
  onSelectProject: (projectId: string) => void;
  onOpenProjectSettings: () => void;
  onOpenCreateProject: () => void;
  onOpenLatestRelease: (releaseId: string) => void;
  children: ReactNode;
};

export default function AppShellChrome({
  projectThemeKey,
  pathname,
  projects,
  selectedProjectId,
  targetsCount,
  activeRunCount,
  projectReleaseCount,
  breadcrumbEntries,
  hasReleaseBanner,
  latestReleaseId,
  latestReleaseVersion,
  outdatedTargetCount,
  unknownTargetCount,
  onSelectProject,
  onOpenProjectSettings,
  onOpenCreateProject,
  onOpenLatestRelease,
  children,
}: AppShellChromeProps) {
  return (
    <main className="mx-auto flex w-[min(1480px,96vw)] flex-col gap-4 py-6" data-project-theme={projectThemeKey}>
      <div className="glass-card animate-fade-up [animation-fill-mode:forwards]">
        <div className="grid gap-4 p-3 xl:grid-cols-[340px_minmax(0,1fr)_480px] xl:items-center">
          <div className="min-w-0 max-w-[340px]">
            <h1 className="text-lg font-semibold uppercase tracking-[0.08em] md:text-xl">
              MAPPO Control Plane
            </h1>
            <div className="mt-2">
              <Suspense fallback={<AppRouteLoadingFallback />}>
                <ProjectSwitcherMenu
                  projects={projects}
                  selectedProjectId={selectedProjectId}
                  onSelectProject={onSelectProject}
                  onOpenProjectSettings={onOpenProjectSettings}
                  onOpenCreateProject={onOpenCreateProject}
                />
              </Suspense>
            </div>
          </div>
          <div className="min-w-0 xl:px-2">
            <Breadcrumb>
              <BreadcrumbList>
                {breadcrumbEntries.map((entry, index) => {
                  const isLast = index === breadcrumbEntries.length - 1;
                  return (
                    <Fragment key={`${entry.label}-${index}`}>
                      {index > 0 ? <BreadcrumbSeparator /> : null}
                      <BreadcrumbItem>
                        {isLast || !entry.to ? (
                          <BreadcrumbPage>{entry.label}</BreadcrumbPage>
                        ) : (
                          <BreadcrumbLink asChild>
                            <NavLink to={entry.to}>{entry.label}</NavLink>
                          </BreadcrumbLink>
                        )}
                      </BreadcrumbItem>
                    </Fragment>
                  );
                })}
              </BreadcrumbList>
            </Breadcrumb>
          </div>
          <div className="grid grid-cols-3 gap-2 xl:w-[480px]">
            <Kpi label="Total Targets" value={String(targetsCount)} />
            <Kpi label="Active Runs" value={String(activeRunCount)} />
            <Kpi label="Releases" value={String(projectReleaseCount)} />
          </div>
        </div>
      </div>

      {projects.length === 0 ? (
        <div className="rounded-lg border border-primary/40 bg-primary/10 p-4">
          <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
            <div className="space-y-1">
              <p className="text-sm font-semibold">No projects configured yet.</p>
              <p className="text-sm text-muted-foreground">
                Start by creating a project profile, then configure targets and release sources.
              </p>
            </div>
            <Button type="button" onClick={onOpenCreateProject}>
              Create First Project
            </Button>
          </div>
        </div>
      ) : null}

      {hasReleaseBanner && latestReleaseId ? (
        <div className="rounded-lg border border-primary/40 bg-primary/10 p-4">
          <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
            <div className="space-y-1">
              <p className="text-sm font-semibold">New release {latestReleaseVersion} is available.</p>
              <p className="text-sm text-muted-foreground">
                {outdatedTargetCount} target{outdatedTargetCount === 1 ? "" : "s"} are behind the latest release.
                {unknownTargetCount > 0
                  ? ` ${unknownTargetCount} target${unknownTargetCount === 1 ? "" : "s"} have no known version yet.`
                  : ""}
              </p>
            </div>
            <Button type="button" onClick={() => onOpenLatestRelease(latestReleaseId)}>
              Deploy {latestReleaseVersion}
            </Button>
          </div>
        </div>
      ) : null}

      <div className="grid gap-4 lg:grid-cols-[260px_minmax(0,1fr)]">
        <Sidebar className="h-fit">
          <SidebarContent>
            {SIDEBAR_NAVIGATION.map((group) => (
              <SidebarGroup key={group.label}>
                <SidebarGroupLabel>{group.label}</SidebarGroupLabel>
                <SidebarMenu>
                  {group.items.map((item) => (
                    <SidebarMenuItem key={item.to}>
                      <SidebarMenuButton asChild isActive={pathname.startsWith(item.to)}>
                        <NavLink to={item.to}>{item.label}</NavLink>
                      </SidebarMenuButton>
                    </SidebarMenuItem>
                  ))}
                </SidebarMenu>
              </SidebarGroup>
            ))}
          </SidebarContent>
        </Sidebar>

        <section className="min-w-0 space-y-4">{children}</section>
      </div>
    </main>
  );
}

function Kpi({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-md border border-border/70 bg-card/80 px-3 py-2">
      <p className="text-[11px] uppercase tracking-[0.08em] text-muted-foreground">{label}</p>
      <p className="text-xl font-semibold">{value}</p>
    </div>
  );
}
