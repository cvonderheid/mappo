# MAPPO Docs

This directory is intentionally small. Only living documentation that matches the current codebase and hosted demo belongs here.

## Current docs
- [`production-handoff-walkthrough.md`](./production-handoff-walkthrough.md): clean first-run handoff path for a new developer/operator.
- [`architecture.md`](./architecture.md): system model, boundaries, deployment modes, and current hosted shape.
- [`operator-guide.md`](./operator-guide.md): how an operator should use MAPPO today.
- [`azure-devops-pipeline-project-setup.md`](./azure-devops-pipeline-project-setup.md): concrete operator manual for an Azure DevOps release-readiness pipeline feeding a separate Azure DevOps deployment pipeline.
- [`deployment-runbook.md`](./deployment-runbook.md): local build/test, local runtime, and Azure rollout commands.
- [`demo-topology.md`](./demo-topology.md): current hosted demo resources and boundary notes.
- [`developer-guide.md`](./developer-guide.md): repo layout, contracts, testing, and engineering workflow.

## What does not belong here
- completed sprint plans
- one-off state audits
- obsolete runbooks for retired paths
- screen specs that no longer match the product
- duplicate copies of the same operational workflow

If a document stops reflecting current behavior, delete it or fold the still-true parts into one of the files above.
