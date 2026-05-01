import { ForwarderLogsDataTable } from "@/features/admin/AdminTables";

type ForwarderLogsPageProps = {
  refreshKey: number;
};

export default function ForwarderLogsPage({ refreshKey }: ForwarderLogsPageProps) {
  return (
    <div className="space-y-4">
      <ForwarderLogsDataTable
        refreshKey={refreshKey}
        title="Forwarder Logs"
        description="Global inbound forwarder operational logs and delivery diagnostics."
        cardClassName="glass-card animate-fade-up [animation-delay:100ms] [animation-fill-mode:forwards]"
      />
    </div>
  );
}
