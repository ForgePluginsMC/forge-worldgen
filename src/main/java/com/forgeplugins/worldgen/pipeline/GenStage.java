package com.forgeplugins.worldgen.pipeline;

import org.jetbrains.annotations.NotNull;

/**
 * One stage of chunk generation (the Iris {@code EngineStage} idea, original
 * code): a small, single-purpose step that reads the shared
 * {@link ChunkContext} and writes into the chunk. Stages run in pipeline
 * order; each is independently understandable and testable.
 */
public interface GenStage {

    /** Human-readable stage name, used in log lines. */
    @NotNull String name();

    /** Runs this stage for one chunk. Must be thread-safe. */
    void generate(@NotNull ChunkContext ctx);
}
