package com.sk89q.worldedit.fabric.fawe;

import com.fastasyncworldedit.core.FAWEPlatformAdapterImpl;
import com.fastasyncworldedit.core.queue.IChunkGet;
import com.fastasyncworldedit.core.queue.implementation.blocks.DataArray;
import com.fastasyncworldedit.core.util.MathMan;
import com.sk89q.worldedit.world.block.BlockTypesCache;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Function;

public class NMSAdapter implements FAWEPlatformAdapterImpl {

    private static final Logger LOGGER = LogManager.getLogger();

    public static int createPalette(
            int[] blockToPalette,
            int[] paletteToBlock,
            int[] blocksCopy,
            DataArray set,
            FabricFaweAdapter adapter,
            short[] nonEmptyBlockCount
    ) {
        short nonAir = 4096;
        int num_palette = 0;
        for (int i = 0; i < 4096; i++) {
            int ordinal = set.getAt(i);
            switch (ordinal) {
                case BlockTypesCache.ReservedIDs.__RESERVED__ -> {
                    ordinal = BlockTypesCache.ReservedIDs.AIR;
                    nonAir--;
                }
                case BlockTypesCache.ReservedIDs.AIR, BlockTypesCache.ReservedIDs.CAVE_AIR, BlockTypesCache.ReservedIDs.VOID_AIR -> nonAir--;
            }
            int palette = blockToPalette[ordinal];
            if (palette == Integer.MAX_VALUE) {
                blockToPalette[ordinal] = num_palette;
                paletteToBlock[num_palette] = ordinal;
                num_palette++;
            }
        }
        int bitsPerEntry = MathMan.log2nlz(num_palette - 1);
        // If bits per entry is over 8, the game uses the global palette.
        if (bitsPerEntry > 8) {
            // Cannot System#array copy char[] -> int[]
            for (int i = 0; i < adapter.getIbdToStateOrdinal().length; i++) {
                paletteToBlock[i] = adapter.getIbdToStateOrdinal()[i];
            }
            System.arraycopy(adapter.getOrdinalToIbdID(), 0, blockToPalette, 0, adapter.getOrdinalToIbdID().length);
        }
        for (int i = 0; i < 4096; i++) {
            int ordinal = set.getAt(i);
            if (ordinal == BlockTypesCache.ReservedIDs.__RESERVED__) {
                ordinal = BlockTypesCache.ReservedIDs.AIR;
            }
            int palette = blockToPalette[ordinal];
            blocksCopy[i] = palette;
        }

        if (nonEmptyBlockCount != null) {
            nonEmptyBlockCount[0] = nonAir;
        }
        return num_palette;
    }

    public static int createPalette(
            int layer,
            int[] blockToPalette,
            int[] paletteToBlock,
            int[] blocksCopy,
            Function<Integer, DataArray> get,
            DataArray set,
            FabricFaweAdapter adapter,
            short[] nonEmptyBlockCount
    ) {
        short nonAir = 4096;
        int num_palette = 0;
        DataArray getArr = null;
        for (int i = 0; i < 4096; i++) {
            int ordinal = set.getAt(i);
            switch (ordinal) {
                case BlockTypesCache.ReservedIDs.__RESERVED__ -> {
                    if (getArr == null) {
                        getArr = get.apply(layer);
                    }
                    // write to set array as this should be a copied array, and will be important when the changes are written
                    // to the GET chunk cached by FAWE
                    set.setAt(i, switch (ordinal = getArr.getAt(i)) {
                        case BlockTypesCache.ReservedIDs.__RESERVED__ -> {
                            nonAir--;
                            yield (ordinal = BlockTypesCache.ReservedIDs.AIR);
                        }
                        case BlockTypesCache.ReservedIDs.AIR, BlockTypesCache.ReservedIDs.CAVE_AIR,
                                BlockTypesCache.ReservedIDs.VOID_AIR -> {
                            nonAir--;
                            yield ordinal;
                        }
                        default -> ordinal;
                    });
                }
                case BlockTypesCache.ReservedIDs.AIR, BlockTypesCache.ReservedIDs.CAVE_AIR, BlockTypesCache.ReservedIDs.VOID_AIR -> nonAir--;
            }
            int palette = blockToPalette[ordinal];
            if (palette == Integer.MAX_VALUE) {
                blockToPalette[ordinal] = num_palette;
                paletteToBlock[num_palette] = ordinal;
                num_palette++;
            }
        }
        int bitsPerEntry = MathMan.log2nlz(num_palette - 1);
        // If bits per entry is over 8, the game uses the global palette.
        if (bitsPerEntry > 8) {
            // Cannot System#array copy char[] -> int[];
            for (int i = 0; i < adapter.getIbdToStateOrdinal().length; i++) {
                paletteToBlock[i] = adapter.getIbdToStateOrdinal()[i];
            }
            System.arraycopy(adapter.getOrdinalToIbdID(), 0, blockToPalette, 0, adapter.getOrdinalToIbdID().length);
        }
        for (int i = 0; i < 4096; i++) {
            int ordinal = set.getAt(i);
            if (ordinal == BlockTypesCache.ReservedIDs.__RESERVED__) {
                ordinal = BlockTypesCache.ReservedIDs.AIR;
            }
            int palette = blockToPalette[ordinal];
            blocksCopy[i] = palette;
        }

        if (nonEmptyBlockCount != null) {
            nonEmptyBlockCount[0] = nonAir;
        }
        return num_palette;
    }

    @Override
    public void sendChunk(IChunkGet chunk, int mask, boolean lighting) {
        if (!(chunk instanceof FabricGetBlocks)) {
            throw new IllegalArgumentException("(IChunkGet) chunk not of type FabricGetBlocks");
        }
        LOGGER.info("sending chunk");
        ((FabricGetBlocks) chunk).send(mask, lighting);
    }

}
