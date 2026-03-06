package com.mappo.tooling;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class GoldenPrinciplesCheckCommand {

    private static final List<String> REQUIRED_PRINCIPLES = List.of(
        "Determinism first",
        "Source-of-truth consistency",
        "No demo leakage in production paths",
        "Contract-before-change",
        "Legibility is required"
    );

    private static final List<String> REQUIRED_ARCHITECTURE_MARKERS = List.of(
        "## Core Model",
        "## Control / Data / Verification Boundaries",
        "## Determinism + Legibility Contract"
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
        Path principlesPath = repo.resolve("docs/golden-principles.md");
        Path architecturePath = repo.resolve("docs/architecture.md");

        if (!java.nio.file.Files.exists(principlesPath)) {
            failures.add("missing required file: docs/golden-principles.md");
        } else {
            String text = FileSupport.readText(principlesPath);
            for (String marker : REQUIRED_PRINCIPLES) {
                if (!text.contains(marker)) {
                    failures.add("docs/golden-principles.md missing principle: " + marker);
                }
            }
        }

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

        if (!failures.isEmpty()) {
            System.out.println("golden-principles-check: FAIL");
            failures.forEach(failure -> System.out.println(" - " + failure));
            return 1;
        }

        System.out.println("golden-principles-check: PASS");
        return 0;
    }
}
