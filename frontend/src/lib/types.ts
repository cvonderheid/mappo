import type { components, operations } from "@/lib/api/generated/schema";

type Schemas = components["schemas"];

export type CreateRunRequest = Schemas["RunCreateRequest"];
export type DeleteTargetRegistrationResponse = Schemas["DeleteRegistrationResultRecord"];
export type ForwarderLogIngestRequest = components["schemas"]["ForwarderLogIngestRequest"];
export type ForwarderLogIngestResponse = Schemas["ForwarderLogIngestResultRecord"];
export type ForwarderLogIngestStatus =
  NonNullable<Schemas["ForwarderLogIngestResultRecord"]["status"]>;
export type ForwarderLogLevel = NonNullable<Schemas["ForwarderLogRecord"]["level"]>;
export type ForwarderLogRecord = Schemas["ForwarderLogRecord"];
export type ForwarderLogPage = Schemas["ForwarderLogPageRecord"];
export type PageMetadata = Schemas["PageMetadataRecord"];
export type ListRunsQuery = NonNullable<operations["listRuns"]["parameters"]["query"]>;
export type ListTargetsPageQuery = NonNullable<operations["listTargetsPage"]["parameters"]["query"]>;
export type ListProjectAuditQuery = NonNullable<operations["listProjectAudit"]["parameters"]["query"]>;
export type ListTargetRegistrationsQuery =
  NonNullable<operations["listRegistrations"]["parameters"]["query"]>;
export type ListMarketplaceEventsQuery =
  NonNullable<operations["listMarketplaceEvents"]["parameters"]["query"]>;
export type ListForwarderLogsQuery =
  NonNullable<operations["listForwarderLogsPage"]["parameters"]["query"]>;
export type ListReleaseWebhookDeliveriesQuery =
  NonNullable<operations["listReleaseWebhookDeliveries"]["parameters"]["query"]>;
export type MarketplaceEventIngestRequest = Schemas["OnboardingEventRequest"];
export type MarketplaceEventIngestResponse = Schemas["EventIngestResultRecord"];
export type MarketplaceEventRecord = Schemas["MarketplaceEventRecord"];
export type MarketplaceEventPage = Schemas["MarketplaceEventPageRecord"];
export type MarketplaceEventStatus = NonNullable<Schemas["MarketplaceEventRecord"]["status"]>;
export type ProjectDefinition = Schemas["ProjectDefinition"] & {
  providerConnectionId?: string;
};
export type ProjectCreateRequest = Schemas["ProjectCreateRequest"] & {
  providerConnectionId?: string;
};
export type ProjectConfigurationPatchRequest = Schemas["ProjectConfigurationPatchRequest"] & {
  providerConnectionId?: string;
};
export type ProjectConfigurationAuditAction =
  NonNullable<Schemas["ProjectConfigurationAuditRecord"]["action"]>;
export type ProjectConfigurationAuditRecord = Schemas["ProjectConfigurationAuditRecord"];
export type ProjectConfigurationAuditPage = Schemas["ProjectConfigurationAuditPageRecord"];
export type ProjectValidationRequest = Schemas["ProjectValidationRequest"];
export type ProjectValidationResult = Schemas["ProjectValidationResultRecord"];
export type ProjectValidationFinding = Schemas["ProjectValidationFindingRecord"];
export type DiscoverProjectAdoPipelinesRequest = {
  organization?: string;
  project?: string;
  providerConnectionId?: string;
  nameContains?: string;
};
export type DiscoverProjectAdoRepositoriesRequest = {
  organization?: string;
  project?: string;
  providerConnectionId?: string;
  nameContains?: string;
};
export type DiscoverProjectAdoServiceConnectionsRequest = {
  organization?: string;
  project?: string;
  providerConnectionId?: string;
  nameContains?: string;
};
export type ProjectAdoRepository = {
  id: string;
  name: string;
  defaultBranch?: string;
  webUrl?: string;
  remoteUrl?: string;
};
export type ProjectAdoRepositoryDiscoveryResult = {
  projectId: string;
  organization: string;
  project: string;
  repositories: ProjectAdoRepository[];
};
export type ProjectAdoPipeline = {
  id: string;
  name: string;
  folder?: string;
  webUrl?: string;
};
export type ProjectAdoPipelineDiscoveryResult = {
  projectId: string;
  organization: string;
  project: string;
  pipelines: ProjectAdoPipeline[];
};
export type ProjectAdoServiceConnection = {
  id: string;
  name: string;
  type?: string;
  webUrl?: string;
};
export type ProjectAdoServiceConnectionDiscoveryResult = {
  projectId: string;
  organization: string;
  project: string;
  serviceConnections: ProjectAdoServiceConnection[];
};
export type Release = Schemas["ReleaseRecord"];
export type ReleaseIngestEndpoint = Schemas["ReleaseIngestEndpointRecord"];
export type ReleaseIngestEndpointCreateRequest = Schemas["ReleaseIngestEndpointCreateRequest"];
export type ReleaseIngestEndpointPatchRequest = Schemas["ReleaseIngestEndpointPatchRequest"];
export type ProviderConnectionProvider = "azure_devops";
export type ProviderConnectionLinkedProject = {
  projectId?: string;
  projectName?: string;
  projectDisplayName?: string;
};
export type ProviderConnection = {
  id?: string;
  name?: string;
  provider?: ProviderConnectionProvider;
  enabled?: boolean;
  organizationUrl?: string;
  personalAccessTokenRef?: string;
  linkedProjects?: ProviderConnectionLinkedProject[];
  createdAt?: string;
  updatedAt?: string;
};
export type ProviderConnectionCreateRequest = {
  id: string;
  name: string;
  provider: ProviderConnectionProvider;
  enabled?: boolean;
  organizationUrl?: string;
  personalAccessTokenRef?: string;
};
export type ProviderConnectionPatchRequest = {
  name?: string;
  provider?: ProviderConnectionProvider;
  enabled?: boolean;
  organizationUrl?: string;
  personalAccessTokenRef?: string;
};
export type ProviderConnectionVerifyRequest = {
  id?: string;
  provider?: ProviderConnectionProvider;
  organizationUrl?: string;
  personalAccessTokenRef?: string;
};
export type ProviderConnectionAdoProject = {
  id: string;
  name: string;
  webUrl?: string;
};
export type ProviderConnectionAdoProjectDiscoveryResult = {
  connectionId: string;
  organizationUrl: string;
  projects: ProviderConnectionAdoProject[];
};
export type ReleaseWebhookDeliveryRecord = Schemas["ReleaseWebhookDeliveryRecord"];
export type ReleaseWebhookDeliveryPage = Schemas["ReleaseWebhookDeliveryPageRecord"];
export type ReleaseWebhookStatus = NonNullable<Schemas["ReleaseWebhookDeliveryRecord"]["status"]>;
export type ReleaseManifestIngestRequest = Schemas["ReleaseManifestIngestRequest"];
export type ReleaseManifestIngestResponse = Schemas["ReleaseManifestIngestResultRecord"];
export type RunDetail = Schemas["RunDetailRecord"];
export type RunPreview = Schemas["RunPreviewRecord"];
export type RunPreviewChange = Schemas["RunPreviewChangeRecord"];
export type RunPreviewPropertyChange = Schemas["RunPreviewPropertyChangeRecord"];
export type RunSummaryPage = Schemas["RunSummaryPageRecord"];
export type RunTargetPreview = Schemas["RunTargetPreviewRecord"];
export type RunStatus = NonNullable<Schemas["RunSummaryRecord"]["status"]>;
export type RunSummary = Schemas["RunSummaryRecord"];
export type StopPolicy = Schemas["RunStopPolicyRecord"];
export type StrategyMode = NonNullable<Schemas["RunCreateRequest"]["strategyMode"]>;
export type StructuredError = Schemas["StageErrorRecord"];
export type Target = Schemas["TargetRecord"];
export type TargetPage = Schemas["TargetPageRecord"];
export type TargetRegistrationPage = Schemas["TargetRegistrationPageRecord"];
export type TargetRegistrationRecord = Schemas["TargetRegistrationRecord"];
export type TargetExecutionRecord = Schemas["RunTargetRecord"];
export type TargetLogEvent = Schemas["TargetLogEventRecord"];
export type TargetRuntimeStatus = NonNullable<Schemas["TargetRecord"]["runtimeStatus"]>;
export type TargetStage = NonNullable<Schemas["TargetStageRecord"]["stage"]>;
export type TargetStageRecord = Schemas["TargetStageRecord"];
export type UpdateTargetRegistrationRequest = Schemas["TargetRegistrationPatchRequest"];
