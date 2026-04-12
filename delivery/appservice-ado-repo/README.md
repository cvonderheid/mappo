# demo-app-service

Source-of-truth files for the Azure DevOps repo used by the MAPPO App Service pipeline demo.

The demo uses two Azure DevOps pipelines:

- `azure-pipelines.release.yml`: triggered by merges to `main` when `/app/release.json` changes. It updates the run name to the demo release version. The ADO service hook for this pipeline notifies MAPPO that a release is ready.
- `azure-pipelines.deploy.yml`: `trigger: none`. MAPPO triggers this pipeline per selected target and passes target-specific App Service parameters.

The release PR helper updates `/app/release.json` through Azure DevOps Git REST APIs:

```bash
./scripts/ado_appservice_release_pr.sh \
  --organization https://dev.azure.com/pg123 \
  --project demo-app-service \
  --repository demo-app-service \
  --version 2026.04.12.1
```

After creating the release-readiness pipeline, point MAPPO and ADO at that pipeline:

```bash
./scripts/release_source_configure_ado.sh \
  --api-base-url https://api.mappopoc.com \
  --pipeline-id <release-readiness-pipeline-id>

./scripts/ado_release_hook_configure.sh \
  --organization https://dev.azure.com/pg123 \
  --project demo-app-service \
  --pipeline-id <release-readiness-pipeline-id> \
  --mappo-api-base-url https://api.mappopoc.com
```
