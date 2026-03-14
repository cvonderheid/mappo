import { ForwarderLogsDataTable } from "@/components/AdminTables";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

type ManagedAppPageProps = {
  refreshKey: number;
};

export default function ManagedAppPage({ refreshKey }: ManagedAppPageProps) {
  return (
    <div className="space-y-4">
      <div className="animate-fade-up [animation-delay:60ms] [animation-fill-mode:forwards]">
        <p className="text-xs text-muted-foreground">
          Managed App forwarder operational logs and delivery diagnostics.
        </p>
      </div>
      <Card className="glass-card animate-fade-up [animation-delay:100ms] [animation-fill-mode:forwards]">
        <CardHeader>
          <CardTitle>Forwarder Logs</CardTitle>
        </CardHeader>
        <CardContent>
          <ForwarderLogsDataTable refreshKey={refreshKey} />
        </CardContent>
      </Card>
    </div>
  );
}
