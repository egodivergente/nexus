package com.axel.apkexplorer;

import java.util.Locale;

/**
 * 256-bit difference hash (17x16 luma grid) of a downscaled frame, plus frame-to-frame
 * change metrics used to tell animations from real transitions.
 */
public final class PerceptualHash {
    public static final int COLS = 16, ROWS = 16;
    /** Distance (of 256) at or below which two frames are the same picture. */
    public static final int TWIN = 10;
    /** Looser distance, only valid together with an identical heading and window layout. */
    public static final int LOOSE = 24;

    private PerceptualHash() {}

    private static double luma(int p) {
        return 0.299 * ((p >> 16) & 255) + 0.587 * ((p >> 8) & 255) + 0.114 * (p & 255);
    }

    /** @param px ARGB pixels of a w*h thumbnail */
    public static long[] dhash(int[] px, int w, int h) {
        int gw = COLS + 1;
        double[] grid = new double[gw * ROWS];
        for (int gy = 0; gy < ROWS; gy++) {
            int y0 = gy * h / ROWS, y1 = Math.max(y0 + 1, (gy + 1) * h / ROWS);
            for (int gx = 0; gx < gw; gx++) {
                int x0 = gx * w / gw, x1 = Math.max(x0 + 1, (gx + 1) * w / gw);
                double sum = 0; int n = 0;
                for (int y = y0; y < y1 && y < h; y++)
                    for (int x = x0; x < x1 && x < w; x++) { sum += luma(px[y * w + x]); n++; }
                grid[gy * gw + gx] = n == 0 ? 0 : sum / n;
            }
        }
        long[] bits = new long[(COLS * ROWS) / 64];
        int k = 0;
        for (int gy = 0; gy < ROWS; gy++)
            for (int gx = 0; gx < COLS; gx++, k++)
                if (grid[gy * gw + gx] > grid[gy * gw + gx + 1] + 2.0) bits[k >> 6] |= 1L << (k & 63);
        return bits;
    }

    public static int distance(long[] a, long[] b) {
        if (a == null || b == null || a.length != b.length) return Integer.MAX_VALUE;
        int d = 0;
        for (int i = 0; i < a.length; i++) d += Long.bitCount(a[i] ^ b[i]);
        return d;
    }

    public static String toHex(long[] h) {
        StringBuilder s = new StringBuilder();
        for (long v : h) s.append(String.format(Locale.ROOT, "%016x", v));
        return s.toString();
    }

    public static long[] fromHex(String s) {
        long[] out = new long[s.length() / 16];
        for (int i = 0; i < out.length; i++) out[i] = Long.parseUnsignedLong(s.substring(i * 16, i * 16 + 16), 16);
        return out;
    }

    /** Fraction of pixels whose colour moved noticeably between two same-sized thumbnails. */
    public static double changedFraction(int[] a, int[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) return 1.0;
        int changed = 0;
        for (int i = 0; i < a.length; i++) {
            int dr = Math.abs(((a[i] >> 16) & 255) - ((b[i] >> 16) & 255));
            int dg = Math.abs(((a[i] >> 8) & 255) - ((b[i] >> 8) & 255));
            int db = Math.abs((a[i] & 255) - (b[i] & 255));
            if (Math.max(dr, Math.max(dg, db)) > 24) changed++;
        }
        return changed / (double) a.length;
    }
}
