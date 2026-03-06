package com.mappo.tooling;

import java.util.List;

final class Arguments {

    private final List<String> args;
    private int index;

    Arguments(List<String> args) {
        this.args = args;
    }

    String nextRequired(String flagName) {
        if (index >= args.size()) {
            throw new ToolingException("missing value for " + flagName, 2);
        }
        return args.get(index++);
    }

    boolean hasNext() {
        return index < args.size();
    }

    String next() {
        return args.get(index++);
    }

    static boolean isHelpFlag(String value) {
        return "-h".equals(value) || "--help".equals(value);
    }
}
