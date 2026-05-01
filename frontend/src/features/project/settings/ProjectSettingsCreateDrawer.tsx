import type { FormEvent } from "react";

import {
  Drawer,
  DrawerContent,
  DrawerDescription,
  DrawerFooter,
  DrawerHeader,
  DrawerTitle,
} from "@/components/ui/drawer";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { DEFAULT_THEME_KEY, PROJECT_THEMES, type ProjectThemeKey } from "@/lib/project-theme";
import type { ProjectDraft } from "@/features/project/settings/shared";
import { PROJECT_THEME_OPTIONS } from "@/features/project/settings/shared";

type ProjectSettingsCreateDrawerProps = {
  open: boolean;
  createDraft: ProjectDraft;
  canCreateProject: boolean;
  createSubmitting: boolean;
  onOpenChange: (open: boolean) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onUpdateName: (name: string) => void;
  onUpdateTheme: (themeKey: ProjectThemeKey) => void;
};

export default function ProjectSettingsCreateDrawer({
  open,
  createDraft,
  canCreateProject,
  createSubmitting,
  onOpenChange,
  onSubmit,
  onUpdateName,
  onUpdateTheme,
}: ProjectSettingsCreateDrawerProps) {
  return (
    <Drawer direction="top" open={open} onOpenChange={onOpenChange}>
      <DrawerContent className="glass-card">
        <DrawerHeader>
          <DrawerTitle>New Project</DrawerTitle>
          <DrawerDescription>
            Create the project shell, then finish setup from the project flow on the Config page.
          </DrawerDescription>
        </DrawerHeader>
        <form onSubmit={onSubmit}>
          <div className="grid grid-cols-1 gap-3 px-4 pb-4 md:grid-cols-2">
            <div className="space-y-1">
              <Label htmlFor="create-project-name">Project name</Label>
              <Input
                id="create-project-name"
                value={createDraft.name}
                onChange={(event) => onUpdateName(event.target.value)}
                placeholder="Azure App Service ADO Pipeline"
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor="create-project-theme">Project theme</Label>
              <Select value={createDraft.themeKey} onValueChange={(value) => onUpdateTheme(value as ProjectThemeKey)}>
                <SelectTrigger id="create-project-theme">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {PROJECT_THEME_OPTIONS.map((theme) => (
                    <SelectItem key={theme.key} value={theme.key}>
                      {theme.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <p className="text-xs text-muted-foreground">
                {PROJECT_THEMES[createDraft.themeKey]?.description ?? PROJECT_THEMES[DEFAULT_THEME_KEY].description}
              </p>
            </div>
            <div className="rounded-md border border-border/70 bg-background/40 px-3 py-2 text-xs text-muted-foreground md:col-span-2">
              Release source, deployment, targets, and health checks are configured next from the project flow.
            </div>
          </div>
          <DrawerFooter>
            <Button type="submit" disabled={!canCreateProject || createSubmitting}>
              {createSubmitting ? "Creating..." : "Create and Configure"}
            </Button>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={createSubmitting}>
              Cancel
            </Button>
          </DrawerFooter>
        </form>
      </DrawerContent>
    </Drawer>
  );
}
