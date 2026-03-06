package com.mappo.tooling;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

final class FileSupport {

    private FileSupport() {
    }

    static Path repoRoot() {
        return Path.of("").toAbsolutePath().normalize();
    }

    static String readText(Path path) {
        try {
            return java.nio.file.Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new ToolingException("failed reading " + path + ": " + exception.getMessage(), 1);
        }
    }

    static List<String> readLines(Path path) {
        try {
            return java.nio.file.Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new ToolingException("failed reading " + path + ": " + exception.getMessage(), 1);
        }
    }

    static void writeText(Path path, String content) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                java.nio.file.Files.createDirectories(parent);
            }
            java.nio.file.Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new ToolingException("failed writing " + path + ": " + exception.getMessage(), 1);
        }
    }

    static Stream<Path> walk(Path root) {
        try {
            return java.nio.file.Files.walk(root);
        } catch (IOException exception) {
            throw new ToolingException("failed walking " + root + ": " + exception.getMessage(), 1);
        }
    }

    static long lineCount(Path path) {
        try (Stream<String> lines = java.nio.file.Files.lines(path, StandardCharsets.UTF_8)) {
            return lines.count();
        } catch (IOException exception) {
            throw new ToolingException("failed counting lines in " + path + ": " + exception.getMessage(), 1);
        }
    }
}
