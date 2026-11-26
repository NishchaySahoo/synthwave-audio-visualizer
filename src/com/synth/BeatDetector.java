package com.synth;

public class BeatDetector {
    private final double[] bassHistory;
    private int historyIndex = 0;
    private int samplesFilled = 0;
    private int cooldownFrames = 0;

    private static final int HISTORY_SIZE = 43;      // ~0.72 seconds at 60 FPS
    private static final double SENSITIVITY = 1.35;  // Spike threshold multiplier
    private static final double MIN_BASS = 0.08;     // Prevents triggers during room silence
    private static final int COOLDOWN = 10;          // Minimum frames between beats (~166 ms)

    public BeatDetector() {
        this.bassHistory = new double[HISTORY_SIZE];
    }

    public boolean detect(double currentBass) {
        if (cooldownFrames > 0) {
            cooldownFrames--;
        }

        // Compute rolling average of recent bass energy
        double sum = 0.0;
        for (int i = 0; i < samplesFilled; i++) {
            sum += bassHistory[i];
        }
        double averageBass = (samplesFilled > 0) ? (sum / samplesFilled) : 0.0;

        // Store current bass into circular history buffer
        bassHistory[historyIndex] = currentBass;
        historyIndex = (historyIndex + 1) % HISTORY_SIZE;
        if (samplesFilled < HISTORY_SIZE) {
            samplesFilled++;
        }

        // Trigger beat if current bass spikes sharply above rolling average
        if (samplesFilled >= 10
                && cooldownFrames == 0
                && currentBass > MIN_BASS
                && currentBass > averageBass * SENSITIVITY) {
            cooldownFrames = COOLDOWN;
            return true;
        }

        return false;
    }
}
