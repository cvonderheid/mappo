#!/usr/bin/env bash
# Source this file from the repository root before publishing images or applying
# the runtime Pulumi stack:
#   source scripts/source_runtime_deploy_env.sh

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  echo "source this script so exported values remain in your shell:" >&2
  echo "  source scripts/source_runtime_deploy_env.sh" >&2
  exit 1
fi

__mappo_source_env_file() {
  local env_file
  env_file="$1"

  if [[ -f "$env_file" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "$env_file"
    local source_status=$?
    set +a
    return "$source_status"
  fi
}

__mappo_require_env() {
  local name value
  name="$1"
  value="${!name:-}"

  if [[ -z "$value" ]]; then
    echo "$name is required. Check .data/pulumi-platform.env and .data/pulumi-runtime.env." >&2
    return 1
  fi
}

__mappo_runtime_deploy_load() {
  local root_dir platform_env_file runtime_env_file image_tag image_prefix acr_name acr_token
  local caller_platform_stack caller_runtime_subscription_id caller_runtime_location caller_pulumi_passphrase
  local platform_stack platform_runtime_subscription_id platform_runtime_location platform_pulumi_passphrase
  root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)" || return 1
  platform_env_file="${MAPPO_PLATFORM_ENV_FILE:-$root_dir/.data/pulumi-platform.env}"
  runtime_env_file="${MAPPO_RUNTIME_ENV_FILE:-$root_dir/.data/pulumi-runtime.env}"

  caller_platform_stack="${MAPPO_PLATFORM_STACK:-}"
  caller_runtime_subscription_id="${MAPPO_RUNTIME_SUBSCRIPTION_ID:-}"
  caller_runtime_location="${MAPPO_RUNTIME_LOCATION:-}"
  caller_pulumi_passphrase="${PULUMI_CONFIG_PASSPHRASE:-}"

  if [[ ! -f "$platform_env_file" && -z "$caller_platform_stack" ]]; then
    echo "missing platform env file: $platform_env_file" >&2
    echo "create it from pulumi-platform.env.example or export MAPPO_PLATFORM_STACK before sourcing this script" >&2
    return 1
  fi
  if [[ ! -f "$runtime_env_file" ]]; then
    echo "missing runtime env file: $runtime_env_file" >&2
    return 1
  fi

  __mappo_source_env_file "$platform_env_file" || return 1
  platform_stack="${caller_platform_stack:-$MAPPO_PLATFORM_STACK}"
  platform_runtime_subscription_id="${caller_runtime_subscription_id:-$MAPPO_RUNTIME_SUBSCRIPTION_ID}"
  platform_runtime_location="${caller_runtime_location:-$MAPPO_RUNTIME_LOCATION}"
  platform_pulumi_passphrase="${caller_pulumi_passphrase:-$PULUMI_CONFIG_PASSPHRASE}"

  __mappo_source_env_file "$runtime_env_file" || return 1

  # Platform identity belongs to .data/pulumi-platform.env. Restore it after
  # loading runtime settings so stale runtime env files cannot target old stacks.
  if [[ -n "$platform_stack" ]]; then
    export MAPPO_PLATFORM_STACK="$platform_stack"
  fi
  if [[ -n "$platform_runtime_subscription_id" ]]; then
    export MAPPO_RUNTIME_SUBSCRIPTION_ID="$platform_runtime_subscription_id"
  fi
  if [[ -n "$platform_runtime_location" ]]; then
    export MAPPO_RUNTIME_LOCATION="$platform_runtime_location"
  fi
  if [[ -n "$platform_pulumi_passphrase" ]]; then
    export PULUMI_CONFIG_PASSPHRASE="$platform_pulumi_passphrase"
  fi

  __mappo_require_env MAPPO_PLATFORM_STACK || return 1
  __mappo_require_env MAPPO_RUNTIME_SUBSCRIPTION_ID || return 1
  __mappo_require_env PULUMI_CONFIG_PASSPHRASE || return 1

  if ! image_tag="$($root_dir/mvnw -q -N initialize help:evaluate -Dexpression=mappo.image.tag -DforceStdout)"; then
    echo "failed to resolve Maven image tag" >&2
    return 1
  fi
  if ! image_prefix="$(cd "$root_dir/infra/pulumi" && pulumi stack output runtimeAcrLoginServer --stack "$MAPPO_PLATFORM_STACK")"; then
    echo "failed to read runtimeAcrLoginServer from platform stack $MAPPO_PLATFORM_STACK" >&2
    echo "check MAPPO_PLATFORM_STACK in $platform_env_file" >&2
    echo "available local Pulumi stacks:" >&2
    (cd "$root_dir/infra/pulumi" && pulumi stack ls 2>/dev/null || true) >&2
    return 1
  fi
  if ! acr_name="$(cd "$root_dir/infra/pulumi" && pulumi stack output runtimeAcrName --stack "$MAPPO_PLATFORM_STACK")"; then
    echo "failed to read runtimeAcrName from platform stack $MAPPO_PLATFORM_STACK" >&2
    echo "check MAPPO_PLATFORM_STACK in $platform_env_file" >&2
    echo "available local Pulumi stacks:" >&2
    (cd "$root_dir/infra/pulumi" && pulumi stack ls 2>/dev/null || true) >&2
    return 1
  fi

  if ! az account set --subscription "$MAPPO_RUNTIME_SUBSCRIPTION_ID"; then
    echo "failed to select Azure subscription $MAPPO_RUNTIME_SUBSCRIPTION_ID" >&2
    return 1
  fi
  if ! acr_token="$(az acr login --name "$acr_name" --expose-token --output tsv --query accessToken)"; then
    echo "failed to get ACR access token for $acr_name" >&2
    return 1
  fi

  export MAPPO_RUNTIME_IMAGE_TAG="$image_tag"
  export MAPPO_IMAGE_PREFIX="$image_prefix"
  export MAPPO_RUNTIME_ACR_NAME="$acr_name"
  export MAPPO_DOCKER_USERNAME="00000000-0000-0000-0000-000000000000"
  export MAPPO_DOCKER_PASSWORD="$acr_token"

  cat <<SUMMARY
Runtime deploy environment loaded:
  MAPPO_PLATFORM_STACK=$MAPPO_PLATFORM_STACK
  MAPPO_RUNTIME_IMAGE_TAG=$MAPPO_RUNTIME_IMAGE_TAG
  MAPPO_IMAGE_PREFIX=$MAPPO_IMAGE_PREFIX
  MAPPO_RUNTIME_ACR_NAME=$MAPPO_RUNTIME_ACR_NAME
  MAPPO_BACKEND_CUSTOM_DOMAIN=${MAPPO_BACKEND_CUSTOM_DOMAIN:-}
  MAPPO_FRONTEND_CUSTOM_DOMAIN=${MAPPO_FRONTEND_CUSTOM_DOMAIN:-}
SUMMARY
}

__mappo_runtime_deploy_load
unset -f __mappo_source_env_file
unset -f __mappo_require_env
unset -f __mappo_runtime_deploy_load
