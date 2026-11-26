# SynthWave Engine — Real-Time Audio-Reactive DSP & Particle Physics Visualizer

A low-latency, 60 FPS audio visualizer and Newtonian particle physics engine built from scratch in **100% Core Java (JDK 21+)** with **zero external DSP, audio, or graphics libraries**.

> **[Add a 15-second screen recording / GIF of the visualizer reacting to music here]**

---

## System Architecture

```text
[Hardware Mic / Stereo Mix (44.1 kHz PCM)]
                  │
                  ▼ (Producer Thread - Non-Blocking)
      [Lock-Free SPSC RingBuffer (8192)]
                  │
                  ▼ (Consumer Thread - 60 FPS Render Loop)
  [Hann Window] ──► [Radix-2 Cooley-Tukey FFT] ──► [Band Separation & Beat Detection]
                                                               │
                                                               ▼
                                          [600-Particle Physics + Java2D Canvas]
```

## Key Engineering Highlights
* **Lock-Free SPSC Concurrency (`RingBuffer.java`):** Decouples blocking hardware audio capture (`javax.sound.sampled`) from the Swing/Java2D rendering thread using `AtomicLong` memory barriers and bitwise power-of-two masking (`index & (N - 1)`), eliminating buffer underruns without locks.
* **First-Principles Digital Signal Processing (`FFTProcessor.java`, `SpectrumAnalyzer.java`):** Implements a recursive $O(N \log N)$ Radix-2 Cooley-Tukey Fast Fourier Transform with Hann windowing to prevent spectral leakage and isolate **Bass (43–258 Hz)**, **Midrange (301–2,494 Hz)**, and **Treble (2,537–10,750 Hz)** energy bands.
* **Adaptive Beat Detection (`BeatDetector.java`):** Computes a rolling energy-flux baseline to trigger shockwave impulses on transient drum hits across varying volume levels.
* **2D Particle Physics & Motion Trails (`Particle.java`):** Simulates 600 autonomous particles driven by radial bass shockwaves, tangential midrange swirl forces, treble shimmer, and alpha-blended `BufferedImage` motion ribbons.
* **Live Loopback Hot-Swapping (`AudioDeviceSelector.java`):** Enumerates system audio mixers to capture either a physical microphone or internal system loopback (*Stereo Mix*).

---

## Keyboard Controls

| Key | Action |
| :--- | :--- |
| **`1` / `2` / `3`** | Switch visual modes (*Particle Nebula*, *360° Radial Spectrum*, *Oscilloscope + Nebula*) |
| **`UP` / `DOWN`** | Increase or decrease input sensitivity (`Gain`) |
| **`M`** | Hot-swap between available microphones and system audio loopback (*Stereo Mix*) |
| **`SPACE`** | Trigger a manual bass shockwave |
| **`H`** | Toggle the live FPS & frequency telemetry HUD |

---

## How to Build & Run

Ensure you have **JDK 17 or higher** installed:

```bash
# 1. Clone the repository
git clone git clone https://github.com/NishchaySahoo/synthwave-audio-visualizer.git
cd synthwave-audio-visualizer

# 2. Compile all source files
javac -d out src/com/synth/*.java

# 3. Run the application
java -cp out com.synth.Main
```