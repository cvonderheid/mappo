import AdminIntegrationFlowDiagram from "@/features/admin/AdminIntegrationFlowDiagram";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardAction, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import type { ProviderConnection, ProviderConnectionAdoProject, SecretReference } from "@/lib/types";

import {
  buildDeploymentConnectionFlowNodes,
  deploymentConnectionDisplayName,
  describeDiscoveredProjectCount,
  describePersonalAccessTokenSource,
  providerConnectionLabel,
  providerConnectionModeLabel,
  resolveConnectionAccountUrl,
} from "@/features/admin/provider-connections/shared";

type ProviderConnectionCardProps = {
  connection: ProviderConnection;
  selectedProjectId: string;
  isDiscovering: boolean;
  isVerified: boolean;
  discoveredProjects: ProviderConnectionAdoProject[];
  discoveryError: string;
  hasDiscoveryState: boolean;
  secretReferenceLookup: Record<string, SecretReference>;
  onDiscover: (connection: ProviderConnection) => void;
  onEdit: (connection: ProviderConnection) => void;
  onDelete: (connection: ProviderConnection) => void;
};

export default function ProviderConnectionCard({
  connection,
  selectedProjectId,
  isDiscovering,
  isVerified,
  discoveredProjects,
  discoveryError,
  hasDiscoveryState,
  secretReferenceLookup,
  onDiscover,
  onEdit,
  onDelete,
}: ProviderConnectionCardProps) {
  const connectionId = (connection.id ?? "").trim();
  const linkedProjects = connection.linkedProjects ?? [];
  const selectedProjectLinked = linkedProjects.some(
    (linked) => (linked.projectId ?? "").trim() === selectedProjectId
  );
  const resolvedAccountUrl = resolveConnectionAccountUrl(connection);
  const credentialSource = describePersonalAccessTokenSource(connection, secretReferenceLookup);
  const flowNodes = buildDeploymentConnectionFlowNodes({
    connection,
    resolvedAccountUrl,
    credentialSource,
    isDiscovering,
    isVerified,
    discoveryError,
    discoveredProjects,
    linkedProjects,
    selectedProjectLinked,
  });

  return (
    <Card className="border border-border/70 bg-card/70">
      <CardHeader className="gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="space-y-1">
          <CardTitle>{deploymentConnectionDisplayName(connection)}</CardTitle>
          <p className="text-sm text-muted-foreground">
            {providerConnectionLabel(connection.provider)} · {providerConnectionModeLabel(connection.provider)}
          </p>
        </div>
        <CardAction className="flex-wrap justify-end">
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => onDiscover(connection)}
            disabled={connectionId === "" || isDiscovering}
          >
            {isDiscovering
              ? "Verifying..."
              : hasDiscoveryState
                ? "Re-verify access"
                : "Verify / refresh access"}
          </Button>
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => onEdit(connection)}
            disabled={connectionId === ""}
          >
            Edit
          </Button>
          <Button
            type="button"
            variant="outline"
            size="sm"
            className="border-destructive/60 text-destructive hover:bg-destructive/10 hover:text-destructive"
            onClick={() => onDelete(connection)}
            disabled={connectionId === ""}
          >
            Delete
          </Button>
        </CardAction>
      </CardHeader>
      <CardContent className="space-y-3">
        <AdminIntegrationFlowDiagram nodes={flowNodes} />

        <div className="flex flex-wrap items-center gap-2">
          <Badge variant="outline">
            Provider: {providerConnectionLabel(connection.provider)}
          </Badge>
          <Badge variant="outline">
            {connection.enabled ? "Enabled" : "Disabled"}
          </Badge>
          {isDiscovering ? (
            <Badge variant="secondary">Verifying...</Badge>
          ) : isVerified ? (
            <Badge variant="outline">Verified access</Badge>
          ) : discoveryError ? (
            <Badge variant="destructive">Verification failed</Badge>
          ) : null}
          <span className="text-xs text-muted-foreground">
            {describeDiscoveredProjectCount(discoveredProjects.length)}
          </span>
        </div>
        <div className="grid gap-2 text-xs text-muted-foreground md:grid-cols-2">
          <p>
            Azure DevOps account scope:{" "}
            <span className="font-mono text-foreground">
              {resolvedAccountUrl || "not configured"}
            </span>
          </p>
          <p>
            API credential source:{" "}
            <span className="font-medium text-foreground">
              {credentialSource}
            </span>
          </p>
        </div>
        {resolvedAccountUrl === "" ? (
          <p className="rounded-md border border-amber-400/40 bg-amber-500/10 px-3 py-2 text-xs text-amber-100">
            This deployment connection still needs verification. Edit it, paste any Azure DevOps project or repository URL from the Azure DevOps account MAPPO should browse, then verify the connection.
          </p>
        ) : null}
        {isVerified ? (
          <p className="rounded-md border border-border/60 bg-background/40 px-3 py-2 text-xs text-muted-foreground">
            Verified Azure DevOps access. MAPPO can browse {discoveredProjects.length} Azure DevOps project{discoveredProjects.length === 1 ? "" : "s"} through this deployment connection.
          </p>
        ) : null}
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-xs text-muted-foreground">MAPPO projects using this connection:</span>
          {linkedProjects.length === 0 ? (
            <Badge variant="secondary">none</Badge>
          ) : (
            linkedProjects.map((linked) => {
              const projectId = linked.projectId ?? "unknown";
              const label = linked.projectDisplayName || linked.projectName || "Project";
              return (
                <Badge
                  key={`${connectionId}-${projectId}`}
                  variant={selectedProjectLinked && projectId === selectedProjectId ? "outline" : "secondary"}
                >
                  {label}
                </Badge>
              );
            })
          )}
        </div>
        {hasDiscoveryState ? (
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-xs text-muted-foreground">Azure DevOps projects MAPPO can browse:</span>
            {discoveredProjects.length ? (
              discoveredProjects.map((project) => (
                <Badge key={`${connectionId}-${project.id}`} variant="outline">
                  {project.name || "Azure DevOps project"}
                </Badge>
              ))
            ) : (
              <Badge variant="secondary">none</Badge>
            )}
          </div>
        ) : null}
        {discoveryError ? (
          <p className="rounded-md border border-destructive/50 bg-destructive/10 px-3 py-2 text-xs text-destructive-foreground">
            {discoveryError}
          </p>
        ) : null}
      </CardContent>
    </Card>
  );
}
