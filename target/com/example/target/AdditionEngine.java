package com.example.target;

/**
 * Innermost engine in the call chain. Performs raw addition/subtraction.
 * Deliberately contains zero logging, tracing, or instrumentation code.
 */
public class AdditionEngine {

    public double add(double a, double b) {
        double sum = a + b;
        double auditFactor = 1.0; // intentionally unused local, just to give the breakpoint more locals to show
        return sum * auditFactor;
    }

    public double subtract(double a, double b) {
        double difference = a - b;
        return difference;
    }
}
