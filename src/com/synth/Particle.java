package com.synth;

import java.awt.*;
import java.util.concurrent.ThreadLocalRandom;

public class Particle {
    private double x;
    private double y;
    private double vx;
    private double vy;
    private final double baseSize;
    private final double orbitPreference;
    private final float hueOffset;

    public Particle(double startX, double startY) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        this.x = startX;
        this.y = startY;

        double angle = rng.nextDouble(0, Math.PI * 2);
        double speed = rng.nextDouble(0.5, 2.5);
        this.vx = Math.cos(angle) * speed;
        this.vy = Math.sin(angle) * speed;

        this.baseSize = rng.nextDouble(2.0, 5.5);
        this.orbitPreference = rng.nextDouble(110.0, 260.0);
        this.hueOffset = rng.nextFloat(-0.08f, 0.08f);
    }

    public void update(
            double centerX,
            double centerY,
            double bass,
            double mid,
            double treble,
            boolean beatTriggered,
            int width,
            int height
    ) {
        double dx = x - centerX;
        double dy = y - centerY;
        double dist = Math.hypot(dx, dy) + 0.001; // Prevent division by zero

        // Unit radial vector (pointing outward from center)
        double rx = dx / dist;
        double ry = dy / dist;

        // Unit tangential vector (perpendicular, for orbital swirl)
        double tx = -ry;
        double ty = rx;

        // 1. Restoring spring force toward preferred orbit radius
        double targetRadius = orbitPreference + (bass * 120.0);
        double radialError = targetRadius - dist;
        double springForce = radialError * 0.0045;

        // 2. Beat impulse: sharp radial blast outward on kick hits
        double beatImpulse = beatTriggered ? (14.0 + bass * 18.0) : 0.0;

        // 3. Tangential swirl driven by midrange energy
        double swirlForce = 0.12 + (mid * 1.35);

        // 4. High-frequency treble jitter
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double jitterX = (treble > 0.15) ? rng.nextDouble(-1.0, 1.0) * treble * 2.2 : 0.0;
        double jitterY = (treble > 0.15) ? rng.nextDouble(-1.0, 1.0) * treble * 2.2 : 0.0;

        // Apply accelerations to velocity
        vx += (rx * (springForce + beatImpulse)) + (tx * swirlForce) + jitterX;
        vy += (ry * (springForce + beatImpulse)) + (ty * swirlForce) + jitterY;

        // Fluid drag (damping) so velocities stay bounded and smooth
        vx *= 0.91;
        vy *= 0.91;

        // Integrate position
        x += vx;
        y += vy;

        // Soft screen boundary bounce
        if (x < 10) { x = 10; vx = Math.abs(vx) * 0.7; }
        if (x > width - 10) { x = width - 10; vx = -Math.abs(vx) * 0.7; }
        if (y < 10) { y = 10; vy = Math.abs(vy) * 0.7; }
        if (y > height - 10) { y = height - 10; vy = -Math.abs(vy) * 0.7; }
    }

    public void draw(Graphics2D g2d, double bass, double mid, double treble) {
        // Hue shifts from deep violet/blue (0.72) toward cyan/gold as treble & mid rise
        float hue = (float) ((0.74 - (treble * 0.58) - (mid * 0.12) + hueOffset + 1.0) % 1.0);
        float saturation = (float) Math.min(1.0, 0.75 + (mid * 0.25));
        float brightness = (float) Math.min(1.0, 0.55 + (treble * 0.45) + (bass * 0.2));

        g2d.setColor(Color.getHSBColor(hue, saturation, brightness));

        int size = (int) Math.round(baseSize + (bass * 4.5) + (treble * 2.5));
        int drawX = (int) Math.round(x - size / 2.0);
        int drawY = (int) Math.round(y - size / 2.0);

        g2d.fillOval(drawX, drawY, size, size);
    }
}
