package com.forgeplugins.worldgen.seed;

/**
 * Central deterministic seed derivation (the Iris {@code SeedManager} idea,
 * original implementation).
 *
 * <p>Every noise field and stochastic choice in v3 derives its seed from the
 * world seed plus a fixed domain string, mixed with splitmix64. There is no
 * other randomness anywhere in generation: the same world seed always
 * produces the same world, and {@code /fgen verify} can prove it.
 *
 * <p>Instances are immutable and thread-safe.
 */
public final class SeedManager {

    private final long root;

    public SeedManager(long root) {
        this.root = root;
    }

    /**
     * Derives a 64-bit seed for a named domain, e.g.
     * {@code "terrain.continental"}. Domain strings are fixed constants at
     * the call sites — never user input.
     */
    public long derive(String domain) {
        long h = root ^ 0x9E3779B97F4A7C15L;
        for (int i = 0; i < domain.length(); i++) {
            h = mix64(h ^ domain.charAt(i));
        }
        return mix64(h ^ 0xBF58476D1CE4E5B9L);
    }

    /** splitmix64 avalanche. Public for cross-package deterministic hashing. */
    public static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Deterministic 64-bit mix of a seed and integer coordinates. */
    public static long hash2(long seed, int x, int z) {
        long h = seed ^ (x * 0x9E3779B1L) ^ (z * 0x85EBCA6BL);
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return h;
    }

    /** Maps a hash to a uniform double in [0, 1). */
    public static double toUnit(long h) {
        return (h >>> 11) * 0x1p-53;
    }
}
