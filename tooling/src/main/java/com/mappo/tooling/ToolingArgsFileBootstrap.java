package com.mappo.tooling;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

public final class ToolingArgsFileBootstrap {

    private ToolingArgsFileBootstrap() {
    }

    public static void main(String[] args) {
        String argsFile = System.getProperty("mappo.argsFile", "").trim();
        if (argsFile.isEmpty()) {
            throw new ToolingException("missing system property: mappo.argsFile", 2);
        }

        List<String> encodedArgs = FileSupport.readLines(Path.of(argsFile).toAbsolutePath().normalize());
        String[] decodedArgs = encodedArgs.stream()
            .map(value -> new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8))
            .toArray(String[]::new);
        ToolingApplication.main(decodedArgs);
    }
}
