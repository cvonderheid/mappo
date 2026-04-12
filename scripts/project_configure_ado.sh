#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
API_BASE_URL="${MAPPO_API_BASE_URL:-}"
PROJECT_ID="azure-appservice-ado-pipeline"
PROJECT_NAME=""
ADO_ORGANIZATION=""
ADO_PROJECT=""
ADO_PIPELINE_ID=""
ADO_BRANCH="main"
RUNTIME_HEALTH_PATH=""

usage() {
  cat <<EOF
usage: $(basename "$0") [options]

Patch MAPPO project configuration for the Azure DevOps pipeline project.

Options:
  --api-base-url <url>            MAPPO API base URL (default: MAPPO_API_BASE_URL)
  --project-id <id>               Project ID (default: azure-appservice-ado-pipeline)
  --project-name <name>           Optional project display name
  --ado-organization <value>      ADO organization URL (e.g. https://dev.azure.com/myorg)
  --ado-project <value>           ADO project name
  --ado-pipeline-id <value>       ADO pipeline ID
  --ado-branch <value>            ADO branch filter (default: main)
  --runtime-health-path <path>    Optional runtime health path override
  -h, --help                      Show help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --api-base-url)
      API_BASE_URL="${2:-}"
      shift 2
      ;;
    --project-id)
      PROJECT_ID="${2:-}"
      shift 2
      ;;
    --project-name)
      PROJECT_NAME="${2:-}"
      shift 2
      ;;
    --ado-organization)
      ADO_ORGANIZATION="${2:-}"
      shift 2
      ;;
    --ado-project)
      ADO_PROJECT="${2:-}"
      shift 2
      ;;
    --ado-pipeline-id)
      ADO_PIPELINE_ID="${2:-}"
      shift 2
      ;;
    --ado-branch)
      ADO_BRANCH="${2:-}"
      shift 2
      ;;
    --runtime-health-path)
      RUNTIME_HEALTH_PATH="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "project-configure-ado: unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -z "${API_BASE_URL}" ]]; then
  echo "project-configure-ado: --api-base-url (or MAPPO_API_BASE_URL) is required." >&2
  exit 2
fi
if [[ -z "${ADO_ORGANIZATION}" || -z "${ADO_PROJECT}" || -z "${ADO_PIPELINE_ID}" ]]; then
  echo "project-configure-ado: --ado-organization, --ado-project, and --ado-pipeline-id are required." >&2
  exit 2
fi
if ! command -v jq >/dev/null 2>&1; then
  echo "project-configure-ado: jq is required." >&2
  exit 1
fi
if ! command -v curl >/dev/null 2>&1; then
  echo "project-configure-ado: curl is required." >&2
  exit 1
fi

PAYLOAD="$(jq -n \
  --arg projectName "${PROJECT_NAME}" \
  --arg adoOrganization "${ADO_ORGANIZATION}" \
  --arg adoProject "${ADO_PROJECT}" \
  --arg adoPipelineId "${ADO_PIPELINE_ID}" \
  --arg adoBranch "${ADO_BRANCH}" \
  --arg runtimeHealthPath "${RUNTIME_HEALTH_PATH}" \
  '{
    accessStrategyConfig: {
      authModel: "pipeline_owned",
      requiresAzureCredential: false,
      requiresTargetExecutionMetadata: true
    },
    deploymentDriverConfig: {
      pipelineSystem: "azure_devops",
      organization: $adoOrganization,
      project: $adoProject,
      pipelineId: $adoPipelineId,
      branch: $adoBranch,
      supportsExternalExecutionHandle: true,
      supportsExternalLogs: true
    }
  }
  + (if ($projectName|length) > 0 then {name: $projectName} else {} end)
  + (if ($runtimeHealthPath|length) > 0 then {runtimeHealthProviderConfig: {path: $runtimeHealthPath}} else {} end)')"

PROJECTS_ENDPOINT="${API_BASE_URL%/}/api/v1/projects/${PROJECT_ID}"
echo "project-configure-ado: patching ${PROJECTS_ENDPOINT}"

RESPONSE="$(curl --fail-with-body --silent --show-error \
  -X PATCH "${PROJECTS_ENDPOINT}" \
  -H "content-type: application/json" \
  --data "${PAYLOAD}")"

echo "${RESPONSE}" | jq .
echo "project-configure-ado: completed."
