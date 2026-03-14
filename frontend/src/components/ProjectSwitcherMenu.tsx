import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import type { ProjectDefinition } from "@/lib/types";
import { cn } from "@/lib/utils";

type ProjectSwitcherMenuProps = {
  projects: ProjectDefinition[];
  selectedProjectId: string;
  onSelectProject: (projectId: string) => void;
  onOpenProjectSettings: () => void;
  onOpenCreateProject: () => void;
};

export default function ProjectSwitcherMenu({
  projects,
  selectedProjectId,
  onSelectProject,
  onOpenProjectSettings,
  onOpenCreateProject,
}: ProjectSwitcherMenuProps) {
  const selectedProject =
    projects.find((project) => project.id === selectedProjectId) ?? null;

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          type="button"
          variant="outline"
          data-testid="project-switcher-trigger"
          className="h-10 w-full justify-between gap-3 bg-background/90 px-3 text-left"
        >
          <div className="min-w-0">
            <p className="truncate text-sm font-semibold">
              {selectedProject?.name ?? "Select project"}
            </p>
            <p className="truncate font-mono text-[11px] text-muted-foreground">
              {selectedProject?.id ?? "No project selected"}
            </p>
          </div>
          <span className="text-xs text-muted-foreground">v</span>
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-[360px] p-1">
        <DropdownMenuItem
          data-testid="project-switcher-new-project"
          onSelect={onOpenCreateProject}
        >
          New Project...
        </DropdownMenuItem>
        <DropdownMenuItem
          data-testid="project-switcher-open-settings"
          onSelect={onOpenProjectSettings}
        >
          Project Settings...
        </DropdownMenuItem>
        <DropdownMenuSeparator />
        <div className="px-2 py-1 text-[11px] uppercase tracking-[0.08em] text-muted-foreground">
          Open Projects
        </div>
        {projects.length === 0 ? (
          <p className="px-2 py-2 text-xs text-muted-foreground">
            No projects configured.
          </p>
        ) : (
          projects.map((project) => {
            const projectId = project.id ?? "";
            const isSelected = projectId === selectedProjectId;
            return (
              <DropdownMenuItem
                key={projectId}
                data-testid={`project-switcher-select-${projectId}`}
                className={cn("flex flex-col items-start gap-0.5")}
                onSelect={() => onSelectProject(projectId)}
              >
                <div className="flex w-full items-center justify-between gap-2">
                  <span className="truncate text-sm">
                    {project.name ?? projectId}
                  </span>
                  {isSelected ? (
                    <span className="text-[11px] font-semibold text-primary">
                      Selected
                    </span>
                  ) : null}
                </div>
                <span className="w-full truncate font-mono text-[11px] text-muted-foreground">
                  {projectId}
                </span>
              </DropdownMenuItem>
            );
          })
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
