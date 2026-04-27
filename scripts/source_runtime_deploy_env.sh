#!/usr/bin/env bash
# Source this file from the repository root before publishing images or applying
# the runtime Pulumi stack:
#   source scripts/source_runtime_deploy_env.sh

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  echo "source this script so exported values remain in your shell:" >&2
  echo "  source scripts/source_runtime_deploy_env.sh" >&2
  exit 1
fi

__mappo_runtime_deploy_load() {
  local root_dir runtime_env_file image_tag image_prefix acr_name acr_token
  root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)" || return 1
  runtime_env_file="${MAPPO_RUNTIME_ENV_FILE:-$root_dir/.data/pulumi-runtime.env}"

  if [[ ! -f "$runtime_env_file" ]]; then
    echo "missing runtime env file: $runtime_env_file" >&2
    return 1
  fi

  set -a
  # shellcheck disable=SC1090
  source "$runtime_env_file" || return 1
  set +a

  if [[ -z "${MAPPO_PLATFORM_STACK:-}" ]]; then
    echo "MAPPO_PLATFORM_STACK is required in $runtime_env_file" >&2
    return 1
  fi

  if ! image_tag="$($root_dir/mvnw -q -N initialize help:evaluate -Dexpression=mappo.image.tag -DforceStdout)"; then
    echo "failed to resolve Maven image tag" >&2
    return 1
  fi
  if ! image_prefix="$(cd "$root_dir/infra/pulumi" && pulumi stack output runtimeAcrLoginServer --stack "$MAPPO_PLATFORM_STACK")"; then
    echo "failed to read runtimeAcrLoginServer from platform stack $MAPPO_PLATFORM_STACK" >&2
    return 1
  fi
  if ! acr_name="$(cd "$root_dir/infra/pulumi" && pulumi stack output runtimeAcrName --stack "$MAPPO_PLATFORM_STACK")"; then
    echo "failed to read runtimeAcrName from platform stack $MAPPO_PLATFORM_STACK" >&2
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
unset -f __mappo_runtime_deploy_load
