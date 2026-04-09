import { ForwarderLogsDataTable } from "@/components/AdminTables";

type ManagedAppPageProps = {
  refreshKey: number;
};

export default function ManagedAppPage({ refreshKey }: ManagedAppPageProps) {
  return (
    <div className="space-y-4">
      <ForwarderLogsDataTable
        refreshKey={refreshKey}
        title="Forwarder Logs"
        description="Managed App forwarder operational logs and delivery diagnostics."
        cardClassName="glass-card animate-fade-up [animation-delay:100ms] [animation-fill-mode:forwards]"
      />
    </div>
  );
}
