package com.example.target;

/**
 * Second calculation engine, handles multiplicative operations.
 * No observability code, by design.
 */
public class MultiplicationEngine {

    public double multiply(double a, double b) {
        double product = a * b;
        return product;
    }

    public double divide(double a, double b) {
        if (b == 0) {
            throw new ArithmeticException("division by zero");
        }
        double quotient = a / b;
        return quotient;
    }
}
