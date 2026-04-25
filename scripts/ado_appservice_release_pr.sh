#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ADO_ENV_FILE="${ROOT_DIR}/.data/mappo.env"
ORGANIZATION="${MAPPO_DEMO_ADO_ORGANIZATION:-}"
PROJECT="${MAPPO_DEMO_ADO_PROJECT:-}"
REPOSITORY="${MAPPO_DEMO_ADO_REPOSITORY:-}"
TARGET_BRANCH="main"
VERSION="$(date -u +%Y.%m.%d.%H%M)"
DATA_MODEL_VERSION="1"
RELEASE_FILE="/app/release.json"
ADO_PAT="${AZURE_DEVOPS_EXT_PAT:-}"
COMPLETE_PR=true
DRY_RUN=false

usage() {
  cat <<EOF
usage: $(basename "$0") [options]

Create a release branch in the Azure App Service demo repo, update release metadata,
open a PR to main, and optionally complete it so the ADO release-readiness pipeline fires.

Options:
  --organization <url|name>      Azure DevOps organization (or MAPPO_DEMO_ADO_ORGANIZATION)
  --project <name>               Azure DevOps project (or MAPPO_DEMO_ADO_PROJECT)
  --repository <name|id>         Azure DevOps repository (or MAPPO_DEMO_ADO_REPOSITORY)
  --target-branch <name>         Merge target branch (default: main)
  --version <value>              Demo release version (default: UTC timestamp)
  --data-model-version <value>   Demo data model version (default: 1)
  --release-file <path>          Repo file to update (default: /app/release.json)
  --ado-pat <value>              Azure DevOps PAT (default: AZURE_DEVOPS_EXT_PAT or .data/mappo.env)
  --ado-env-file <path>          Optional env file to source first (default: .data/mappo.env)
  --no-complete                  Leave PR open instead of completing it
  --dry-run                      Print planned operations without calling Azure DevOps
  -h, --help                     Show help
EOF
}

normalize_org() {
  local raw="${1:-}"
  raw="${raw%/}"
  if [[ "${raw}" == https://* ]]; then
    echo "${raw}"
  elif [[ "${raw}" == dev.azure.com/* ]]; then
    echo "https://${raw}"
  else
    echo "https://dev.azure.com/${raw}"
  fi
}

urlencode() {
  jq -rn --arg value "$1" '$value|@uri'
}

curl_ado() {
  local method="$1"
  local url="$2"
  local body="${3:-}"
  if [[ "${DRY_RUN}" == "true" ]]; then
    echo "ado-appservice-release-pr: ${method} ${url}" >&2
    if [[ -n "${body}" ]]; then
      echo "${body}" | jq . >&2
    fi
    echo '{}'
    return
  fi
  if [[ -n "${body}" ]]; then
    curl --fail-with-body --silent --show-error \
      -u ":${ADO_PAT}" \
      -X "${method}" \
      -H "content-type: application/json" \
      --data "${body}" \
      "${url}"
  else
    curl --fail-with-body --silent --show-error \
      -u ":${ADO_PAT}" \
      -X "${method}" \
      "${url}"
  fi
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --organization)
      ORGANIZATION="${2:-}"
      shift 2
      ;;
    --project)
      PROJECT="${2:-}"
      shift 2
      ;;
    --repository)
      REPOSITORY="${2:-}"
      shift 2
      ;;
    --target-branch)
      TARGET_BRANCH="${2:-}"
      shift 2
      ;;
    --version)
      VERSION="${2:-}"
      shift 2
      ;;
    --data-model-version)
      DATA_MODEL_VERSION="${2:-}"
      shift 2
      ;;
    --release-file)
      RELEASE_FILE="${2:-}"
      shift 2
      ;;
    --ado-pat)
      ADO_PAT="${2:-}"
      shift 2
      ;;
    --ado-env-file)
      ADO_ENV_FILE="${2:-}"
      shift 2
      ;;
    --no-complete)
      COMPLETE_PR=false
      shift 1
      ;;
    --dry-run)
      DRY_RUN=true
      shift 1
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "ado-appservice-release-pr: unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -f "${ADO_ENV_FILE}" ]]; then
  # shellcheck disable=SC1090
  source "${ADO_ENV_FILE}"
  ADO_PAT="${ADO_PAT:-${AZURE_DEVOPS_EXT_PAT:-}}"
  ORGANIZATION="${ORGANIZATION:-${MAPPO_DEMO_ADO_ORGANIZATION:-}}"
  PROJECT="${PROJECT:-${MAPPO_DEMO_ADO_PROJECT:-}}"
  REPOSITORY="${REPOSITORY:-${MAPPO_DEMO_ADO_REPOSITORY:-}}"
fi

if [[ -z "${ORGANIZATION}" || -z "${PROJECT}" || -z "${REPOSITORY}" ]]; then
  echo "ado-appservice-release-pr: --organization, --project, and --repository are required unless MAPPO_DEMO_ADO_* env vars are set." >&2
  exit 2
fi
ORGANIZATION="$(normalize_org "${ORGANIZATION}")"
BRANCH_NAME="release/mappo-${VERSION//[^A-Za-z0-9._-]/-}"
if [[ "${RELEASE_FILE}" != /* ]]; then
  RELEASE_FILE="/${RELEASE_FILE}"
fi

if ! command -v jq >/dev/null 2>&1; then
  echo "ado-appservice-release-pr: jq is required." >&2
  exit 1
fi
if ! command -v curl >/dev/null 2>&1; then
  echo "ado-appservice-release-pr: curl is required." >&2
  exit 1
fi
if [[ "${DRY_RUN}" != "true" && -z "${ADO_PAT}" ]]; then
  echo "ado-appservice-release-pr: --ado-pat or AZURE_DEVOPS_EXT_PAT is required." >&2
  exit 2
fi

project_path="$(urlencode "${PROJECT}")"
repo_path="$(urlencode "${REPOSITORY}")"
api_root="${ORGANIZATION}/${project_path}/_apis/git/repositories"

repo_json="$(curl_ado GET "${api_root}/${repo_path}?api-version=7.1-preview.1")"
repo_id="$(jq -r '.id // empty' <<<"${repo_json}")"
if [[ "${DRY_RUN}" == "true" ]]; then
  repo_id="${REPOSITORY}"
fi
if [[ -z "${repo_id}" ]]; then
  echo "ado-appservice-release-pr: repository not found: ${REPOSITORY}" >&2
  exit 1
fi

refs_url="${api_root}/$(urlencode "${repo_id}")/refs?filter=$(urlencode "heads/${TARGET_BRANCH}")&api-version=7.1-preview.1"
refs_json="$(curl_ado GET "${refs_url}")"
base_object_id="$(jq -r '.value[0].objectId // empty' <<<"${refs_json}")"
if [[ "${DRY_RUN}" == "true" ]]; then
  base_object_id="0000000000000000000000000000000000000000"
fi
if [[ -z "${base_object_id}" ]]; then
  echo "ado-appservice-release-pr: target branch not found: ${TARGET_BRANCH}" >&2
  exit 1
fi

release_file_query="$(urlencode "${RELEASE_FILE}")"
if [[ "${DRY_RUN}" == "true" ]]; then
  item_status="404"
else
  item_status="$(
    curl --silent --output /dev/null --write-out "%{http_code}" \
      -u ":${ADO_PAT}" \
      "${api_root}/$(urlencode "${repo_id}")/items?path=${release_file_query}&versionDescriptor.version=$(urlencode "${TARGET_BRANCH}")&api-version=7.1-preview.1" \
      || true
  )"
fi
change_type="add"
if [[ "${item_status}" == "200" ]]; then
  change_type="edit"
fi

release_content="$(jq -n \
  --arg version "${VERSION}" \
  --arg dataModelVersion "${DATA_MODEL_VERSION}" \
  --arg source "mappo-demo" \
  --arg createdAt "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
  '{version: $version, dataModelVersion: $dataModelVersion, source: $source, createdAt: $createdAt}')"

push_body="$(jq -n \
  --arg branch "refs/heads/${BRANCH_NAME}" \
  --arg oldObjectId "${base_object_id}" \
  --arg comment "Create MAPPO App Service demo release ${VERSION}" \
  --arg changeType "${change_type}" \
  --arg path "${RELEASE_FILE}" \
  --arg content "${release_content}" \
  '{
    refUpdates: [{name: $branch, oldObjectId: $oldObjectId}],
    commits: [{
      comment: $comment,
      changes: [{
        changeType: $changeType,
        item: {path: $path},
        newContent: {content: $content, contentType: "rawtext"}
      }]
    }]
  }')"

push_json="$(curl_ado POST "${api_root}/$(urlencode "${repo_id}")/pushes?api-version=7.1-preview.2" "${push_body}")"
commit_id="$(jq -r '.commits[0].commitId // empty' <<<"${push_json}")"
if [[ "${DRY_RUN}" == "true" ]]; then
  commit_id="0000000000000000000000000000000000000000"
fi

pr_body="$(jq -n \
  --arg source "refs/heads/${BRANCH_NAME}" \
  --arg target "refs/heads/${TARGET_BRANCH}" \
  --arg title "MAPPO demo release ${VERSION}" \
  --arg description "Automated demo release generated by scripts/ado_appservice_release_pr.sh." \
  '{
    sourceRefName: $source,
    targetRefName: $target,
    title: $title,
    description: $description,
    completionOptions: {
      deleteSourceBranch: true,
      squashMerge: true,
      mergeCommitMessage: $title
    }
  }')"

pr_json="$(curl_ado POST "${api_root}/$(urlencode "${repo_id}")/pullrequests?api-version=7.1-preview.1" "${pr_body}")"
pr_id="$(jq -r '.pullRequestId // empty' <<<"${pr_json}")"
pr_url="$(jq -r '.repository.webUrl // empty' <<<"${pr_json}")/pullrequest/${pr_id}"
last_merge_source_commit="$(jq -r '.lastMergeSourceCommit.commitId // empty' <<<"${pr_json}")"
if [[ "${DRY_RUN}" == "true" ]]; then
  pr_id="dry-run"
  pr_url="${ORGANIZATION}/${project_path}/_git/${repo_path}/pullrequest/dry-run"
  last_merge_source_commit="${commit_id}"
fi

if [[ "${COMPLETE_PR}" == "true" ]]; then
  completion_commit="${last_merge_source_commit:-${commit_id}}"
  complete_body="$(jq -n \
    --arg commitId "${completion_commit}" \
    --arg message "Merge MAPPO demo release ${VERSION}" \
    '{
      status: "completed",
      lastMergeSourceCommit: {commitId: $commitId},
      completionOptions: {
        deleteSourceBranch: true,
        squashMerge: true,
        mergeCommitMessage: $message
      }
    }')"
  curl_ado PATCH "${api_root}/$(urlencode "${repo_id}")/pullrequests/${pr_id}?api-version=7.1-preview.1" "${complete_body}" >/dev/null
  echo "ado-appservice-release-pr: created and completed PR ${pr_id} for version ${VERSION}"
else
  echo "ado-appservice-release-pr: created PR ${pr_id} for version ${VERSION}"
fi

echo "ado-appservice-release-pr: branch=${BRANCH_NAME}"
echo "ado-appservice-release-pr: pr=${pr_url}"
