package com.mappo.tooling;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

final class BackendFileSizeCheckCommand {

    int run(List<String> rawArgs) {
        Arguments args = new Arguments(rawArgs);
        Path root = Path.of("backend/src/main/java");
        int maxLines = Integer.parseInt(System.getenv().getOrDefault("MAPPO_BACKEND_MAX_LINES", "750"));

        while (args.hasNext()) {
            String arg = args.next();
            switch (arg) {
                case "--root" -> root = Path.of(args.nextRequired("--root"));
                case "--max-lines" -> maxLines = Integer.parseInt(args.nextRequired("--max-lines"));
                case "-h", "--help" -> {
                    printUsage();
                    return 0;
                }
                default -> throw new ToolingException("backend-file-size-check: unknown argument: " + arg, 2);
            }
        }

        if (!java.nio.file.Files.exists(root)) {
            System.out.println("backend-file-size-check: FAIL root not found: " + root);
            return 1;
        }

        final int maxLinesLimit = maxLines;

        List<Violation> violations = FileSupport.walk(root)
            .filter(path -> java.nio.file.Files.isRegularFile(path))
            .filter(path -> path.toString().endsWith(".java"))
            .filter(path -> !path.toString().replace('\\', '/').contains("/target/"))
            .map(path -> new Violation(path, FileSupport.lineCount(path)))
            .filter(violation -> violation.lines() > maxLinesLimit)
            .sorted(Comparator.comparingLong(Violation::lines).reversed())
            .toList();

        long checked = FileSupport.walk(root)
            .filter(path -> java.nio.file.Files.isRegularFile(path))
            .filter(path -> path.toString().endsWith(".java"))
            .filter(path -> !path.toString().replace('\\', '/').contains("/target/"))
            .count();

        if (!violations.isEmpty()) {
            System.out.printf(
                "backend-file-size-check: FAIL %d files exceed %d lines (checked %d)%n",
                violations.size(),
                maxLinesLimit,
                checked
            );
            for (Violation violation : violations) {
                System.out.printf("  %s:%d%n", violation.path(), violation.lines());
            }
            return 1;
        }

        System.out.printf("backend-file-size-check: PASS checked=%d max_lines=%d%n", checked, maxLinesLimit);
        return 0;
    }

    private void printUsage() {
        System.out.println("""
            usage: backend-file-size-check [--root <path>] [--max-lines <int>]
            """);
    }

    private record Violation(Path path, long lines) {
    }
}
