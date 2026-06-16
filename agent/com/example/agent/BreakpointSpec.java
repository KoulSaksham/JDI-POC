package com.example.agent;

/**
 * Describes one dynamic breakpoint request: a fully-qualified class
 * name and a method name within it. This is the "accept a target
 * breakpoint" piece of the spec — in a fuller implementation this
 * could be parsed from CLI args or a config file at agent startup.
 */
public record BreakpointSpec(String className, String methodName) {

    public static BreakpointSpec parse(String spec) {
        // format: com.example.target.AdditionEngine#add
        String[] parts = spec.split("#");
        if (parts.length != 2) {
            throw new IllegalArgumentException(
                    "breakpoint spec must be ClassName#methodName, got: " + spec);
        }
        return new BreakpointSpec(parts[0], parts[1]);
    }

    @Override
    public String toString() {
        return className + "#" + methodName;
    }
}
