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
    // --- Plains ---
    PLAINS(Biome.PLAINS, "Plains", "plains",
            Material.GRASS_BLOCK, Material.DIRT, 3, Material.COARSE_DIRT, Material.DIRT_PATH),
    SUNFLOWER_PLAINS(Biome.SUNFLOWER_PLAINS, "Sunflower plains", "sunflower plains",
            Material.GRASS_BLOCK, Material.DIRT, 3, Material.COARSE_DIRT, Material.DIRT_PATH),
    SNOWY_PLAINS(Biome.SNOWY_PLAINS, "Snowy plains", "snowy plains",
            Material.GRASS_BLOCK, Material.DIRT, 3, Material.PODZOL, Material.COARSE_DIRT),
    ICE_SPIKES(Biome.ICE_SPIKES, "Ice spikes", "ice spikes",
            Material.PACKED_ICE, Material.PACKED_ICE, 3, Material.BLUE_ICE, Material.GRAVEL),
    // --- Forest ---
    FOREST(Biome.FOREST, "Forest", "forest",
            Material.GRASS_BLOCK, Material.DIRT, 3, Material.PODZOL, Material.MOSS_BLOCK),
    FLOWER_FOREST(Biome.FLOWER_FOREST, "Flower forest", "flower forest",
            Material.GRASS_BLOCK, Material.DIRT, 3, Material.PODZOL, Material.COARSE_DIRT),
    BIRCH_FOREST(Biome.BIRCH_FOREST, "Birch forest", "birch forest",
            Material.GRASS_BLOCK, Material.DIRT, 3, Material.PODZOL, Material.COARSE_DIRT),
    OLD_GROWTH_BIRCH_FOREST(Biome.OLD_GROWTH_BIRCH_FOREST, "Old growth birch forest", "old growth birch forest",
            Material.GRASS_BLOCK, Material.DIRT, 3, Material.PODZOL, Material.MYCELIUM),
    DARK_FOREST(Biome.DARK_FOREST, "Dark forest", "dark forest",
            Material.GRASS_BLOCK, Material.DIRT, 3, Material.PODZOL, Material.MYCELIUM),
    CHERRY_GROVE(Biome.CHERRY_GROVE, "Cherry grove", "cherry grove",
            Material.GRASS_BLOCK, Material.DIRT, 3, Material.COARSE_DIRT, Material.PODZOL),
    PALE_GARDEN(Biome.PALE_GARDEN, "Pale garden", "pale garden",
            Material.PALE_MOSS_BLOCK, Material.DIRT, 3, Material.GRAVEL, Material.MOSS_BLOCK),
    DAPPLED_FOREST(Biome.FOREST, "Dappled forest", "dappled forest",
            Material.GRASS_BLOCK, Material.DIRT, 3, Material.MOSS_BLOCK, Material.PODZOL),
    // --- Taiga ---
    TAIGA(Biome.TAIGA, "Taiga", "taiga",
            Material.GRASS_BLOCK, Material.DIRT, 3, Material.PODZOL, Material.COARSE_DIRT),
    SNOWY_TAIGA(Biome.SNOWY_TAIGA, "Snowy taiga", "snowy taiga",
            Material.GRASS_BLOCK, Material.DIRT, 3, Material.PODZOL, Material.COARSE_DIRT),
    OLD_GROWTH_PINE_TAIGA(Biome.OLD_GROWTH_PINE_TAIGA, "Old growth pine taiga", "old growth pine taiga",
            Material.GRASS_BLOCK, Material.DIRT, 3, Material.PODZOL, Material.MYCELIUM),
    OLD_GROWTH_SPRUCE_TAIGA(Biome.OLD_GROWTH_SPRUCE_TAIGA, "Old growth spruce taiga", "old growth spruce taiga",
            Material.GRASS_BLOCK, Material.DIRT, 3, Material.PODZOL, Material.COARSE_DIRT),
    // --- Jungle ---
    JUNGLE(Biome.JUNGLE, "Jungle", "jungle",
            Material.GRASS_BLOCK, Material.DIRT, 3, Material.PODZOL, Material.MOSS_BLOCK),
    SPARSE_JUNGLE(Biome.SPARSE_JUNGLE, "Sparse jungle", "sparse jungle",
            Material.GRASS_BLOCK, Material.DIRT, 3, Material.COARSE_DIRT, Material.PODZOL),
    BAMBOO_JUNGLE(Biome.BAMBOO_JUNGLE, "Bamboo jungle", "bamboo jungle",
            Material.GRASS_BLOCK, Material.DIRT, 3, Material.PODZOL, Material.MOSS_BLOCK),
    // --- Savanna ---
    SAVANNA(Biome.SAVANNA, "Savanna", "savanna",
            Material.GRASS_BLOCK, Material.DIRT, 3, Material.COARSE_DIRT, Material.DIRT_PATH),
    SAVANNA_PLATEAU(Biome.SAVANNA_PLATEAU, "Savanna plateau", "savanna plateau",
            Material.GRASS_BLOCK, Material.DIRT, 3, Material.COARSE_DIRT, Material.STONE),
    WINDSWEPT_SAVANNA(Biome.WINDSWEPT_SAVANNA, "Windswept savanna", "windswept savanna",
            Material.GRASS_BLOCK, Material.STONE, 3, Material.GRAVEL, Material.COARSE_DIRT),
    // --- Badlands ---
    BADLANDS(Biome.BADLANDS, "Badlands", "badlands",
            Material.RED_SAND, Material.TERRACOTTA, 4, Material.RED_SANDSTONE, Material.ORANGE_TERRACOTTA),
    WOODED_BADLANDS(Biome.WOODED_BADLANDS, "Wooded badlands", "wooded badlands",
            Material.RED_SAND, Material.TERRACOTTA, 4, Material.COARSE_DIRT, Material.ORANGE_TERRACOTTA),
    ERODED_BADLANDS(Biome.ERODED_BADLANDS, "Eroded badlands", "eroded badlands",
            Material.TERRACOTTA, Material.TERRACOTTA, 4, Material.RED_SAND, Material.YELLOW_TERRACOTTA),
    // --- Wet / Dry ---
    SWAMP(Biome.SWAMP, "Swamp", "swamp",
            Material.GRASS_BLOCK, Material.DIRT, 3, Material.CLAY, Material.MUD),
    MANGROVE_SWAMP(Biome.MANGROVE_SWAMP, "Mangrove swamp", "mangrove swamp",
            Material.MUD, Material.MUD, 3, Material.CLAY, Material.DIRT),
    DESERT(Biome.DESERT, "Desert", "desert",
            Material.SAND, Material.SAND, 4, Material.SANDSTONE, Material.SMOOTH_SANDSTONE),
    // --- Mountain ---
    MEADOW(Biome.MEADOW, "Meadow", "meadow",
            Material.GRASS_BLOCK, Material.DIRT, 3, Material.COARSE_DIRT, Material.DIRT_PATH),
    GROVE(Biome.GROVE, "Grove", "grove",
            Material.GRASS_BLOCK, Material.DIRT, 3, Material.PODZOL, Material.GRAVEL),
    SNOWY_SLOPES(Biome.SNOWY_SLOPES, "Snowy slopes", "snowy slopes",
            Material.STONE, Material.STONE, 3, Material.GRAVEL, Material.PACKED_ICE),
    FROZEN_PEAKS(Biome.FROZEN_PEAKS, "Frozen peaks", "frozen peaks",
            Material.STONE, Material.STONE, 3, Material.PACKED_ICE, Material.GRAVEL),
    JAGGED_PEAKS(Biome.JAGGED_PEAKS, "Jagged peaks", "jagged peaks",
            Material.STONE, Material.STONE, 3, Material.GRAVEL, Material.TUFF),
    STONY_PEAKS(Biome.STONY_PEAKS, "Stony peaks", "stony peaks",
            Material.STONE, Material.STONE, 3, Material.GRAVEL, Material.TUFF),
    WINDSWEPT_HILLS(Biome.WINDSWEPT_HILLS, "Windswept hills", "windswept hills",
            Material.STONE, Material.STONE, 3, Material.GRAVEL, Material.TUFF),
    WINDSWEPT_GRAVELLY_HILLS(Biome.WINDSWEPT_GRAVELLY_HILLS, "Windswept gravelly hills", "windswept gravelly hills",
            Material.GRAVEL, Material.STONE, 3, Material.STONE, Material.TUFF),
    WINDSWEPT_FOREST(Biome.WINDSWEPT_FOREST, "Windswept forest", "windswept forest",
            Material.GRASS_BLOCK, Material.DIRT, 3, Material.GRAVEL, Material.STONE),
    // --- Custom (Forge enhanced) ---
    VOLCANIC(Biome.STONY_PEAKS, "Volcanic", "stony peaks",
            Material.BASALT, Material.BASALT, 3, Material.GRAVEL, Material.BLACKSTONE),
    SCABLAND(Biome.SAVANNA, "Scabland", "savanna",
            Material.STONE, Material.BASALT, 3, Material.BASALT, Material.COARSE_DIRT),
    // --- Shore / River ---
    RIVER(Biome.RIVER, "River", "river",
            Material.SAND, Material.GRAVEL, 3, Material.CLAY, Material.GRAVEL),
    FROZEN_RIVER(Biome.FROZEN_RIVER, "Frozen river", "frozen river",
            Material.SAND, Material.GRAVEL, 3, Material.CLAY, Material.PACKED_ICE),
    BEACH(Biome.BEACH, "Beach", "beach",
            Material.SAND, Material.SAND, 3, Material.GRAVEL, Material.SANDSTONE),
    SNOWY_BEACH(Biome.SNOWY_BEACH, "Snowy beach", "snowy beach",
            Material.SAND, Material.SAND, 3, Material.GRAVEL, Material.PACKED_ICE),
    STONY_SHORE(Biome.STONY_SHORE, "Stony shore", "stony shore",
            Material.STONE, Material.STONE, 3, Material.GRAVEL, Material.TUFF),
    MUSHROOM_FIELDS(Biome.MUSHROOM_FIELDS, "Mushroom fields", "mushroom fields",
            Material.MYCELIUM, Material.DIRT, 3, Material.PODZOL, Material.MYCELIUM),
    // --- Ocean ---
    WARM_OCEAN(Biome.WARM_OCEAN, "Warm ocean", "warm ocean",
            Material.SAND, Material.SAND, 3, Material.GRAVEL, Material.CLAY),
    LUKEWARM_OCEAN(Biome.LUKEWARM_OCEAN, "Lukewarm ocean", "lukewarm ocean",
            Material.SAND, Material.DIRT, 3, Material.GRAVEL, Material.CLAY),
    DEEP_LUKEWARM_OCEAN(Biome.DEEP_LUKEWARM_OCEAN, "Deep lukewarm ocean", "deep lukewarm ocean",
            Material.GRAVEL, Material.DIRT, 3, Material.CLAY, Material.SAND),
    OCEAN(Biome.OCEAN, "Ocean", "ocean",
            Material.SAND, Material.DIRT, 3, Material.GRAVEL, Material.CLAY),
    DEEP_OCEAN(Biome.DEEP_OCEAN, "Deep ocean", "deep ocean",
            Material.GRAVEL, Material.DIRT, 3, Material.CLAY, Material.SAND),
    COLD_OCEAN(Biome.COLD_OCEAN, "Cold ocean", "cold ocean",
            Material.SAND, Material.DIRT, 3, Material.GRAVEL, Material.CLAY),
    DEEP_COLD_OCEAN(Biome.DEEP_COLD_OCEAN, "Deep cold ocean", "deep cold ocean",
            Material.GRAVEL, Material.DIRT, 3, Material.CLAY, Material.SAND),
    FROZEN_OCEAN(Biome.FROZEN_OCEAN, "Frozen ocean", "frozen ocean",
            Material.ICE, Material.GRAVEL, 3, Material.CLAY, Material.PACKED_ICE),
    DEEP_FROZEN_OCEAN(Biome.DEEP_FROZEN_OCEAN, "Deep frozen ocean", "deep frozen ocean",
            Material.ICE, Material.DIRT, 3, Material.CLAY, Material.PACKED_ICE),
    // --- Cave (surface outcrops) ---
    LUSH_CAVES(Biome.LUSH_CAVES, "Lush caves", "lush caves",
            Material.MOSS_BLOCK, Material.DIRT, 3, Material.CLAY, Material.GRASS_BLOCK),
    DRIPSTONE_CAVES(Biome.DRIPSTONE_CAVES, "Dripstone caves", "dripstone caves",
            Material.STONE, Material.STONE, 3, Material.GRAVEL, Material.TUFF),
    DEEP_DARK(Biome.DEEP_DARK, "Deep dark", "deep dark",
            Material.STONE, Material.DEEPSLATE, 3, Material.GRAVEL, Material.TUFF),
    SULFUR_CAVES(Biome.DRIPSTONE_CAVES, "Sulfur caves", "sulfur caves",
            Material.STONE, Material.BASALT, 3, Material.GRAVEL, Material.BLACKSTONE);

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
