import {
  Menubar,
  MenubarContent,
  MenubarItem,
  MenubarLabel,
  MenubarMenu,
  MenubarSeparator,
  MenubarTrigger,
} from "@/components/ui/menubar";
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
    <Menubar className="h-auto w-auto justify-start border-0 bg-transparent p-0 shadow-none">
      <MenubarMenu>
        <MenubarTrigger
          data-testid="project-switcher-trigger"
          className="h-10 min-w-[280px] max-w-[340px] justify-between gap-3 rounded-md border border-input bg-background/90 px-3 text-left shadow-none"
        >
          <div className="min-w-0">
            <p className="truncate text-sm font-semibold">
              {selectedProject?.name ?? "Select project"}
            </p>
          </div>
          <span className="text-xs text-muted-foreground">▼</span>
        </MenubarTrigger>
        <MenubarContent align="start" className="w-[380px] p-1">
          <MenubarItem
          data-testid="project-switcher-new-project"
          onSelect={onOpenCreateProject}
        >
          New Project...
          </MenubarItem>
          <MenubarItem
          data-testid="project-switcher-open-settings"
          onSelect={onOpenProjectSettings}
        >
          Project Settings...
          </MenubarItem>
          <MenubarSeparator />
          <MenubarLabel>Open Projects</MenubarLabel>
          {projects.length === 0 ? (
            <p className="px-2 py-2 text-xs text-muted-foreground">
              No projects configured.
            </p>
          ) : (
            projects.map((project) => {
              const projectId = project.id ?? "";
              const isSelected = projectId === selectedProjectId;
              return (
                <MenubarItem
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
                </MenubarItem>
              );
            })
          )}
        </MenubarContent>
      </MenubarMenu>
    </Menubar>
  );
}
