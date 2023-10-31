package com.sk89q.worldedit.fabric.fawe;

import com.fastasyncworldedit.core.Fawe;
import com.fastasyncworldedit.core.FaweCache;
import com.fastasyncworldedit.core.math.BitArrayUnstretched;
import com.fastasyncworldedit.core.queue.implementation.blocks.DataArray;
import com.fastasyncworldedit.core.util.MathMan;
import com.fastasyncworldedit.core.util.ReflectionUtils;
import com.fastasyncworldedit.core.util.TaskManager;
import com.google.common.collect.Iterators;
import com.mojang.datafixers.util.Either;
import com.sk89q.worldedit.fabric.FabricWorldEdit;
import com.sk89q.worldedit.fabric.internal.FabricTransmogrifier;
import com.sk89q.worldedit.world.biome.BiomeType;
import com.sk89q.worldedit.world.biome.BiomeTypes;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockTypesCache;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMap;
import net.minecraft.core.Registry;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.BitStorage;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.ThreadingDetector;
import net.minecraft.util.ZeroBitStorage;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.GlobalPalette;
import net.minecraft.world.level.chunk.HashMapPalette;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.LinearPalette;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.SingleValuePalette;
import sun.misc.Unsafe;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.stream.Collectors;

public class FabricPlatformAdapter extends NMSAdapter {

    private static final int CHUNKSECTION_BASE;
    private static final int CHUNKSECTION_SHIFT;

    static {
        try {
            Unsafe unsafe = ReflectionUtils.getUnsafe();
            CHUNKSECTION_BASE = unsafe.arrayBaseOffset(LevelChunkSection[].class);
            int scale = unsafe.arrayIndexScale(LevelChunkSection[].class);
            if ((scale & (scale - 1)) != 0) {
                throw new Error("data type scale not a power of two");
            }
            CHUNKSECTION_SHIFT = 31 - Integer.numberOfLeadingZeros(scale);
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable rethrow) {
            rethrow.printStackTrace();
            throw new RuntimeException(rethrow);
        }
    }

    static boolean setSectionAtomic(
            LevelChunkSection[] sections,
            LevelChunkSection expected,
            LevelChunkSection value,
            int layer
    ) {
        long offset = ((long) layer << CHUNKSECTION_SHIFT) + CHUNKSECTION_BASE;
        if (layer >= 0 && layer < sections.length) {
            return ReflectionUtils.getUnsafe().compareAndSwapObject(sections, offset, expected, value);
        }
        return false;
    }

    // There is no point in having a functional semaphore for paper servers.
    private static final ThreadLocal<DelegateSemaphore> SEMAPHORE_THREAD_LOCAL =
            ThreadLocal.withInitial(() -> new DelegateSemaphore(1, null));

    static DelegateSemaphore applyLock(LevelChunkSection section) {
        try {
            synchronized (section) {
                Unsafe unsafe = ReflectionUtils.getUnsafe();
                PalettedContainer<net.minecraft.world.level.block.state.BlockState> blocks = section.getStates();
                ThreadingDetector currentThreadingDetector = blocks.threadingDetector;
                synchronized (currentThreadingDetector) {
                    Semaphore currentLock = currentThreadingDetector.lock;
                    if (currentLock instanceof DelegateSemaphore delegateSemaphore) {
                        return delegateSemaphore;
                    }
                    DelegateSemaphore newLock = new DelegateSemaphore(1, currentLock);
                    currentThreadingDetector.lock = newLock;
                    return newLock;
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static LevelChunk ensureLoaded(ServerLevel serverLevel, int chunkX, int chunkZ) {
        LevelChunk nmsChunk = serverLevel.getChunkSource().getChunk(chunkX, chunkZ, false);
        if (nmsChunk != null) {
            return nmsChunk;
        }
        if (Fawe.isMainThread()) {
            return serverLevel.getChunk(chunkX, chunkZ);
        }
        return TaskManager.taskManager().sync(() -> serverLevel.getChunk(chunkX, chunkZ));
    }

    public static ChunkHolder getPlayerChunk(ServerLevel nmsWorld, final int chunkX, final int chunkZ) {
        ChunkMap chunkMap = nmsWorld.getChunkSource().chunkMap;
        try {
            return chunkMap.getVisibleChunkIfPresent(ChunkPos.asLong(chunkX, chunkZ));
        } catch (Throwable thr) {
            throw new RuntimeException(thr);
        }
    }

    @SuppressWarnings("deprecation")
    public static void sendChunk(ServerLevel nmsWorld, int chunkX, int chunkZ, boolean lighting) {
        ChunkHolder chunkHolder = getPlayerChunk(nmsWorld, chunkX, chunkZ);
        if (chunkHolder == null) {
            return;
        }
        ChunkPos coordIntPair = new ChunkPos(chunkX, chunkZ);
        LevelChunk levelChunk;
        levelChunk = ((Optional<LevelChunk>) ((Either) chunkHolder
                .getTickingChunkFuture() // method is not present with new paper chunk system
                .getNow(ChunkHolder.UNLOADED_LEVEL_CHUNK)).left())
                .orElse(null);
        if (levelChunk == null) {
            return;
        }
        TaskManager.taskManager().task(() -> {
            ClientboundLevelChunkWithLightPacket packet;
            // deprecated on paper - deprecation suppressed
            packet = new ClientboundLevelChunkWithLightPacket(
                    levelChunk,
                    nmsWorld.getChunkSource().getLightEngine(),
                    null,
                    null,
                    true
            );
            nearbyPlayers(nmsWorld, coordIntPair).forEach(p -> p.connection.send(packet));
        });
    }

    private static List<ServerPlayer> nearbyPlayers(ServerLevel serverLevel, ChunkPos coordIntPair) {
        return serverLevel.getChunkSource().chunkMap.getPlayers(coordIntPair, false);
    }

    /*
    NMS conversion
     */
    public static LevelChunkSection newChunkSection(
            final int layer,
            final DataArray blocks,
            FabricFaweAdapter adapter,
            Registry<Biome> biomeRegistry,
            @Nullable PalettedContainer<Holder<Biome>> biomes
    ) {
        return newChunkSection(layer, null, blocks, adapter, biomeRegistry, biomes);
    }

    public static LevelChunkSection newChunkSection(
            final int layer,
            final Function<Integer, DataArray> get,
            DataArray set,
            FabricFaweAdapter adapter,
            Registry<Biome> biomeRegistry,
            @Nullable PalettedContainer<Holder<Biome>> biomes
    ) {
        if (set == null) {
            return newChunkSection(layer, biomeRegistry, biomes);
        }
        final int[] blockToPalette = FaweCache.INSTANCE.BLOCK_TO_PALETTE.get();
        final int[] paletteToBlock = FaweCache.INSTANCE.PALETTE_TO_BLOCK.get();
        final long[] blockStates = FaweCache.INSTANCE.BLOCK_STATES.get();
        final int[] blocksCopy = FaweCache.INSTANCE.SECTION_BLOCKS.get();
        try {
            int num_palette;
            if (get == null) {
                num_palette = createPalette(blockToPalette, paletteToBlock, blocksCopy, set, adapter, null);
            } else {
                num_palette = createPalette(layer, blockToPalette, paletteToBlock, blocksCopy, get, set, adapter, null);
            }

            int bitsPerEntry = MathMan.log2nlz(num_palette - 1);
            if (bitsPerEntry > 0 && bitsPerEntry < 5) {
                bitsPerEntry = 4;
            } else if (bitsPerEntry > 8) {
                bitsPerEntry = MathMan.log2nlz(Block.BLOCK_STATE_REGISTRY.size() - 1);
            }
            int bitsPerEntryNonZero = Math.max(bitsPerEntry, 1); // We do want to use zero sometimes
            final int blocksPerLong = MathMan.floorZero((double) 64 / bitsPerEntryNonZero);
            final int blockBitArrayEnd = MathMan.ceilZero((float) 4096 / blocksPerLong);

            if (num_palette == 1) {
                for (int i = 0; i < blockBitArrayEnd; i++) {
                    blockStates[i] = 0;
                }
            } else {
                final BitArrayUnstretched bitArray = new BitArrayUnstretched(bitsPerEntryNonZero, 4096, blockStates);
                bitArray.fromRaw(blocksCopy);
            }

            final long[] bits = Arrays.copyOfRange(blockStates, 0, blockBitArrayEnd);
            final BitStorage nmsBits;
            if (bitsPerEntry == 0) {
                nmsBits = new ZeroBitStorage(4096);
            } else {
                nmsBits = new SimpleBitStorage(bitsPerEntry, 4096, bits);
            }
            List<net.minecraft.world.level.block.state.BlockState> palette;
            if (bitsPerEntry < 9) {
                palette = new ArrayList<>();
                for (int i = 0; i < num_palette; i++) {
                    int ordinal = paletteToBlock[i];
                    blockToPalette[ordinal] = Integer.MAX_VALUE;
                    final BlockState state = BlockTypesCache.states[ordinal];
                    palette.add(FabricTransmogrifier.transmogToMinecraft(state));
                }
            } else {
                palette = List.of();
            }

            // Create palette with data
            @SuppressWarnings("deprecation") // constructor is deprecated on paper, but needed to keep compatibility with spigot
            final PalettedContainer<net.minecraft.world.level.block.state.BlockState> blockStatePalettedContainer =
                    new PalettedContainer<>(
                            Block.BLOCK_STATE_REGISTRY,
                            PalettedContainer.Strategy.SECTION_STATES,
                            PalettedContainer.Strategy.SECTION_STATES.getConfiguration(Block.BLOCK_STATE_REGISTRY, bitsPerEntry),
                            nmsBits,
                            palette
                    );
            if (biomes == null) {
                IdMap<Holder<Biome>> biomeHolderIdMap = biomeRegistry.asHolderIdMap();
                biomes = new PalettedContainer<>(
                        biomeHolderIdMap,
                        biomeHolderIdMap.byIdOrThrow(FabricWorldEdit.inst.getFaweAdapter()
                                .getInternalBiomeId(
                                        BiomeTypes.PLAINS)),
                        PalettedContainer.Strategy.SECTION_BIOMES
                );
            }

            return new LevelChunkSection(layer, blockStatePalettedContainer, biomes);
        } catch (final Throwable e) {
            throw e;
        } finally {
            Arrays.fill(blockToPalette, Integer.MAX_VALUE);
            Arrays.fill(paletteToBlock, Integer.MAX_VALUE);
            Arrays.fill(blockStates, 0);
            Arrays.fill(blocksCopy, 0);
        }
    }

    @SuppressWarnings("deprecation") // Only deprecated in paper
    private static LevelChunkSection newChunkSection(
            int layer,
            Registry<Biome> biomeRegistry,
            @Nullable PalettedContainer<Holder<Biome>> biomes
    ) {
        if (biomes == null) {
            return new LevelChunkSection(layer, biomeRegistry);
        }
        PalettedContainer<net.minecraft.world.level.block.state.BlockState> dataPaletteBlocks = new PalettedContainer<>(
                Block.BLOCK_STATE_REGISTRY,
                Blocks.AIR.defaultBlockState(),
                PalettedContainer.Strategy.SECTION_STATES
        );
        return new LevelChunkSection(layer, dataPaletteBlocks, biomes);
    }

    /**
     * Create a new {@link PalettedContainer<Biome>}. Should only be used if no biome container existed beforehand.
     */
    public static PalettedContainer<Holder<Biome>> getBiomePalettedContainer(
            BiomeType[] biomes,
            IdMap<Holder<Biome>> biomeRegistry
    ) {
        if (biomes == null) {
            return null;
        }
        FabricFaweAdapter adapter = FabricWorldEdit.inst.getFaweAdapter();
        // Don't stream this as typically will see 1-4 biomes; stream overhead is large for the small length
        Map<BiomeType, Holder<Biome>> palette = new HashMap<>();
        for (BiomeType biomeType : new LinkedList<>(Arrays.asList(biomes))) {
            Holder<Biome> biome;
            if (biomeType == null) {
                biome = biomeRegistry.byId(adapter.getInternalBiomeId(BiomeTypes.PLAINS));
            } else {
                biome = biomeRegistry.byId(adapter.getInternalBiomeId(biomeType));
            }
            palette.put(biomeType, biome);
        }
        int biomeCount = palette.size();
        int bitsPerEntry = MathMan.log2nlz(biomeCount - 1);
        PalettedContainer.Configuration<Biome> configuration = PalettedContainer.Strategy.SECTION_STATES.getConfiguration(
                new FakeIdMapBiome(biomeCount),
                bitsPerEntry
        );
        if (bitsPerEntry > 3) {
            bitsPerEntry = MathMan.log2nlz(biomeRegistry.size() - 1);
        }
        PalettedContainer<Holder<Biome>> biomePalettedContainer = new PalettedContainer<>(
                biomeRegistry,
                biomeRegistry.byIdOrThrow(adapter.getInternalBiomeId(BiomeTypes.PLAINS)),
                PalettedContainer.Strategy.SECTION_BIOMES
        );

        final Palette<Holder<Biome>> biomePalette;
        if (bitsPerEntry == 0) {
            biomePalette = new SingleValuePalette<>(
                    biomePalettedContainer.registry,
                    biomePalettedContainer,
                    new ArrayList<>(palette.values()) // Must be modifiable
            );
        } else if (bitsPerEntry == 4) {
            biomePalette = LinearPalette.create(
                    4,
                    biomePalettedContainer.registry,
                    biomePalettedContainer,
                    new ArrayList<>(palette.values()) // Must be modifiable
            );
        } else if (bitsPerEntry < 9) {
            biomePalette = HashMapPalette.create(
                    bitsPerEntry,
                    biomePalettedContainer.registry,
                    biomePalettedContainer,
                    new ArrayList<>(palette.values()) // Must be modifiable
            );
        } else {
            biomePalette = GlobalPalette.create(
                    bitsPerEntry,
                    biomePalettedContainer.registry,
                    biomePalettedContainer,
                    null // unused
            );
        }

        int bitsPerEntryNonZero = Math.max(bitsPerEntry, 1); // We do want to use zero sometimes
        final int blocksPerLong = MathMan.floorZero((double) 64 / bitsPerEntryNonZero);
        final int arrayLength = MathMan.ceilZero(64f / blocksPerLong);


        BitStorage bitStorage = bitsPerEntry == 0 ? new ZeroBitStorage(64) : new SimpleBitStorage(
                bitsPerEntry,
                64,
                new long[arrayLength]
        );

        PalettedContainer.Data data = new PalettedContainer.Data(configuration, bitStorage, biomePalette);
        biomePalettedContainer.data = data;
        int index = 0;
        for (int y = 0; y < 4; y++) {
            for (int z = 0; z < 4; z++) {
                for (int x = 0; x < 4; x++, index++) {
                    BiomeType biomeType = biomes[index];
                    if (biomeType == null) {
                        continue;
                    }
                    Holder<Biome> biome = biomeRegistry.byId(adapter
                            .getInternalBiomeId(biomeType));
                    if (biome == null) {
                        continue;
                    }
                    biomePalettedContainer.set(x, y, z, biome);
                }
            }
        }
        return biomePalettedContainer;
    }

    public static void clearCounts(final LevelChunkSection section) throws IllegalAccessException {
        section.tickingFluidCount = (short) 0;
        section.tickingBlockCount = (short) 0;
    }

    public static BiomeType adapt(Holder<Biome> biome, LevelAccessor levelAccessor) {
        final Registry<Biome> biomeRegistry = levelAccessor.registryAccess().ownedRegistryOrThrow(Registry.BIOME_REGISTRY);
        if (biomeRegistry.getKey(biome.value()) == null) {
            return biomeRegistry.asHolderIdMap().getId(biome) == -1 ? BiomeTypes.OCEAN
                    : null;
        }
        return BiomeTypes.get(biome.unwrapKey().orElseThrow().location().toString());
    }

    @SuppressWarnings("unchecked")
    static void removeBeacon(BlockEntity beacon, LevelChunk levelChunk) {
        try {
            // Do the method ourselves to avoid trying to reflect generic method parameters
            // similar to removeGameEventListener
            if (levelChunk.loaded || levelChunk.getLevel().isClientSide()) {
                BlockEntity blockEntity = levelChunk.blockEntities.remove(beacon.getBlockPos());
                if (blockEntity != null) {
                    if (!levelChunk.getLevel().isClientSide) {
                        levelChunk.removeGameEventListener(
                                beacon,
                                levelChunk.getLevel().getServer().getLevel(levelChunk.getLevel().dimension())
                        );
                    }
                    beacon.remove = true;
                }
            }
            levelChunk.removeBlockEntityTicker(beacon.getBlockPos());
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }

    static List<Entity> getEntities(LevelChunk chunk) {
        return Arrays.stream(Iterators.toArray(chunk
                .getLevel()
                .getServer()
                .getLevel(chunk.getLevel().dimension()).entityManager
                .getEntityGetter()
                .getAll()
                .iterator(), Entity.class)).collect(
                Collectors.toList());
        /*ExceptionCollector<RuntimeException> collector = new ExceptionCollector<>();
        try {
            //noinspection unchecked
            return ((PersistentEntitySectionManager<Entity>) (chunk.getLevel().getServer().getLevel(chunk.getLevel().dimension()).entityManager.
        } catch (IllegalAccessException e) {
            collector.add(new RuntimeException("Failed to lookup entities [PAPER=false]", e));
        }
        collector.throwIfPresent();*/
        //return List.of();
    }

    record FakeIdMapBlock(int size) implements IdMap<net.minecraft.world.level.block.state.BlockState> {

        @Override
        public int getId(final net.minecraft.world.level.block.state.BlockState entry) {
            return 0;
        }

        @Nullable
        @Override
        public net.minecraft.world.level.block.state.BlockState byId(final int index) {
            return null;
        }

        @Nonnull
        @Override
        public Iterator<net.minecraft.world.level.block.state.BlockState> iterator() {
            return Collections.emptyIterator();
        }

    }

    record FakeIdMapBiome(int size) implements IdMap<Biome> {

        @Override
        public int getId(final Biome entry) {
            return 0;
        }

        @Nullable
        @Override
        public Biome byId(final int index) {
            return null;
        }

        @Nonnull
        @Override
        public Iterator<Biome> iterator() {
            return Collections.emptyIterator();
        }

    }

}
