import type { Dispatch, FormEvent, MutableRefObject, SetStateAction } from "react";
import { useCallback } from "react";
import type { NavigateFunction } from "react-router-dom";

import { DEFAULT_FORM, type StartRunFormState } from "@/lib/deployment-form";
import {
  adminDeleteTargetRegistration,
  adminIngestGithubReleaseManifest,
  adminIngestMarketplaceEvent,
  adminRegisterOperatorTarget,
  adminUpdateTargetRegistration,
  createProject,
  createRun,
  deleteProject,
  discoverProjectAdoBranches,
  discoverProjectAdoPipelines,
  discoverProjectAdoRepositories,
  getRun,
  patchProjectConfiguration,
  previewRun,
  resumeRun,
  retryFailed,
  validateProjectConfiguration,
} from "@/lib/api";
import type {
  CreateRunRequest,
  MarketplaceEventIngestRequest,
  MarketplaceEventIngestResponse,
  ProjectAdoBranchDiscoveryResult,
  ProjectAdoPipelineDiscoveryResult,
  ProjectAdoRepositoryDiscoveryResult,
  ProjectConfigurationPatchRequest,
  ProjectCreateRequest,
  ProjectDefinition,
  ProjectValidationRequest,
  ProjectValidationResult,
  Release,
  ReleaseManifestIngestRequest,
  ReleaseManifestIngestResponse,
  RunPreview,
  Target,
  TargetExecutionRecord,
  UpdateTargetRegistrationRequest,
} from "@/lib/types";

type UseAppShellActionsArgs = {
  navigate: NavigateFunction;
  selectedRelease: Release | null;
  selectedReleaseId: string;
  formState: StartRunFormState;
  targetGroupFilter: string;
  selectedTargetIds: string[];
  deploymentTargetCount: number;
  targets: Target[];
  previewAbortControllerRef: MutableRefObject<AbortController | null>;
  refreshRuns: () => Promise<void>;
  refreshRunDetail: (runId: string) => Promise<void>;
  refreshTargets: () => Promise<void>;
  refreshRegistrationOptions: () => Promise<void>;
  refreshReleases: () => Promise<void>;
  refreshProjects: () => Promise<void>;
  setIsSubmitting: Dispatch<SetStateAction<boolean>>;
  setIsPreviewing: Dispatch<SetStateAction<boolean>>;
  setPreviewTargetCount: Dispatch<SetStateAction<number>>;
  setErrorMessage: Dispatch<SetStateAction<string>>;
  setPreviewErrorMessage: Dispatch<SetStateAction<string>>;
  setRunPreview: Dispatch<SetStateAction<RunPreview | null>>;
  setDeploymentControlsOpen: Dispatch<SetStateAction<boolean>>;
  setSelectedRunId: Dispatch<SetStateAction<string>>;
  setRunPage: Dispatch<SetStateAction<number>>;
  setAdminIsSubmitting: Dispatch<SetStateAction<boolean>>;
  setAdminResult: Dispatch<SetStateAction<MarketplaceEventIngestResponse | null>>;
  setAdminErrorMessage: Dispatch<SetStateAction<string>>;
  setReleaseIngestIsSubmitting: Dispatch<SetStateAction<boolean>>;
  setSelectedReleaseId: Dispatch<SetStateAction<string>>;
  setFormState: Dispatch<SetStateAction<StartRunFormState>>;
  setTargetGroupFilter: Dispatch<SetStateAction<string>>;
  setSelectedTargetIds: Dispatch<SetStateAction<string[]>>;
  setSelectedProjectId: Dispatch<SetStateAction<string>>;
};

export function useAppShellActions({
  navigate,
  selectedRelease,
  selectedReleaseId,
  formState,
  targetGroupFilter,
  selectedTargetIds,
  deploymentTargetCount,
  targets,
  previewAbortControllerRef,
  refreshRuns,
  refreshRunDetail,
  refreshTargets,
  refreshRegistrationOptions,
  refreshReleases,
  refreshProjects,
  setIsSubmitting,
  setIsPreviewing,
  setPreviewTargetCount,
  setErrorMessage,
  setPreviewErrorMessage,
  setRunPreview,
  setDeploymentControlsOpen,
  setSelectedRunId,
  setRunPage,
  setAdminIsSubmitting,
  setAdminResult,
  setAdminErrorMessage,
  setReleaseIngestIsSubmitting,
  setSelectedReleaseId,
  setFormState,
  setTargetGroupFilter,
  setSelectedTargetIds,
  setSelectedProjectId,
}: UseAppShellActionsArgs) {
  const buildRunRequest = useCallback((): CreateRunRequest | null => {
    if (!selectedRelease) {
      return null;
    }
    const request: CreateRunRequest = {
      releaseId: selectedRelease.id ?? selectedReleaseId,
      strategyMode: formState.strategyMode,
      waveTag: "ring",
      waveOrder: ["canary", "prod"],
      concurrency: formState.concurrency,
      targetTags: targetGroupFilter === "all" ? {} : { ring: targetGroupFilter },
      stopPolicy: {
        maxFailureCount: formState.maxFailureCount.trim() === "" ? undefined : Number(formState.maxFailureCount),
        maxFailureRate:
          formState.maxFailureRatePercent.trim() === ""
            ? undefined
            : Number(formState.maxFailureRatePercent) / 100,
      },
    };
    if (selectedTargetIds.length > 0) {
      request.targetIds = [...selectedTargetIds].sort();
    }
    return request;
  }, [formState, selectedRelease, selectedReleaseId, selectedTargetIds, targetGroupFilter]);

  async function handleStartRun(event: FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    const request = buildRunRequest();
    if (!request) {
      setErrorMessage("No releases are available yet.");
      return;
    }
    setIsSubmitting(true);
    try {
      const created = await createRun(request);
      if (created.id) {
        setSelectedRunId(created.id);
        setRunPage(0);
      }
      await refreshRuns();
      if (created.id) {
        await refreshRunDetail(created.id);
      }
      await refreshTargets();
      setErrorMessage("");
      setRunPreview(null);
      setPreviewErrorMessage("");
      setDeploymentControlsOpen(false);
    } catch (error) {
      setErrorMessage((error as Error).message);
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handlePreviewRun(): Promise<void> {
    const request = buildRunRequest();
    if (!request) {
      setPreviewErrorMessage("No releases are available yet.");
      return;
    }
    previewAbortControllerRef.current?.abort();
    const abortController = new AbortController();
    previewAbortControllerRef.current = abortController;
    setPreviewTargetCount(request.targetIds?.length ?? deploymentTargetCount);
    setIsPreviewing(true);
    try {
      setRunPreview(null);
      setPreviewErrorMessage("");
      setRunPreview(await previewRun(request, abortController.signal));
    } catch (error) {
      if (error instanceof DOMException && error.name === "AbortError") {
        setPreviewErrorMessage("Preview canceled before Azure returned a result.");
      } else {
        setPreviewErrorMessage((error as Error).message);
      }
    } finally {
      if (previewAbortControllerRef.current === abortController) {
        previewAbortControllerRef.current = null;
      }
      setIsPreviewing(false);
    }
  }

  function handleCancelPreview(): void {
    previewAbortControllerRef.current?.abort();
  }

  async function handleResumeRun(runId: string): Promise<void> {
    try {
      await resumeRun(runId);
      await refreshRuns();
      await refreshRunDetail(runId);
      await refreshTargets();
      setSelectedRunId(runId);
      setRunPage(0);
      setErrorMessage("");
    } catch (error) {
      setErrorMessage((error as Error).message);
    }
  }

  async function handleRetryFailed(runId: string): Promise<void> {
    try {
      await retryFailed(runId);
      await refreshRuns();
      await refreshRunDetail(runId);
      await refreshTargets();
      setSelectedRunId(runId);
      setRunPage(0);
      setErrorMessage("");
    } catch (error) {
      setErrorMessage((error as Error).message);
    }
  }

  async function handleCloneRun(runId: string): Promise<void> {
    try {
      const sourceRun = await getRun(runId);
      const clonedTargetIds = Array.from(
        new Set(
          (sourceRun.targetRecords ?? []).flatMap((record: TargetExecutionRecord) =>
            record.targetId ? [record.targetId] : []
          )
        )
      ).sort();
      const clonedTargetGroups = [
        ...new Set(
          clonedTargetIds
            .map((targetId) => targets.find((target) => target.id === targetId)?.tags?.ring)
            .filter((group): group is string => Boolean(group))
        ),
      ];
      const stopPolicy = sourceRun.stopPolicy ?? {};
      setSelectedReleaseId(sourceRun.releaseId ?? "");
      setFormState({
        strategyMode: sourceRun.strategyMode ?? DEFAULT_FORM.strategyMode,
        concurrency: sourceRun.concurrency ?? DEFAULT_FORM.concurrency,
        maxFailureCount:
          stopPolicy.maxFailureCount === null || stopPolicy.maxFailureCount === undefined ? "" : String(stopPolicy.maxFailureCount),
        maxFailureRatePercent:
          stopPolicy.maxFailureRate === null || stopPolicy.maxFailureRate === undefined
            ? ""
            : String(Math.round(stopPolicy.maxFailureRate * 100)),
      });
      setTargetGroupFilter(clonedTargetGroups.length === 1 ? (clonedTargetGroups[0] ?? "all") : "all");
      setSelectedTargetIds(clonedTargetIds);
      setDeploymentControlsOpen(true);
      navigate("/deployments");
      setErrorMessage("");
    } catch (error) {
      setErrorMessage((error as Error).message);
    }
  }

  function handleOpenDeploymentForRelease(releaseId: string): void {
    navigate(`/deployments?releaseId=${encodeURIComponent(releaseId)}&controls=open`);
  }

  async function handleAdminIngestMarketplaceEvent(
    request: MarketplaceEventIngestRequest,
    ingestToken?: string
  ): Promise<void> {
    setAdminIsSubmitting(true);
    try {
      setAdminResult(await adminIngestMarketplaceEvent(request, ingestToken));
      setAdminErrorMessage("");
      await refreshTargets();
      await refreshRegistrationOptions();
    } catch (error) {
      setAdminErrorMessage((error as Error).message);
      throw error;
    } finally {
      setAdminIsSubmitting(false);
    }
  }

  async function handleAdminRegisterOperatorTarget(request: MarketplaceEventIngestRequest): Promise<void> {
    setAdminIsSubmitting(true);
    try {
      setAdminResult(await adminRegisterOperatorTarget(request));
      setAdminErrorMessage("");
      await refreshTargets();
      await refreshRegistrationOptions();
    } catch (error) {
      setAdminErrorMessage((error as Error).message);
      throw error;
    } finally {
      setAdminIsSubmitting(false);
    }
  }

  async function handleAdminUpdateRegistration(targetId: string, request: UpdateTargetRegistrationRequest): Promise<void> {
    await adminUpdateTargetRegistration(targetId, request);
    await refreshTargets();
    await refreshRegistrationOptions();
  }

  async function handleAdminDeleteRegistration(targetId: string): Promise<void> {
    await adminDeleteTargetRegistration(targetId);
    await refreshTargets();
    await refreshRegistrationOptions();
  }

  async function handleIngestManagedAppReleases(
    request?: ReleaseManifestIngestRequest
  ): Promise<ReleaseManifestIngestResponse> {
    setReleaseIngestIsSubmitting(true);
    try {
      const result = await adminIngestGithubReleaseManifest(request);
      await refreshReleases();
      await refreshRegistrationOptions();
      return result;
    } finally {
      setReleaseIngestIsSubmitting(false);
    }
  }

  async function handleCreateProject(request: ProjectCreateRequest): Promise<ProjectDefinition> {
    const created = await createProject(request);
    await refreshProjects();
    if (created.id) {
      setSelectedProjectId(created.id);
      navigate("/projects");
    }
    return created;
  }

  async function handlePatchProject(
    projectId: string,
    request: ProjectConfigurationPatchRequest
  ): Promise<ProjectDefinition> {
    const updated = await patchProjectConfiguration(projectId, request);
    await refreshProjects();
    if (updated.id) {
      setSelectedProjectId(updated.id);
    }
    return updated;
  }

  async function handleDeleteProject(projectId: string): Promise<void> {
    await deleteProject(projectId);
    await refreshProjects();
    navigate("/projects");
  }

  function handleValidateProject(projectId: string, request: ProjectValidationRequest): Promise<ProjectValidationResult> {
    return validateProjectConfiguration(projectId, request);
  }

  function handleDiscoverProjectAdoPipelines(
    projectId: string,
    request: { organization?: string; project?: string; providerConnectionId?: string; nameContains?: string }
  ): Promise<ProjectAdoPipelineDiscoveryResult> {
    return discoverProjectAdoPipelines(projectId, request);
  }

  function handleDiscoverProjectAdoRepositories(
    projectId: string,
    request: { organization?: string; project?: string; providerConnectionId?: string; nameContains?: string }
  ): Promise<ProjectAdoRepositoryDiscoveryResult> {
    return discoverProjectAdoRepositories(projectId, request);
  }

  function handleDiscoverProjectAdoBranches(
    projectId: string,
    request: {
      organization?: string;
      project?: string;
      providerConnectionId?: string;
      repositoryId?: string;
      repository?: string;
      nameContains?: string;
    }
  ): Promise<ProjectAdoBranchDiscoveryResult> {
    return discoverProjectAdoBranches(projectId, request);
  }

  return {
    handleStartRun,
    handlePreviewRun,
    handleCancelPreview,
    handleResumeRun,
    handleRetryFailed,
    handleCloneRun,
    handleOpenDeploymentForRelease,
    handleAdminIngestMarketplaceEvent,
    handleAdminRegisterOperatorTarget,
    handleAdminUpdateRegistration,
    handleAdminDeleteRegistration,
    handleIngestManagedAppReleases,
    handleCreateProject,
    handlePatchProject,
    handleDeleteProject,
    handleValidateProject,
    handleDiscoverProjectAdoPipelines,
    handleDiscoverProjectAdoRepositories,
    handleDiscoverProjectAdoBranches,
  };
}
