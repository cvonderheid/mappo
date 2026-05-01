import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from "@/components/ui/accordion";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import FieldHelpTooltip from "@/features/shared/FieldHelpTooltip";
import type { ReleaseIngestEndpoint } from "@/lib/types";
import type { ProjectDraft, ReleaseSystem } from "@/features/project/settings/shared";
import { RELEASE_SYSTEM_LABELS } from "@/features/project/settings/shared";

type ProjectReleaseSettingsSectionProps = {
  draft: ProjectDraft;
  availableReleaseSystems: ReleaseSystem[];
  effectiveReleaseSystem: ReleaseSystem;
  releaseSourceTypeLabel: string;
  releaseIngestEndpointOptions: ReleaseIngestEndpoint[];
  selectedReleaseIngestEndpoint: ReleaseIngestEndpoint | null;
  isLoadingReleaseIngestEndpoints: boolean;
  onSelectReleaseSystem: (value: ReleaseSystem) => void;
  onUpdateReleaseIngestEndpoint: (value: string) => void;
  onRefreshReleaseSources: () => void;
};

export default function ProjectReleaseSettingsSection({
  draft,
  availableReleaseSystems,
  effectiveReleaseSystem,
  releaseSourceTypeLabel,
  releaseIngestEndpointOptions,
  selectedReleaseIngestEndpoint,
  isLoadingReleaseIngestEndpoints,
  onSelectReleaseSystem,
  onUpdateReleaseIngestEndpoint,
  onRefreshReleaseSources,
}: ProjectReleaseSettingsSectionProps) {
  return (
    <div className="space-y-3">
      <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
        <div className="space-y-1">
          <div className="flex items-center gap-1">
            <Label htmlFor="release-system">Release provider</Label>
            <FieldHelpTooltip content="Which external system tells MAPPO about new versions for this project. MAPPO filters the linked release sources below to this provider." />
          </div>
          <Select
            value={effectiveReleaseSystem}
            onValueChange={(value) => onSelectReleaseSystem(value as ReleaseSystem)}
            disabled={draft.deploymentDriver === "pipeline_trigger" || availableReleaseSystems.length <= 1}
          >
            <SelectTrigger id="release-system">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {availableReleaseSystems.map((provider) => (
                <SelectItem key={provider} value={provider}>
                  {RELEASE_SYSTEM_LABELS[provider]}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <p className="text-xs text-muted-foreground">
            {draft.deploymentDriver === "pipeline_trigger"
              ? "Pipeline-driven rollout always listens for Azure DevOps release events."
              : availableReleaseSystems.length <= 1
                ? `This project currently has one release provider option: ${RELEASE_SYSTEM_LABELS[effectiveReleaseSystem]}.`
                : "Choose the provider MAPPO should check for new versions for this project."}
          </p>
        </div>
        <div className="space-y-1">
          <div className="flex items-center gap-1">
            <Label htmlFor="release-source-type">Release source type</Label>
            <FieldHelpTooltip content="How the selected provider tells MAPPO about deployable versions for this project." />
          </div>
          <Input id="release-source-type" value={releaseSourceTypeLabel} disabled />
          <p className="text-xs text-muted-foreground">
            {draft.deploymentDriver === "pipeline_trigger"
              ? "MAPPO learns about versions from an inbound Azure DevOps pipeline release event."
              : "MAPPO reads deployable versions from the selected provider's release manifest."}
          </p>
        </div>
        <div className="space-y-1 md:col-span-2">
          <div className="flex items-center gap-1">
            <Label htmlFor="release-ingest-endpoint-id">Linked release source</Label>
            <FieldHelpTooltip content="Pick the global release source from Admin > Release Sources. MAPPO uses it to decide which inbound webhook or release notifications are trusted for this project." />
          </div>
          <div className="flex flex-col gap-2 md:flex-row">
            <Select
              value={draft.releaseIngestEndpointId.trim() === "" ? "__none" : draft.releaseIngestEndpointId}
              onValueChange={(value) => onUpdateReleaseIngestEndpoint(value === "__none" ? "" : value)}
            >
              <SelectTrigger id="release-ingest-endpoint-id" className="md:flex-1">
                <SelectValue placeholder="Select release source" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="__none">No linked release source</SelectItem>
                {releaseIngestEndpointOptions
                  .filter((endpoint) => (endpoint.id ?? "").trim() !== "")
                  .map((endpoint) => (
                    <SelectItem key={endpoint.id ?? endpoint.name} value={endpoint.id ?? ""}>
                      {endpoint.name || endpoint.id}
                      {" ("}
                      {endpoint.id}
                      {")"}
                    </SelectItem>
                  ))}
              </SelectContent>
            </Select>
            <Button
              type="button"
              variant="outline"
              onClick={onRefreshReleaseSources}
              disabled={isLoadingReleaseIngestEndpoints}
            >
              {isLoadingReleaseIngestEndpoints ? "Reloading..." : "Reload release sources"}
            </Button>
          </div>
          {draft.releaseIngestEndpointId.trim() !== "" && selectedReleaseIngestEndpoint ? (
            <div className="rounded-md border border-border/60 bg-background/40 px-3 py-2 text-xs text-muted-foreground">
              Using <span className="font-medium text-foreground">{selectedReleaseIngestEndpoint.name || selectedReleaseIngestEndpoint.id}</span>{" "}
              from <span className="font-medium text-foreground">Admin → Release Sources</span>.
            </div>
          ) : null}
          {releaseIngestEndpointOptions.length === 0 ? (
            <p className="text-xs text-muted-foreground">
              No {RELEASE_SYSTEM_LABELS[effectiveReleaseSystem]} release sources are available yet. Create one in{" "}
              <span className="font-medium text-foreground">Admin → Release Sources</span>.
            </p>
          ) : draft.releaseIngestEndpointId.trim() === "" ? (
            <p className="text-xs text-muted-foreground">
              Select the {RELEASE_SYSTEM_LABELS[effectiveReleaseSystem]} release source MAPPO should trust for this project.
            </p>
          ) : null}
        </div>
      </div>
      {effectiveReleaseSystem === "github" && draft.deploymentDriver !== "pipeline_trigger" ? (
        <Accordion type="single" collapsible className="rounded-md border border-border/70 bg-background/40 px-3">
          <AccordionItem value="release-manifest-reference" className="border-none">
            <AccordionTrigger className="py-3 text-sm font-medium text-foreground hover:no-underline">
              Release manifest reference
            </AccordionTrigger>
            <AccordionContent className="space-y-3 pt-0 text-xs text-muted-foreground">
              <p>
                MAPPO expects a JSON object with a <span className="font-mono text-foreground">releases</span> array.
                MAPPO Azure SDK rows need <span className="font-mono text-foreground">source_ref</span>,{" "}
                <span className="font-mono text-foreground">source_version</span>,{" "}
                <span className="font-mono text-foreground">source_type</span>,{" "}
                <span className="font-mono text-foreground">source_version_ref</span>, and any release-level{" "}
                <span className="font-mono text-foreground">parameter_defaults</span> the template needs.
              </p>
              <pre className="overflow-x-auto rounded-md border border-border/60 bg-background/70 p-3 text-[11px] leading-relaxed text-foreground">
{`{
  "releases": [
    {
      "source_ref": "github://owner/repo/artifacts/mainTemplate.json",
      "source_version": "2026.03.10.1",
      "source_type": "deployment_stack",
      "source_version_ref": "https://storage.example/releases/2026.03.10.1/mainTemplate.json",
      "parameter_defaults": {
        "containerImage": "registry.example/app:2026.03.10.1",
        "softwareVersion": "2026.03.10.1",
        "dataModelVersion": "8"
      }
    }
  ]
}`}
              </pre>
              <p>
                Do not put MAPPO project IDs, publish workflow state, registry secrets, or local artifact bookkeeping in
                publisher manifests. MAPPO routes releases through the project-linked Release Source.
              </p>
            </AccordionContent>
          </AccordionItem>
        </Accordion>
      ) : null}
    </div>
  );
}
