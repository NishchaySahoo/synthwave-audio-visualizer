package com.synth;

public class FFTProcessor {

    // 1. Applies a Hann Window to taper edges smoothly to 0.0 (prevents spectral leakage)
    public static double[] applyHannWindow(double[] samples) {
        int n = samples.length;
        double[] windowed = new double[n];
        for (int i = 0; i < n; i++) {
            // TODO 1: Compute multiplier = 0.5 * (1.0 - Math.cos((2.0 * Math.PI * i) / (n - 1)))
            //         Then set windowed[i] = samples[i] * multiplier;
            double multiplier = 0.5 * (1.0 - Math.cos((2.0 * Math.PI * i) / (n - 1)));
            windowed[i] = samples[i] * multiplier;
        }
        return windowed;
    }

    // 2. Recursive Cooley-Tukey Radix-2 FFT (requires input length 'n' to be a power of 2, like 1024)
    public static Complex[] fft(Complex[] x) {
        int n = x.length;

        // Base case: a 1-element signal is already transformed
        if (n == 1) {
            return new Complex[]{ x[0] };
        }

        // Split into even-indexed and odd-indexed halves
        Complex[] even = new Complex[n / 2];
        Complex[] odd = new Complex[n / 2];
        for (int k = 0; k < n / 2; k++) {
            even[k] = x[2 * k];
            odd[k] = x[2 * k + 1];
        }

        // Recursively transform both halves
        Complex[] evenFFT = fft(even);
        Complex[] oddFFT = fft(odd);

        // Combine halves using "twiddle factors" (points spinning around the unit circle)
        Complex[] result = new Complex[n];
        for (int k = 0; k < n / 2; k++) {
            double angle = -2.0 * Math.PI * k / n;
            Complex twiddle = new Complex(Math.cos(angle), Math.sin(angle));
            Complex t = twiddle.multiply(oddFFT[k]);

            // TODO 2: Butterfly combination!
            result[k] = evenFFT[k].add(t);
            result[k + n / 2] = evenFFT[k].sub(t);
        }
        return result;
    }

    // 3. Test harness: Generate a pure 1000 Hz sine wave and verify the FFT finds it!
    public static void main(String[] args) {
        int n = 1024;
        double sampleRate = 44100.0;
        double testFreq = 1000.0; // Pure 1000 Hz tone

        // Generate synthetic wave
        double[] rawSamples = new double[n];
        for (int i = 0; i < n; i++) {
            rawSamples[i] = Math.sin(2.0 * Math.PI * testFreq * i / sampleRate);
        }

        // Apply Hann window and convert to Complex array
        double[] windowed = applyHannWindow(rawSamples);
        Complex[] complexInput = new Complex[n];
        for (int i = 0; i < n; i++) {
            complexInput[i] = new Complex(windowed[i], 0.0);
        }

        // Run FFT and find which bin (0 to 511) has the highest magnitude
        Complex[] spectrum = fft(complexInput);
        int peakBin = 0;
        double maxMag = 0.0;
        for (int bin = 0; bin < n / 2; bin++) {
            double mag = spectrum[bin].magnitude();
            if (mag > maxMag) {
                maxMag = mag;
                peakBin = bin;
            }
        }

        // Convert bin index back to Hz: freq = bin * sampleRate / n
        double detectedFreq = (peakBin * sampleRate) / n;
        System.out.printf("Expected: %.1f Hz | Peak Bin: %d | Detected: %.1f Hz (Magnitude: %.1f)%n",
                testFreq, peakBin, detectedFreq, maxMag);
    }
}