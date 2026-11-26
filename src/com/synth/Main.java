package com.synth;

import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame window = new JFrame("SynthWave Engine — Real-Time DSP & Particle Physics Visualizer");
            window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            window.setSize(1100, 800);
            window.setLocationRelativeTo(null);

            VisualizerPanel canvas = new VisualizerPanel();
            window.add(canvas);
            window.setVisible(true);
            canvas.requestFocusInWindow();
        });
    }
}

class VisualizerPanel extends JPanel {
    private static final int PARTICLE_COUNT = 600;
    private static final AudioFormat AUDIO_FORMAT = new AudioFormat(44100.0f, 16, 1, true, false);

    private final RingBuffer ringBuffer = new RingBuffer(8192);
    private final BeatDetector beatDetector = new BeatDetector();
    private final double[] analysisBuffer = new double[1024];
    private final double[] radialBins = new double[64];

    private final List<Particle> particles = new ArrayList<>(PARTICLE_COUNT);
    private final List<Shockwave> shockwaves = new ArrayList<>();
    private BufferedImage trailCanvas;

    // Audio device hot-swap state
    private final List<AudioDeviceSelector.InputDevice> availableDevices;
    private volatile int currentDeviceIndex = 0;
    private final AtomicBoolean switchDeviceRequested = new AtomicBoolean(false);

    // Interactive state
    private int visualMode = 1; // 1 = Particle Nebula, 2 = Radial Spectrum, 3 = Wave + Nebula
    private double inputGain = 1.0;
    private boolean showHud = true;
    private boolean manualBeatRequested = false;

    // Smoothed DSP metrics & FPS tracking
    private double smoothBass = 0.0;
    private double smoothMid = 0.0;
    private double smoothTreble = 0.0;
    private boolean initializedParticles = false;

    private long lastFpsTime = System.nanoTime();
    private int frameCounter = 0;
    private int currentFps = 60;

    private static class Shockwave {
        double radius;
        float alpha;

        Shockwave(double startRadius) {
            this.radius = startRadius;
            this.alpha = 0.95f;
        }

        boolean update() {
            radius += 8.5;
            alpha -= 0.026f;
            return alpha > 0.0f;
        }
    }

    public VisualizerPanel() {
        setFocusable(true);
        this.availableDevices = AudioDeviceSelector.getAvailableDevices(AUDIO_FORMAT);
        if (!availableDevices.isEmpty()) {
            this.currentDeviceIndex = AudioDeviceSelector.findPreferredDeviceIndex(availableDevices);
        }

        setupKeyboardControls();
        startAudioThread();

        Timer loopTimer = new Timer(16, e -> {
            updateFps();

            int w = Math.max(1, getWidth());
            int h = Math.max(1, getHeight());

            if (!initializedParticles && w > 1 && h > 1) {
                double cx = w / 2.0;
                double cy = h / 2.0;
                for (int i = 0; i < PARTICLE_COUNT; i++) {
                    particles.add(new Particle(cx, cy));
                }
                initializedParticles = true;
            }

            boolean beatHit = manualBeatRequested;
            manualBeatRequested = false;

            if (ringBuffer.readLatest(analysisBuffer)) {
                // Apply user sensitivity gain
                for (int i = 0; i < analysisBuffer.length; i++) {
                    analysisBuffer[i] = Math.max(-1.0, Math.min(1.0, analysisBuffer[i] * inputGain));
                }

                SpectrumAnalyzer.FrequencyBands bands = SpectrumAnalyzer.analyze(analysisBuffer);
                computeRadialSpectrum(analysisBuffer);

                smoothBass = (smoothBass * 0.70) + (bands.bass() * 0.30);
                smoothMid = (smoothMid * 0.70) + (bands.mid() * 0.30);
                smoothTreble = (smoothTreble * 0.70) + (bands.treble() * 0.30);

                if (beatDetector.detect(bands.bass())) {
                    beatHit = true;
                }
            }

            if (beatHit) {
                shockwaves.add(new Shockwave(55 + (smoothBass * 110)));
            }

            double cx = w / 2.0;
            double cy = h / 2.0;
            for (Particle p : particles) {
                p.update(cx, cy, smoothBass, smoothMid, smoothTreble, beatHit, w, h);
            }

            Iterator<Shockwave> it = shockwaves.iterator();
            while (it.hasNext()) {
                if (!it.next().update()) {
                    it.remove();
                }
            }

            repaint();
        });
        loopTimer.start();
    }

    private void setupKeyboardControls() {
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_1 -> visualMode = 1;
                    case KeyEvent.VK_2 -> visualMode = 2;
                    case KeyEvent.VK_3 -> visualMode = 3;
                    case KeyEvent.VK_UP -> inputGain = Math.min(4.0, inputGain + 0.25);
                    case KeyEvent.VK_DOWN -> inputGain = Math.max(0.25, inputGain - 0.25);
                    case KeyEvent.VK_H -> showHud = !showHud;
                    case KeyEvent.VK_SPACE -> manualBeatRequested = true;
                    case KeyEvent.VK_M -> {
                        if (!availableDevices.isEmpty()) {
                            currentDeviceIndex = (currentDeviceIndex + 1) % availableDevices.size();
                            switchDeviceRequested.set(true);
                        }
                    }
                }
            }
        });
    }

    private void computeRadialSpectrum(double[] samples) {
        double[] windowed = FFTProcessor.applyHannWindow(samples);
        Complex[] input = new Complex[windowed.length];
        for (int i = 0; i < windowed.length; i++) {
            input[i] = new Complex(windowed[i], 0.0);
        }
        Complex[] spectrum = FFTProcessor.fft(input);

        for (int i = 0; i < radialBins.length; i++) {
            int binIndex = 1 + (i * 3); // Covers ~43 Hz to ~8,200 Hz
            double mag = spectrum[binIndex].magnitude() / 25.0;
            double clamped = Math.min(1.0, mag);
            radialBins[i] = (radialBins[i] * 0.65) + (clamped * 0.35);
        }
    }

    private void updateFps() {
        frameCounter++;
        long now = System.nanoTime();
        if (now - lastFpsTime >= 1_000_000_000L) {
            currentFps = frameCounter;
            frameCounter = 0;
            lastFpsTime = now;
        }
    }

    private void startAudioThread() {
        Thread audioThread = new Thread(() -> {
            TargetDataLine line = null;
            byte[] buffer = new byte[512];

            while (true) {
                try {
                    if (line == null || switchDeviceRequested.getAndSet(false)) {
                        if (line != null) {
                            line.stop();
                            line.close();
                        }
                        AudioDeviceSelector.InputDevice dev = availableDevices.isEmpty()
                                ? null
                                : availableDevices.get(currentDeviceIndex);
                        line = AudioDeviceSelector.openLine(dev, AUDIO_FORMAT);
                    }

                    int bytesRead = line.read(buffer, 0, buffer.length);
                    for (int i = 0; i < bytesRead - 1; i += 2) {
                        int low = buffer[i] & 0xFF;
                        int high = buffer[i + 1];
                        short sample = (short) ((high << 8) | low);
                        ringBuffer.write(sample / 32768.0);
                    }
                } catch (Exception ex) {
                    try {
                        Thread.sleep(250);
                    } catch (InterruptedException ignored) {
                        break;
                    }
                }
            }
        }, "AudioCaptureThread");

        audioThread.setDaemon(true);
        audioThread.start();
    }

    private void ensureTrailCanvas(int width, int height) {
        if (trailCanvas == null || trailCanvas.getWidth() != width || trailCanvas.getHeight() != height) {
            BufferedImage newCanvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = newCanvas.createGraphics();
            g.setColor(new Color(10, 10, 16));
            g.fillRect(0, 0, width, height);
            g.dispose();
            trailCanvas = newCanvas;
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int w = Math.max(1, getWidth());
        int h = Math.max(1, getHeight());
        ensureTrailCanvas(w, h);

        Graphics2D tg = trailCanvas.createGraphics();
        tg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Motion trail fade
        tg.setComposite(AlphaComposite.SrcOver);
        tg.setColor(new Color(10, 10, 16, visualMode == 2 ? 75 : 36));
        tg.fillRect(0, 0, w, h);

        int centerX = w / 2;
        int centerY = h / 2;

        // Draw beat shockwaves across all modes
        tg.setStroke(new BasicStroke(2.5f));
        for (Shockwave sw : shockwaves) {
            int r = (int) sw.radius;
            tg.setColor(new Color(160, 90, 255, Math.max(0, (int) (sw.alpha * 220))));
            tg.drawOval(centerX - r, centerY - r, r * 2, r * 2);
        }

        // Mode 1 & Mode 3: Render 600-particle physics simulation
        if (visualMode == 1 || visualMode == 3) {
            for (Particle p : particles) {
                p.draw(tg, smoothBass, smoothMid, smoothTreble);
            }
        }

        // Mode 2: Render 360-degree Radial FFT Spectrum Bars
        if (visualMode == 2) {
            drawRadialSpectrum(tg, centerX, centerY);
        }

        // Mode 3: Render Live Time-Domain Oscilloscope Waveform
        if (visualMode == 3) {
            drawOscilloscope(tg, w, centerY);
        }

        // Central 3-band harmonic core
        int bassRadius = (int) (42 + (smoothBass * 90));
        tg.setColor(new Color(128,  143,  112, 140));
        tg.fillOval(centerX - bassRadius, centerY - bassRadius, bassRadius * 2, bassRadius * 2);

        int midRadius = (int) (30 + (smoothMid * 70));
        tg.setStroke(new BasicStroke(3.0f));
        tg.setColor(new Color(135, 169, 107, 200));
        tg.drawOval(centerX - midRadius, centerY - midRadius, midRadius * 2, midRadius * 2);

        int trebleRadius = (int) (12 + (smoothTreble * 42));
        tg.setColor(new Color(200, 213, 185));
        tg.fillOval(centerX - trebleRadius, centerY - trebleRadius, trebleRadius * 2, trebleRadius * 2);

        tg.dispose();
        g.drawImage(trailCanvas, 0, 0, null);

        if (showHud) {
            drawHud((Graphics2D) g, w, h);
        }
    }

    private void drawRadialSpectrum(Graphics2D g2d, int centerX, int centerY) {
        double baseRadius = 120 + (smoothBass * 45);
        g2d.setStroke(new BasicStroke(4.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        for (int i = 0; i < radialBins.length; i++) {
            double angle = (2.0 * Math.PI * i) / radialBins.length - (Math.PI / 2.0);
            double barLength = 15 + (radialBins[i] * 170);

            int x1 = (int) (centerX + Math.cos(angle) * baseRadius);
            int y1 = (int) (centerY + Math.sin(angle) * baseRadius);
            int x2 = (int) (centerX + Math.cos(angle) * (baseRadius + barLength));
            int y2 = (int) (centerY + Math.sin(angle) * (baseRadius + barLength));

            float hue = (float) (0.78 - (i / (double) radialBins.length) * 0.65);
            g2d.setColor(Color.getHSBColor(hue, 0.85f, 0.95f));
            g2d.drawLine(x1, y1, x2, y2);
        }
    }

    private void drawOscilloscope(Graphics2D g2d, int width, int centerY) {
        g2d.setStroke(new BasicStroke(2.2f));
        g2d.setColor(new Color(0, 240, 255, 190));

        int step = 2;
        int prevX = 0;
        int prevY = centerY;

        for (int i = 0; i < analysisBuffer.length; i += step) {
            int x = (int) ((i / (double) analysisBuffer.length) * width);
            int y = (int) (centerY - (analysisBuffer[i] * 160));
            if (i > 0) {
                g2d.drawLine(prevX, prevY, x, y);
            }
            prevX = x;
            prevY = y;
        }
    }

    private void drawHud(Graphics2D g2d, int width, int height) {
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Translucent HUD card in top-left
        g2d.setColor(new Color(15, 17, 26, 200));
        g2d.fillRoundRect(16, 16, 360, 148, 12, 12);
        g2d.setColor(new Color(80, 90, 120, 160));
        g2d.drawRoundRect(16, 16, 360, 148, 12, 12);

        g2d.setFont(new Font("Monospaced", Font.BOLD, 13));
        g2d.setColor(new Color(0, 230, 255));
        String modeLabel = switch (visualMode) {
            case 1 -> "1: PARTICLE NEBULA";
            case 2 -> "2: RADIAL SPECTRUM";
            default -> "3: WAVE + NEBULA";
        };
        g2d.drawString(String.format("FPS: %-3d | GAIN: %.2fx | %s", currentFps, inputGain, modeLabel), 28, 38);

        String deviceName = availableDevices.isEmpty()
                ? "Default System Input"
                : availableDevices.get(currentDeviceIndex).name();
        if (deviceName.length() > 36) {
            deviceName = deviceName.substring(0, 33) + "...";
        }
        g2d.setFont(new Font("Monospaced", Font.PLAIN, 12));
        g2d.setColor(new Color(200, 210, 230));
        g2d.drawString("INPUT [M]: " + deviceName, 28, 58);

        // Live frequency band meters
        drawHudBar(g2d, "BASS  ", smoothBass, new Color(128,  143,  112), 28, 80);
        drawHudBar(g2d, "MIDS  ", smoothMid, new Color(135, 169, 107), 28, 102);
        drawHudBar(g2d, "TREBLE", smoothTreble, new Color(200, 213, 185), 28, 124);

        g2d.setFont(new Font("Monospaced", Font.PLAIN, 11));
        g2d.setColor(new Color(140, 150, 175));
        g2d.drawString("[1/2/3] Mode  [UP/DN] Gain  [M] Mic/Loopback  [H] HUD", 28, 150);
    }

    private void drawHudBar(Graphics2D g2d, String label, double value, Color color, int x, int y) {
        g2d.setColor(new Color(210, 215, 230));
        g2d.drawString(label, x, y);

        int barX = x + 60;
        int barWidth = 265;
        int barHeight = 10;

        g2d.setColor(new Color(35, 40, 55));
        g2d.fillRoundRect(barX, y - 9, barWidth, barHeight, 6, 6);

        g2d.setColor(color);
        int fillWidth = (int) (Math.min(1.0, value) * barWidth);
        g2d.fillRoundRect(barX, y - 9, fillWidth, barHeight, 6, 6);
    }
}