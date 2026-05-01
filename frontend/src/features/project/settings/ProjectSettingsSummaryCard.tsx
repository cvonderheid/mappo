import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import type { ProjectAdoPipeline, ReleaseIngestEndpoint, ProviderConnection } from "@/lib/types";
import type { DeploymentSystem, ProjectDraft, ReleaseSystem } from "@/features/project/settings/shared";
import {
  ACCESS_STRATEGY_LABELS,
  DEPLOYMENT_DRIVER_LABELS,
  DEPLOYMENT_SYSTEM_LABELS,
  RELEASE_SYSTEM_LABELS,
  RUNTIME_HEALTH_LABELS,
} from "@/features/project/settings/shared";

type ProjectSettingsSummaryCardProps = {
  releaseSourceConfigured: boolean;
  effectiveReleaseSystem: ReleaseSystem;
  releaseSourceLabel: string;
  selectedReleaseIngestEndpoint: ReleaseIngestEndpoint | null;
  deploymentConfigured: boolean;
  selectedDeploymentSystem: DeploymentSystem;
  draft: ProjectDraft;
  selectedProviderConnection: ProviderConnection | null;
  selectedDiscoveredAdoProjectName: string;
  discoveredPipelines: ProjectAdoPipeline[];
  runtimeHealthConfigured: boolean;
};

export default function ProjectSettingsSummaryCard({
  releaseSourceConfigured,
  effectiveReleaseSystem,
  releaseSourceLabel,
  selectedReleaseIngestEndpoint,
  deploymentConfigured,
  selectedDeploymentSystem,
  draft,
  selectedProviderConnection,
  selectedDiscoveredAdoProjectName,
  discoveredPipelines,
  runtimeHealthConfigured,
}: ProjectSettingsSummaryCardProps) {
  return (
    <Card className="h-fit border-border/70 bg-background/50 xl:sticky xl:top-4">
      <CardHeader className="pb-2">
        <CardTitle className="text-sm uppercase tracking-[0.08em]">Setup Summary</CardTitle>
        <p className="text-xs text-muted-foreground">Operator-facing summary of the current project configuration.</p>
      </CardHeader>
      <CardContent className="pt-0">
        <div className="space-y-4 text-xs">
          <div className="space-y-2">
            <p className="text-[11px] uppercase tracking-[0.08em] text-muted-foreground">Release source</p>
            <dl className="space-y-1">
              <div className="flex justify-between gap-3">
                <dt className="text-muted-foreground">Provider</dt>
                <dd className="text-right text-foreground">
                  {releaseSourceConfigured ? RELEASE_SYSTEM_LABELS[effectiveReleaseSystem] : "Not configured"}
                </dd>
              </div>
              <div className="flex justify-between gap-3">
                <dt className="text-muted-foreground">Type</dt>
                <dd className="text-right text-foreground">
                  {releaseSourceConfigured ? releaseSourceLabel : "Not configured"}
                </dd>
              </div>
              <div className="flex justify-between gap-3">
                <dt className="text-muted-foreground">Linked source</dt>
                <dd className="text-right text-foreground">
                  {selectedReleaseIngestEndpoint?.name || selectedReleaseIngestEndpoint?.id || "Not linked"}
                </dd>
              </div>
            </dl>
          </div>

          <div className="space-y-2">
            <p className="text-[11px] uppercase tracking-[0.08em] text-muted-foreground">Deployment</p>
            <dl className="space-y-1">
              <div className="flex justify-between gap-3">
                <dt className="text-muted-foreground">System</dt>
                <dd className="text-right text-foreground">
                  {deploymentConfigured ? DEPLOYMENT_SYSTEM_LABELS[selectedDeploymentSystem] : "Not configured"}
                </dd>
              </div>
              <div className="flex justify-between gap-3">
                <dt className="text-muted-foreground">Method</dt>
                <dd className="text-right text-foreground">
                  {deploymentConfigured ? DEPLOYMENT_DRIVER_LABELS[draft.deploymentDriver] : "Not configured"}
                </dd>
              </div>
              {draft.deploymentDriver === "pipeline_trigger" ? (
                <>
                  <div className="flex justify-between gap-3">
                    <dt className="text-muted-foreground">Deployment connection</dt>
                    <dd className="text-right text-foreground">
                      {selectedProviderConnection?.name || selectedProviderConnection?.id || "Not linked"}
                    </dd>
                  </div>
                  <div className="flex justify-between gap-3">
                    <dt className="text-muted-foreground">Azure DevOps project</dt>
                    <dd className="text-right text-foreground">
                      {selectedDiscoveredAdoProjectName || "Not selected"}
                    </dd>
                  </div>
                  <div className="flex justify-between gap-3">
                    <dt className="text-muted-foreground">Repository</dt>
                    <dd className="text-right text-foreground">{draft.driver.repository || "Not selected"}</dd>
                  </div>
                  <div className="flex justify-between gap-3">
                    <dt className="text-muted-foreground">Pipeline</dt>
                    <dd className="text-right text-foreground">
                      {discoveredPipelines.find((pipeline) => pipeline.id === draft.driver.pipelineId)?.name
                        || draft.driver.pipelineId
                        || "Not selected"}
                    </dd>
                  </div>
                  <div className="flex justify-between gap-3">
                    <dt className="text-muted-foreground">Branch</dt>
                    <dd className="text-right text-foreground">{draft.driver.branch || "main"}</dd>
                  </div>
                </>
              ) : null}
            </dl>
          </div>

          <div className="space-y-2">
            <p className="text-[11px] uppercase tracking-[0.08em] text-muted-foreground">Access and health</p>
            <dl className="space-y-1">
              <div className="flex justify-between gap-3">
                <dt className="text-muted-foreground">Access model</dt>
                <dd className="text-right text-foreground">{ACCESS_STRATEGY_LABELS[draft.accessStrategy]}</dd>
              </div>
              <div className="flex justify-between gap-3">
                <dt className="text-muted-foreground">Runtime check</dt>
                <dd className="text-right text-foreground">
                  {runtimeHealthConfigured
                    ? (RUNTIME_HEALTH_LABELS[draft.runtimeHealthProvider] ?? "HTTP endpoint")
                    : "Not configured"}
                </dd>
              </div>
              <div className="flex justify-between gap-3">
                <dt className="text-muted-foreground">Health path</dt>
                <dd className="text-right text-foreground">
                  {runtimeHealthConfigured ? (draft.runtime.path || "/") : "Not configured"}
                </dd>
              </div>
              <div className="flex justify-between gap-3">
                <dt className="text-muted-foreground">Expected status</dt>
                <dd className="text-right text-foreground">
                  {runtimeHealthConfigured ? (draft.runtime.expectedStatus || "200") : "Not configured"}
                </dd>
              </div>
              <div className="flex justify-between gap-3">
                <dt className="text-muted-foreground">Timeout</dt>
                <dd className="text-right text-foreground">
                  {runtimeHealthConfigured ? `${draft.runtime.timeoutMs || "5000"} ms` : "Not configured"}
                </dd>
              </div>
            </dl>
          </div>
        </div>
      </CardContent>
    </Card>
  );
}
