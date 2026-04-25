package com.mappo.tooling;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class DocsConsistencyCheckCommand {

    private static final List<String> REQUIRED_FILES = List.of(
        "README.md",
        "docs/README.md",
        "docs/architecture.md",
        "docs/operator-guide.md",
        "docs/deployment-runbook.md",
        "docs/demo-topology.md",
        "docs/developer-guide.md"
    );

    private static final List<String> NO_MAKE_REFERENCE_FILES = List.of(
        "README.md",
        "docs/README.md",
        "docs/architecture.md",
        "docs/operator-guide.md",
        "docs/deployment-runbook.md",
        "docs/demo-topology.md",
        "docs/developer-guide.md"
    );

    private static final List<String> FORBIDDEN_ACTIVE_DOC_MARKERS = List.of(
        "backend-java",
        "FastAPI",
        "uv run",
        "SQLAlchemy-style"
    );

    private static final Map<String, List<String>> CONTENT_RULES = new LinkedHashMap<>();

    static {
        CONTENT_RULES.put("docs/README.md", List.of("## Current docs", "## What does not belong here"));
        CONTENT_RULES.put("docs/architecture.md", List.of("## Overview", "## Core objects", "## Deployment modes"));
        CONTENT_RULES.put("docs/operator-guide.md", List.of("## Mental model", "## Global admin screens", "## Project screens"));
        CONTENT_RULES.put("docs/deployment-runbook.md", List.of("## Local build and test", "## Publish and roll out to Azure"));
        CONTENT_RULES.put("docs/demo-topology.md", List.of("## Current truth", "## Current project shapes"));
        CONTENT_RULES.put("docs/developer-guide.md", List.of("## Repo layout", "## Development rules"));
        CONTENT_RULES.put("pom.xml", List.of("<module>backend</module>", "<module>frontend</module>"));
        CONTENT_RULES.put(
            "README.md",
            List.of(
                "./mvnw -pl backend verify",
                "./mvnw -pl frontend package",
                "./backend/target/openapi/openapi.json"
            )
        );
    }

    int run(List<String> rawArgs) {
        for (String arg : rawArgs) {
            if (Arguments.isHelpFlag(arg)) {
                System.out.println("usage: docs-consistency-check");
                return 0;
            }
            throw new ToolingException("docs-consistency-check: unknown argument: " + arg, 2);
        }

        Path repo = FileSupport.repoRoot();
        List<String> failures = new ArrayList<>();

        for (String relative : REQUIRED_FILES) {
            if (!java.nio.file.Files.exists(repo.resolve(relative))) {
                failures.add("missing required documentation file: " + relative);
            }
        }

        CONTENT_RULES.forEach((relative, markers) -> {
            Path path = repo.resolve(relative);
            if (!java.nio.file.Files.exists(path)) {
                return;
            }
            String text = FileSupport.readText(path);
            for (String marker : markers) {
                if (!text.contains(marker)) {
                    failures.add(relative + " missing marker: " + marker);
                }
            }
        });

        for (String relative : NO_MAKE_REFERENCE_FILES) {
            Path path = repo.resolve(relative);
            if (!java.nio.file.Files.exists(path)) {
                continue;
            }
            String text = FileSupport.readText(path);
            if (text.contains("make ") || text.contains("`make")) {
                failures.add(relative + " still references removed Makefile workflow");
            }
            for (String marker : FORBIDDEN_ACTIVE_DOC_MARKERS) {
                if (text.contains(marker)) {
                    failures.add(relative + " still references removed Python-era marker: " + marker);
                }
            }
        }

        if (!failures.isEmpty()) {
            System.out.println("docs-consistency-check: FAIL");
            failures.forEach(failure -> System.out.println(" - " + failure));
            return 1;
        }

        System.out.println("docs-consistency-check: PASS");
        return 0;
    }
}
