package com.forgeplugins.worldgen.gen;

import org.bukkit.block.data.BlockData;
import org.jetbrains.annotations.NotNull;

/**
 * Pre-built farmland and crop BlockData templates for the wheat-field
 * feature. BlockData is created once on the main thread (in
 * {@link com.forgeplugins.worldgen.ForgeWorldGen}) and reused by every
 * chunk — chunk generation never builds BlockData itself.
 */
public record FarmKit(@NotNull BlockData farmland,
                      @NotNull BlockData wheatOld,
                      @NotNull BlockData wheatYoung,
                      @NotNull BlockData carrotOld,
                      @NotNull BlockData carrotYoung,
                      @NotNull BlockData potatoOld,
                      @NotNull BlockData potatoYoung,
                      @NotNull BlockData beetOld,
                      @NotNull BlockData beetYoung) {
}
