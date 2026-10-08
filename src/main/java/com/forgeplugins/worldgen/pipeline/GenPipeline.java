package com.forgeplugins.worldgen.pipeline;

import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * The ordered stage lists. Noise stages run inside
 * {@code ChunkGenerator.generateNoise}; surface stages run inside
 * {@code generateSurface} (vanilla paints first per the API contract, our
 * surface stage repaints). v3.0 ships two stages; v3.1 reserves
 * {@code CarveStage} and continuation-safe {@code ObjectStage} slots here.
 */
public record GenPipeline(
        @NotNull List<GenStage> noiseStages,
        @NotNull List<GenStage> surfaceStages) {

    public void runNoise(@NotNull ChunkContext ctx) {
        for (GenStage stage : noiseStages) {
            stage.generate(ctx);
        }
    }

    public void runSurface(@NotNull ChunkContext ctx) {
        for (GenStage stage : surfaceStages) {
            stage.generate(ctx);
        }
    }
}
