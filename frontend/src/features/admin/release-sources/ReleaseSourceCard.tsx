import AdminIntegrationFlowDiagram from "@/features/admin/AdminIntegrationFlowDiagram";
import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from "@/components/ui/accordion";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardAction, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import type { ReleaseIngestEndpoint, SecretReference } from "@/lib/types";

import {
  buildReleaseSourceFlowNodes,
  describeWebhookSecretSource,
  formatTimestamp,
  releaseProviderLabel,
  releaseSourceDisplayName,
  releaseSourceModeLabel,
  type ReleaseIngestProvider,
} from "@/features/admin/release-sources/shared";

type ReleaseSourceCardProps = {
  endpoint: ReleaseIngestEndpoint;
  provider: ReleaseIngestProvider;
  webhookUrl: string;
  selectedProjectId: string;
  secretReferenceLookup: Record<string, SecretReference>;
  onCopyWebhookUrl: (endpoint: ReleaseIngestEndpoint) => void;
  onEdit: (endpoint: ReleaseIngestEndpoint) => void;
  onDelete: (endpoint: ReleaseIngestEndpoint) => void;
};

export default function ReleaseSourceCard({
  endpoint,
  provider,
  webhookUrl,
  selectedProjectId,
  secretReferenceLookup,
  onCopyWebhookUrl,
  onEdit,
  onDelete,
}: ReleaseSourceCardProps) {
  const endpointId = endpoint.id ?? "";
  const linkedProjects = endpoint.linkedProjects ?? [];
  const selectedProjectLinked = linkedProjects.some(
    (linked) => (linked.projectId ?? "") === selectedProjectId
  );
  const secretSource = describeWebhookSecretSource(endpoint, secretReferenceLookup);
  const flowNodes = buildReleaseSourceFlowNodes({
    endpoint,
    provider,
    webhookUrl,
    secretSource,
    linkedProjects,
    selectedProjectLinked,
  });

  return (
    <Card className="border border-border/70 bg-card/70">
      <CardHeader className="gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="space-y-1">
          <CardTitle>{releaseSourceDisplayName(endpoint)}</CardTitle>
          <p className="text-xs text-muted-foreground">
            {releaseProviderLabel(provider)} · {releaseSourceModeLabel(provider)}
          </p>
        </div>
        <CardAction className="flex-wrap justify-end">
          <Button type="button" variant="outline" size="sm" onClick={() => onCopyWebhookUrl(endpoint)}>
            Copy Webhook URL
          </Button>
          <Button type="button" variant="outline" size="sm" onClick={() => onEdit(endpoint)}>
            Edit
          </Button>
          <Button
            type="button"
            variant="outline"
            size="sm"
            className="border-destructive/50 text-destructive hover:bg-destructive/10 hover:text-destructive"
            onClick={() => onDelete(endpoint)}
          >
            Delete
          </Button>
        </CardAction>
      </CardHeader>
      <CardContent className="space-y-3">
        <AdminIntegrationFlowDiagram nodes={flowNodes} />

        <div className="flex flex-wrap items-center gap-2">
          <Badge variant="outline">Provider: {releaseProviderLabel(provider)}</Badge>
          <Badge variant={endpoint.enabled ? "default" : "secondary"}>
            {endpoint.enabled ? "Enabled" : "Disabled"}
          </Badge>
          <Badge variant="secondary">
            Verification: {secretSource}
          </Badge>
          {formatTimestamp(endpoint.updatedAt) ? (
            <Badge variant="secondary">Updated {formatTimestamp(endpoint.updatedAt)}</Badge>
          ) : null}
        </div>

        <div className="rounded-md border border-border/60 bg-background/50 p-2">
          <p className="text-[11px] uppercase tracking-[0.06em] text-muted-foreground">Webhook URL</p>
          <p className="mt-1 break-all font-mono text-[11px] text-foreground">{webhookUrl}</p>
        </div>

        {endpoint.repoFilter || endpoint.branchFilter || endpoint.pipelineIdFilter || endpoint.manifestPath ? (
          <Accordion type="single" collapsible className="rounded-md border border-border/60 bg-background/40 px-3">
            <AccordionItem value="advanced-routing" className="border-none">
              <AccordionTrigger className="py-2 text-xs font-medium text-muted-foreground hover:no-underline">
                Optional routing filters
              </AccordionTrigger>
              <AccordionContent>
                <div className="grid gap-2 text-xs text-muted-foreground md:grid-cols-2">
                  {endpoint.branchFilter ? (
                    <p>
                      Branch filter: <span className="font-mono text-foreground">{endpoint.branchFilter}</span>
                    </p>
                  ) : null}
                  {provider === "github" && endpoint.repoFilter ? (
                    <p>
                      Repository filter: <span className="font-mono text-foreground">{endpoint.repoFilter}</span>
                    </p>
                  ) : null}
                  {provider === "github" && endpoint.manifestPath ? (
                    <p>
                      Manifest path: <span className="font-mono text-foreground">{endpoint.manifestPath}</span>
                    </p>
                  ) : null}
                  {provider === "azure_devops" && endpoint.pipelineIdFilter ? (
                    <p>
                      Pipeline filter: <span className="font-mono text-foreground">{endpoint.pipelineIdFilter}</span>
                    </p>
                  ) : null}
                </div>
              </AccordionContent>
            </AccordionItem>
          </Accordion>
        ) : null}

        <div>
          <p className="text-[11px] uppercase tracking-[0.06em] text-muted-foreground">Linked projects</p>
          <div className="mt-1 flex flex-wrap gap-1">
            {linkedProjects.length === 0 ? (
              <span className="text-xs text-muted-foreground">No linked projects</span>
            ) : (
              linkedProjects.map((linked) => {
                const linkedProjectId = linked.projectId ?? "";
                const isSelected = linkedProjectId === selectedProjectId;
                return (
                  <Badge
                    key={`${endpointId}-${linkedProjectId}`}
                    variant={isSelected ? "default" : "secondary"}
                  >
                    {linked.projectName || "Project"}
                  </Badge>
                );
              })
            )}
          </div>
          {selectedProjectLinked ? (
            <p className="mt-1 text-[11px] text-primary">Selected project is linked to this endpoint.</p>
          ) : null}
        </div>
      </CardContent>
    </Card>
  );
}
