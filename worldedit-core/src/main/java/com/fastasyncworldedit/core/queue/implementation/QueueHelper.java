package com.fastasyncworldedit.core.queue.implementation;

import com.fastasyncworldedit.core.queue.IChunkCache;
import com.fastasyncworldedit.core.queue.IChunkSet;
import com.fastasyncworldedit.core.queue.implementation.blocks.DataArraySetBlocks;
import org.jetbrains.annotations.NotNull;

public class QueueHelper {

    @NotNull
    protected static IChunkCache<IChunkSet> getIChunkSetIChunkCache() {
        IChunkCache<IChunkSet> set;
        set = (x, z) -> DataArraySetBlocks.newInstance();
        return set;
    }

}

