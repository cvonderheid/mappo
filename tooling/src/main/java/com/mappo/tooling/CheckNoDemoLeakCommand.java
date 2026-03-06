package com.mappo.tooling;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

final class CheckNoDemoLeakCommand {

    private static final List<Pattern> BANNED = List.of(
        Pattern.compile("demo\\s*bypass", Pattern.CASE_INSENSITIVE),
        Pattern.compile("hardcoded\\s*demo", Pattern.CASE_INSENSITIVE),
        Pattern.compile("temporary\\s*demo\\s*hack", Pattern.CASE_INSENSITIVE),
        Pattern.compile("todo_demo_hack", Pattern.CASE_INSENSITIVE),
        Pattern.compile("skip_auth_for_demo", Pattern.CASE_INSENSITIVE),
        Pattern.compile("def\\s+reset_demo_data\\s*\\(", Pattern.CASE_INSENSITIVE),
        Pattern.compile("def\\s+_seed_targets\\s*\\(", Pattern.CASE_INSENSITIVE),
        Pattern.compile("def\\s+_seed_releases\\s*\\(", Pattern.CASE_INSENSITIVE),
        Pattern.compile("execution_mode\\s*:\\s*ExecutionMode\\s*=\\s*ExecutionMode\\.DEMO", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bcontrol_plane_storage\\b", Pattern.CASE_INSENSITIVE)
    );

    private static final Set<String> CODE_SUFFIXES = Set.of(".java", ".py", ".ts", ".tsx", ".js", ".jsx");
    private static final List<String> SCAN_DIRS = List.of("backend/src/main/java", "frontend/src");

    int run(List<String> rawArgs) {
        for (String arg : rawArgs) {
            if (Arguments.isHelpFlag(arg)) {
                System.out.println("usage: check-no-demo-leak");
                return 0;
            }
            throw new ToolingException("check-no-demo-leak: unknown argument: " + arg, 2);
        }

        Path repo = FileSupport.repoRoot();
        List<String> failures = new ArrayList<>();

        for (String relative : SCAN_DIRS) {
            Path base = repo.resolve(relative);
            if (!java.nio.file.Files.exists(base)) {
                continue;
            }
            try (var stream = FileSupport.walk(base)) {
                stream.filter(path -> java.nio.file.Files.isRegularFile(path))
                    .filter(path -> CODE_SUFFIXES.contains(suffix(path)))
                    .forEach(path -> {
                        String text = FileSupport.readText(path);
                        for (Pattern pattern : BANNED) {
                            if (pattern.matcher(text).find()) {
                                failures.add(repo.relativize(path) + " matched banned pattern: " + pattern.pattern());
                            }
                        }
                    });
            }
        }

        if (!failures.isEmpty()) {
            System.out.println("check-no-demo-leak: FAIL");
            failures.forEach(failure -> System.out.println(" - " + failure));
            return 1;
        }

        System.out.println("check-no-demo-leak: PASS");
        return 0;
    }

    private String suffix(Path path) {
        String name = path.getFileName().toString();
        int dotIndex = name.lastIndexOf('.');
        return dotIndex >= 0 ? name.substring(dotIndex) : "";
    }
}
