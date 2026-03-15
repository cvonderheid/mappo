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
export type ProjectDefinition = Schemas["ProjectDefinition"];
export type ProjectCreateRequest = Schemas["ProjectCreateRequest"];
export type ProjectConfigurationPatchRequest = Schemas["ProjectConfigurationPatchRequest"];
export type ProjectConfigurationAuditAction =
  NonNullable<Schemas["ProjectConfigurationAuditRecord"]["action"]>;
export type ProjectConfigurationAuditRecord = Schemas["ProjectConfigurationAuditRecord"];
export type ProjectConfigurationAuditPage = Schemas["ProjectConfigurationAuditPageRecord"];
export type ProjectValidationRequest = Schemas["ProjectValidationRequest"];
export type ProjectValidationResult = Schemas["ProjectValidationResultRecord"];
export type ProjectValidationFinding = Schemas["ProjectValidationFindingRecord"];
export type Release = Schemas["ReleaseRecord"];
export type ReleaseIngestEndpoint = Schemas["ReleaseIngestEndpointRecord"];
export type ReleaseIngestEndpointCreateRequest = Schemas["ReleaseIngestEndpointCreateRequest"];
export type ReleaseIngestEndpointPatchRequest = Schemas["ReleaseIngestEndpointPatchRequest"];
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
