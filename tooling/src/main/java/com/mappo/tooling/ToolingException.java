package com.mappo.tooling;

public final class ToolingException extends RuntimeException {

    private final int exitCode;

    public ToolingException(String message, int exitCode) {
        super(message);
        this.exitCode = exitCode;
    }

    public int exitCode() {
        return exitCode;
    }
}
