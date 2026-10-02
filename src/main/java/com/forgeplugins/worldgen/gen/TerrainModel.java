package com.forgeplugins.worldgen.gen;

import com.forgeplugins.worldgen.noise.SimplexNoise;
import org.jetbrains.annotations.Nullable;

/**
 * The heart of ForgeWorldGen: turns world coordinates into terrain height,
 * volcano placement, climate values and region masks.
 *
 * <p>All methods are allocation-free and thread-safe after construction, so a
 * single instance can be shared by parallel chunk-generation threads.
 */
public final class TerrainModel {

    /** Size of one volcano cell in blocks; at most one volcano per cell. */
    private static final int VOLCANO_CELL = 512;
    /** Volcano cone radius in blocks. */
    private static final double VOLCANO_RADIUS = 96.0;
    /** Extra height added at the centre of a volcano cone. */
    private static final double VOLCANO_HEIGHT = 78.0;
    /** Pond cell size; at most one pond per cell. */
    private static final int POND_CELL = 128;
    /** Granite dome cell size; at most one dome per cell. */
    private static final int DOME_CELL = 1024;

    /**
     * Large-scale special regions, chosen from low-frequency noise so each
     * region is a coherent territory, never confetti.
     */
    public enum Region {
        NONE,
        /** Banded red/cream/ochre canyon mesas (Grand Canyon). */
        PAINTED_CANYON,
        /** Red-gray mottled badlands with lava pools (volcanic waste). */
        BARREN_WASTELAND,
        /** Rolling sand dunes with slip faces (Sahara). */
        DUNE_SEA,
        /** Geothermal basin: hot springs and geyser cones (Yellowstone). */
        GEYSER_BASIN,
        /** Yellow brimstone-stained volcanic flats (sulfur country). */
        SULFUR_FLATS,
    }

    private final long seed;
    private final int seaLevel;
    private final int snowMinElevation;
    private final double volcanoRarity;
    private final double mountainScale;
    private final double mountainRarity;

    private final SimplexNoise continent;
    private final SimplexNoise mountains;
    private final SimplexNoise detail;
    private final SimplexNoise climate;
    private final SimplexNoise region;
    private final SimplexNoise cliff;
    private final SimplexNoise extra;

    public TerrainModel(long seed, int seaLevel, int snowMinElevation, double volcanoRarity,
                        double mountainScale, double mountainRarity) {
        this.seed = seed;
        this.seaLevel = seaLevel;
        this.snowMinElevation = snowMinElevation;
        this.volcanoRarity = volcanoRarity;
        this.mountainScale = mountainScale;
        this.mountainRarity = mountainRarity;
        this.continent = new SimplexNoise(seed ^ 0xC0717E37L);
        this.mountains = new SimplexNoise(seed ^ 0xA017A15L);
        this.detail = new SimplexNoise(seed ^ 0xD37A11L);
        this.climate = new SimplexNoise(seed ^ 0xC11AA73L);
        this.region = new SimplexNoise(seed ^ 0x2E610A5L);
        this.cliff = new SimplexNoise(seed ^ 0xC11FF5L);
        this.extra = new SimplexNoise(seed ^ 0xE7712A1L);
    }

    public long seed() {
        return seed;
    }

    public int seaLevel() {
        return seaLevel;
    }

    /**
     * Minimum elevation for snow cover and snowy biomes. Below this line the
     * world stays green and rocky no matter how cold the climate noise gets.
     */
    public int snowMinElevation() {
        return snowMinElevation;
    }

    /**
     * Continental-scale heat 0..1: very-low-frequency temperature field that
     * constrains biome layout so hot biomes never border frozen ones. Hot
     * biomes only appear in warm zones, frozen biomes only in cold zones, and
     * temperate biomes fill the wide gradient between them.
     */
    public double heatAt(int x, int z) {
        return clamp01(0.5 + 0.5 * climate.fbm(x * 0.00003 + 11000.0, z * 0.00003 - 11000.0,
                2, 2.0, 0.5));
    }

    /**
     * Special region for a world column. Rare territories carved out of the
     * default terrain by low-frequency fields. Hot regions are gated on the
     * continental heat field so lava country never borders glaciers — the
     * terrain and the biome pick both go through here, so they always agree.
     */
    public Region regionAt(int x, int z) {
        double heat = heatAt(x, z);
        double a = region.fbm(x * 0.00007 + 3000.0, z * 0.00007 - 3000.0, 2, 2.0, 0.5);
        if (a > 0.60 && heat > 0.35) {
            return Region.PAINTED_CANYON;
        }
        if (a < -0.60 && heat > 0.30) {
            return Region.BARREN_WASTELAND;
        }
        double b = region.fbm(x * 0.00009 - 3000.0, z * 0.00009 + 3000.0, 2, 2.0, 0.5);
        if (b > 0.64 && heat > 0.45) {
            return Region.DUNE_SEA;
        }
        if (b < -0.64 && heat > 0.30) {
            return Region.GEYSER_BASIN;
        }
        double c = region.fbm(x * 0.00011 + 9000.0, z * 0.00011 - 9000.0, 2, 2.0, 0.5);
        if (c > 0.62 && heat > 0.40) {
            return Region.SULFUR_FLATS;
        }
        return Region.NONE;
    }

    /** 0..1 mask for the granite valley territory (Yosemite): sheer pale walls. */
    public double graniteMaskAt(int x, int z) {
        return smoothstep(0.32, 0.58,
                extra.fbm(x * 0.00012 + 7000.0, z * 0.00012 - 7000.0, 2, 2.0, 0.5));
    }

    /** 0..1 mask for the high plains territory: elevated grassland plateau. */
    public double highPlainsMaskAt(int x, int z) {
        return smoothstep(0.30, 0.55,
                extra.fbm(x * 0.00016 - 5000.0, z * 0.00016 + 5000.0, 2, 2.0, 0.5));
    }

    /**
     * Small mountain pond near a world column, or null. Ponds are "nice
     * little ponds in the mountains" by construction: one candidate per
     * 128-block cell, radius 3-7 blocks, only where the centre sits in a
     * mountain-valley dip above the sea. Pure function of coordinates, so
     * shores line up across chunk borders.
     */
    public static final class Pond {
        /** World X of the pond centre. */
        public final int cx;
        /** World Z of the pond centre. */
        public final int cz;
        /** Pond radius in blocks (3-7). */
        public final int r;
        /** Water surface Y (terrain height at the centre). */
        public final int level;

        Pond(int cx, int cz, int r, int level) {
            this.cx = cx;
            this.cz = cz;
            this.r = r;
            this.level = level;
        }
    }

    /**
     * Finds the pond influencing a world column, or null. Scans the 3x3
     * neighbourhood of pond cells; most columns reject on the cell hash
     * alone, so the expensive height checks only run near real ponds.
     */
    public @Nullable Pond pondAt(int x, int z) {
        int cellX = Math.floorDiv(x, POND_CELL);
        int cellZ = Math.floorDiv(z, POND_CELL);
        Pond best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                long ch = hash2(seed ^ 0x907D5L, cellX + dx, cellZ + dz);
                double roll = (ch >>> 11) * 0x1p-53;
                if (roll > 0.50) {
                    continue;
                }
                int pcx = (cellX + dx) * POND_CELL + (int) (((ch >>> 21) * 0x1p-43) * POND_CELL);
                int pcz = (cellZ + dz) * POND_CELL + (int) (((ch >>> 42) * 0x1p-22) * POND_CELL);
                int r = 3 + (int) (((ch >>> 53) * 0x1p-11) * 5.0); // 3..7
                double dist = Math.hypot(x - pcx, z - pcz);
                if (dist > r + 4 || dist >= bestDist) {
                    continue;
                }
                int hc = heightAt(pcx, pcz);
                if (!pondEligible(pcx, pcz, hc, r)) {
                    continue;
                }
                best = new Pond(pcx, pcz, r, hc);
                bestDist = dist;
            }
        }
        return best;
    }

    /**
     * A pond centre is eligible only in a genuine mountain-valley dip: hilly
     * ground, above the sea, below the peaks, a local low point, and nowhere
     * near a volcano.
     */
    private boolean pondEligible(int pcx, int pcz, int hc, int r) {
        if (hc <= seaLevel + 10 || hc >= seaLevel + 150) {
            return false;
        }
        if (mountainMaskAt(pcx, pcz) < 0.22) {
            return false;
        }
        if (volcanoDistance(pcx, pcz) >= 0.0) {
            return false;
        }
        int reach = r + 5;
        int low = Math.min(Math.min(heightAt(pcx - reach, pcz), heightAt(pcx + reach, pcz)),
                Math.min(heightAt(pcx, pcz - reach), heightAt(pcx, pcz + reach)));
        return hc <= low + 2;
    }

    /** One candidate wheat field per 64-block cell. */
    private static final int FIELD_CELL = 64;

    /**
     * A cultivated field: a neat farmed rectangle, border-agnostic. Every
     * column derives the same field from world coordinates, so fields cross
     * chunk borders seamlessly.
     */
    public static final class Field {
        /** World X of the field centre. */
        public final int cx;
        /** World Z of the field centre. */
        public final int cz;
        /** Half-width in blocks (6-9). */
        public final int hw;
        /** Half-depth in blocks (5-8). */
        public final int hd;
        /** Crop: 0 wheat, 1 carrots, 2 potatoes, 3 beetroots. */
        public final int crop;

        Field(int cx, int cz, int hw, int hd, int crop) {
            this.cx = cx;
            this.cz = cz;
            this.hw = hw;
            this.hd = hd;
            this.crop = crop;
        }
    }

    /**
     * Finds the field covering a world column, or null. One candidate per
     * 64-block cell at 8% of cells, inset so the rectangle always fits
     * inside its cell, and only on flat ground (corners within 3 of the
     * centre height). The biome gate lives in the generator.
     */
    public @Nullable Field fieldAt(int x, int z) {
        int cellX = Math.floorDiv(x, FIELD_CELL);
        int cellZ = Math.floorDiv(z, FIELD_CELL);
        long ch = hash2(seed ^ 0xFA291L, cellX, cellZ);
        if ((ch >>> 11) * 0x1p-53 > 0.08) {
            return null;
        }
        int hw = 6 + (int) (((ch >>> 21) * 0x1p-43) * 4.0); // 6..9
        int hd = 5 + (int) (((ch >>> 32) * 0x1p-32) * 4.0); // 5..8
        int cx = cellX * FIELD_CELL + hw + 2
                + (int) (((ch >>> 43) * 0x1p-21) * (FIELD_CELL - 2 * hw - 4));
        int cz = cellZ * FIELD_CELL + hd + 2
                + (int) (((ch >>> 54) * 0x1p-10) * (FIELD_CELL - 2 * hd - 4));
        int crop = (int) ((ch >>> 60) & 3);
        if (Math.abs(x - cx) > hw || Math.abs(z - cz) > hd) {
            return null;
        }
        int hc = heightAt(cx, cz);
        if (Math.abs(heightAt(cx - hw, cz - hd) - hc) > 3) {
            return null;
        }
        if (Math.abs(heightAt(cx + hw, cz - hd) - hc) > 3) {
            return null;
        }
        if (Math.abs(heightAt(cx - hw, cz + hd) - hc) > 3) {
            return null;
        }
        if (Math.abs(heightAt(cx + hw, cz + hd) - hc) > 3) {
            return null;
        }
        return new Field(cx, cz, hw, hd, crop);
    }

    /**
     * Mountain-range mask 0..1 at a world column, extracted from
     * {@link #heightAt} so pond placement and terrain agree on what counts
     * as mountains.
     */
    public double mountainMaskAt(int x, int z) {
        double threshold = 0.05 + (1.0 - mountainRarity) * 0.45;
        double maskField = mountains.fbm(x * 0.00011 + 100.0, z * 0.00011 - 100.0, 3, 2.0, 0.5);
        return smoothstep(threshold, threshold + 0.4, maskField);
    }

    /**
     * Smooth low-frequency pocket field 0..1 (8-block cells, bilinearly
     * interpolated) that modulates grass colonization: grass creeps into the
     * gentler pockets of stone hills instead of obeying one global cutoff.
     * Pure function of coordinates — chunk-border safe.
     */
    public double pocketAt(int x, int z) {
        int cx = Math.floorDiv(x, 8);
        int cz = Math.floorDiv(z, 8);
        double fx = (x - cx * 8) / 8.0;
        double fz = (z - cz * 8) / 8.0;
        double ux = fx * fx * (3.0 - 2.0 * fx);
        double uz = fz * fz * (3.0 - 2.0 * fz);
        double a = columnRandom(cx, cz, 0x90C4E7L);
        double b = columnRandom(cx + 1, cz, 0x90C4E7L);
        double c = columnRandom(cx, cz + 1, 0x90C4E7L);
        double d = columnRandom(cx + 1, cz + 1, 0x90C4E7L);
        return (a * (1.0 - ux) + b * ux) * (1.0 - uz) + (c * (1.0 - ux) + d * ux) * uz;
    }

    /**
     * Granite dome height contribution (Yosemite): at most one half-dome per
     * 1024-block cell, deterministic per world seed.
     */
    public double graniteDome(int x, int z) {
        int cellX = Math.floorDiv(x, DOME_CELL);
        int cellZ = Math.floorDiv(z, DOME_CELL);
        double best = 0.0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                long ch = hash2(seed ^ 0xD09E5L, cellX + dx, cellZ + dz);
                double roll = (ch >>> 11) * 0x1p-53;
                if (roll > 0.7) {
                    continue;
                }
                double cx = (cellX + dx) * DOME_CELL + ((ch >>> 21) * 0x1p-43) * DOME_CELL;
                double cz = (cellZ + dz) * DOME_CELL + ((ch >>> 42) * 0x1p-22) * DOME_CELL;
                double rd = 70.0 + ((ch >>> 53) * 0x1p-11) * 40.0;
                double dist = Math.hypot(x - cx, z - cz);
                if (dist < rd) {
                    double t = 1.0 - dist / rd;
                    double dome = (40.0 + ((ch >>> 47) * 0x1p-17) * 30.0) * Math.sqrt(t);
                    if (dome > best) {
                        best = dome;
                    }
                }
            }
        }
        return best;
    }

    /**
     * Terrain height (top solid block Y) for a world column. Combines a
     * continental base, masked rounded-craggy mountains, escarpment cliffs,
     * rolling hills, river gorges, region territories and volcano cones.
     * Result is clamped to sane world bounds by the caller.
     */
    public int heightAt(int x, int z) {
        double cont = continent.fbm(x * 0.00055, z * 0.00055, 4, 2.02, 0.5);
        double base = seaLevel + 4.0 + cont * 28.0;
        // Deeper open oceans with occasional islands.
        double deep = smoothstep(2.0, 14.0, seaLevel - base);
        base -= deep * 14.0;

        // Mountain ranges: rare regional bands, not the norm (Earth-like
        // distribution). Higher rarity lowers the mask threshold toward the
        // old always-mountainous behaviour; the default keeps most land
        // mellow and reserves high relief for occasional ranges.
        double mask = mountainMaskAt(x, z);

        double mountainH = 0.0;
        if (mask > 0.001) {
            // Rugged but rounded crests (Growth reference terrain): the
            // v1.0.1 ridges overshot into needles, so the ridge power and
            // crag weight are softened — craggy texture, rounded tops.
            double ridge = 1.0 - Math.abs(mountains.noise(x * 0.0016, z * 0.0016));
            double crag = 1.0 - Math.abs(mountains.noise(x * 0.006 + 40.0, z * 0.006 - 40.0));
            double rounded = Math.pow(ridge, 1.35) * 0.82 + Math.pow(crag, 2.0) * 0.22;
            mountainH = mask * rounded * 185.0 * mountainScale;
        }

        double highPlains = highPlainsMaskAt(x, z);
        double hills = detail.fbm(x * 0.004, z * 0.004, 3, 2.1, 0.5) * 11.0 * (1.0 - mask * 0.7);

        // Escarpment cliffs: a low-frequency terrace field lifts plateaus at
        // mountain fronts and high-plains edges, so the lift boundary reads
        // as a sheer rock face with talus at its base. The two masks merge
        // with a smooth OR so no crease line forms where they meet.
        double cliffN = cliff.fbm(x * 0.00033 + 200.0, z * 0.00033 - 200.0, 2, 2.0, 0.5);
        double liftMask = mask + highPlains - mask * highPlains;
        double cliffH = smoothstep(0.02, 0.22, cliffN) * liftMask * 44.0;

        double h = base + mountainH + hills + cliffH + highPlains * 26.0;

        Region region = regionAt(x, z);
        double gm = graniteMaskAt(x, z);
        if (region == Region.DUNE_SEA) {
            // Transverse dunes: sinuous ridges warped by noise, slip faces on
            // the lee side. The base stays flat so the dunes read as dunes.
            double warp = extra.fbm(x * 0.004 + 77.0, z * 0.004 - 77.0, 2, 2.0, 0.5) * 5.0;
            double dune = Math.abs(Math.sin((x * 0.045 + z * 0.075) + warp * 0.35));
            h = base + Math.pow(dune, 1.7) * 9.0 + hills * 0.3;
        } else if (region == Region.PAINTED_CANYON) {
            // Mesas: quantised terraces so the height steps themselves form
            // cliffs, painted by the strata surfacing. The quantisation
            // domain is warped by low-frequency noise so terrace edges wiggle
            // instead of running straight, and each step bevels across a
            // transition band instead of snapping — no staircases, no sharp
            // 90-degree corners. Step height breathes slowly per territory.
            double step = 12.0 + extra.fbm(x * 0.0004 - 7.0, z * 0.0004 + 7.0, 2, 2.0, 0.5) * 2.5;
            double warp = extra.fbm(x * 0.0016 + 31.0, z * 0.0016 - 31.0, 2, 2.0, 0.5) * 7.0;
            double t = (h - seaLevel + warp) / step;
            double qi = Math.floor(t);
            double f = t - qi;
            double s = smoothstep(0.30, 0.70, f);
            h = seaLevel + (qi + s) * step + detail.fbm(x * 0.01, z * 0.01, 1, 2.0, 0.5) * 2.5;
        } else if (region == Region.BARREN_WASTELAND) {
            // Shallow dips that become lava pools in the surfacing pass.
            double dip = smoothstep(0.35, 0.75,
                    extra.fbm(x * 0.0021 + 400.0, z * 0.0021 - 400.0, 2, 2.0, 0.5)) * 9.0;
            h -= dip;
        } else if (region == Region.SULFUR_FLATS) {
            // Flat volcanic plain pocked with shallow sulfur pits.
            h = base + hills * 0.35;
            double pit = smoothstep(0.45, 0.75,
                    extra.fbm(x * 0.003 + 900.0, z * 0.003 - 900.0, 2, 2.0, 0.5)) * 4.0;
            h -= pit;
        }
        if (gm > 0.001) {
            // Granite valley (Yosemite): a half-dome rising beside a wide
            // U-shaped glacial carve.
            h += gm * graniteDome(x, z);
            double vn = 1.0 - Math.abs(extra.fbm(x * 0.00045 + 9000.0, z * 0.00045 - 9000.0,
                    2, 2.0, 0.5));
            h -= (1.0 - smoothstep(0.02, 0.28, vn)) * gm * 46.0;
        }

        // Rivers: V-shaped water-cut gorges. Narrow band, deep parabolic
        // carve so valleys read as carved by water, not just low noise.
        double riverBand = 1.0 - Math.abs(detail.fbm(x * 0.0009 + 500.0, z * 0.0009 - 500.0,
                2, 2.0, 0.5));
        double carveT = 1.0 - smoothstep(0.008, 0.06, riverBand); // 1 at the gorge centreline
        double valley = 1.0 - smoothstep(6.0, 42.0, Math.abs(base - seaLevel));
        h -= carveT * carveT * 22.0 * valley * (1.0 + gm * 0.6);

        h += volcanoCone(x, z);

        return (int) Math.round(h);
    }

    /**
     * Lava-lake level for a wasteland column, or -1 when the column holds no
     * lake. Only the deep parts of the wasteland dips pool lava; the level
     * is per 128-block cell, so shores line up across chunk borders.
     */
    public int wastelandPoolAt(int x, int z) {
        double dip = extra.fbm(x * 0.0021 + 400.0, z * 0.0021 - 400.0, 2, 2.0, 0.5);
        if (dip < 0.55) {
            return -1;
        }
        long ch = hash2(seed ^ 0x1A9A5L, Math.floorDiv(x, 128), Math.floorDiv(z, 128));
        double roll = (ch >>> 11) * 0x1p-53;
        if (roll > 0.5) {
            return -1;
        }
        return seaLevel + 2 + (int) (((ch >>> 21) * 0x1p-43) * 8.0);
    }

    /**
     * Volcano cone height contribution at a world column, 0 when no volcano
     * is near. Deterministic per world seed.
     */
    public double volcanoCone(int x, int z) {
        if (volcanoRarity <= 0.0) {
            return 0.0;
        }
        int cellX = Math.floorDiv(x, VOLCANO_CELL);
        int cellZ = Math.floorDiv(z, VOLCANO_CELL);
        double best = 0.0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                long cellHash = hash2(seed ^ 0x001CA401L, cellX + dx, cellZ + dz);
                double roll = (cellHash >>> 11) * 0x1p-53; // [0,1)
                if (roll >= volcanoRarity) {
                    continue;
                }
                int originX = (cellX + dx) * VOLCANO_CELL;
                int originZ = (cellZ + dz) * VOLCANO_CELL;
                double cx = originX + ((cellHash >>> 21) * 0x1p-43) * VOLCANO_CELL;
                double cz = originZ + ((cellHash >>> 42) * 0x1p-22) * VOLCANO_CELL;
                double dist = Math.hypot(x - cx, z - cz);
                if (dist < VOLCANO_RADIUS) {
                    double t = 1.0 - dist / VOLCANO_RADIUS;
                    double cone = t * t * VOLCANO_HEIGHT;
                    if (cone > best) {
                        best = cone;
                    }
                }
            }
        }
        return best;
    }

    /**
     * Distance in blocks to the nearest volcano centre, or -1 when none is
     * within cone range. Used to paint caldera biomes and lava craters.
     */
    public double volcanoDistance(int x, int z) {
        if (volcanoRarity <= 0.0) {
            return -1.0;
        }
        int cellX = Math.floorDiv(x, VOLCANO_CELL);
        int cellZ = Math.floorDiv(z, VOLCANO_CELL);
        double best = Double.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                long cellHash = hash2(seed ^ 0x001CA401L, cellX + dx, cellZ + dz);
                double roll = (cellHash >>> 11) * 0x1p-53;
                if (roll >= volcanoRarity) {
                    continue;
                }
                int originX = (cellX + dx) * VOLCANO_CELL;
                int originZ = (cellZ + dz) * VOLCANO_CELL;
                double cx = originX + ((cellHash >>> 21) * 0x1p-43) * VOLCANO_CELL;
                double cz = originZ + ((cellHash >>> 42) * 0x1p-22) * VOLCANO_CELL;
                double dist = Math.hypot(x - cx, z - cz);
                if (dist < VOLCANO_RADIUS && dist < best) {
                    best = dist;
                }
            }
        }
        return best == Double.MAX_VALUE ? -1.0 : best;
    }

    /**
     * Packed centre of the volcano nearest a column:
     * {@code (cx << 32) | (cz & 0xffffffffL)}. Only valid when
     * {@link #volcanoDistance(int, int)} is non-negative; both methods pick
     * the same nearest volcano, so the pairing is consistent.
     */
    public long volcanoCenter(int x, int z) {
        int cellX = Math.floorDiv(x, VOLCANO_CELL);
        int cellZ = Math.floorDiv(z, VOLCANO_CELL);
        double best = Double.MAX_VALUE;
        long packed = 0L;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                long cellHash = hash2(seed ^ 0x001CA401L, cellX + dx, cellZ + dz);
                double roll = (cellHash >>> 11) * 0x1p-53; // [0,1)
                if (roll >= volcanoRarity) {
                    continue;
                }
                int originX = (cellX + dx) * VOLCANO_CELL;
                int originZ = (cellZ + dz) * VOLCANO_CELL;
                double cx = originX + ((cellHash >>> 21) * 0x1p-43) * VOLCANO_CELL;
                double cz = originZ + ((cellHash >>> 42) * 0x1p-22) * VOLCANO_CELL;
                double dist = Math.hypot(x - cx, z - cz);
                if (dist < VOLCANO_RADIUS && dist < best) {
                    best = dist;
                    packed = (((long) (int) cx) << 32) | ((int) cz & 0xffffffffL);
                }
            }
        }
        return packed;
    }

    /** Air temperature 0..1 at a column: climate noise minus altitude lapse. */
    public double temperatureAt(int x, int z, int height) {
        double t = 0.55 + 0.45 * climate.fbm(x * 0.0004 + 1000.0, z * 0.0004 - 1000.0, 2, 2.0, 0.5);
        t -= Math.max(0, height - seaLevel) * 0.0038;
        return clamp01(t);
    }

    /** Moisture 0..1 at a column. */
    public double moistureAt(int x, int z) {
        return clamp01(0.5 + 0.5 * climate.fbm(x * 0.0005 - 1000.0, z * 0.0005 + 1000.0, 2, 2.0, 0.5));
    }

    /**
     * Low-frequency warp (in blocks) applied to sedimentary strata bands so
     * the banding undulates naturally instead of slicing perfectly level.
     */
    public double strataWarp(int x, int z) {
        return detail.fbm(x * 0.0021 + 900.0, z * 0.0021 - 900.0, 2, 2.0, 0.5) * 3.0;
    }

    /**
     * Deterministic per-column random in [0, 1). Used for tree/flora
     * placement so the world is stable across restarts.
     */
    public double columnRandom(int x, int z, long salt) {
        long h = hash2(seed ^ salt, x, z);
        return (h >>> 11) * 0x1p-53;
    }

    /**
     * 64-bit deterministic hash of a seed and two ints (splitmix64-style).
     * Allocation-free.
     */
    public static long hash2(long seed, int x, int z) {
        long h = seed + (long) x * 0x9E3779B97F4A7C15L + (long) z * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }

    private static double smoothstep(double edge0, double edge1, double v) {
        double t = clamp01((v - edge0) / (edge1 - edge0));
        return t * t * (3.0 - 2.0 * t);
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}
