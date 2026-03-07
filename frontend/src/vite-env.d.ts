/// <reference types="vite/client" />

type MappoRuntimeConfig = {
  apiBaseUrl?: string;
};

interface Window {
  __MAPPO_RUNTIME_CONFIG__?: MappoRuntimeConfig;
}
