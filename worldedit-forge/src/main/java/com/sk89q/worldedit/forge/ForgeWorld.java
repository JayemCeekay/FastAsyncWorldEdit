/*
 * WorldEdit, a Minecraft world manipulation toolkit
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldEdit team and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.sk89q.worldedit.forge;

import com.fastasyncworldedit.core.Fawe;
import com.fastasyncworldedit.core.queue.IChunkGet;
import com.fastasyncworldedit.core.queue.implementation.packet.ChunkPacket;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.sk89q.jnbt.CompoundTag;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.blocks.BaseItem;
import com.sk89q.worldedit.blocks.BaseItemStack;
import com.sk89q.worldedit.entity.BaseEntity;
import com.sk89q.worldedit.entity.Entity;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.forge.internal.ForgeWorldNativeAccess;
import com.sk89q.worldedit.forge.internal.NBTConverter;
import com.sk89q.worldedit.forge.internal.TileEntityUtils;
import com.sk89q.worldedit.internal.Constants;
import com.sk89q.worldedit.internal.block.BlockStateIdAccess;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.util.Direction;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.util.SideEffect;
import com.sk89q.worldedit.util.SideEffectSet;
import com.sk89q.worldedit.util.TreeGenerator.TreeType;
import com.sk89q.worldedit.world.AbstractWorld;
import com.sk89q.worldedit.world.biome.BiomeType;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import com.sk89q.worldedit.world.item.ItemTypes;
import com.sk89q.worldedit.world.weather.WeatherType;
import com.sk89q.worldedit.world.weather.WeatherTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.Clearable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * An adapter to Minecraft worlds for WorldEdit.
 */
public class ForgeWorld extends AbstractWorld {

    private static final Random random = new Random();

    private static final net.minecraft.world.level.block.state.BlockState JUNGLE_LOG = Blocks.JUNGLE_LOG.defaultBlockState();
    private static final net.minecraft.world.level.block.state.BlockState JUNGLE_LEAF =
            Blocks.JUNGLE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, Boolean.TRUE);
    private static final net.minecraft.world.level.block.state.BlockState JUNGLE_SHRUB =
            Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, Boolean.TRUE);

    private final WeakReference<Level> worldRef;
    private final ForgeWorldNativeAccess nativeAccess;

    /**
     * Construct a new world.
     *
     * @param world the world
     */
    ForgeWorld(Level world) {
        checkNotNull(world);
        this.worldRef = new WeakReference<>(world);
        this.nativeAccess = new ForgeWorldNativeAccess(worldRef);
    }

    /**
     * Get the underlying handle to the world.
     *
     * @return the world
     * @throws WorldEditException thrown if a reference to the world was lost (i.e. world was unloaded)
     */
    public Level getWorldChecked() throws WorldEditException {
        Level world = worldRef.get();
        if (world != null) {
            return world;
        } else {
            throw new WorldReferenceLostException("The reference to the world was lost (i.e. the world may have been unloaded)");
        }
    }

    /**
     * Get the underlying handle to the world.
     *
     * @return the world
     * @throws RuntimeException thrown if a reference to the world was lost (i.e. world was unloaded)
     */
    public Level getWorld() {
        Level world = worldRef.get();
        if (world != null) {
            return world;
        } else {
            throw new RuntimeException("The reference to the world was lost (i.e. the world may have been unloaded)");
        }
    }

    @Override
    public String getName() {
        return getWorld().dimension().location().toString();
    }

    @Override
    public String getNameUnsafe() {
        return getWorld().dimension().location().toString();
    }

    @Override
    public String getId() {
        return String.valueOf(getWorld()
                .registryAccess()
                .registry(Registry.DIMENSION_TYPE_REGISTRY)
                .get()
                .getId(getWorld().dimensionType()));
    }

    @Override
    public void refreshChunk(final int chunkX, final int chunkZ) {

    }

    @Override
    public IChunkGet get(final int x, final int z) {
        return null;
    }

    @Override
    public void sendFakeChunk(@org.jetbrains.annotations.Nullable final Player player, final ChunkPacket packet) {

    }

    @Override
    public Path getStoragePath() {
        final Level world = getWorld();
        if (world instanceof ServerLevel) {
            return ((ServerLevel) world).getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath();
        }
        return null;
    }

    @Override
    public <B extends BlockStateHolder<B>> boolean setBlock(BlockVector3 position, B block, SideEffectSet sideEffects) throws
            WorldEditException {
        return nativeAccess.setBlock(position, block, sideEffects);
    }

    @Override
    public Set<SideEffect> applySideEffects(BlockVector3 position, BlockState previousType, SideEffectSet sideEffectSet) throws
            WorldEditException {
        nativeAccess.applySideEffects(position, previousType, sideEffectSet);
        return Sets.intersection(
                (Set<SideEffect>) Fawe.instance().getWorldEdit().getPlatformManager().getSupportedSideEffects(),
                sideEffectSet.getSideEffectsToApply()
        );
    }

    @Override
    public int getBlockLightLevel(BlockVector3 position) {
        checkNotNull(position);
        return getWorld().getLightEmission(ForgeAdapter.toBlockPos(position));
    }

    @Override
    public boolean clearContainerBlockContents(BlockVector3 position) {
        checkNotNull(position);
        BlockEntity tile = getWorld().getBlockEntity(ForgeAdapter.toBlockPos(position));
        if (tile instanceof Clearable) {
            ((Clearable) tile).clearContent();
            return true;
        }
        return false;
    }

    @Override
    public BiomeType getBiome(BlockVector3 position) {
        checkNotNull(position);
        return ForgeAdapter.adapt(getWorld().getBiome(new BlockPos(position.getBlockX(), 0, position.getBlockZ())).value());
    }

    @Override
    public boolean setTile(final int x, final int y, final int z, final CompoundTag tile) throws WorldEditException {
        return false;
    }

    @Override
    public boolean setBiome(BlockVector3 position, BiomeType biome) {
        checkNotNull(position);
        checkNotNull(biome);

        ChunkAccess chunk = getWorld().getChunk(position.getBlockX() >> 4, position.getBlockZ() >> 4, ChunkStatus.FULL, false);
        Holder<Biome> container = chunk == null ? null : chunk.getNoiseBiome(position.getBlockX(), position.getBlockY(),
                position.getBlockZ()
        );
        if (chunk == null || container == null) {
            return false;
        }
        chunk.setUnsaved(true);
        return true;
    }

    @Override
    public void flush() {

    }

    private static LoadingCache<ServerLevel, WorldEditFakePlayer> fakePlayers
            = CacheBuilder.newBuilder().weakKeys().softValues().build(CacheLoader.from(WorldEditFakePlayer::new));

    @Override
    public boolean useItem(BlockVector3 position, BaseItem item, Direction face) {
        ItemStack stack = ForgeAdapter.adapt(new BaseItemStack(item.getType(), item.getNbtData(), 1));
        ServerLevel world = (ServerLevel) getWorld();
        final WorldEditFakePlayer fakePlayer;
        try {
            fakePlayer = fakePlayers.get(world);
        } catch (ExecutionException ignored) {
            return false;
        }
        fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, stack);
        fakePlayer.absMoveTo(position.getBlockX(), position.getBlockY(), position.getBlockZ(),
                (float) face.toVector().toYaw(), (float) face.toVector().toPitch()
        );
        final BlockPos blockPos = ForgeAdapter.toBlockPos(position);
        final BlockHitResult rayTraceResult = new BlockHitResult(ForgeAdapter.toVec3(position),
                ForgeAdapter.adapt(face), blockPos, false
        );
        UseOnContext itemUseContext = new UseOnContext(
                fakePlayer,
                InteractionHand.MAIN_HAND,
                rayTraceResult
        );
        InteractionResult used = stack.useOn(itemUseContext);
        if (used != InteractionResult.SUCCESS) {
            // try activating the block
            InteractionResult resultType = getWorld().getBlockState(blockPos)
                    .use(world, fakePlayer, InteractionHand.MAIN_HAND, rayTraceResult);
            if (resultType.consumesAction()) {
                used = resultType;
            } else {
                used = stack.getItem().use(world, fakePlayer, InteractionHand.MAIN_HAND).getResult();
            }
        }
        return used == InteractionResult.SUCCESS;
    }

    @Override
    public void dropItem(Vector3 position, BaseItemStack item) {
        checkNotNull(position);
        checkNotNull(item);

        if (item.getType() == ItemTypes.AIR) {
            return;
        }

        ItemEntity entity = new ItemEntity(
                getWorld(),
                position.getX(),
                position.getY(),
                position.getZ(),
                ForgeAdapter.adapt(item)
        );
        entity.setPickUpDelay(10);
        getWorld().addFreshEntity(entity);
    }

    @Override
    public void simulateBlockMine(BlockVector3 position) {
        BlockPos pos = ForgeAdapter.toBlockPos(position);
        getWorld().destroyBlock(pos, true);
    }

    @Override
    public boolean regenerate(Region region, EditSession editSession) {
        // Don't even try to regen if it's going to fail.
        ChunkSource provider = getWorld().getChunkSource();
        if (!(provider instanceof ServerChunkCache)) {
            return false;
        }

        File saveFolder = Files.createTempDir();
        // register this just in case something goes wrong
        // normally it should be deleted at the end of this method
        saveFolder.deleteOnExit();

        try {
            ServerLevel originalWorld = (ServerLevel) getWorld();

            MinecraftServer server = originalWorld.getServer();

            net.minecraft.world.level.storage.LevelStorageSource saveHandler = new LevelStorageSource(
                    saveFolder.toPath(),
                    originalWorld.getServer().getWorldPath(LevelResource.LEVEL_DATA_FILE).getFileName(),
                    server.getFixerUpper()
            );
            try (Level freshWorld = new ServerLevel(
                    server,
                    server,
                    saveHandler.createAccess(String.valueOf(originalWorld.getFreeMapId())),
                    originalWorld.getServer().getWorldData().overworldData(),
                    originalWorld.dimension(),
                    originalWorld.dimensionTypeRegistration(),
                    new NoOpChunkStatusListener(),
                    originalWorld.getChunkSource().getGenerator(),
                    false,
                    originalWorld.getSeed(),
                    List.of(),
                    false

            )) {

                // Pre-gen all the chunks
                // We need to also pull one more chunk in every direction
                CuboidRegion expandedPreGen = new CuboidRegion(
                        region.getMinimumPoint().subtract(16, 0, 16),
                        region.getMaximumPoint().add(16, 0, 16)
                );
                for (BlockVector2 chunk : expandedPreGen.getChunks()) {
                    freshWorld.getChunk(chunk.getBlockX(), chunk.getBlockZ());
                }

                ForgeWorld from = new ForgeWorld(freshWorld);
                for (BlockVector3 vec : region) {
                    editSession.setBlock(vec, from.getFullBlock(vec));
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } catch (MaxChangedBlocksException e) {
            throw new RuntimeException(e);
        } finally {
            saveFolder.delete();
        }

        return true;
    }

    @Nullable
    private static Feature<? extends FeatureConfiguration> createTreeFeatureGenerator(TreeType type) {
        switch (type) {
            case TREE:
                TreeFeature.TREE.delegate.get();
            case REDWOOD:
                TreeFeatures.SPRUCE.value().feature().delegate.get();
                // case TALL_REDWOOD: return new TallTaigaTreeFeature(NoFeatureConfig::deserialize, true);
            case MEGA_REDWOOD:
                TreeFeatures.MEGA_SPRUCE.value().feature().delegate.get();
            case BIRCH:
                TreeFeatures.BIRCH.value().feature().delegate.get();
            case JUNGLE:
                TreeFeatures.MEGA_JUNGLE_TREE.value().feature().delegate.get();
            case SMALL_JUNGLE:
                TreeFeatures.JUNGLE_TREE_NO_VINE.value().feature().delegate.get();
            case SHORT_JUNGLE:
                TreeFeatures.JUNGLE_TREE.value().feature().delegate.get();
            case JUNGLE_BUSH:
                TreeFeatures.JUNGLE_BUSH.value().feature().delegate.get();
            case SWAMP:
                TreeFeatures.SWAMP_OAK.value().feature().delegate.get();
            case ACACIA:
                TreeFeatures.ACACIA.value().feature().delegate.get();
            case DARK_OAK:
                TreeFeatures.DARK_OAK.value().feature().delegate.get();
                //case TALL_BIRCH: return new BirchTreeFeature(NoFeatureConfig::deserialize, true, true);
            case RED_MUSHROOM:
                Feature.HUGE_RED_MUSHROOM.delegate.get();
            case BROWN_MUSHROOM:
                Feature.HUGE_BROWN_MUSHROOM.delegate.get();
            case RANDOM:
                return createTreeFeatureGenerator(TreeType.values()[ThreadLocalRandom
                        .current()
                        .nextInt(TreeType.values().length)]);
            default:
                return null;
        }
    }


    private FeatureConfiguration createFeatureConfig(TreeType type) {
        if (type == TreeType.RED_MUSHROOM) {
            return TreeFeatures.HUGE_RED_MUSHROOM.value().config();
        } else if (type == TreeType.BROWN_MUSHROOM) {
            return TreeFeatures
                    .HUGE_BROWN_MUSHROOM.value().config();
        } else {
            return new NoneFeatureConfiguration();
        }
    }

    @Override
    public boolean generateTree(TreeType type, EditSession editSession, BlockVector3 position) throws
            MaxChangedBlocksException {
        @SuppressWarnings("unchecked")
        Feature<FeatureConfiguration> generator = (Feature<FeatureConfiguration>) createTreeFeatureGenerator(type);
        return generator != null
                && generator.place(createFeatureConfig(type), ((ServerLevel) getWorld()),
                ((ServerLevel) getWorld()).getChunkSource().getGenerator(),
                random,
                ForgeAdapter.toBlockPos(position)
        );
    }


    @Override
    public void checkLoadedChunk(BlockVector3 pt) {
        getWorld().getChunk(ForgeAdapter.toBlockPos(pt));
    }

    @Override
    public void fixAfterFastMode(Iterable<BlockVector2> chunks) {
        fixLighting(chunks);
    }

    @Override
    public void fixLighting(Iterable<BlockVector2> chunks) {
        Level world = getWorld();
        for (BlockVector2 chunk : chunks) {
            world.getChunkSource().getLightEngine().retainData(new ChunkPos(chunk.getBlockX(), chunk.getBlockZ()), true);
        }
    }

    @Override
    public boolean playEffect(Vector3 position, int type, int data) {
        getWorld().globalLevelEvent(type, ForgeAdapter.toBlockPos(position.toBlockPoint()), data);
        return true;
    }

    @Override
    public WeatherType getWeather() {
        if (getWorld().isThundering()) {
            return WeatherTypes.THUNDER_STORM;
        }
        if (getWorld().isRaining()) {
            return WeatherTypes.RAIN;
        }
        return WeatherTypes.CLEAR;
    }

    @Override
    public long getRemainingWeatherDuration() {
        if (getWorld().isThundering()) {
            return (long) getWorld().thunderLevel;
        }
        if (getWorld().isRaining()) {
            return (long) getWorld().rainLevel;
        }
        return 0L;
    }

    @Override
    public void setWeather(WeatherType weatherType) {
        setWeather(weatherType, 0);
    }

    @Override
    public void setWeather(WeatherType weatherType, long duration) {
        if (weatherType == WeatherTypes.THUNDER_STORM) {
            getWorld().setThunderLevel(duration);
        } else if (weatherType == WeatherTypes.RAIN) {
            getWorld().setRainLevel(duration);
        } else if (weatherType == WeatherTypes.CLEAR) {
            getWorld().setRainLevel(0);
            getWorld().setThunderLevel(0);
        }
    }

    @Override
    public int getMinY() {
        // Note: This method exists to be re-written by mods that vary world height
        return 0;
    }

    @Override
    public int getMaxY() {
        return getWorld().getMaxBuildHeight() - 1;
    }

    @Override
    public BlockVector3 getSpawnPosition() {
        return BlockVector3.at(getWorld().getLevelData().getXSpawn(), getWorld().getLevelData().getYSpawn(),
                getWorld().getLevelData().getZSpawn()
        );
    }

    @Override
    public BlockState getBlock(BlockVector3 position) {
        net.minecraft.world.level.block.state.BlockState mcState = getWorld()
                .getChunk(position.getBlockX() >> 4, position.getBlockZ() >> 4)
                .getBlockState(ForgeAdapter.toBlockPos(position));

        BlockState matchingBlock = BlockStateIdAccess.getBlockStateById(Block.getId(mcState));
        if (matchingBlock != null) {
            return matchingBlock;
        }

        return ForgeAdapter.adapt(mcState);
    }

    @Override
    public BaseBlock getFullBlock(BlockVector3 position) {
        BlockPos pos = new BlockPos(position.getBlockX(), position.getBlockY(), position.getBlockZ());
        BlockEntity tile = getWorld().getChunk(pos).getBlockEntity(pos);

        if (tile != null) {
            return getBlock(position).toBaseBlock(NBTConverter.fromNative(TileEntityUtils.copyNbtData(tile)));
        } else {
            return getBlock(position).toBaseBlock();
        }
    }

    @Override
    public int hashCode() {
        return getWorld().hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        } else if ((o instanceof ForgeWorld)) {
            ForgeWorld other = ((ForgeWorld) o);
            Level otherWorld = other.worldRef.get();
            Level thisWorld = worldRef.get();
            return otherWorld != null && otherWorld.equals(thisWorld);
        } else if (o instanceof com.sk89q.worldedit.world.World) {
            return ((com.sk89q.worldedit.world.World) o).getName().equals(getName());
        } else {
            return false;
        }
    }

    @Override
    public List<? extends Entity> getEntities(Region region) {
        final Level world = getWorld();
        if (!(world instanceof ServerLevel)) {
            return Collections.emptyList();
        }
        return StreamSupport.stream(((ServerLevel) world).getEntities().getAll().spliterator(), false)
                .filter(e -> region.contains(ForgeAdapter.adapt(e.blockPosition())))
                .map(ForgeEntity::new).collect(Collectors.toList());
    }

    @Override
    public List<? extends Entity> getEntities() {
        final Level world = getWorld();
        if (!(world instanceof ServerLevel)) {
            return Collections.emptyList();
        }
        return StreamSupport
                .stream(((ServerLevel) world).getEntities().getAll().spliterator(), false)
                .map(ForgeEntity::new)
                .collect(Collectors.toList());
    }

    @Nullable
    @Override
    public Entity createEntity(Location location, BaseEntity entity) {
        Level world = getWorld();
        final Optional<EntityType<?>> entityType = EntityType.byString(entity.getType().getId());
        if (!entityType.isPresent()) {
            return null;
        }
        net.minecraft.world.entity.Entity createdEntity = entityType.get().create(world);
        if (createdEntity != null) {
            CompoundTag nativeTag = entity.getNbtData();
            if (nativeTag != null) {
                net.minecraft.nbt.CompoundTag tag = (net.minecraft.nbt.CompoundTag) NBTConverter.toNative(entity.getNbtData());
                for (String name : Constants.NO_COPY_ENTITY_NBT_FIELDS) {
                    tag.remove(name);
                }
                createdEntity.load(tag);
            }

            createdEntity.absMoveTo(
                    location.getX(),
                    location.getY(),
                    location.getZ(),
                    location.getYaw(),
                    location.getPitch()
            );

            world.addFreshEntity(createdEntity);
            return new ForgeEntity(createdEntity);
        } else {
            return null;
        }
    }

    /**
     * Thrown when the reference to the world is lost.
     */
    @SuppressWarnings("serial")
    private static final class WorldReferenceLostException extends WorldEditException {

        private WorldReferenceLostException(String message) {
            super(message);
        }

    }

    private static class NoOpChunkStatusListener implements ChunkProgressListener {

        @Override
        public void updateSpawnPos(final ChunkPos p_9617_) {

        }

        @Override
        public void start() {
        }

        @Override
        public void onStatusChange(ChunkPos chunkPos, @Nullable ChunkStatus chunkStatus) {
        }

        @Override
        public void stop() {
        }

    }

}
