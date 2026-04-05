package com.mappo.tooling;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class WorkflowDisciplineCheckCommand {

    private static final Map<String, List<String>> REQUIRED = new LinkedHashMap<>();

    static {
        REQUIRED.put(
            "tasks/todo.md",
            List.of("## Next demo / operator UX", "## Data and configuration cleanup", "## Pre-production work")
        );
        REQUIRED.put(
            "tasks/lessons.md",
            List.of("## Operator UX", "## Product boundaries", "## Repo hygiene")
        );
    }

    int run(List<String> rawArgs) {
        for (String arg : rawArgs) {
            if (Arguments.isHelpFlag(arg)) {
                System.out.println("usage: workflow-discipline-check");
                return 0;
            }
            throw new ToolingException("workflow-discipline-check: unknown argument: " + arg, 2);
        }

        Path repo = FileSupport.repoRoot();
        List<String> failures = new ArrayList<>();

        REQUIRED.forEach((relativePath, markers) -> {
            Path path = repo.resolve(relativePath);
            if (!java.nio.file.Files.exists(path)) {
                failures.add("missing required file: " + relativePath);
                return;
            }
            String text = FileSupport.readText(path);
            for (String marker : markers) {
                if (!text.contains(marker)) {
                    failures.add(relativePath + " missing marker: " + marker);
                }
            }
        });

        if (!failures.isEmpty()) {
            System.out.println("workflow-discipline-check: FAIL");
            failures.forEach(failure -> System.out.println(" - " + failure));
            return 1;
        }

        System.out.println("workflow-discipline-check: PASS");
        return 0;
    }
}
