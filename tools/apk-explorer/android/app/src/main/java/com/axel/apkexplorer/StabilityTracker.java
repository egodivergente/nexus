package com.axel.apkexplorer;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides when the UI has settled after an action, instead of sleeping a fixed time.
 *
 * Real transitions change the structure (texts, controls, windows). Animations, shimmer and
 * spinners change pixels but leave the structure alone, so they are accepted once the
 * structure has been constant long enough. A visible progress indicator keeps waiting, up to a cap.
 */
public final class StabilityTracker {
    public enum Verdict { WAIT, STABLE, STABLE_ANIMATING, TIMEOUT_LOADING, TIMEOUT_UNSTABLE }

    private static final class Sample {
        final long t; final String key; final int[] thumb; final boolean loading;
        Sample(long t, String key, int[] thumb, boolean loading) { this.t = t; this.key = key; this.thumb = thumb; this.loading = loading; }
    }

    private final long start, minMs, maxMs, maxLoadingMs;
    private final List<Sample> samples = new ArrayList<>();
    public static final double STATIC_FRAME = 0.004;
    public static final long STATIC_SPAN_MS = 450, ANIMATING_SPAN_MS = 1500;
    public static final int STATIC_SAMPLES = 3;

    public StabilityTracker(long startMs, long minMs, long maxMs, long maxLoadingMs) {
        this.start = startMs; this.minMs = minMs; this.maxMs = maxMs; this.maxLoadingMs = maxLoadingMs;
    }

    public Verdict add(long now, String structureKey, int[] thumb, boolean loading) {
        samples.add(new Sample(now, structureKey, thumb, loading));
        long elapsed = now - start;
        if (elapsed < minMs) return Verdict.WAIT;
        // Trailing run with identical structure.
        int run = 1;
        Sample last = samples.get(samples.size() - 1);
        for (int i = samples.size() - 2; i >= 0 && samples.get(i).key.equals(last.key); i--) run++;
        long span = last.t - samples.get(samples.size() - run).t;
        boolean framesStatic = run >= 2;
        for (int i = samples.size() - run + 1; i < samples.size() && framesStatic; i++)
            if (PerceptualHash.changedFraction(samples.get(i - 1).thumb, samples.get(i).thumb) > STATIC_FRAME) framesStatic = false;
        if (!last.loading) {
            if (run >= STATIC_SAMPLES && span >= STATIC_SPAN_MS && framesStatic) return Verdict.STABLE;
            if (span >= ANIMATING_SPAN_MS) return Verdict.STABLE_ANIMATING;
        }
        if (last.loading && elapsed >= maxLoadingMs) return Verdict.TIMEOUT_LOADING;
        if (!last.loading && elapsed >= maxMs) return Verdict.TIMEOUT_UNSTABLE;
        return Verdict.WAIT;
    }

    public int samples() { return samples.size(); }
}
