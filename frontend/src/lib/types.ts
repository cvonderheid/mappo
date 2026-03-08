import type { components } from "@/lib/api/generated/schema";

type Schemas = components["schemas"];

export type CreateRunRequest = Schemas["RunCreateRequest"];
export type AdminOnboardingSnapshotResponse = Schemas["OnboardingSnapshotRecord"];
export type DeleteTargetRegistrationResponse = Schemas["DeleteRegistrationResultRecord"];
export type ForwarderLogIngestRequest = components["schemas"]["ForwarderLogIngestRequest"];
export type ForwarderLogIngestResponse = Schemas["ForwarderLogIngestResultRecord"];
export type ForwarderLogIngestStatus =
  NonNullable<Schemas["ForwarderLogIngestResultRecord"]["status"]>;
export type ForwarderLogLevel = NonNullable<Schemas["ForwarderLogRecord"]["level"]>;
export type ForwarderLogRecord = Schemas["ForwarderLogRecord"];
export type MarketplaceEventIngestRequest = Schemas["OnboardingEventRequest"];
export type MarketplaceEventIngestResponse = Schemas["EventIngestResultRecord"];
export type MarketplaceEventRecord = Schemas["MarketplaceEventRecord"];
export type MarketplaceEventStatus = NonNullable<Schemas["MarketplaceEventRecord"]["status"]>;
export type Release = Schemas["ReleaseRecord"];
export type ReleaseManifestIngestRequest = Schemas["ReleaseManifestIngestRequest"];
export type ReleaseManifestIngestResponse = Schemas["ReleaseManifestIngestResultRecord"];
export type RunDetail = Schemas["RunDetailRecord"];
export type RunPreview = Schemas["RunPreviewRecord"];
export type RunPreviewChange = Schemas["RunPreviewChangeRecord"];
export type RunPreviewPropertyChange = Schemas["RunPreviewPropertyChangeRecord"];
export type RunTargetPreview = Schemas["RunTargetPreviewRecord"];
export type RunStatus = NonNullable<Schemas["RunSummaryRecord"]["status"]>;
export type RunSummary = Schemas["RunSummaryRecord"];
export type StopPolicy = Schemas["RunStopPolicyRecord"];
export type StrategyMode = NonNullable<Schemas["RunCreateRequest"]["strategyMode"]>;
export type StructuredError = Schemas["StageErrorRecord"];
export type Target = Schemas["TargetRecord"];
export type TargetRegistrationRecord = Schemas["TargetRegistrationRecord"];
export type TargetExecutionRecord = Schemas["RunTargetRecord"];
export type TargetLogEvent = Schemas["TargetLogEventRecord"];
export type TargetStage = NonNullable<Schemas["TargetStageRecord"]["stage"]>;
export type TargetStageRecord = Schemas["TargetStageRecord"];
export type UpdateTargetRegistrationRequest = Schemas["TargetRegistrationPatchRequest"];
