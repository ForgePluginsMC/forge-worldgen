package com.forgeplugins.worldgen.noise;

/**
 * 2D simplex noise with fractal Brownian motion support.
 *
 * <p>Original implementation of the standard simplex-noise algorithm
 * (Gustavson-style 2D simplex). The instance is immutable after construction:
 * {@link #noise(double, double)} and {@link #fbm(double, double, int, double, double)}
 * perform zero allocation and are safe to call from multiple threads, which
 * is what lets chunk generation run on the server's parallel chunk threads.
 */
public final class SimplexNoise {

    private static final double F2 = 0.5 * (Math.sqrt(3.0) - 1.0);
    private static final double G2 = (3.0 - Math.sqrt(3.0)) / 6.0;

    // 2D gradient table: 8 unit-ish gradients around the compass.
    private static final double[] GRAD_X = {1, -1, 1, -1, 1, -1, 0, 0};
    private static final double[] GRAD_Y = {1, 1, -1, -1, 0, 0, 1, -1};

    private final byte[] perm = new byte[512];

    public SimplexNoise(long seed) {
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) {
            p[i] = i;
        }
        java.util.Random rng = new java.util.Random(seed);
        for (int i = 255; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int tmp = p[i];
            p[i] = p[j];
            p[j] = tmp;
        }
        for (int i = 0; i < 512; i++) {
            perm[i] = (byte) p[i & 0xFF];
        }
    }

    /**
     * Simplex noise in roughly [-1, 1]. Allocation-free and thread-safe.
     */
    public double noise(double xin, double yin) {
        double n0;
        double n1;
        double n2;

        double s = (xin + yin) * F2;
        int i = fastFloor(xin + s);
        int j = fastFloor(yin + s);
        double t = (i + j) * G2;
        double x0 = xin - (i - t);
        double y0 = yin - (j - t);

        int i1;
        int j1;
        if (x0 > y0) {
            i1 = 1;
            j1 = 0;
        } else {
            i1 = 0;
            j1 = 1;
        }

        double x1 = x0 - i1 + G2;
        double y1 = y0 - j1 + G2;
        double x2 = x0 - 1.0 + 2.0 * G2;
        double y2 = y0 - 1.0 + 2.0 * G2;

        int ii = i & 0xFF;
        int jj = j & 0xFF;

        double t0 = 0.5 - x0 * x0 - y0 * y0;
        if (t0 < 0) {
            n0 = 0.0;
        } else {
            t0 *= t0;
            int g = perm[ii + (perm[jj] & 0xFF)] & 7;
            n0 = t0 * t0 * (GRAD_X[g] * x0 + GRAD_Y[g] * y0);
        }

        double t1 = 0.5 - x1 * x1 - y1 * y1;
        if (t1 < 0) {
            n1 = 0.0;
        } else {
            t1 *= t1;
            int g = perm[ii + i1 + (perm[jj + j1] & 0xFF)] & 7;
            n1 = t1 * t1 * (GRAD_X[g] * x1 + GRAD_Y[g] * y1);
        }

        double t2 = 0.5 - x2 * x2 - y2 * y2;
        if (t2 < 0) {
            n2 = 0.0;
        } else {
            t2 *= t2;
            int g = perm[ii + 1 + (perm[jj + 1] & 0xFF)] & 7;
            n2 = t2 * t2 * (GRAD_X[g] * x2 + GRAD_Y[g] * y2);
        }

        // Scale to roughly [-1, 1].
        return 70.0 * (n0 + n1 + n2);
    }

    /**
     * Fractal Brownian motion: {@code octaves} layers of simplex noise with
     * geometric frequency/amplitude scaling. Result is roughly [-1, 1].
     * Allocation-free and thread-safe.
     */
    public double fbm(double x, double y, int octaves, double lacunarity, double gain) {
        double amplitude = 1.0;
        double frequency = 1.0;
        double sum = 0.0;
        double norm = 0.0;
        for (int o = 0; o < octaves; o++) {
            sum += amplitude * noise(x * frequency, y * frequency);
            norm += amplitude;
            amplitude *= gain;
            frequency *= lacunarity;
        }
        return norm == 0.0 ? 0.0 : sum / norm;
    }

    private static int fastFloor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }
}
