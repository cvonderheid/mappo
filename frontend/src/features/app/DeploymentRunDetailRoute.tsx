import { useEffect } from "react";
import { useParams } from "react-router-dom";

import { RunDetailPanel } from "@/features/app/lazyRoutes";
import type { RunDetail } from "@/lib/types";

type DeploymentRunDetailRouteProps = {
  errorMessage: string;
  runDetail: RunDetail | null;
  onRunChange: (runId: string) => void;
};

export default function DeploymentRunDetailRoute({
  errorMessage,
  runDetail,
  onRunChange,
}: DeploymentRunDetailRouteProps) {
  const params = useParams<{ runId: string }>();
  const runId = params.runId ? decodeURIComponent(params.runId) : "";

  useEffect(() => {
    if (runId) {
      onRunChange(runId);
    }
  }, [onRunChange, runId]);

  return (
    <>
      {errorMessage ? (
        <div className="rounded-md border border-destructive/60 bg-destructive/10 p-2 text-xs text-destructive-foreground">
          {errorMessage}
        </div>
      ) : null}
      <RunDetailPanel run={runDetail && runDetail.id === runId ? runDetail : null} />
    </>
  );
}
