package com.sk89q.worldedit.fabric.fawe;

import com.fastasyncworldedit.core.extent.processor.lighting.NullRelighter;
import com.fastasyncworldedit.core.extent.processor.lighting.RelightMode;
import com.fastasyncworldedit.core.extent.processor.lighting.Relighter;
import com.fastasyncworldedit.core.extent.processor.lighting.RelighterFactory;
import com.fastasyncworldedit.core.queue.IQueueChunk;
import com.fastasyncworldedit.core.queue.IQueueExtent;
import com.fastasyncworldedit.core.wrappers.WorldWrapper;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.fabric.FabricWorldEdit;
import com.sk89q.worldedit.world.World;
import org.jetbrains.annotations.NotNull;

public class FabricStarlightRelighterFactory implements RelighterFactory {

    @Override
    public @NotNull Relighter createRelighter(final RelightMode relightMode, final World world, final IQueueExtent<?> queue) {
        if (world == null) {
            return NullRelighter.INSTANCE;
        }

        return new FabricStarlightRelighter(FabricWorldEdit.server.getLevel(FabricAdapter.adapt(WorldWrapper.unwrap(world)).dimension()),
                (IQueueExtent<IQueueChunk>) queue
        );
    }
}

