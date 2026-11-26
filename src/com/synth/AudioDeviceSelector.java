package com.synth;

import javax.sound.sampled.*;
import java.util.ArrayList;
import java.util.List;

public class AudioDeviceSelector {

    public record InputDevice(String name, Mixer.Info mixerInfo, boolean isLoopback) {}

    public static List<InputDevice> getAvailableDevices(AudioFormat format) {
        List<InputDevice> devices = new ArrayList<>();
        DataLine.Info lineInfo = new DataLine.Info(TargetDataLine.class, format);

        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            try {
                Mixer mixer = AudioSystem.getMixer(info);
                if (mixer.isLineSupported(lineInfo)) {
                    String lower = info.getName().toLowerCase();
                    boolean loopback = lower.contains("stereo mix")
                            || lower.contains("loopback")
                            || lower.contains("what u hear")
                            || lower.contains("cable output")
                            || lower.contains("wave out");
                    devices.add(new InputDevice(info.getName(), info, loopback));
                }
            } catch (Exception ignored) {
                // Skip virtual/unsupported mixers
            }
        }
        return devices;
    }

    public static int findPreferredDeviceIndex(List<InputDevice> devices) {
        for (int i = 0; i < devices.size(); i++) {
            if (devices.get(i).isLoopback()) {
                return i; // Prefer system audio loopback if enabled
            }
        }
        return 0; // Default to primary microphone
    }

    public static TargetDataLine openLine(InputDevice device, AudioFormat format) throws LineUnavailableException {
        DataLine.Info lineInfo = new DataLine.Info(TargetDataLine.class, format);
        TargetDataLine line;
        if (device != null && device.mixerInfo() != null) {
            Mixer mixer = AudioSystem.getMixer(device.mixerInfo());
            line = (TargetDataLine) mixer.getLine(lineInfo);
        } else {
            line = (TargetDataLine) AudioSystem.getLine(lineInfo);
        }
        line.open(format);
        line.start();
        return line;
    }
}