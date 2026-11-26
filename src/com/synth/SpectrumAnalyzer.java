package com.synth;

public class SpectrumAnalyzer {

    // Holds the 3 separated frequency energies for one moment in time
    public record FrequencyBands(double bass, double mid, double treble) {}

    public static FrequencyBands analyze(double[] samples1024) {
        // 1. Apply Hann window & convert to Complex array
        double[] windowed = FFTProcessor.applyHannWindow(samples1024);
        Complex[] input = new Complex[windowed.length];
        for (int i = 0; i < windowed.length; i++) {
            input[i] = new Complex(windowed[i], 0.0);
        }

        // 2. Run FFT to get 512 usable frequency magnitudes
        Complex[] spectrum = FFTProcessor.fft(input);

        // 3. Average magnitudes inside each frequency range (normalized gently)
        double bass = averageBand(spectrum, 1, 6) / 40.0;
        double mid = averageBand(spectrum, 7, 58) / 15.0;
        double treble = averageBand(spectrum, 59, 250) / 5.0;

        // Cap each band cleanly between 0.0 and 1.0
        return new FrequencyBands(
                Math.min(1.0, bass),
                Math.min(1.0, mid),
                Math.min(1.0, treble)
        );
    }

    private static double averageBand(Complex[] spectrum, int startBin, int endBin) {
        // TODO: Loop from startBin to endBin (inclusive), sum up spectrum[i].magnitude(),
        //       and return the average (sum / number of bins in this range).
        double sum = 0.0;
        for (int i = startBin; i <= endBin; i++) {
            sum += spectrum[i].magnitude();
        }
        int count = (endBin - startBin) + 1;
        return sum / count;
    }
}