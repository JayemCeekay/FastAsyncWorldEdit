package com.sk89q.worldedit.fabric.fawe;

import com.fastasyncworldedit.core.FaweCache;
import com.fastasyncworldedit.core.queue.IBatchProcessor;
import com.fastasyncworldedit.core.queue.IChunkGet;
import com.fastasyncworldedit.core.queue.implementation.packet.ChunkPacket;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.sk89q.jnbt.AdventureNBTConverter;
import com.sk89q.jnbt.Tag;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.fabric.FabricWorldEdit;
import com.sk89q.worldedit.fabric.internal.FabricTransmogrifier;
import com.sk89q.worldedit.internal.util.LogManagerCompat;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.registry.state.BooleanProperty;
import com.sk89q.worldedit.registry.state.DirectionalProperty;
import com.sk89q.worldedit.registry.state.EnumProperty;
import com.sk89q.worldedit.registry.state.IntegerProperty;
import com.sk89q.worldedit.registry.state.Property;
import com.sk89q.worldedit.util.Direction;
import com.sk89q.worldedit.util.SideEffect;
import com.sk89q.worldedit.util.SideEffectSet;
import com.sk89q.worldedit.util.nbt.BinaryTag;
import com.sk89q.worldedit.util.nbt.ByteArrayBinaryTag;
import com.sk89q.worldedit.util.nbt.ByteBinaryTag;
import com.sk89q.worldedit.util.nbt.CompoundBinaryTag;
import com.sk89q.worldedit.util.nbt.DoubleBinaryTag;
import com.sk89q.worldedit.util.nbt.EndBinaryTag;
import com.sk89q.worldedit.util.nbt.FloatBinaryTag;
import com.sk89q.worldedit.util.nbt.IntArrayBinaryTag;
import com.sk89q.worldedit.util.nbt.IntBinaryTag;
import com.sk89q.worldedit.util.nbt.ListBinaryTag;
import com.sk89q.worldedit.util.nbt.LongArrayBinaryTag;
import com.sk89q.worldedit.util.nbt.LongBinaryTag;
import com.sk89q.worldedit.util.nbt.ShortBinaryTag;
import com.sk89q.worldedit.util.nbt.StringBinaryTag;
import com.sk89q.worldedit.world.biome.BiomeType;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import com.sk89q.worldedit.world.block.BlockTypesCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.chunk.LevelChunk;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class FabricFaweAdapter extends FabricAdapter {

    private static final Logger LOGGER = LogManagerCompat.getLogger();

    // ------------------------------------------------------------------------
    // Code that may break between versions of Minecraft
    // ------------------------------------------------------------------------
    private final FabricMapChunkUtil mapUtil = new FabricMapChunkUtil();
    private int[] ibdToStateOrdinal = null;
    private int[] ordinalToIbdID = null;
    private boolean initialised = false;
    private Map<String, List<Property<?>>> allBlockProperties = null;

    public FabricFaweAdapter() throws NoSuchFieldException, NoSuchMethodException {
        super();
    }

    @Nullable
    private static String getEntityId(Entity entity) {
        ResourceLocation resourceLocation = net.minecraft.world.entity.EntityType.getKey(entity.getType());
        return resourceLocation == null ? null : resourceLocation.toString();
    }

    private static void readEntityIntoTag(Entity entity, net.minecraft.nbt.CompoundTag compoundTag) {
        entity.save(compoundTag);
    }

    public boolean isInitialised() {
        return initialised;
    }

    public synchronized boolean init() {
        if (ibdToStateOrdinal != null && ibdToStateOrdinal[1] != 0) {
            return false;
        }
        ibdToStateOrdinal = new int[BlockTypesCache.states.length]; // size
        ordinalToIbdID = new int[ibdToStateOrdinal.length]; // size
        for (int i = 0; i < ibdToStateOrdinal.length; i++) {
            BlockState blockState = BlockTypesCache.states[i];
            int id = Block.BLOCK_STATE_REGISTRY.getId(FabricTransmogrifier.transmogToMinecraft(blockState));
            int ordinal = blockState.getOrdinal();
            ibdToStateOrdinal[id] = ordinal;
            ordinalToIbdID[ordinal] = id;
        }


        Map<String, List<Property<?>>> properties = new HashMap<>();
        try {
            for (Field field : BlockStateProperties.class.getDeclaredFields()) {
                Object obj = field.get(null);
                if (!(obj instanceof net.minecraft.world.level.block.state.properties.Property<?> state)) {
                    continue;
                }
                Property<?> property;
                if (state instanceof net.minecraft.world.level.block.state.properties.BooleanProperty) {
                    property = new BooleanProperty(
                            state.getName(),
                            (List<Boolean>) ImmutableList.copyOf(state.getPossibleValues())
                    );
                } else if (state instanceof DirectionProperty) {
                    property = new DirectionalProperty(
                            state.getName(),
                            state
                                    .getPossibleValues()
                                    .stream()
                                    .map(e -> Direction.valueOf(((StringRepresentable) e).getSerializedName().toUpperCase()))
                                    .collect(Collectors.toList())
                    );
                } else if (state instanceof net.minecraft.world.level.block.state.properties.EnumProperty) {
                    property = new EnumProperty(
                            state.getName(),
                            state
                                    .getPossibleValues()
                                    .stream()
                                    .map(e -> ((StringRepresentable) e).getSerializedName())
                                    .collect(Collectors.toList())
                    );
                } else if (state instanceof net.minecraft.world.level.block.state.properties.IntegerProperty) {
                    property = new IntegerProperty(
                            state.getName(),
                            (List<Integer>) ImmutableList.copyOf(state.getPossibleValues())
                    );
                } else {
                    throw new IllegalArgumentException("FastAsyncWorldEdit needs an update to support " + state
                            .getClass()
                            .getSimpleName());
                }
                properties.compute(property.getName().toLowerCase(Locale.ROOT), (k, v) -> {
                    if (v == null) {
                        v = new ArrayList<>(Collections.singletonList(property));
                    } else {
                        v.add(property);
                    }
                    return v;
                });
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } finally {
            allBlockProperties = ImmutableMap.copyOf(properties);
        }
        initialised = true;
        return true;
    }

    public Set<SideEffect> getSupportedSideEffects() {
        return SideEffectSet.defaults().getSideEffectsToApply();
    }

    public int adaptToInt(net.minecraft.world.level.block.state.BlockState blockState) {
        int id = Block.BLOCK_STATE_REGISTRY.getId(blockState);
        if (initialised) {
            return ibdToStateOrdinal[id];
        }
        synchronized (this) {
            if (initialised) {
                return ibdToStateOrdinal[id];
            }
            try {
                init();
                return ibdToStateOrdinal[id];
            } catch (ArrayIndexOutOfBoundsException e1) {
                LOGGER.error("Attempted to convert {} with ID {} to int. ibdToStateOrdinal length: {}. Defaulting to air!",
                        blockState.getBlock(), Block.BLOCK_STATE_REGISTRY.getId(blockState), ibdToStateOrdinal.length, e1
                );
                return BlockTypesCache.ReservedIDs.AIR;
            }
        }
    }

    public int ibdIDToOrdinal(int id) {
        if (initialised) {
            return ibdToStateOrdinal[id];
        }
        synchronized (this) {
            if (initialised) {
                return ibdToStateOrdinal[id];
            }
            init();
            return ibdToStateOrdinal[id];
        }
    }

    public int[] getIbdToStateOrdinal() {
        if (initialised) {
            return ibdToStateOrdinal;
        }
        synchronized (this) {
            if (initialised) {
                return ibdToStateOrdinal;
            }
            init();
            return ibdToStateOrdinal;
        }
    }

    public int ordinalToIbdID(int ordinal) {
        if (initialised) {
            return ordinalToIbdID[ordinal];
        }
        synchronized (this) {
            if (initialised) {
                return ordinalToIbdID[ordinal];
            }
            init();
            return ordinalToIbdID[ordinal];
        }
    }

    public int[] getOrdinalToIbdID() {
        if (initialised) {
            return ordinalToIbdID;
        }
        synchronized (this) {
            if (initialised) {
                return ordinalToIbdID;
            }
            init();
            return ordinalToIbdID;
        }
    }

    public <B extends BlockStateHolder<B>> net.minecraft.world.level.block.state.BlockState adapt(B state) {
        return FabricAdapter.adapt(state.toImmutableState());
    }

    public void sendFakeChunk(ServerLevel world, Player player, ChunkPacket chunkPacket) {
        ServerLevel nmsWorld = world;
        ChunkHolder map = FabricPlatformAdapter.getPlayerChunk(nmsWorld,
                chunkPacket.getChunkX(), chunkPacket.getChunkZ()
        );
        if (map != null && map.wasAccessibleSinceLastSave()) {
            // PlayerChunk.d players = map.players;
            Stream<ServerPlayer> stream = /*players.a(new ChunkCoordIntPair(packet.getChunkX(), packet.getChunkZ()), flag)
             */ Stream.empty();

            ServerPlayer checkPlayer = player == null ? null : (ServerPlayer) player;
            stream.filter(entityPlayer -> checkPlayer == null || entityPlayer == checkPlayer)
                    .forEach(entityPlayer -> {
                        synchronized (chunkPacket) {
                            ClientboundLevelChunkWithLightPacket nmsPacket = (ClientboundLevelChunkWithLightPacket) chunkPacket.getNativePacket();
                            if (nmsPacket == null) {
                                nmsPacket = mapUtil.create(chunkPacket);
                                chunkPacket.setNativePacket(nmsPacket);
                            }
                            try {
                                FaweCache.INSTANCE.CHUNK_FLAG.get().set(true);
                                entityPlayer.connection.send(nmsPacket);
                            } finally {
                                FaweCache.INSTANCE.CHUNK_FLAG.get().set(false);
                            }
                        }
                    });
        }
    }



    @Deprecated
    public Tag toNative(net.minecraft.nbt.Tag foreign) {
        return AdventureNBTConverter.fromAdventure(toNativeBinary(foreign));
    }

    /**
     * Converts from a non-native NMS NBT structure to a native WorldEdit NBT
     * structure.
     *
     * @param foreign non-native NMS NBT structure
     * @return native WorldEdit NBT structure
     */
    //FAWE start - BinaryTag
    public BinaryTag toNativeBinary(net.minecraft.nbt.Tag foreign) {
        if (foreign == null) {
            return null;
        }
        if (foreign instanceof net.minecraft.nbt.CompoundTag) {
            Map<String, BinaryTag> values = new HashMap<>();
            Set<String> foreignKeys = ((net.minecraft.nbt.CompoundTag) foreign).getAllKeys();

            for (String str : foreignKeys) {
                net.minecraft.nbt.Tag base = ((net.minecraft.nbt.CompoundTag) foreign).get(str);
                values.put(str, toNativeBinary(base));
            }
            return CompoundBinaryTag.from(values);
        } else if (foreign instanceof net.minecraft.nbt.ByteTag) {
            return ByteBinaryTag.of(((net.minecraft.nbt.ByteTag) foreign).getAsByte());
        } else if (foreign instanceof net.minecraft.nbt.ByteArrayTag) {
            return ByteArrayBinaryTag.of(((net.minecraft.nbt.ByteArrayTag) foreign).getAsByteArray());
        } else if (foreign instanceof net.minecraft.nbt.DoubleTag) {
            return DoubleBinaryTag.of(((net.minecraft.nbt.DoubleTag) foreign).getAsDouble());
        } else if (foreign instanceof net.minecraft.nbt.FloatTag) {
            return FloatBinaryTag.of(((net.minecraft.nbt.FloatTag) foreign).getAsFloat());
        } else if (foreign instanceof net.minecraft.nbt.IntTag) {
            return IntBinaryTag.of(((net.minecraft.nbt.IntTag) foreign).getAsInt());
        } else if (foreign instanceof net.minecraft.nbt.IntArrayTag) {
            return IntArrayBinaryTag.of(((net.minecraft.nbt.IntArrayTag) foreign).getAsIntArray());
        } else if (foreign instanceof net.minecraft.nbt.LongArrayTag) {
            return LongArrayBinaryTag.of(((net.minecraft.nbt.LongArrayTag) foreign).getAsLongArray());
        } else if (foreign instanceof net.minecraft.nbt.ListTag) {
            try {
                return toNativeList((net.minecraft.nbt.ListTag) foreign);
            } catch (Throwable e) {
                LOGGER.log(Level.WARN, "Failed to convert net.minecraft.nbt.ListTag", e);
                return ListBinaryTag.empty();
            }
        } else if (foreign instanceof net.minecraft.nbt.LongTag) {
            return LongBinaryTag.of(((net.minecraft.nbt.LongTag) foreign).getAsLong());
        } else if (foreign instanceof net.minecraft.nbt.ShortTag) {
            return ShortBinaryTag.of(((net.minecraft.nbt.ShortTag) foreign).getAsShort());
        } else if (foreign instanceof net.minecraft.nbt.StringTag) {
            return StringBinaryTag.of(foreign.getAsString());
        } else if (foreign instanceof net.minecraft.nbt.EndTag) {
            return EndBinaryTag.get();
        } else {
            throw new IllegalArgumentException("Don't know how to make native " + foreign.getClass().getCanonicalName());
        }
    }

    @Deprecated
    public net.minecraft.nbt.Tag fromNative(Tag foreign) {
        if (foreign == null) {
            return null;
        }
        return fromNativeBinary(foreign.asBinaryTag());
    }

    /**
     * Converts a WorldEdit-native NBT structure to a NMS structure.
     *
     * @param foreign structure to convert
     * @return non-native structure
     */
    public net.minecraft.nbt.Tag fromNativeBinary(BinaryTag foreign) {
        if (foreign == null) {
            return null;
        }
        if (foreign instanceof CompoundBinaryTag) {
            net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
            for (String key : ((CompoundBinaryTag) foreign).keySet()) {
                tag.put(key, fromNativeBinary(((CompoundBinaryTag) foreign).get(key)));
            }
            return tag;
        } else if (foreign instanceof ByteBinaryTag) {
            return net.minecraft.nbt.ByteTag.valueOf(((ByteBinaryTag) foreign).value());
        } else if (foreign instanceof ByteArrayBinaryTag) {
            return new net.minecraft.nbt.ByteArrayTag(((ByteArrayBinaryTag) foreign).value());
        } else if (foreign instanceof DoubleBinaryTag) {
            return net.minecraft.nbt.DoubleTag.valueOf(((DoubleBinaryTag) foreign).value());
        } else if (foreign instanceof FloatBinaryTag) {
            return net.minecraft.nbt.FloatTag.valueOf(((FloatBinaryTag) foreign).value());
        } else if (foreign instanceof IntBinaryTag) {
            return net.minecraft.nbt.IntTag.valueOf(((IntBinaryTag) foreign).value());
        } else if (foreign instanceof IntArrayBinaryTag) {
            return new net.minecraft.nbt.IntArrayTag(((IntArrayBinaryTag) foreign).value());
        } else if (foreign instanceof LongArrayBinaryTag) {
            return new net.minecraft.nbt.LongArrayTag(((LongArrayBinaryTag) foreign).value());
        } else if (foreign instanceof ListBinaryTag) {
            net.minecraft.nbt.ListTag tag = new net.minecraft.nbt.ListTag();
            ListBinaryTag foreignList = (ListBinaryTag) foreign;
            for (BinaryTag t : foreignList) {
                tag.add(fromNativeBinary(t));
            }
            return tag;
        } else if (foreign instanceof LongBinaryTag) {
            return net.minecraft.nbt.LongTag.valueOf(((LongBinaryTag) foreign).value());
        } else if (foreign instanceof ShortBinaryTag) {
            return net.minecraft.nbt.ShortTag.valueOf(((ShortBinaryTag) foreign).value());
        } else if (foreign instanceof StringBinaryTag) {
            return net.minecraft.nbt.StringTag.valueOf(((StringBinaryTag) foreign).value());
        } else if (foreign instanceof EndBinaryTag) {
            return net.minecraft.nbt.EndTag.INSTANCE;
        } else {
            throw new IllegalArgumentException("Don't know how to make NMS " + foreign.getClass().getCanonicalName());
        }
    }

    /**
     * Convert a foreign NBT list tag into a native WorldEdit one.
     *
     * @param foreign the foreign tag
     * @return the converted tag
     * @throws SecurityException        on error
     * @throws IllegalArgumentException on error
     */
    private ListBinaryTag toNativeList(net.minecraft.nbt.ListTag foreign) throws SecurityException, IllegalArgumentException {
        ListBinaryTag.Builder values = ListBinaryTag.builder();

        for (net.minecraft.nbt.Tag tag : foreign) {
            values.add(toNativeBinary(tag));
        }

        return values.build();
    }
/*
    public BlockState getBlock(BlockVector3 position) {
        CraftWorld craftWorld = ((CraftWorld) location.getWorld());
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        final ServerLevel handle = craftWorld.getHandle();
        LevelChunk chunk = handle.getChunk(x >> 4, z >> 4);
        final BlockPos blockPos = new BlockPos(x, y, z);
        final net.minecraft.world.level.block.state.BlockState blockData = chunk.getBlockState(blockPos);
        BlockState state = adapt(blockData);
        if (state == null) {;
            state = FabricAdapter.adapt(handle.getBlockState(blockPos));
        }
        return state;
    }

    public BaseBlock getFullBlock(final BlockVector3 position) {
        BlockPos pos = new BlockPos(position.getBlockX(), position.getBlockY(), position.getBlockZ());

        BlockState state = getBlock(position);

        // Read the NBT data
        BlockEntity te = ((LevelChunk) getWorld().getChunk(pos)).getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK);

        if (te != null) {
            net.minecraft.nbt.CompoundTag tag = te.saveWithId();
            //FAWE start - BinaryTag
            return state.toBaseBlock((CompoundBinaryTag) adapter.toNativeBinary(tag));
            //FAWE end
        }

        return state.toBaseBlock();
    }*/

    public IChunkGet get(ServerLevel world, int chunkX, int chunkZ) {
        return new FabricGetBlocks(world, chunkX, chunkZ);
    }

    public int getInternalBiomeId(BiomeType biomeType) {
        final Registry<Biome> registry = FabricWorldEdit.server
                .registryAccess()
                .ownedRegistryOrThrow(Registry.BIOME_REGISTRY);
        ResourceLocation resourceLocation = ResourceLocation.tryParse(biomeType.getId());
        Biome biome = registry.get(resourceLocation);
        return registry.getId(biome);
    }

    public Map<String, List<Property<?>>> getAllProperties() {
        if (initialised) {
            return allBlockProperties;
        }
        synchronized (this) {
            if (initialised) {
                return allBlockProperties;
            }
            init();
            return allBlockProperties;
        }
    }

    public IBatchProcessor getTickingPostProcessor() {
        return new FabricPostProcessor();
    }


}
