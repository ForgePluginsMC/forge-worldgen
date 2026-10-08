package com.forgeplugins.worldgen.biome;

import java.util.Arrays;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.jetbrains.annotations.NotNull;

/**
 * ForgeWorldGen's biomes. Each entry carries:
 * <ul>
 *   <li>a vanilla <em>derivative</em> (the Iris trick) — vanilla decoration,
 *       structure and mob systems see a normal vanilla biome, so they keep
 *       working on our terrain;</li>
 *   <li>a surface palette — the top block and the subsurface block/depth our
 *       {@code SurfaceStage} paints.</li>
 * </ul>
 */
public enum ForgeBiome {
    // --- Classic ---
    PLAINS(Biome.PLAINS, "Plains", "plains",
            Material.GRASS_BLOCK, Material.DIRT, 3, Material.COARSE_DIRT, Material.DIRT_PATH),
    FOREST(Biome.FOREST, "Forest", "forest",
            Material.GRASS_BLOCK, Material.DIRT, 3, Material.PODZOL, Material.MOSS_BLOCK),
    DESERT(Biome.DESERT, "Desert", "desert",
            Material.SAND, Material.SAND, 4, Material.SANDSTONE, Material.SMOOTH_SANDSTONE),
    // --- Forest variants ---
    BIRCH_FOREST(Biome.BIRCH_FOREST, "Birch forest", "birch forest",
            Material.GRASS_BLOCK, Material.DIRT, 3, Material.PODZOL, Material.COARSE_DIRT),
    DARK_FOREST(Biome.DARK_FOREST, "Dark forest", "dark forest",
            Material.GRASS_BLOCK, Material.DIRT, 3, Material.PODZOL, Material.MYCELIUM),
    JUNGLE(Biome.JUNGLE, "Jungle", "jungle",
            Material.GRASS_BLOCK, Material.DIRT, 3, Material.PODZOL, Material.MOSS_BLOCK),
    TAIGA(Biome.TAIGA, "Taiga", "taiga",
            Material.GRASS_BLOCK, Material.DIRT, 3, Material.PODZOL, Material.COARSE_DIRT),
    CHERRY_GROVE(Biome.CHERRY_GROVE, "Cherry grove", "cherry grove",
            Material.GRASS_BLOCK, Material.DIRT, 3, Material.COARSE_DIRT, Material.PODZOL),
    // --- Cold ---
    SNOWY(Biome.SNOWY_PLAINS, "Snowy", "snowy plains",
            Material.GRASS_BLOCK, Material.DIRT, 3, Material.PODZOL, Material.COARSE_DIRT),
    SNOWY_TAIGA(Biome.SNOWY_TAIGA, "Snowy taiga", "snowy taiga",
            Material.GRASS_BLOCK, Material.DIRT, 3, Material.PODZOL, Material.COARSE_DIRT),
    GROVE(Biome.GROVE, "Grove", "grove",
            Material.GRASS_BLOCK, Material.DIRT, 3, Material.PODZOL, Material.GRAVEL),
    FROZEN_PEAKS(Biome.FROZEN_PEAKS, "Frozen peaks", "frozen peaks",
            Material.STONE, Material.STONE, 3, Material.PACKED_ICE, Material.GRAVEL),
    // --- Dry ---
    BADLANDS(Biome.BADLANDS, "Badlands", "badlands",
            Material.RED_SAND, Material.TERRACOTTA, 4, Material.RED_SANDSTONE, Material.ORANGE_TERRACOTTA),
    ERODED_BADLANDS(Biome.ERODED_BADLANDS, "Eroded badlands", "eroded badlands",
            Material.TERRACOTTA, Material.TERRACOTTA, 4, Material.RED_SAND, Material.YELLOW_TERRACOTTA),
    // --- Wet ---
    SWAMP(Biome.SWAMP, "Swamp", "swamp",
            Material.GRASS_BLOCK, Material.DIRT, 3, Material.CLAY, Material.MUD),
    MANGROVE_SWAMP(Biome.MANGROVE_SWAMP, "Mangrove swamp", "mangrove swamp",
            Material.MUD, Material.MUD, 3, Material.CLAY, Material.DIRT),
    // --- Mountain ---
    MOUNTAINS(Biome.WINDSWEPT_HILLS, "Mountains", "windswept hills",
            Material.STONE, Material.STONE, 3, Material.GRAVEL, Material.TUFF),
    MEADOW(Biome.MEADOW, "Meadow", "meadow",
            Material.GRASS_BLOCK, Material.DIRT, 3, Material.COARSE_DIRT, Material.DIRT_PATH),
    VOLCANIC(Biome.STONY_PEAKS, "Volcanic", "stony peaks",
            Material.BASALT, Material.BASALT, 3, Material.GRAVEL, Material.BLACKSTONE),
    SCABLAND(Biome.SAVANNA, "Scabland", "savanna",
            Material.STONE, Material.BASALT, 3, Material.BASALT, Material.COARSE_DIRT),
    // --- Ocean ---
    OCEAN(Biome.OCEAN, "Ocean", "ocean",
            Material.SAND, Material.DIRT, 3, Material.GRAVEL, Material.CLAY),
    DEEP_OCEAN(Biome.DEEP_OCEAN, "Deep ocean", "deep ocean",
            Material.GRAVEL, Material.DIRT, 3, Material.CLAY, Material.SAND),
    WARM_OCEAN(Biome.WARM_OCEAN, "Warm ocean", "warm ocean",
            Material.SAND, Material.SAND, 3, Material.GRAVEL, Material.CLAY),
    LUKEWARM_OCEAN(Biome.LUKEWARM_OCEAN, "Lukewarm ocean", "lukewarm ocean",
            Material.SAND, Material.DIRT, 3, Material.GRAVEL, Material.CLAY),
    BEACH(Biome.BEACH, "Beach", "beach",
            Material.SAND, Material.SAND, 3, Material.GRAVEL, Material.SANDSTONE),
    STONY_SHORE(Biome.STONY_SHORE, "Stony shore", "stony shore",
            Material.STONE, Material.STONE, 3, Material.GRAVEL, Material.TUFF),
    RIVER(Biome.RIVER, "River", "river",
            Material.SAND, Material.GRAVEL, 3, Material.CLAY, Material.GRAVEL),
    // --- Special ---
    MUSHROOM_FIELDS(Biome.MUSHROOM_FIELDS, "Mushroom fields", "mushroom fields",
            Material.MYCELIUM, Material.DIRT, 3, Material.PODZOL, Material.MYCELIUM);

    private final Biome vanilla;
    private final String label;
    private final String vanillaLabel;
    private final Material surface;
    private final Material subsurface;
    private final int subDepth;
    private final Material patchWarm;
    private final Material patchCold;

    ForgeBiome(@NotNull Biome vanilla, @NotNull String label, @NotNull String vanillaLabel,
               @NotNull Material surface, @NotNull Material subsurface, int subDepth,
               @NotNull Material patchWarm, @NotNull Material patchCold) {
        this.vanilla = vanilla;
        this.label = label;
        this.vanillaLabel = vanillaLabel;
        this.surface = surface;
        this.subsurface = subsurface;
        this.subDepth = subDepth;
        this.patchWarm = patchWarm;
        this.patchCold = patchCold;
    }

    /** Human-readable name, e.g. "Volcanic". */
    public @NotNull String label() {
        return label;
    }

    /** Human-readable name of the vanilla derivative, e.g. "stony peaks". */
    public @NotNull String vanillaLabel() {
        return vanillaLabel;
    }

    /** The vanilla biome vanilla systems see for this biome. */
    public @NotNull Biome vanilla() {
        return vanilla;
    }

    public @NotNull Material surface() {
        return surface;
    }

    public @NotNull Material subsurface() {
        return subsurface;
    }

    public int subDepth() {
        return subDepth;
    }

    /** Warm patch material (used where the variation noise runs high). */
    public @NotNull Material patchWarm() {
        return patchWarm;
    }

    /** Cold patch material (used where the variation noise runs low). */
    public @NotNull Material patchCold() {
        return patchCold;
    }

    private static final List<Biome> VANILLA_LIST = Arrays.stream(values())
            .map(ForgeBiome::vanilla)
            .distinct()
            .toList();

    /** Every vanilla biome this provider can return. */
    public static @NotNull List<Biome> vanillaList() {
        return VANILLA_LIST;
    }
}
