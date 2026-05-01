import { LuActivity, LuBoxes, LuWorkflow } from "react-icons/lu";
import { SiGithub } from "react-icons/si";
import { VscAzureDevops } from "react-icons/vsc";

import type { AdminIntegrationFlowNode } from "@/features/admin/AdminIntegrationFlowDiagram";
import type {
  ReleaseIngestEndpoint,
  SecretReference,
} from "@/lib/types";

export type ReleaseIngestProvider = "github" | "azure_devops";
export type WebhookSecretMode = "provider_default" | "environment_variable" | "key_vault_secret" | "secret_reference";

export type EndpointDraft = {
  id: string;
  name: string;
  provider: ReleaseIngestProvider;
  enabled: boolean;
  webhookSecretMode: WebhookSecretMode;
  webhookSecretEnvVar: string;
  webhookSecretKeyVaultSecret: string;
  webhookSecretReferenceId: string;
  repoFilter: string;
  branchFilter: string;
  pipelineIdFilter: string;
  manifestPath: string;
};

export type LinkedProject = NonNullable<ReleaseIngestEndpoint["linkedProjects"]>[number];

export function emptyDraft(): EndpointDraft {
  return {
    id: "",
    name: "",
    provider: "github",
    enabled: true,
    webhookSecretMode: "provider_default",
    webhookSecretEnvVar: "",
    webhookSecretKeyVaultSecret: "",
    webhookSecretReferenceId: "",
    repoFilter: "",
    branchFilter: "",
    pipelineIdFilter: "",
    manifestPath: "",
  };
}

export function providerDefaultSecretRef(provider: ReleaseIngestProvider): string {
  return provider === "azure_devops"
    ? "mappo.azure-devops.webhook-secret"
    : "mappo.managed-app-release.webhook-secret";
}

export function parseWebhookSecretRef(
  value: string | undefined
): Pick<EndpointDraft, "webhookSecretMode" | "webhookSecretEnvVar" | "webhookSecretKeyVaultSecret" | "webhookSecretReferenceId"> {
  const normalized = (value ?? "").trim();
  if (normalized.startsWith("secret:")) {
    return {
      webhookSecretMode: "secret_reference",
      webhookSecretEnvVar: "",
      webhookSecretKeyVaultSecret: "",
      webhookSecretReferenceId: normalized.slice("secret:".length).trim(),
    };
  }
  if (normalized.startsWith("env:")) {
    return {
      webhookSecretMode: "environment_variable",
      webhookSecretEnvVar: normalized.slice("env:".length).trim(),
      webhookSecretKeyVaultSecret: "",
      webhookSecretReferenceId: "",
    };
  }
  if (normalized.startsWith("kv:")) {
    return {
      webhookSecretMode: "key_vault_secret",
      webhookSecretEnvVar: "",
      webhookSecretKeyVaultSecret: normalized.slice("kv:".length).trim(),
      webhookSecretReferenceId: "",
    };
  }
  return {
    webhookSecretMode: "provider_default",
    webhookSecretEnvVar: "",
    webhookSecretKeyVaultSecret: "",
    webhookSecretReferenceId: "",
  };
}

export function buildWebhookSecretRef(draft: EndpointDraft): string {
  if (draft.webhookSecretMode === "secret_reference") {
    const secretReferenceId = draft.webhookSecretReferenceId.trim();
    return secretReferenceId === "" ? "" : `secret:${secretReferenceId}`;
  }
  if (draft.webhookSecretMode === "environment_variable") {
    const envVarName = draft.webhookSecretEnvVar.trim();
    return envVarName === "" ? "" : `env:${envVarName}`;
  }
  if (draft.webhookSecretMode === "key_vault_secret") {
    const secretName = draft.webhookSecretKeyVaultSecret.trim();
    return secretName === "" ? "" : `kv:${secretName}`;
  }
  return providerDefaultSecretRef(draft.provider);
}

export function describeWebhookSecretSource(
  endpoint: ReleaseIngestEndpoint,
  secretReferenceLookup: Record<string, SecretReference>
): string {
  const provider = (endpoint.provider ?? "github") as ReleaseIngestProvider;
  const normalized = (endpoint.secretRef ?? "").trim() || providerDefaultSecretRef(provider);
  if (normalized === providerDefaultSecretRef(provider)) {
    return "MAPPO runtime secret";
  }
  if (normalized.startsWith("secret:")) {
    const secretReferenceId = normalized.slice("secret:".length).trim();
    return `Secret reference (${secretReferenceLookup[secretReferenceId]?.name || secretReferenceId})`;
  }
  if (normalized.startsWith("env:")) {
    return `Environment variable (${normalized.slice("env:".length).trim()})`;
  }
  if (normalized.startsWith("kv:")) {
    return `Azure Key Vault secret (${normalized.slice("kv:".length).trim()})`;
  }
  return "Named runtime secret";
}

export function releaseProviderLabel(provider: ReleaseIngestProvider): string {
  return provider === "azure_devops" ? "Azure DevOps" : "GitHub";
}

export function releaseProviderIcon(provider: ReleaseIngestProvider, className: string) {
  return provider === "azure_devops" ? (
    <VscAzureDevops className={className} />
  ) : (
    <SiGithub className={className} />
  );
}

export function releaseSourceDisplayName(endpoint: ReleaseIngestEndpoint): string {
  return endpoint.name?.trim() || "Unnamed release source";
}

export function releaseResultLabel(provider: ReleaseIngestProvider): string {
  return provider === "azure_devops"
    ? "Pipeline event is emitted"
    : "Release manifest is updated";
}

export function releaseSourceModeLabel(provider: ReleaseIngestProvider): string {
  return provider === "azure_devops" ? "Pipeline release event" : "Release manifest webhook";
}

export function buildReleaseSourceFlowNodes({
  endpoint,
  provider,
  webhookUrl,
  secretSource,
  linkedProjects,
  selectedProjectLinked,
}: {
  endpoint: ReleaseIngestEndpoint;
  provider: ReleaseIngestProvider;
  webhookUrl: string;
  secretSource: string;
  linkedProjects: LinkedProject[];
  selectedProjectLinked: boolean;
}): AdminIntegrationFlowNode[] {
  const enabled = endpoint.enabled ?? true;
  const providerLabel = releaseProviderLabel(provider);
  const hasRoutingFilters = Boolean(
    endpoint.repoFilter || endpoint.branchFilter || endpoint.pipelineIdFilter || endpoint.manifestPath
  );
  const displayName = releaseSourceDisplayName(endpoint);
  return [
    {
      step: "00",
      icon: releaseProviderIcon(provider, "h-5 w-5"),
      eyebrow: "Outside MAPPO",
      title: "Release performed",
      details: [
        { label: "Provider", value: providerLabel },
        { label: "Result", value: releaseResultLabel(provider) },
      ],
    },
    {
      step: "01",
      icon: releaseProviderIcon(provider, "h-5 w-5"),
      eyebrow: "Provider",
      title: providerLabel,
      tone: enabled ? "default" : "warning",
      details: [
        { label: "Source", value: displayName },
        { label: "Status", value: enabled ? "Enabled" : "Disabled" },
      ],
    },
    {
      step: "02",
      icon: <LuWorkflow className="h-5 w-5" />,
      eyebrow: "Ingress",
      title: "Webhook delivery",
      details: [
        { label: "Mode", value: releaseSourceModeLabel(provider) },
        { label: "URL", value: webhookUrl },
      ],
    },
    {
      step: "03",
      icon: <LuActivity className="h-5 w-5" />,
      eyebrow: "Verification",
      title: "Inbound secret check",
      tone: secretSource === "MAPPO runtime secret" ? "default" : "muted",
      details: [
        { label: "Secret source", value: secretSource },
        { label: "Routing filters", value: hasRoutingFilters ? "Configured" : "None" },
      ],
    },
    {
      step: "04",
      icon: <LuBoxes className="h-5 w-5" />,
      eyebrow: "Consumers",
      title: linkedProjects.length > 0 ? "Linked MAPPO projects" : "No linked projects",
      details: [
        { label: "Count", value: linkedProjects.length },
        { label: "Selected project", value: selectedProjectLinked ? "Linked" : "Not linked" },
      ],
    },
  ];
}

export function formatTimestamp(value: string | null | undefined): string {
  if (!value) {
    return "";
  }
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return "";
  }
  return parsed.toLocaleString();
}

export function webhookPathFor(endpointId: string, provider: ReleaseIngestProvider): string {
  return provider === "azure_devops"
    ? `/api/v1/admin/releases/webhooks/azure-devops/${endpointId}`
    : `/api/v1/admin/releases/webhooks/github/${endpointId}`;
}

export function toDraft(endpoint: ReleaseIngestEndpoint): EndpointDraft {
  const secretReference = parseWebhookSecretRef(endpoint.secretRef);
  return {
    id: endpoint.id ?? "",
    name: endpoint.name ?? "",
    provider: (endpoint.provider ?? "github") as ReleaseIngestProvider,
    enabled: endpoint.enabled ?? true,
    ...secretReference,
    repoFilter: endpoint.repoFilter ?? "",
    branchFilter: endpoint.branchFilter ?? "",
    pipelineIdFilter: endpoint.pipelineIdFilter ?? "",
    manifestPath: endpoint.manifestPath ?? "",
  };
}
