package com.synth;

public class Complex {
    public final double real;
    public final double imag;

    public Complex(double real, double imag) {
        this.real = real;
        this.imag = imag;
    }

    public Complex add(Complex other) {
        return new Complex(this.real + other.real, this.imag + other.imag);
    }

    public Complex sub(Complex other) {
        return new Complex(this.real - other.real, this.imag - other.imag);
    }

    public Complex multiply(Complex other) {
        // Formula: (a + bi)(c + di) = (ac - bd) + (ad + bc)i
        double r = (this.real * other.real) - (this.imag * other.imag);
        double i = (this.real * other.imag) + (this.imag * other.real);
        return new Complex(r, i);
    }

    public double magnitude() {
        // Distance from (0, 0) to (real, imag)
        return Math.sqrt((real * real) + (imag * imag));
    }
}