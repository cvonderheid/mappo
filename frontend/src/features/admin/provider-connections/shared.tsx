import { LuActivity, LuBoxes, LuWorkflow } from "react-icons/lu";
import { VscAzureDevops } from "react-icons/vsc";

import type { AdminIntegrationFlowNode } from "@/features/admin/AdminIntegrationFlowDiagram";
import type {
  ProviderConnectionAdoProject,
  ProviderConnection,
  ProviderConnectionProvider,
  SecretReference,
} from "@/lib/types";

export type ProviderConnectionDraft = {
  id: string;
  name: string;
  provider: ProviderConnectionProvider;
  enabled: boolean;
  organizationUrl: string;
  personalAccessTokenMode: "backend_default" | "environment_variable" | "key_vault_secret" | "secret_reference";
  personalAccessTokenEnvVar: string;
  personalAccessTokenKeyVaultSecret: string;
  personalAccessTokenSecretReferenceId: string;
};

export type LinkedProject = NonNullable<ProviderConnection["linkedProjects"]>[number];

export type DraftVerificationResult = {
  effectiveDraft: ProviderConnectionDraft;
  projects: ProviderConnectionAdoProject[];
  normalizedOrganizationUrl: string;
  verifiedSignature: string;
};

export const DEFAULT_AZURE_DEVOPS_PAT_REF = "mappo.azure-devops.personal-access-token";

export const AZURE_DEVOPS_SCOPE_REQUIRED_MESSAGE =
  "Paste any Azure DevOps project or repository URL from the Azure DevOps account MAPPO should browse. The access token proves MAPPO can authenticate; the URL tells MAPPO which Azure DevOps account to browse so it can load the projects operators choose later.";

export function normalize(value: string): string {
  return value.trim();
}

export function normalizeDeploymentConnectionError(message: string): string {
  const trimmed = normalize(
    message
      .replace(/^discoverProviderConnectionAdoProjects failed \(\d+\):\s*/i, "")
      .replace(/^verifyProviderConnectionAdoProjects failed \(\d+\):\s*/i, "")
      .replace(/^patchProviderConnection failed \(\d+\):\s*/i, "")
      .replace(/^createProviderConnection failed \(\d+\):\s*/i, "")
  );
  const normalized = trimmed.toLowerCase();
  if (
    normalized.includes("project or repo url is required")
    || normalized.includes("azure devops url is required")
    || normalized.includes("account url is required")
  ) {
    return AZURE_DEVOPS_SCOPE_REQUIRED_MESSAGE;
  }
  if (normalized.includes("pat could not be resolved")) {
    return "MAPPO could not resolve the Azure DevOps API credential for this deployment connection. Use the MAPPO backend secret, a named secret reference, a backend environment variable, or an Azure Key Vault secret that actually exists, then verify the connection again.";
  }
  if (normalized.includes("no accessible azure devops projects were returned")) {
    return "MAPPO authenticated to Azure DevOps, but that access token could not see any Azure DevOps projects in the selected account. Confirm the URL is correct and that the token can read at least one project.";
  }
  return trimmed;
}

export function deriveAzureDevOpsAccountUrl(value: string): string {
  const normalized = normalize(value);
  if (normalized === "") {
    return "";
  }
  try {
    const parsed = new URL(normalized);
    const host = parsed.hostname.toLowerCase();
    if (host === "dev.azure.com") {
      const [organization] = parsed.pathname.split("/").filter(Boolean);
      return organization ? `${parsed.protocol}//${parsed.host}/${organization}` : "";
    }
    if (host.endsWith(".visualstudio.com")) {
      return `${parsed.protocol}//${parsed.host}`;
    }
    return "";
  } catch {
    return "";
  }
}

export function resolveConnectionAccountUrl(connection: ProviderConnection | null | undefined): string {
  const persisted = normalize(connection?.organizationUrl ?? "");
  if (persisted !== "") {
    return deriveAzureDevOpsAccountUrl(persisted) || persisted;
  }
  for (const project of connection?.discoveredProjects ?? []) {
    const derived = deriveAzureDevOpsAccountUrl(project.webUrl ?? "");
    if (derived !== "") {
      return derived;
    }
  }
  return "";
}

export function emptyDraft(): ProviderConnectionDraft {
  return {
    id: "",
    name: "",
    provider: "azure_devops",
    enabled: true,
    organizationUrl: "",
    personalAccessTokenMode: "backend_default",
    personalAccessTokenEnvVar: "",
    personalAccessTokenKeyVaultSecret: "",
    personalAccessTokenSecretReferenceId: "",
  };
}

export function parsePersonalAccessTokenRef(
  value: string | undefined
): Pick<
  ProviderConnectionDraft,
  "personalAccessTokenMode" | "personalAccessTokenEnvVar" | "personalAccessTokenKeyVaultSecret" | "personalAccessTokenSecretReferenceId"
> {
  const normalized = normalize(value ?? "");
  if (normalized.startsWith("secret:")) {
    return {
      personalAccessTokenMode: "secret_reference",
      personalAccessTokenEnvVar: "",
      personalAccessTokenKeyVaultSecret: "",
      personalAccessTokenSecretReferenceId: normalize(normalized.slice("secret:".length)),
    };
  }
  if (normalized.startsWith("env:")) {
    return {
      personalAccessTokenMode: "environment_variable",
      personalAccessTokenEnvVar: normalize(normalized.slice("env:".length)),
      personalAccessTokenKeyVaultSecret: "",
      personalAccessTokenSecretReferenceId: "",
    };
  }
  if (normalized.startsWith("kv:")) {
    return {
      personalAccessTokenMode: "key_vault_secret",
      personalAccessTokenEnvVar: "",
      personalAccessTokenKeyVaultSecret: normalize(normalized.slice("kv:".length)),
      personalAccessTokenSecretReferenceId: "",
    };
  }
  return {
    personalAccessTokenMode: "backend_default",
    personalAccessTokenEnvVar: "",
    personalAccessTokenKeyVaultSecret: "",
    personalAccessTokenSecretReferenceId: "",
  };
}

export function toDraft(connection: ProviderConnection): ProviderConnectionDraft {
  const tokenReference = parsePersonalAccessTokenRef(connection.personalAccessTokenRef);
  return {
    id: connection.id ?? "",
    name: connection.name ?? "",
    provider: (connection.provider ?? "azure_devops") as ProviderConnectionProvider,
    enabled: connection.enabled ?? true,
    organizationUrl: resolveConnectionAccountUrl(connection) || (connection.organizationUrl ?? ""),
    ...tokenReference,
  };
}

export function maskedSecretRef(value: string): string {
  const normalized = normalize(value);
  if (normalized.startsWith("literal:")) {
    return "literal:(hidden)";
  }
  return normalized;
}

export function isUsableAzureDevOpsConnection(connection: ProviderConnection | null | undefined): boolean {
  return (
    (connection?.provider ?? "").toLowerCase() === "azure_devops"
    && (connection?.enabled ?? true)
    && resolveConnectionAccountUrl(connection) !== ""
    && (connection?.discoveredProjects?.length ?? 0) > 0
  );
}

export function buildPersonalAccessTokenRef(draft: ProviderConnectionDraft): string {
  if (draft.personalAccessTokenMode === "secret_reference") {
    const secretReferenceId = normalize(draft.personalAccessTokenSecretReferenceId);
    return secretReferenceId === "" ? "" : `secret:${secretReferenceId}`;
  }
  if (draft.personalAccessTokenMode === "environment_variable") {
    const envVarName = normalize(draft.personalAccessTokenEnvVar);
    return envVarName === "" ? "" : `env:${envVarName}`;
  }
  if (draft.personalAccessTokenMode === "key_vault_secret") {
    const secretName = normalize(draft.personalAccessTokenKeyVaultSecret);
    return secretName === "" ? "" : `kv:${secretName}`;
  }
  return DEFAULT_AZURE_DEVOPS_PAT_REF;
}

export function isAzureDevOpsScopeMissing(draft: ProviderConnectionDraft): boolean {
  return draft.provider === "azure_devops" && draft.enabled && normalize(draft.organizationUrl) === "";
}

export function buildDraftSignature(draft: ProviderConnectionDraft): string {
  return [
    normalize(draft.provider),
    draft.enabled ? "enabled" : "disabled",
    normalize(draft.organizationUrl),
    normalize(buildPersonalAccessTokenRef(draft)),
  ].join("|");
}

export function deriveDraftAccountUrl(draft: ProviderConnectionDraft): string {
  return deriveAzureDevOpsAccountUrl(normalize(draft.organizationUrl));
}

export function describePersonalAccessTokenSource(
  connection: ProviderConnection,
  secretReferenceLookup: Record<string, SecretReference>
): string {
  const normalized = normalize(connection.personalAccessTokenRef ?? DEFAULT_AZURE_DEVOPS_PAT_REF);
  if (normalized === DEFAULT_AZURE_DEVOPS_PAT_REF) {
    return "MAPPO runtime secret";
  }
  if (normalized.startsWith("secret:")) {
    const secretReferenceId = normalize(normalized.slice("secret:".length));
    return `Secret reference (${secretReferenceLookup[secretReferenceId]?.name || secretReferenceId})`;
  }
  if (normalized.startsWith("env:")) {
    return `Environment variable (${normalize(normalized.slice("env:".length))})`;
  }
  if (normalized.startsWith("kv:")) {
    return `Azure Key Vault secret (${normalize(normalized.slice("kv:".length))})`;
  }
  return maskedSecretRef(normalized);
}

export function describeDiscoveredProjectCount(count: number): string {
  if (count <= 0) {
    return "No Azure DevOps projects verified yet";
  }
  return `${count} Azure DevOps project${count === 1 ? "" : "s"} available`;
}

export function providerConnectionLabel(provider: ProviderConnectionProvider | string | null | undefined): string {
  return normalize(`${provider ?? "azure_devops"}`) === "azure_devops" ? "Azure DevOps" : `${provider ?? "Provider"}`;
}

export function providerConnectionModeLabel(provider: ProviderConnectionProvider | string | null | undefined): string {
  return normalize(`${provider ?? "azure_devops"}`) === "azure_devops"
    ? "Deployment API credential"
    : "External deployment access";
}

export function providerConnectionIcon(provider: ProviderConnectionProvider | string | null | undefined, className: string) {
  if (normalize(`${provider ?? "azure_devops"}`) === "azure_devops") {
    return <VscAzureDevops className={className} />;
  }
  return <LuWorkflow className={className} />;
}

export function summarizeLinkedProjects(linkedProjects: LinkedProject[]): string {
  if (linkedProjects.length === 0) {
    return "No linked projects";
  }
  const labels = linkedProjects.map((linked) =>
    linked.projectDisplayName || linked.projectName || "Project"
  );
  const visible = labels.slice(0, 3).join(", ");
  const overflow = labels.length - 3;
  return overflow > 0 ? `${visible}, +${overflow} more` : visible;
}

export function summarizeDiscoveredProjects(projects: ProviderConnectionAdoProject[]): string {
  if (projects.length === 0) {
    return "No projects loaded";
  }
  const labels = projects.map((project) => project.name || "Azure DevOps project");
  const visible = labels.slice(0, 3).join(", ");
  const overflow = labels.length - 3;
  return overflow > 0 ? `${visible}, +${overflow} more` : visible;
}

export function deploymentConnectionDisplayName(connection: ProviderConnection): string {
  return connection.name?.trim() || "Unnamed deployment connection";
}

function verificationTitle({
  isDiscovering,
  isVerified,
  error,
}: {
  isDiscovering: boolean;
  isVerified: boolean;
  error: string;
}): string {
  if (isDiscovering) {
    return "Verification running";
  }
  if (isVerified) {
    return "Access verified";
  }
  if (error) {
    return "Verification failed";
  }
  return "Needs verification";
}

export function buildDeploymentConnectionFlowNodes({
  connection,
  resolvedAccountUrl,
  credentialSource,
  isDiscovering,
  isVerified,
  discoveryError,
  discoveredProjects,
  linkedProjects,
  selectedProjectLinked,
}: {
  connection: ProviderConnection;
  resolvedAccountUrl: string;
  credentialSource: string;
  isDiscovering: boolean;
  isVerified: boolean;
  discoveryError: string;
  discoveredProjects: ProviderConnectionAdoProject[];
  linkedProjects: LinkedProject[];
  selectedProjectLinked: boolean;
}): AdminIntegrationFlowNode[] {
  const provider = connection.provider ?? "azure_devops";
  const providerLabel = providerConnectionLabel(provider);
  const displayName = deploymentConnectionDisplayName(connection);
  return [
    {
      step: "00",
      icon: providerConnectionIcon(provider, "h-5 w-5"),
      eyebrow: "External account",
      title: resolvedAccountUrl || "Account scope not set",
      tone: resolvedAccountUrl ? "default" : "warning",
      details: [
        { label: "Provider", value: providerLabel },
        { label: "Purpose", value: "Account MAPPO can browse" },
      ],
    },
    {
      step: "01",
      icon: providerConnectionIcon(provider, "h-5 w-5"),
      eyebrow: "Deployment system",
      title: providerLabel,
      tone: connection.enabled === false ? "warning" : "default",
      details: [
        { label: "Connection", value: displayName },
        { label: "Status", value: connection.enabled ? "Enabled" : "Disabled" },
      ],
    },
    {
      step: "02",
      icon: <LuWorkflow className="h-5 w-5" />,
      eyebrow: "API credential",
      title: "Server-side credential",
      details: [
        { label: "Direction", value: "MAPPO outbound API access" },
        { label: "Source", value: credentialSource },
      ],
    },
    {
      step: "03",
      icon: <LuActivity className="h-5 w-5" />,
      eyebrow: "Verification",
      title: verificationTitle({ isDiscovering, isVerified, error: discoveryError }),
      tone: discoveryError ? "danger" : isDiscovering ? "muted" : isVerified ? "default" : "warning",
      details: [
        { label: "Check", value: "Azure DevOps API browse" },
        { label: "Result", value: discoveryError || describeDiscoveredProjectCount(discoveredProjects.length) },
      ],
    },
    {
      step: "04",
      icon: <LuBoxes className="h-5 w-5" />,
      eyebrow: "Discovered scope",
      title: "Azure DevOps projects",
      details: [
        { label: "Count", value: discoveredProjects.length },
        { label: "Projects", value: summarizeDiscoveredProjects(discoveredProjects) },
      ],
    },
    {
      step: "05",
      icon: <LuBoxes className="h-5 w-5" />,
      eyebrow: "Consumers",
      title: linkedProjects.length > 0 ? "Linked MAPPO projects" : "No linked projects",
      details: [
        { label: "Count", value: linkedProjects.length },
        { label: "Projects", value: summarizeLinkedProjects(linkedProjects) },
        { label: "Selected project", value: selectedProjectLinked ? "Linked" : "Not linked" },
      ],
    },
  ];
}
