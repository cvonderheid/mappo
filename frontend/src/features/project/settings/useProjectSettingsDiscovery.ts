import { useCallback, useEffect, useMemo, useRef, useState, type Dispatch, type SetStateAction } from "react";
import { toast } from "sonner";

import type {
  ProjectAdoBranch,
  ProjectAdoPipeline,
  ProjectAdoRepository,
  ProviderConnection,
} from "@/lib/types";
import type {
  ProjectDraft,
  ProjectSettingsDiscoveryState,
  ProjectSettingsPageProps,
  ReleaseSystem,
} from "@/features/project/settings/shared";
import { useProjectSettingsAdoSelections } from "@/features/project/settings/useProjectSettingsAdoSelections";
import { useProjectSettingsOptionLists } from "@/features/project/settings/useProjectSettingsOptionLists";
import {
  RELEASE_SOURCE_TYPE_LABELS,
  RELEASE_SYSTEM_ORDER,
  defaultReleaseSystemForDriver,
  deriveAzureDevOpsAccountUrl,
  deriveDeploymentSystem,
  normalizeDiscoveryError,
  normalizeReleaseSystem,
  resolveProviderConnectionAccountUrl,
} from "@/features/project/settings/shared";

type UseProjectSettingsDiscoveryArgs = Pick<
  ProjectSettingsPageProps,
  "project" | "selectedProjectId" | "onDiscoverAdoBranches" | "onDiscoverAdoPipelines" | "onDiscoverAdoRepositories"
> & {
  draft: ProjectDraft;
  setDraft: Dispatch<SetStateAction<ProjectDraft>>;
};

export function useProjectSettingsDiscovery({
  project,
  selectedProjectId,
  draft,
  setDraft,
  onDiscoverAdoBranches,
  onDiscoverAdoPipelines,
  onDiscoverAdoRepositories,
}: UseProjectSettingsDiscoveryArgs): ProjectSettingsDiscoveryState {
  const [selectedReleaseSystem, setSelectedReleaseSystem] = useState<ReleaseSystem>("github");
  const [isDiscoveringBranches, setIsDiscoveringBranches] = useState(false);
  const [isDiscoveringRepositories, setIsDiscoveringRepositories] = useState(false);
  const [isDiscoveringPipelines, setIsDiscoveringPipelines] = useState(false);
  const [branchDiscoveryError, setBranchDiscoveryError] = useState("");
  const [repositoryDiscoveryError, setRepositoryDiscoveryError] = useState("");
  const [pipelineDiscoveryError, setPipelineDiscoveryError] = useState("");
  const [discoveredBranches, setDiscoveredBranches] = useState<ProjectAdoBranch[]>([]);
  const [discoveredRepositories, setDiscoveredRepositories] = useState<ProjectAdoRepository[]>([]);
  const [discoveredPipelines, setDiscoveredPipelines] = useState<ProjectAdoPipeline[]>([]);
  const branchDiscoveryKeyRef = useRef("");
  const repositoryDiscoveryKeyRef = useRef("");
  const pipelineDiscoveryKeyRef = useRef("");
  const {
    releaseIngestEndpoints,
    providerConnections,
    isLoadingReleaseIngestEndpoints,
    isLoadingProviderConnections,
    refreshReleaseIngestEndpointOptions,
    refreshProviderConnectionOptions,
  } = useProjectSettingsOptionLists();
  useEffect(() => {
    setSelectedReleaseSystem(defaultReleaseSystemForDriver(draft.deploymentDriver));
    setDiscoveredBranches([]);
    setDiscoveredRepositories([]);
    setDiscoveredPipelines([]);
    setBranchDiscoveryError("");
    setRepositoryDiscoveryError("");
    setPipelineDiscoveryError("");
    branchDiscoveryKeyRef.current = "";
    repositoryDiscoveryKeyRef.current = "";
    pipelineDiscoveryKeyRef.current = "";
  }, [draft.deploymentDriver, project, selectedProjectId]);
  const sortedReleaseIngestEndpoints = useMemo(
    () =>
      [...releaseIngestEndpoints].sort((left, right) => {
        const leftName = `${left.name ?? left.id ?? ""}`.toLowerCase();
        const rightName = `${right.name ?? right.id ?? ""}`.toLowerCase();
        return leftName.localeCompare(rightName);
      }),
    [releaseIngestEndpoints]
  );
  const sortedProviderConnections = useMemo(
    () =>
      [...providerConnections].sort((left, right) => {
        const leftName = `${left.name ?? left.id ?? ""}`.toLowerCase();
        const rightName = `${right.name ?? right.id ?? ""}`.toLowerCase();
        return leftName.localeCompare(rightName);
      }),
    [providerConnections]
  );
  const selectedProviderConnection = useMemo(() => {
    const connectionId = draft.providerConnectionId.trim();
    if (connectionId === "") {
      return null;
    }
    return (
      providerConnections.find((connection) => (connection.id ?? "").trim() === connectionId) ?? null
    );
  }, [providerConnections, draft.providerConnectionId]);
  const isVerifiedAzureDevOpsConnection = useCallback(
    (connection: ProviderConnection | null | undefined) =>
      (connection?.provider ?? "").toLowerCase() === "azure_devops"
      && (connection?.enabled ?? true)
      && resolveProviderConnectionAccountUrl(connection) !== ""
      && (connection?.discoveredProjects?.length ?? 0) > 0,
    []
  );
  const pipelineProviderConnections = useMemo(
    () =>
      sortedProviderConnections.filter(
        (connection) =>
          (connection.provider ?? "").toLowerCase() === "azure_devops" && (connection.enabled ?? true)
      ),
    [sortedProviderConnections]
  );
  const verifiedPipelineProviderConnections = useMemo(
    () => pipelineProviderConnections.filter((connection) => isVerifiedAzureDevOpsConnection(connection)),
    [isVerifiedAzureDevOpsConnection, pipelineProviderConnections]
  );
  const selectedProviderConnectionIsAzureDevOps =
    (selectedProviderConnection?.provider ?? "").toLowerCase() === "azure_devops";
  const selectedProviderConnectionDiscoveryUrl = resolveProviderConnectionAccountUrl(
    selectedProviderConnection
  );
  const cachedProviderConnectionProjects = useMemo(
    () =>
      [...(selectedProviderConnection?.discoveredProjects ?? [])].sort((left, right) =>
        `${left.name ?? ""}`.localeCompare(`${right.name ?? ""}`, undefined, { sensitivity: "base" })
      ),
    [selectedProviderConnection]
  );
  const selectedProviderConnectionRequiresVerification =
    draft.deploymentDriver === "pipeline_trigger"
    && draft.providerConnectionId.trim() !== ""
    && selectedProviderConnectionIsAzureDevOps
    && !isVerifiedAzureDevOpsConnection(selectedProviderConnection);
  const selectedDeploymentSystem = deriveDeploymentSystem(draft.deploymentDriver);
  const selectedDiscoveredAdoProjectId = useMemo(() => {
    const currentValue = draft.driver.project.trim();
    if (currentValue === "") {
      return "__none";
    }
    const matching = cachedProviderConnectionProjects.find((projectOption) => projectOption.name === currentValue);
    return matching ? matching.id : "__none";
  }, [cachedProviderConnectionProjects, draft.driver.project]);
  const selectedDiscoveredAdoProject = useMemo(() => {
    if (selectedDiscoveredAdoProjectId === "__none") {
      return null;
    }
    return (
      cachedProviderConnectionProjects.find((projectOption) => projectOption.id === selectedDiscoveredAdoProjectId)
      ?? null
    );
  }, [cachedProviderConnectionProjects, selectedDiscoveredAdoProjectId]);
  const resolvedAdoOrganization = draft.driver.organization.trim();
  const resolvedAdoProject = draft.driver.project.trim();
  const canSelectAzureDevOpsProject =
    draft.deploymentDriver === "pipeline_trigger"
    && draft.providerConnectionId.trim() !== ""
    && !selectedProviderConnectionRequiresVerification
    && cachedProviderConnectionProjects.length > 0;
  const hasSelectedAzureDevOpsProject = resolvedAdoOrganization !== "" && resolvedAdoProject !== "";
  const canDiscoverAzureDevOpsProjectResources =
    canSelectAzureDevOpsProject && hasSelectedAzureDevOpsProject;
  const providerConnectionOptions = useMemo(() => {
    if (draft.deploymentDriver !== "pipeline_trigger") {
      return sortedProviderConnections;
    }
    const base = [...pipelineProviderConnections];
    if (
      selectedProviderConnection
      && !isVerifiedAzureDevOpsConnection(selectedProviderConnection)
      && !base.some((connection) => (connection.id ?? "").trim() === (selectedProviderConnection.id ?? "").trim())
    ) {
      base.unshift(selectedProviderConnection);
    }
    return base;
  }, [
    draft.deploymentDriver,
    isVerifiedAzureDevOpsConnection,
    pipelineProviderConnections,
    selectedProviderConnection,
    sortedProviderConnections,
  ]);
  const selectedReleaseIngestEndpoint = useMemo(() => {
    const endpointId = draft.releaseIngestEndpointId.trim();
    if (endpointId === "") {
      return null;
    }
    return (
      releaseIngestEndpoints.find((endpoint) => (endpoint.id ?? "").trim() === endpointId) ?? null
    );
  }, [draft.releaseIngestEndpointId, releaseIngestEndpoints]);
  const pipelineReleaseIngestEndpoints = useMemo(
    () =>
      sortedReleaseIngestEndpoints.filter(
        (endpoint) => (endpoint.provider ?? "").toLowerCase() === "azure_devops"
      ),
    [sortedReleaseIngestEndpoints]
  );
  const selectedReleaseIngestEndpointIsAzureDevOps =
    (selectedReleaseIngestEndpoint?.provider ?? "").toLowerCase() === "azure_devops";
  const availableReleaseSystems = useMemo(() => {
    const discovered = new Set<ReleaseSystem>();
    sortedReleaseIngestEndpoints.forEach((endpoint) => {
      const provider = normalizeReleaseSystem(endpoint.provider);
      if (!provider) {
        return;
      }
      if (draft.deploymentDriver === "pipeline_trigger" && provider !== "azure_devops") {
        return;
      }
      discovered.add(provider);
    });
    if (draft.deploymentDriver === "pipeline_trigger") {
      discovered.add("azure_devops");
    }
    if (discovered.size === 0) {
      discovered.add(draft.deploymentDriver === "pipeline_trigger" ? "azure_devops" : "github");
    }
    return RELEASE_SYSTEM_ORDER.filter((provider) => discovered.has(provider));
  }, [draft.deploymentDriver, sortedReleaseIngestEndpoints]);

  const effectiveReleaseSystem = useMemo<ReleaseSystem>(() => {
    if (draft.deploymentDriver === "pipeline_trigger") {
      return "azure_devops";
    }
    if (availableReleaseSystems.includes(selectedReleaseSystem)) {
      return selectedReleaseSystem;
    }
    return availableReleaseSystems[0] ?? "github";
  }, [availableReleaseSystems, draft.deploymentDriver, selectedReleaseSystem]);
  const releaseSourceTypeLabel =
    draft.deploymentDriver === "pipeline_trigger"
      ? RELEASE_SOURCE_TYPE_LABELS.external_deployment_inputs
      : RELEASE_SOURCE_TYPE_LABELS[draft.releaseArtifactSource];

  const releaseIngestEndpointOptions = useMemo(() => {
    if (draft.deploymentDriver !== "pipeline_trigger") {
      return sortedReleaseIngestEndpoints.filter(
        (endpoint) => normalizeReleaseSystem(endpoint.provider) === effectiveReleaseSystem
      );
    }
    const base = [...pipelineReleaseIngestEndpoints];
    if (
      selectedReleaseIngestEndpoint
      && !selectedReleaseIngestEndpointIsAzureDevOps
      && !base.some((endpoint) => (endpoint.id ?? "").trim() === (selectedReleaseIngestEndpoint.id ?? "").trim())
    ) {
      base.unshift(selectedReleaseIngestEndpoint);
    }
    return base;
  }, [
    draft.deploymentDriver,
    effectiveReleaseSystem,
    pipelineReleaseIngestEndpoints,
    selectedReleaseIngestEndpoint,
    selectedReleaseIngestEndpointIsAzureDevOps,
    sortedReleaseIngestEndpoints,
  ]);
  const hasSingleCachedAdoProject = cachedProviderConnectionProjects.length === 1;
  const {
    selectedDiscoveredPipelineId,
    hasSavedPipelineOutsideDiscovery,
    selectedDiscoveredRepositoryId,
    selectedDiscoveredRepository,
    selectedDiscoveredBranchRef,
    hasSingleDiscoveredBranch,
    hasSingleDiscoveredRepository,
    hasSingleDiscoveredPipeline,
  } = useProjectSettingsAdoSelections({
    draft,
    discoveredBranches,
    discoveredRepositories,
    discoveredPipelines,
  });
  useEffect(() => {
    setDiscoveredBranches([]);
    setDiscoveredRepositories([]);
    setDiscoveredPipelines([]);
    setBranchDiscoveryError("");
    setRepositoryDiscoveryError("");
    setPipelineDiscoveryError("");
    branchDiscoveryKeyRef.current = "";
    repositoryDiscoveryKeyRef.current = "";
    pipelineDiscoveryKeyRef.current = "";
  }, [cachedProviderConnectionProjects, draft.providerConnectionId]);
  useEffect(() => {
    setDiscoveredBranches([]);
    setDiscoveredRepositories([]);
    setDiscoveredPipelines([]);
    setBranchDiscoveryError("");
    setRepositoryDiscoveryError("");
    setPipelineDiscoveryError("");
    branchDiscoveryKeyRef.current = "";
    repositoryDiscoveryKeyRef.current = "";
    pipelineDiscoveryKeyRef.current = "";
  }, [draft.driver.organization, draft.driver.project]);
  useEffect(() => {
    setDiscoveredBranches([]);
    setBranchDiscoveryError("");
    branchDiscoveryKeyRef.current = "";
  }, [draft.driver.repository]);

  useEffect(() => {
    if (
      draft.deploymentDriver !== "pipeline_trigger"
      || draft.providerConnectionId.trim() === ""
      || draft.driver.project.trim() !== ""
      || cachedProviderConnectionProjects.length !== 1
    ) {
      return;
    }
    const onlyProject = cachedProviderConnectionProjects[0];
    if (!onlyProject?.name) {
      return;
    }
    const resolvedOrganization =
      deriveAzureDevOpsAccountUrl(onlyProject.webUrl ?? "") || selectedProviderConnectionDiscoveryUrl;
    if (resolvedOrganization === "") {
      return;
    }
    setDraft((current) => ({
      ...current,
      driver: {
        ...current.driver,
        organization: resolvedOrganization,
        project: onlyProject.name,
      },
    }));
  }, [
    cachedProviderConnectionProjects,
    draft.deploymentDriver,
    draft.driver.project,
    draft.providerConnectionId,
    selectedProviderConnectionDiscoveryUrl,
    setDraft,
  ]);

  useEffect(() => {
    if (draft.deploymentDriver !== "pipeline_trigger") {
      return;
    }
    if (draft.providerConnectionId.trim() === "") {
      return;
    }
    if (draft.driver.project.trim() === "") {
      return;
    }
    if (cachedProviderConnectionProjects.length === 0) {
      return;
    }
    const matchingProject = cachedProviderConnectionProjects.find(
      (projectOption) => projectOption.name === draft.driver.project.trim()
    );
    if (matchingProject) {
      return;
    }
    setDraft((current) => ({
      ...current,
      driver: {
        ...current.driver,
        organization: "",
        project: "",
        repository: "",
        pipelineId: "",
      },
    }));
  }, [
    cachedProviderConnectionProjects,
    draft.deploymentDriver,
    draft.driver.project,
    draft.providerConnectionId,
    setDraft,
  ]);

  useEffect(() => {
    if (draft.deploymentDriver !== "pipeline_trigger") {
      return;
    }
    if (draft.providerConnectionId.trim() !== "") {
      return;
    }
    if (verifiedPipelineProviderConnections.length !== 1) {
      return;
    }
    const onlyConnectionId = (verifiedPipelineProviderConnections[0]?.id ?? "").trim();
    if (onlyConnectionId === "") {
      return;
    }
    setDraft((current) => {
      if (current.providerConnectionId.trim() !== "") {
        return current;
      }
      return {
        ...current,
        providerConnectionId: onlyConnectionId,
      };
    });
  }, [
    draft.deploymentDriver,
    draft.providerConnectionId,
    setDraft,
    verifiedPipelineProviderConnections,
  ]);

  useEffect(() => {
    if (draft.deploymentDriver === "pipeline_trigger" && selectedReleaseSystem !== "azure_devops") {
      setSelectedReleaseSystem("azure_devops");
    }
  }, [draft.deploymentDriver, selectedReleaseSystem]);

  useEffect(() => {
    const linkedProvider = normalizeReleaseSystem(selectedReleaseIngestEndpoint?.provider);
    if (linkedProvider && linkedProvider !== selectedReleaseSystem) {
      setSelectedReleaseSystem(linkedProvider);
    }
  }, [selectedReleaseIngestEndpoint, selectedReleaseSystem]);

  useEffect(() => {
    const linkedProvider = normalizeReleaseSystem(selectedReleaseIngestEndpoint?.provider);
    if (!linkedProvider || linkedProvider === effectiveReleaseSystem) {
      return;
    }
    setDraft((current) => ({
      ...current,
      releaseIngestEndpointId: "",
    }));
  }, [effectiveReleaseSystem, selectedReleaseIngestEndpoint, setDraft]);

  const discoverAdoPipelines = useCallback(async (options?: { silent?: boolean }): Promise<void> => {
    if (!project?.id) {
      return;
    }
    if (selectedProviderConnectionRequiresVerification) {
      const message =
        "Open Admin → Deployment Connections, verify the selected connection, and select an Azure DevOps project before MAPPO can browse pipelines.";
      setPipelineDiscoveryError(message);
      if (!options?.silent) {
        toast.error(message);
      }
      return;
    }
    const resolvedOrganization = resolvedAdoOrganization || undefined;
    const resolvedProject = resolvedAdoProject || undefined;
    setIsDiscoveringPipelines(true);
    setPipelineDiscoveryError("");
    try {
      const response = await onDiscoverAdoPipelines(project.id, {
        organization: resolvedOrganization,
        project: resolvedProject,
        providerConnectionId: draft.providerConnectionId.trim() || undefined,
      });
      const pipelines = [...(response.pipelines ?? [])].sort((a, b) =>
        `${a.name ?? ""}`.localeCompare(`${b.name ?? ""}`, undefined, { sensitivity: "base" })
      );
      setDiscoveredPipelines(pipelines);
      const currentPipelineId = draft.driver.pipelineId.trim();
      const hasMatchingSelectedPipeline =
        currentPipelineId !== "" && pipelines.some((pipeline) => pipeline.id === currentPipelineId);
      if (!hasMatchingSelectedPipeline && currentPipelineId === "" && pipelines.length === 1) {
        setDraft((current) => ({
          ...current,
          driver: { ...current.driver, pipelineId: pipelines[0]?.id ?? "" },
        }));
      }
      if (!options?.silent) {
        toast.success(
          `Discovered ${pipelines.length} pipeline${pipelines.length === 1 ? "" : "s"} from ${response.organization}/${response.project}.`
        );
      }
    } catch (error) {
      const message = normalizeDiscoveryError((error as Error).message, "Azure DevOps");
      setPipelineDiscoveryError(message);
      if (!options?.silent) {
        toast.error(`Unable to load Azure DevOps pipelines. ${message}`);
      }
    } finally {
      setIsDiscoveringPipelines(false);
    }
  }, [
    draft.driver.pipelineId,
    draft.providerConnectionId,
    onDiscoverAdoPipelines,
    project?.id,
    resolvedAdoOrganization,
    resolvedAdoProject,
    selectedProviderConnectionRequiresVerification,
    setDraft,
  ]);

  const discoverAdoRepositories = useCallback(async (options?: { silent?: boolean }): Promise<void> => {
    if (!project?.id) {
      return;
    }
    if (selectedProviderConnectionRequiresVerification) {
      const message =
        "Open Admin → Deployment Connections, verify the selected connection, and select an Azure DevOps project before MAPPO can browse repositories.";
      setRepositoryDiscoveryError(message);
      if (!options?.silent) {
        toast.error(message);
      }
      return;
    }
    const resolvedOrganization = resolvedAdoOrganization || undefined;
    const resolvedProject = resolvedAdoProject || undefined;
    setIsDiscoveringRepositories(true);
    setRepositoryDiscoveryError("");
    try {
      const response = await onDiscoverAdoRepositories(project.id, {
        organization: resolvedOrganization,
        project: resolvedProject,
        providerConnectionId: draft.providerConnectionId.trim() || undefined,
      });
      const repositories = [...(response.repositories ?? [])].sort((a, b) =>
        `${a.name ?? ""}`.localeCompare(`${b.name ?? ""}`, undefined, { sensitivity: "base" })
      );
      setDiscoveredRepositories(repositories);
      const currentRepositoryValue = draft.driver.repository.trim();
      const selectedRepository = repositories.find(
        (repository) =>
          repository.name === currentRepositoryValue || repository.id === currentRepositoryValue
      );
      const onlyRepository = repositories.length === 1 ? repositories[0] : null;
      const repositoryToApply = selectedRepository ?? (currentRepositoryValue === "" ? onlyRepository : null);
      if (repositoryToApply) {
        setDraft((current) => ({
          ...current,
          driver: {
            ...current.driver,
            repository:
              current.driver.repository.trim() === ""
                ? (repositoryToApply?.name ?? "")
                : current.driver.repository,
            branch:
              current.driver.branch.trim() === "" || current.driver.branch.trim() === "main"
                ? (repositoryToApply?.defaultBranch?.replace(/^refs\/heads\//, "") ?? current.driver.branch)
                : current.driver.branch,
          },
        }));
      }
      if (!options?.silent) {
        toast.success(
          `Discovered ${repositories.length} repositor${repositories.length === 1 ? "y" : "ies"} from ${response.organization}/${response.project}.`
        );
      }
    } catch (error) {
      const message = normalizeDiscoveryError((error as Error).message, "Azure DevOps");
      setRepositoryDiscoveryError(message);
      if (!options?.silent) {
        toast.error(`Unable to load Azure DevOps repositories. ${message}`);
      }
    } finally {
      setIsDiscoveringRepositories(false);
    }
  }, [
    draft.driver.repository,
    draft.providerConnectionId,
    onDiscoverAdoRepositories,
    project?.id,
    resolvedAdoOrganization,
    resolvedAdoProject,
    selectedProviderConnectionRequiresVerification,
    setDraft,
  ]);

  const discoverAdoBranches = useCallback(async (options?: { silent?: boolean }): Promise<void> => {
    if (!project?.id) {
      return;
    }
    if (selectedProviderConnectionRequiresVerification) {
      const message =
        "Open Admin → Deployment Connections, verify the selected connection, and select an Azure DevOps project before MAPPO can browse branches.";
      setBranchDiscoveryError(message);
      if (!options?.silent) {
        toast.error(message);
      }
      return;
    }
    const repositoryId = selectedDiscoveredRepositoryId === "__none" ? undefined : selectedDiscoveredRepositoryId;
    const repository = draft.driver.repository.trim() || undefined;
    if (!repositoryId && !repository) {
      const message = "Choose an Azure DevOps repository before MAPPO can load branches.";
      setBranchDiscoveryError(message);
      if (!options?.silent) {
        toast.error(message);
      }
      return;
    }
    const resolvedOrganization = resolvedAdoOrganization || undefined;
    const resolvedProject = resolvedAdoProject || undefined;
    setIsDiscoveringBranches(true);
    setBranchDiscoveryError("");
    try {
      const response = await onDiscoverAdoBranches(project.id, {
        organization: resolvedOrganization,
        project: resolvedProject,
        providerConnectionId: draft.providerConnectionId.trim() || undefined,
        repositoryId,
        repository,
      });
      const branches = [...(response.branches ?? [])].sort((a, b) =>
        `${a.name ?? ""}`.localeCompare(`${b.name ?? ""}`, undefined, { sensitivity: "base" })
      );
      setDiscoveredBranches(branches);
      const currentBranch = draft.driver.branch.trim();
      const hasMatchingSelectedBranch =
        currentBranch !== ""
        && branches.some((branch) => branch.name === currentBranch || branch.refName === currentBranch);
      if (!hasMatchingSelectedBranch) {
        const preferredBranch =
          branches.find(
            (branch) =>
              branch.name === selectedDiscoveredRepository?.defaultBranch?.replace(/^refs\/heads\//, "")
          ) ?? (branches.length === 1 ? branches[0] : null);
        if (preferredBranch && (currentBranch === "" || currentBranch === "main")) {
          setDraft((current) => ({
            ...current,
            driver: { ...current.driver, branch: preferredBranch.name },
          }));
        }
      }
      if (!options?.silent) {
        toast.success(
          `Discovered ${branches.length} branch${branches.length === 1 ? "" : "es"} for ${response.repository || draft.driver.repository.trim()}.`
        );
      }
    } catch (error) {
      const message = normalizeDiscoveryError((error as Error).message, "Azure DevOps");
      setBranchDiscoveryError(message);
      if (!options?.silent) {
        toast.error(`Unable to load Azure DevOps branches. ${message}`);
      }
    } finally {
      setIsDiscoveringBranches(false);
    }
  }, [
    draft.driver.branch,
    draft.driver.repository,
    draft.providerConnectionId,
    onDiscoverAdoBranches,
    project?.id,
    resolvedAdoOrganization,
    resolvedAdoProject,
    selectedDiscoveredRepository?.defaultBranch,
    selectedDiscoveredRepositoryId,
    selectedProviderConnectionRequiresVerification,
    setDraft,
  ]);

  useEffect(() => {
    if (
      draft.deploymentDriver !== "pipeline_trigger"
      || draft.driver.pipelineSystem !== "azure_devops"
      || draft.providerConnectionId.trim() === ""
      || selectedProviderConnectionRequiresVerification
      || selectedProviderConnectionDiscoveryUrl === ""
      || !hasSelectedAzureDevOpsProject
    ) {
      return;
    }

    const discoveryKey = `${draft.providerConnectionId.trim()}|${resolvedAdoOrganization}|${resolvedAdoProject}`;

    if (repositoryDiscoveryKeyRef.current !== discoveryKey) {
      repositoryDiscoveryKeyRef.current = discoveryKey;
      void discoverAdoRepositories({ silent: true });
    }
    const branchDiscoveryKey = `${discoveryKey}|${draft.driver.repository.trim()}`;
    if (draft.driver.repository.trim() !== "" && branchDiscoveryKeyRef.current !== branchDiscoveryKey) {
      branchDiscoveryKeyRef.current = branchDiscoveryKey;
      void discoverAdoBranches({ silent: true });
    }
    if (pipelineDiscoveryKeyRef.current !== discoveryKey) {
      pipelineDiscoveryKeyRef.current = discoveryKey;
      void discoverAdoPipelines({ silent: true });
    }
  }, [
    discoverAdoBranches,
    discoverAdoPipelines,
    discoverAdoRepositories,
    draft.deploymentDriver,
    draft.driver.pipelineSystem,
    draft.driver.repository,
    draft.providerConnectionId,
    hasSelectedAzureDevOpsProject,
    resolvedAdoOrganization,
    resolvedAdoProject,
    selectedProviderConnectionDiscoveryUrl,
    selectedProviderConnectionRequiresVerification,
  ]);

  return {
    releaseIngestEndpoints,
    providerConnections,
    isLoadingReleaseIngestEndpoints,
    isLoadingProviderConnections,
    selectedReleaseSystem,
    setSelectedReleaseSystem,
    selectedProviderConnection,
    selectedProviderConnectionRequiresVerification,
    selectedProviderConnectionDiscoveryUrl,
    verifiedPipelineProviderConnections,
    providerConnectionOptions,
    releaseIngestEndpointOptions,
    selectedReleaseIngestEndpoint,
    availableReleaseSystems,
    effectiveReleaseSystem,
    releaseSourceTypeLabel,
    selectedDeploymentSystem,
    selectedDiscoveredAdoProjectId,
    selectedDiscoveredAdoProject,
    cachedProviderConnectionProjects,
    resolvedAdoOrganization,
    resolvedAdoProject,
    canSelectAzureDevOpsProject,
    hasSelectedAzureDevOpsProject,
    canDiscoverAzureDevOpsProjectResources,
    discoveredBranches,
    discoveredRepositories,
    discoveredPipelines,
    isDiscoveringBranches,
    isDiscoveringRepositories,
    isDiscoveringPipelines,
    branchDiscoveryError,
    repositoryDiscoveryError,
    pipelineDiscoveryError,
    selectedDiscoveredPipelineId,
    selectedDiscoveredRepositoryId,
    selectedDiscoveredRepository,
    selectedDiscoveredBranchRef,
    hasSavedPipelineOutsideDiscovery,
    hasSingleCachedAdoProject,
    hasSingleDiscoveredBranch,
    hasSingleDiscoveredRepository,
    hasSingleDiscoveredPipeline,
    isVerifiedAzureDevOpsConnection,
    refreshReleaseIngestEndpointOptions,
    refreshProviderConnectionOptions,
    discoverAdoBranches,
    discoverAdoPipelines,
    discoverAdoRepositories,
  };
}
