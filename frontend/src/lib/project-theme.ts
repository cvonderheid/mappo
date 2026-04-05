export type ProjectThemeKey = "harbor-teal" | "vectr-signal" | "scalr-slate";

export type ProjectTheme = {
  key: ProjectThemeKey;
  name: string;
  description: string;
};

export const PROJECT_THEMES: Record<ProjectThemeKey, ProjectTheme> = {
  "harbor-teal": {
    key: "harbor-teal",
    name: "Harbor Teal",
    description: "MAPPO default teal/navy control-plane theme.",
  },
  "vectr-signal": {
    key: "vectr-signal",
    name: "Vectr Signal",
    description: "High-contrast graphite and ultraviolet accents inspired by vectr.io.",
  },
  "scalr-slate": {
    key: "scalr-slate",
    name: "Scalr Slate",
    description: "Warm slate and amber operations theme inspired by scalr.sra.io.",
  },
};

const THEME_BY_PROJECT_ID: Record<string, ProjectThemeKey> = {
  "azure-managed-app-deployment-stack": "harbor-teal",
  "azure-managed-app-template-spec": "vectr-signal",
  "azure-appservice-ado-pipeline": "scalr-slate",
};

export const DEFAULT_THEME_KEY: ProjectThemeKey = "harbor-teal";

export function projectThemeForProject(
  projectId: string | null | undefined,
  explicitThemeKey?: string | null | undefined
): ProjectTheme {
  const normalizedThemeKey = (explicitThemeKey ?? "").trim() as ProjectThemeKey;
  const normalizedProjectId = (projectId ?? "").trim();
  const key =
    (normalizedThemeKey && PROJECT_THEMES[normalizedThemeKey] ? normalizedThemeKey : undefined) ??
    THEME_BY_PROJECT_ID[normalizedProjectId] ??
    DEFAULT_THEME_KEY;
  return PROJECT_THEMES[key];
}
