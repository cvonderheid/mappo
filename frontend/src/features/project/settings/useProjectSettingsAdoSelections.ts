import { useMemo } from "react";

import type { ProjectAdoBranch, ProjectAdoPipeline, ProjectAdoRepository } from "@/lib/types";
import type { ProjectDraft } from "@/features/project/settings/shared";

type UseProjectSettingsAdoSelectionsArgs = {
  draft: ProjectDraft;
  discoveredBranches: ProjectAdoBranch[];
  discoveredRepositories: ProjectAdoRepository[];
  discoveredPipelines: ProjectAdoPipeline[];
};

export function useProjectSettingsAdoSelections({
  draft,
  discoveredBranches,
  discoveredRepositories,
  discoveredPipelines,
}: UseProjectSettingsAdoSelectionsArgs) {
  const selectedDiscoveredPipelineId = useMemo(() => {
    const currentValue = draft.driver.pipelineId.trim();
    return currentValue === "" ? "__none" : currentValue;
  }, [draft.driver.pipelineId]);

  const selectedDiscoveredPipeline = useMemo(() => {
    const currentValue = draft.driver.pipelineId.trim();
    if (currentValue === "") {
      return null;
    }
    return discoveredPipelines.find((pipeline) => pipeline.id === currentValue) ?? null;
  }, [discoveredPipelines, draft.driver.pipelineId]);

  const selectedDiscoveredRepositoryId = useMemo(() => {
    const currentValue = draft.driver.repository.trim();
    if (currentValue === "") {
      return "__none";
    }
    const matching = discoveredRepositories.find(
      (repository) => repository.name === currentValue || repository.id === currentValue
    );
    return matching ? matching.id : "__none";
  }, [discoveredRepositories, draft.driver.repository]);

  const selectedDiscoveredRepository = useMemo(() => {
    if (selectedDiscoveredRepositoryId === "__none") {
      return null;
    }
    return discoveredRepositories.find((repository) => repository.id === selectedDiscoveredRepositoryId) ?? null;
  }, [discoveredRepositories, selectedDiscoveredRepositoryId]);

  const selectedDiscoveredBranchRef = useMemo(() => {
    const currentValue = draft.driver.branch.trim();
    if (currentValue === "") {
      return "__none";
    }
    const matching = discoveredBranches.find(
      (branch) => branch.name === currentValue || branch.refName === currentValue
    );
    return matching ? matching.refName : "__none";
  }, [discoveredBranches, draft.driver.branch]);

  return {
    selectedDiscoveredPipelineId,
    selectedDiscoveredPipeline,
    hasSavedPipelineOutsideDiscovery:
      draft.driver.pipelineId.trim() !== "" && selectedDiscoveredPipeline === null,
    selectedDiscoveredRepositoryId,
    selectedDiscoveredRepository,
    selectedDiscoveredBranchRef,
    hasSingleDiscoveredBranch: discoveredBranches.length === 1,
    hasSingleDiscoveredRepository: discoveredRepositories.length === 1,
    hasSingleDiscoveredPipeline: discoveredPipelines.length === 1,
  };
}
