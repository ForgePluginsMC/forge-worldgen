package com.forgeplugins.worldgen.gen;

import org.bukkit.Material;
import org.bukkit.block.Biome;

/**
 * ForgeWorldGen's nineteen original biomes. Each maps to the closest vanilla
 * biome so mob spawning, vanilla decorations and structures keep working on
 * top of our terrain, while the names, climate rules and surface palettes are
 * our own.
 */
public enum ForgeBiome {

    /** Steep mountain slopes with exposed rock. */
    EMBER_HIGHLANDS(Biome.WINDSWEPT_HILLS, Material.STONE, TreeType.NONE, 0.0, 0.030),
    /** Snow-capped peaks above the cloud line. */
    FROSTCAP_PEAKS(Biome.FROZEN_PEAKS, Material.SNOW_BLOCK, TreeType.WINDSWEPT_PINE, 0.08, 0.020),
    /** Rolling green valleys. */
    VERDANT_VALE(Biome.MEADOW, Material.GRASS_BLOCK, TreeType.OAK, 0.20, 0.010),
    /** Dense dark forest with a thick canopy. */
    MISTWOOD(Biome.DARK_FOREST, Material.GRASS_BLOCK, TreeType.OAK, 0.55, 0.010),
    /** Cold coniferous hills. */
    WHISPERING_PINES(Biome.OLD_GROWTH_PINE_TAIGA, Material.GRASS_BLOCK, TreeType.PINE, 0.45, 0.020),
    /** Dry golden grassland dotted with acacia-like trees. */
    GOLDEN_SAVANNA(Biome.SAVANNA, Material.GRASS_BLOCK, TreeType.ACACIA, 0.10, 0.015),
    /** Hot desert dunes with cacti. */
    SUNBAKED_DUNES(Biome.DESERT, Material.SAND, TreeType.CACTUS, 0.05, 0.0),
    /** Flower-filled meadows. */
    BLOOMING_MEADOW(Biome.FLOWER_FOREST, Material.GRASS_BLOCK, TreeType.OAK, 0.12, 0.005),
    /** Shallow seas and beaches, palms at the waterline. */
    TIDEWATER_SHALLOWS(Biome.BEACH, Material.SAND, TreeType.PALM, 0.06, 0.0),
    /** Basalt volcano slopes with a lava crater. */
    ASHEN_CALDERA(Biome.STONY_PEAKS, Material.BASALT, TreeType.NONE, 0.0, 0.0),
    /** Classic rolling grassland, the mellow default. */
    ROLLING_PLAINS(Biome.PLAINS, Material.GRASS_BLOCK, TreeType.OAK, 0.10, 0.020),
    /** Elevated grassland plateau with bluff edges. */
    HIGH_PLAINS(Biome.SAVANNA_PLATEAU, Material.GRASS_BLOCK, TreeType.WINDSWEPT_PINE, 0.07, 0.030),
    /** Deep open water with occasional islands. */
    DEEP_OCEAN(Biome.DEEP_OCEAN, Material.GRAVEL, TreeType.NONE, 0.0, 0.0),
    /** Red-gray mottled badlands, dead snags, lava pools in the dips. */
    BARREN_WASTELAND(Biome.BADLANDS, Material.TERRACOTTA, TreeType.DEAD_SNAG, 0.03, 0.020),
    /** Geothermal basin: hot-spring pools and geyser cones (Yellowstone). */
    GEYSER_BASIN(Biome.MEADOW, Material.GRASS_BLOCK, TreeType.OAK, 0.08, 0.010),
    /** Sheer pale-granite valley walls, a great dome, waterfalls (Yosemite). */
    GRANITE_VALLEY(Biome.WINDSWEPT_HILLS, Material.GRASS_BLOCK, TreeType.PINE, 0.15, 0.030),
    /** Banded red/cream/ochre strata mesas (Grand Canyon). */
    PAINTED_CANYON(Biome.BADLANDS, Material.RED_SAND, TreeType.NONE, 0.0, 0.010),
    /** Rolling sand dunes with slip faces (Sahara). */
    DUNE_SEA(Biome.DESERT, Material.SAND, TreeType.CACTUS, 0.02, 0.0);

    private final Biome vanilla;
    private final Material surface;
    private final TreeType trees;
    /** Base chance per eligible column to grow a tree (multiplied by config tree-density). */
    private final double treeChance;
    /** Chance per eligible column to drop a glacial-erratic boulder. */
    private final double boulderChance;

    ForgeBiome(Biome vanilla, Material surface, TreeType trees, double treeChance,
               double boulderChance) {
        this.vanilla = vanilla;
        this.surface = surface;
        this.trees = trees;
        this.treeChance = treeChance;
        this.boulderChance = boulderChance;
    }

    public Biome vanilla() {
        return vanilla;
    }

    public Material surface() {
        return surface;
    }

    public TreeType trees() {
        return trees;
    }

    public double treeChance() {
        return treeChance;
    }

    public double boulderChance() {
        return boulderChance;
    }

    /** Tree designs used by the generator. Original low-block-count designs. */
    public enum TreeType {
        NONE,
        /** Broadleaf: short trunk with a round canopy. */
        OAK,
        /** Conifer: tall trunk with stacked leaf discs. */
        PINE,
        /** Flat-topped dryland tree. */
        ACACIA,
        /** Desert cactus column. */
        CACTUS,
        /** Tall rainforest giant with hanging leaf strands. */
        JUNGLE_GIANT,
        /** Short pine leaning with the wind, canopy swept to one side. */
        WINDSWEPT_PINE,
        /** Bare dead trunk with branch stubs. */
        DEAD_SNAG,
        /** Curving trunk with radiating fronds, grows at the waterline. */
        PALM,
        /** Rare 2x2-trunk ancient landmark tree. */
        MEGA_TREE,
    }
}
