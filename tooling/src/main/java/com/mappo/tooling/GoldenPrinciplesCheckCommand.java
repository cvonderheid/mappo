package com.mappo.tooling;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class GoldenPrinciplesCheckCommand {

    private static final List<String> REQUIRED_ARCHITECTURE_MARKERS = List.of(
        "## Overview",
        "## Core objects",
        "## Inbound vs outbound boundaries",
        "## Deployment modes"
    );

    private static final List<String> REQUIRED_DEVELOPER_GUIDE_MARKERS = List.of(
        "## Development rules",
        "## Documentation rule"
    );

    int run(List<String> rawArgs) {
        for (String arg : rawArgs) {
            if (Arguments.isHelpFlag(arg)) {
                System.out.println("usage: golden-principles-check");
                return 0;
            }
            throw new ToolingException("golden-principles-check: unknown argument: " + arg, 2);
        }

        Path repo = FileSupport.repoRoot();
        List<String> failures = new ArrayList<>();
        Path architecturePath = repo.resolve("docs/architecture.md");
        Path developerGuidePath = repo.resolve("docs/developer-guide.md");

        if (!java.nio.file.Files.exists(architecturePath)) {
            failures.add("missing required file: docs/architecture.md");
        } else {
            String text = FileSupport.readText(architecturePath);
            for (String marker : REQUIRED_ARCHITECTURE_MARKERS) {
                if (!text.contains(marker)) {
                    failures.add("docs/architecture.md missing marker: " + marker);
                }
            }
        }

        if (!java.nio.file.Files.exists(developerGuidePath)) {
            failures.add("missing required file: docs/developer-guide.md");
        } else {
            String text = FileSupport.readText(developerGuidePath);
            for (String marker : REQUIRED_DEVELOPER_GUIDE_MARKERS) {
                if (!text.contains(marker)) {
                    failures.add("docs/developer-guide.md missing marker: " + marker);
                }
            }
        }

        if (!failures.isEmpty()) {
            System.out.println("golden-principles-check: FAIL");
            failures.forEach(failure -> System.out.println(" - " + failure));
            return 1;
        }

        System.out.println("golden-principles-check: PASS");
        return 0;
    }
}
