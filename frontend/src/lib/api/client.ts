import createClient from "openapi-fetch";

import type { paths } from "@/lib/api/generated/schema";

function resolveBaseUrl(): string {
  const runtimeBaseUrl = window.__MAPPO_RUNTIME_CONFIG__?.apiBaseUrl?.trim();
  if (runtimeBaseUrl) {
    return runtimeBaseUrl;
  }

  const viteBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim();
  if (viteBaseUrl) {
    return viteBaseUrl;
  }

  return "http://localhost:8010";
}

const baseUrl = resolveBaseUrl();

export const apiClient = createClient<paths>({ baseUrl });
