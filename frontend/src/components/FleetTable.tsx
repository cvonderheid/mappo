import type { Target } from "@/lib/types";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";

type FleetTableProps = {
  targets: Target[];
};

function healthVariant(healthStatus: string): "default" | "secondary" | "destructive" | "outline" {
  if (healthStatus === "healthy") {
    return "default";
  }
  if (healthStatus === "degraded") {
    return "secondary";
  }
  return "destructive";
}

export default function FleetTable({ targets }: FleetTableProps) {
  return (
    <Card className="glass-card animate-fade-up [animation-delay:80ms] [animation-fill-mode:forwards]">
      <CardHeader className="flex-row items-center justify-between space-y-0">
        <CardTitle>Fleet Targets</CardTitle>
        <Badge variant="outline" className="font-mono text-[11px]">
          {targets.length} subscriptions
        </Badge>
      </CardHeader>
      <CardContent>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Target</TableHead>
              <TableHead>Tenant</TableHead>
              <TableHead>Subscription</TableHead>
              <TableHead>Target Group</TableHead>
              <TableHead>Region</TableHead>
              <TableHead>Tier</TableHead>
              <TableHead>Version</TableHead>
              <TableHead>Health</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {targets.map((target) => (
              <TableRow key={target.id}>
                <TableCell className="font-mono text-xs">{target.id}</TableCell>
                <TableCell>{target.tenant_id}</TableCell>
                <TableCell>{target.subscription_id}</TableCell>
                <TableCell className="capitalize">{target.tags.ring}</TableCell>
                <TableCell>{target.tags.region}</TableCell>
                <TableCell>{target.tags.tier}</TableCell>
                <TableCell>{target.last_deployed_release}</TableCell>
                <TableCell>
                  <Badge variant={healthVariant(target.health_status)} className="capitalize">
                    {target.health_status}
                  </Badge>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  );
}
