export type TargetStage =
  | "QUEUED"
  | "VALIDATING"
  | "DEPLOYING"
  | "VERIFYING"
  | "SUCCEEDED"
  | "FAILED";

export type RunStatus = "running" | "succeeded" | "failed" | "partial" | "halted";
export type StrategyMode = "all_at_once" | "waves";

export type Target = {
  id: string;
  tenant_id: string;
  subscription_id: string;
  managed_app_id: string;
  tags: Record<string, string>;
  last_deployed_release: string;
  health_status: string;
  last_check_in_at: string;
};

export type Release = {
  id: string;
  template_spec_id: string;
  template_spec_version: string;
  parameter_defaults: Record<string, string>;
  release_notes: string;
  verification_hints: string[];
  created_at: string;
};

export type StopPolicy = {
  max_failure_count?: number;
  max_failure_rate?: number;
};

export type RunSummary = {
  id: string;
  release_id: string;
  status: RunStatus;
  strategy_mode: StrategyMode;
  created_at: string;
  started_at: string | null;
  ended_at: string | null;
  total_targets: number;
  succeeded_targets: number;
  failed_targets: number;
  in_progress_targets: number;
  queued_targets: number;
  halt_reason: string | null;
};

export type StructuredError = {
  code: string;
  message: string;
  details?: Record<string, unknown>;
};

export type TargetLogEvent = {
  timestamp: string;
  level: string;
  stage: TargetStage;
  message: string;
  correlation_id: string;
};

export type TargetStageRecord = {
  stage: TargetStage;
  started_at: string;
  ended_at: string | null;
  message: string;
  error: StructuredError | null;
  correlation_id: string;
  portal_link: string;
};

export type TargetExecutionRecord = {
  target_id: string;
  subscription_id: string;
  tenant_id: string;
  status: TargetStage;
  attempt: number;
  updated_at: string;
  stages: TargetStageRecord[];
  logs: TargetLogEvent[];
};

export type RunDetail = {
  id: string;
  release_id: string;
  status: RunStatus;
  strategy_mode: StrategyMode;
  wave_tag: string;
  wave_order: string[];
  concurrency: number;
  stop_policy: StopPolicy;
  created_at: string;
  started_at: string | null;
  ended_at: string | null;
  updated_at: string;
  halt_reason: string | null;
  target_records: TargetExecutionRecord[];
};

export type CreateRunRequest = {
  release_id: string;
  strategy_mode: StrategyMode;
  wave_tag: string;
  wave_order: string[];
  concurrency: number;
  target_tags: Record<string, string>;
  stop_policy: StopPolicy;
};
