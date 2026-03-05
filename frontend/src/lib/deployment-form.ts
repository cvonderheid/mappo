import type { StrategyMode } from "@/lib/types";

export type StartRunFormState = {
  strategyMode: StrategyMode;
  concurrency: number;
  maxFailureCount: string;
  maxFailureRatePercent: string;
};

export const DEFAULT_FORM: StartRunFormState = {
  strategyMode: "waves",
  concurrency: 3,
  maxFailureCount: "2",
  maxFailureRatePercent: "35",
};

