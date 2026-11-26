package com.synth;

import java.util.concurrent.atomic.AtomicLong;

public class RingBuffer {
    private final double[] buffer;
    private final int capacity;
    private final int mask;

    private final AtomicLong writeIndex = new AtomicLong(0);
    private final AtomicLong readIndex = new AtomicLong(0);

    public RingBuffer(int capacityPowerOfTwo) {
        if ((capacityPowerOfTwo & (capacityPowerOfTwo - 1)) != 0) {
            throw new IllegalArgumentException("Capacity must be a power of 2");
        }
        this.capacity = capacityPowerOfTwo;
        this.mask = capacityPowerOfTwo - 1;
        this.buffer = new double[capacityPowerOfTwo];
    }

    public void write(double sample) {
        long currentWrite = writeIndex.get();
        long currentRead = readIndex.get();

        if (currentWrite - currentRead >= capacity) {
            readIndex.lazySet(currentRead + 1);
        }

        buffer[(int) (currentWrite & mask)] = sample;
        writeIndex.set(currentWrite + 1);
    }

    public boolean readLatest(double[] destination) {
        int count = destination.length;
        long currentWrite = writeIndex.get();

        if (currentWrite < count) {
            return false;
        }

        long start = currentWrite - count;
        for (int i = 0; i < count; i++) {
            destination[i] = buffer[(int) ((start + i) & mask)];
        }

        readIndex.lazySet(currentWrite);
        return true;
    }
}