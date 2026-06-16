package com.example.target;

/**
 * Middle-tier service. Decides which calculation engine handles the
 * requested operation and forwards the call. No logging/tracing here.
 */
public class MathService {

    private final AdditionEngine additionEngine = new AdditionEngine();
    private final MultiplicationEngine multiplicationEngine = new MultiplicationEngine();

    public double calculate(String op, double a, double b) {
        String normalizedOp = op.trim().toLowerCase();
        double result;

        switch (normalizedOp) {
            case "add":
                result = additionEngine.add(a, b);
                break;
            case "subtract":
                result = additionEngine.subtract(a, b);
                break;
            case "multiply":
                result = multiplicationEngine.multiply(a, b);
                break;
            case "divide":
                result = multiplicationEngine.divide(a, b);
                break;
            default:
                throw new IllegalArgumentException("unsupported operation: " + op);
        }

        return result;
    }
}
