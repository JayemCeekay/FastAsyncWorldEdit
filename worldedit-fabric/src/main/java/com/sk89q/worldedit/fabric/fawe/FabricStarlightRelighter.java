package com.sk89q.worldedit.fabric.fawe;

import ca.spottedleaf.starlight.common.light.StarLightInterface;
import com.fastasyncworldedit.core.configuration.Settings;
import com.fastasyncworldedit.core.extent.processor.lighting.Relighter;
import com.fastasyncworldedit.core.queue.IQueueChunk;
import com.fastasyncworldedit.core.queue.IQueueExtent;
import com.fastasyncworldedit.core.util.MathMan;
import com.fastasyncworldedit.core.util.TaskManager;
import com.mojang.datafixers.util.Either;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.fabric.FabricNMSRelighter;
import com.sk89q.worldedit.fabric.FabricWorldEdit;
import com.sk89q.worldedit.internal.util.LogManagerCompat;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import static net.minecraft.server.level.ChunkMap.MAX_VIEW_DISTANCE;

public class FabricStarlightRelighter implements Relighter {

    private static final Logger LOGGER = LogManagerCompat.getLogger();
    private static final int CHUNKS_PER_BATCH = 1024; // 32 * 32
    private static final int CHUNKS_PER_BATCH_SQRT_LOG2 = 5; // for shifting

    private static final TicketType<Unit> FAWE_TICKET = TicketType.create("fawe_ticket", (a, b) -> 0);
    private static final int LIGHT_LEVEL = MAX_VIEW_DISTANCE + ChunkStatus.getDistance(ChunkStatus.LIGHT);

    private final ServerLevel serverLevel;
    private final ReentrantLock lock = new ReentrantLock();
    private final Long2ObjectLinkedOpenHashMap<LongSet> regions = new Long2ObjectLinkedOpenHashMap<>();
    private final ReentrantLock areaLock = new ReentrantLock();
    private final FabricNMSRelighter delegate;

    private StarLightInterface starLightInterface;

    @SuppressWarnings("rawtypes")
    public FabricStarlightRelighter(ServerLevel serverLevel, IQueueExtent<IQueueChunk> queue) {
        this.serverLevel = serverLevel;
        this.delegate = new FabricNMSRelighter(queue);
        this.starLightInterface = new StarLightInterface(this.serverLevel.getChunkSource(), true, true,
                serverLevel.getChunkSource().getLightEngine()
        );
    }

    @Override
    public boolean addChunk(int cx, int cz, byte[] skipReason, int bitmask) {
        areaLock.lock();
        try {
            long key = MathMan.pairInt(cx >> CHUNKS_PER_BATCH_SQRT_LOG2, cz >> CHUNKS_PER_BATCH_SQRT_LOG2);
            // TODO probably submit here already if chunks.size == CHUNKS_PER_BATCH?
            LongSet chunks = this.regions.computeIfAbsent(key, k -> new LongArraySet(CHUNKS_PER_BATCH >> 2));
            chunks.add(ChunkPos.asLong(cx, cz));
        } finally {
            areaLock.unlock();
        }
        return true;
    }

    @Override
    public void addLightUpdate(int x, int y, int z) {
        delegate.addLightUpdate(x, y, z);
    }

    /*
     * This method is called "recursively", iterating and removing elements
     * from the regions linked map. This way, chunks are loaded in batches to avoid
     * OOMEs.
     */
    @Override
    public void fixLightingSafe(boolean sky) {
        this.areaLock.lock();
        try {
            if (regions.isEmpty()) {
                return;
            }
            LongSet first = regions.removeFirst();
            try {
                fixLighting(first, () -> fixLightingSafe(true));
            } catch (ExecutionException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        } finally {
            this.areaLock.unlock();
        }
    }

    private static CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> lightChunk(
            ChunkStatus status,
            ThreadedLevelLightEngine lightingProvider,
            ChunkAccess chunk
    ) {
        boolean flag = ChunkStatus.isLighted(status, chunk);

        if (!chunk.getStatus().isOrAfter(status)) {
            ((ProtoChunk) chunk).setStatus(status);
        }

        return lightingProvider.lightChunk(chunk, flag).thenApply(Either::left);
    }

    /*
     * Processes a set of chunks and runs an action afterwards.
     * The action is run async, the chunks are partly processed on the main thread
     * (as required by the server).
     */
    private void fixLighting(LongSet chunks, Runnable andThen) throws ExecutionException, InterruptedException {
        // convert from long keys to ChunkPos
        Set<ChunkPos> coords = new HashSet<>();
        LongIterator iterator = chunks.iterator();
        while (iterator.hasNext()) {
            coords.add(new ChunkPos(iterator.nextLong()));
        }
        TaskManager.taskManager().async(() -> {
            // trigger chunk load and apply ticket on main thread
            List<CompletableFuture<?>> futures = new ArrayList<>();
            for (ChunkPos pos : coords) {
                try {
                    serverLevel.getChunkSource().getLightEngine().lightChunk(serverLevel.getChunk(pos.x, pos.z, ChunkStatus.FULL,
                                    true),
                            false).get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }

                /*
                        serverLevel.getChunkSource().addRegionTicket(
                                FAWE_TICKET,
                                pos,
                                LIGHT_LEVEL,
                                Unit.INSTANCE
                        );*/
            }

            serverLevel.getChunkSource().getLightEngine().tryScheduleUpdate();
             postProcessChunks(coords);
            TaskManager.taskManager().async(andThen);

            // collect futures and trigger relight once all chunks are loaded
           // CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenAccept(v ->
/*
                    invokeRelight(
                            coords,
                            c -> {
                            }, // no callback for single chunks required
                            i -> {
                                if (i != coords.size()) {
                                    LOGGER.warn("Processed {} chunks instead of {}", i, coords.size());
                                }
                                // post process chunks on main thread
                                System.out.println("Post processing chunks");
                                TaskManager.taskManager().task(() -> postProcessChunks(coords));
                                // call callback on our own threads
                                System.out.println("Calling callback");
                                TaskManager.taskManager().async(andThen);
                            }
                    );*/
            //);
       });
    }

    protected long relightCounter;
    public static final TicketType<Long> CHUNK_RELIGHT = TicketType.create("light_update", Long::compareTo);

    private void invokeRelight(
            Set<ChunkPos> coords,
            Consumer<ChunkPos> chunkCallback,
            IntConsumer processCallback
    ) {
        try {

            java.util.Set<ChunkPos> chunks = new java.util.LinkedHashSet<>(coords);
            // add tickets
            java.util.Map<ChunkPos, Long> ticketIds = new java.util.HashMap<>();
            for (java.util.Iterator<ChunkPos> iterator = chunks.iterator(); iterator.hasNext(); ) {
                final ChunkPos chunkPos = iterator.next();

                final ChunkAccess chunk = (ChunkAccess) serverLevel.getChunkSource().getChunkForLighting(chunkPos.x, chunkPos.z);
                if (chunk == null || !chunk.isLightCorrect() || !chunk.getStatus().isOrAfter(ChunkStatus.LIGHT)) {
                    // cannot relight this chunk
                    iterator.remove();
                    continue;
                }
                //serverLevel.getChunkSource().getLightEngine().lightChunk(chunk, false);
                final Long id = this.relightCounter++;

                serverLevel.getChunkSource().addRegionTicket(CHUNK_RELIGHT, chunkPos,
                        LIGHT_LEVEL, id
                );
                ticketIds.put(chunkPos, id);
            }
            serverLevel.getChunkSource().getLightEngine().taskMailbox.tell(() -> {
                        this.starLightInterface.relightChunks(coords, (ChunkPos chunkPos) -> {
                            chunkCallback.accept(chunkPos);
                            ((java.util.concurrent.Executor) serverLevel.getChunkSource().mainThreadProcessor).execute(() -> {
                                serverLevel.getChunkSource().chunkMap
                                        .getUpdatingChunkIfPresent(chunkPos.toLong())
                                        .broadcast(new net.minecraft.network.protocol.game.ClientboundLightUpdatePacket(
                                                chunkPos,
                                                serverLevel.getChunkSource().getLightEngine(),
                                                null,
                                                null,
                                                true
                                        ), false);
                                serverLevel.getChunkSource().removeRegionTicket(CHUNK_RELIGHT, chunkPos,
                                        LIGHT_LEVEL, ticketIds.get(chunkPos)
                                );
                            });
                        }, processCallback);
                    });
            //});
            serverLevel.getChunkSource().getLightEngine().tryScheduleUpdate();
        } catch (Throwable throwable) {
            LOGGER.error("Error occurred on relighting", throwable);
        }
    }

    /*
     * Allow the server to unload the chunks again.
     * Also, if chunk packets are sent delayed, we need to do that here
     */
    private void postProcessChunks(Set<ChunkPos> coords) {
        boolean delay = Settings.settings().LIGHTING.DELAY_PACKET_SENDING;
        for (ChunkPos pos : coords) {
            int x = pos.x;
            int z = pos.z;
            if (delay) { // we still need to send the block changes of that chunk
                FabricPlatformAdapter.sendChunk(serverLevel, x, z, false);

            }
            serverLevel.getChunkSource().removeRegionTicket(FAWE_TICKET, pos, LIGHT_LEVEL, Unit.INSTANCE);
        }
    }

    @Override
    public void clear() {

    }

    @Override
    public void removeLighting() {
        this.delegate.removeLighting();
    }

    @Override
    public void fixBlockLighting() {
        fixLightingSafe(true);
    }

    @Override
    public void fixSkyLighting() {
        fixLightingSafe(true);
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public ReentrantLock getLock() {
        return this.lock;
    }

    @Override
    public boolean isFinished() {
        return false;
    }

    @Override
    public void close() throws Exception {
        fixLightingSafe(true);
    }

}
