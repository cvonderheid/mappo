import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";

import { listProviderConnections, listReleaseIngestEndpoints } from "@/lib/api";
import type { ProviderConnection, ReleaseIngestEndpoint } from "@/lib/types";

export function useProjectSettingsOptionLists() {
  const [releaseIngestEndpoints, setReleaseIngestEndpoints] = useState<ReleaseIngestEndpoint[]>([]);
  const [providerConnections, setProviderConnections] = useState<ProviderConnection[]>([]);
  const [isLoadingReleaseIngestEndpoints, setIsLoadingReleaseIngestEndpoints] = useState(false);
  const [isLoadingProviderConnections, setIsLoadingProviderConnections] = useState(false);

  const refreshReleaseIngestEndpointOptions = useCallback(async (silent = false): Promise<void> => {
    if (!silent) {
      setIsLoadingReleaseIngestEndpoints(true);
    }
    try {
      const endpoints = await listReleaseIngestEndpoints();
      setReleaseIngestEndpoints(endpoints ?? []);
    } catch (error) {
      toast.error((error as Error).message);
    } finally {
      setIsLoadingReleaseIngestEndpoints(false);
    }
  }, []);

  const refreshProviderConnectionOptions = useCallback(async (silent = false): Promise<void> => {
    if (!silent) {
      setIsLoadingProviderConnections(true);
    }
    try {
      const connections = await listProviderConnections();
      setProviderConnections(connections ?? []);
    } catch (error) {
      toast.error((error as Error).message);
    } finally {
      setIsLoadingProviderConnections(false);
    }
  }, []);

  useEffect(() => {
    void refreshReleaseIngestEndpointOptions(true);
    void refreshProviderConnectionOptions(true);
  }, [refreshProviderConnectionOptions, refreshReleaseIngestEndpointOptions]);

  return {
    releaseIngestEndpoints,
    providerConnections,
    isLoadingReleaseIngestEndpoints,
    isLoadingProviderConnections,
    refreshReleaseIngestEndpointOptions,
    refreshProviderConnectionOptions,
  };
}
