package com.forgeplugins.worldgen.gen;

import org.bukkit.Material;
import org.bukkit.block.Biome;

/**
 * ForgeWorldGen's ten original biomes. Each maps to the closest vanilla biome
 * so mob spawning, vanilla decorations and structures keep working on top of
 * our terrain, while the names, climate rules and surface palettes are our own.
 */
public enum ForgeBiome {

    /** Steep mountain slopes with exposed rock. */
    EMBER_HIGHLANDS(Biome.WINDSWEPT_HILLS, Material.STONE, TreeType.NONE, 0.0),
    /** Snow-capped peaks above the cloud line. */
    FROSTCAP_PEAKS(Biome.FROZEN_PEAKS, Material.SNOW_BLOCK, TreeType.PINE, 0.06),
    /** Rolling green valleys, the default lowland. */
    VERDANT_VALE(Biome.MEADOW, Material.GRASS_BLOCK, TreeType.OAK, 0.22),
    /** Dense dark forest with a thick canopy. */
    MISTWOOD(Biome.DARK_FOREST, Material.GRASS_BLOCK, TreeType.OAK, 0.55),
    /** Cold coniferous hills. */
    WHISPERING_PINES(Biome.OLD_GROWTH_PINE_TAIGA, Material.GRASS_BLOCK, TreeType.PINE, 0.45),
    /** Dry golden grassland dotted with acacia-like trees. */
    GOLDEN_SAVANNA(Biome.SAVANNA, Material.GRASS_BLOCK, TreeType.ACACIA, 0.10),
    /** Hot desert dunes with cacti. */
    SUNBAKED_DUNES(Biome.DESERT, Material.SAND, TreeType.CACTUS, 0.05),
    /** Flower-filled meadows. */
    BLOOMING_MEADOW(Biome.FLOWER_FOREST, Material.GRASS_BLOCK, TreeType.OAK, 0.12),
    /** Shallow seas and beaches. */
    TIDEWATER_SHALLOWS(Biome.OCEAN, Material.SAND, TreeType.NONE, 0.0),
    /** Basalt volcano slopes with a lava crater. */
    ASHEN_CALDERA(Biome.STONY_PEAKS, Material.BASALT, TreeType.NONE, 0.0);

    private final Biome vanilla;
    private final Material surface;
    private final TreeType trees;
    /** Base chance per eligible column to grow a tree (multiplied by config tree-density). */
    private final double treeChance;

    ForgeBiome(Biome vanilla, Material surface, TreeType trees, double treeChance) {
        this.vanilla = vanilla;
        this.surface = surface;
        this.trees = trees;
        this.treeChance = treeChance;
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
        CACTUS
    }
}
