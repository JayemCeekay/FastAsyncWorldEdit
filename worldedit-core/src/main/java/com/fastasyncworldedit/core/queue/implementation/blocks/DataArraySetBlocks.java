package com.fastasyncworldedit.core.queue.implementation.blocks;

import com.fastasyncworldedit.core.FaweCache;
import com.fastasyncworldedit.core.configuration.Settings;
import com.fastasyncworldedit.core.extent.processor.heightmap.HeightMapType;
import com.fastasyncworldedit.core.math.BlockVector3ChunkMap;
import com.fastasyncworldedit.core.queue.IChunkSet;
import com.fastasyncworldedit.core.queue.Pool;
import com.sk89q.jnbt.CompoundTag;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.biome.BiomeType;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import com.sk89q.worldedit.world.block.BlockTypesCache;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

public class DataArraySetBlocks extends DataArrayBlocks implements IChunkSet {

    private static final Pool<DataArraySetBlocks> POOL = FaweCache.INSTANCE.registerPool(
            DataArraySetBlocks.class,
            DataArraySetBlocks::new,
            Settings.settings().QUEUE.POOL
    );

    public static DataArraySetBlocks newInstance() {
        return POOL.poll();
    }

    public BiomeType[][] biomes;
    public char[][] light;
    public char[][] skyLight;
    public BlockVector3ChunkMap<CompoundTag> tiles;
    public HashSet<CompoundTag> entities;
    public HashSet<UUID> entityRemoves;
    public Map<HeightMapType, int[]> heightMaps;
    private boolean fastMode = false;
    private int bitMask = -1;

    private DataArraySetBlocks() {
        // Expand as we go
        super(0, 15);
    }

    @Override
    public synchronized void recycle() {
        reset();
        POOL.offer(this);
    }

    @Override
    public BiomeType[][] getBiomes() {
        return biomes;
    }

    @Override
    public BiomeType getBiomeType(int x, int y, int z) {
        return getBiomeType(x, y, z, biomes, minSectionPosition, maxSectionPosition);
    }

    @Override
    public Map<BlockVector3, CompoundTag> getTiles() {
        return tiles == null ? Collections.emptyMap() : tiles;
    }

    @Override
    public CompoundTag getTile(int x, int y, int z) {
        return tiles == null ? null : tiles.get(x, y, z);
    }

    @Override
    public Set<CompoundTag> getEntities() {
        return entities == null ? Collections.emptySet() : entities;
    }

    @Override
    public Set<UUID> getEntityRemoves() {
        return entityRemoves == null ? Collections.emptySet() : entityRemoves;
    }

    @Override
    public Map<HeightMapType, int[]> getHeightMaps() {
        return heightMaps == null ? new EnumMap<>(HeightMapType.class) : heightMaps;
    }

    @Override
    public boolean setBiome(int x, int y, int z, BiomeType biome) {
        updateSectionIndexRange(y >> 4);
        int layer = (y >> 4) - minSectionPosition;
        if (biomes == null) {
            biomes = new BiomeType[sectionCount][];
            biomes[layer] = new BiomeType[64];
        } else if (biomes[layer] == null) {
            biomes[layer] = new BiomeType[64];
        }
        biomes[layer][(y & 12) << 2 | (z & 12) | (x & 12) >> 2] = biome;
        return true;
    }

    @Override
    public <T extends BlockStateHolder<T>> boolean setBlock(int x, int y, int z, T holder) {
        updateSectionIndexRange(y >> 4);
        set(x, y, z, holder.getOrdinal());
        holder.applyTileEntity(this, x, y, z);
        return true;
    }

    @Override
    public void setBlocks(int layer, final DataArray data) {
        updateSectionIndexRange(layer);
        layer -= minSectionPosition;
        this.sections[layer] = data == null ? EMPTY : FULL;
        this.blocks[layer] = data;
    }

    @Override
    public <T extends BlockStateHolder<T>> boolean setBlock(BlockVector3 position, T block)
            throws WorldEditException {
        return setBlock(position.getX(), position.getY(), position.getZ(), block);
    }

    @Override
    public boolean setTile(int x, int y, int z, CompoundTag tile) {
        if (tiles == null) {
            tiles = new BlockVector3ChunkMap<>();
        }
        updateSectionIndexRange(y >> 4);
        tiles.put(x, y, z, tile);
        return true;
    }

    @Override
    public void setBlockLight(int x, int y, int z, int value) {
        updateSectionIndexRange(y >> 4);
        if (light == null) {
            light = new char[sectionCount][];
        }
        final int layer = (y >> 4) - minSectionPosition;
        if (light[layer] == null) {
            char[] c = new char[4096];
            Arrays.fill(c, (char) 16);
            light[layer] = c;
        }
        final int index = (y & 15) << 8 | (z & 15) << 4 | (x & 15);
        light[layer][index] = (char) value;
    }

    @Override
    public void setSkyLight(int x, int y, int z, int value) {
        updateSectionIndexRange(y >> 4);
        if (skyLight == null) {
            skyLight = new char[sectionCount][];
        }
        final int layer = (y >> 4) - minSectionPosition;
        if (skyLight[layer] == null) {
            char[] c = new char[4096];
            Arrays.fill(c, (char) 16);
            skyLight[layer] = c;
        }
        final int index = (y & 15) << 8 | (z & 15) << 4 | (x & 15);
        skyLight[layer][index] = (char) value;
    }

    @Override
    public void setHeightMap(HeightMapType type, int[] heightMap) {
        if (heightMaps == null) {
            heightMaps = new EnumMap<>(HeightMapType.class);
        }
        heightMaps.put(type, heightMap);
    }

    @Override
    public void setLightLayer(int layer, char[] toSet) {
        updateSectionIndexRange(layer);
        if (light == null) {
            light = new char[sectionCount][];
        }
        layer -= minSectionPosition;
        light[layer] = toSet;
    }

    @Override
    public void setSkyLightLayer(int layer, char[] toSet) {
        updateSectionIndexRange(layer);
        if (skyLight == null) {
            skyLight = new char[sectionCount][];
        }
        layer -= minSectionPosition;
        skyLight[layer] = toSet;
    }

    @Override
    public char[][] getLight() {
        return light;
    }

    @Override
    public char[][] getSkyLight() {
        return skyLight;
    }

    @Override
    public void removeSectionLighting(int layer, boolean sky) {
        updateSectionIndexRange(layer);
        layer -= minSectionPosition;
        if (light == null) {
            light = new char[sectionCount][];
        }
        if (light[layer] == null) {
            light[layer] = new char[4096];
        }
        Arrays.fill(light[layer], (char) 0);
        if (sky) {
            if (skyLight == null) {
                skyLight = new char[sectionCount][];
            }
            if (skyLight[layer] == null) {
                skyLight[layer] = new char[4096];
            }
            Arrays.fill(skyLight[layer], (char) 0);
        }
    }

    @Override
    public void setFullBright(int layer) {
        updateSectionIndexRange(layer);
        layer -= minSectionPosition;
        if (light == null) {
            light = new char[sectionCount][];
        }
        if (light[layer] == null) {
            light[layer] = new char[4096];
        }
        if (skyLight == null) {
            skyLight = new char[sectionCount][];
        }
        if (skyLight[layer] == null) {
            skyLight[layer] = new char[4096];
        }
        Arrays.fill(light[layer], (char) 15);
        Arrays.fill(skyLight[layer], (char) 15);
    }

    @Override
    public boolean setBiome(BlockVector3 position, BiomeType biome) {
        return setBiome(position.getX(), position.getY(), position.getZ(), biome);
    }

    @Override
    public void setEntity(CompoundTag tag) {
        if (entities == null) {
            entities = new HashSet<>();
        }
        entities.add(tag);
    }

    @Override
    public void removeEntity(UUID uuid) {
        if (entityRemoves == null) {
            entityRemoves = new HashSet<>();
        }
        entityRemoves.add(uuid);
    }

    @Override
    public void setFastMode(boolean fastMode) {
        this.fastMode = fastMode;
    }

    @Override
    public boolean isFastMode() {
        return fastMode;
    }

    @Override
    public void setBitMask(int bitMask) {
        this.bitMask = bitMask;
    }

    @Override
    public int getBitMask() {
        return bitMask;
    }

    @Override
    public boolean isEmpty() {
        if (biomes != null
                || light != null
                || skyLight != null
                || (entities != null && !entities.isEmpty())
                || (tiles != null && !tiles.isEmpty())
                || (entityRemoves != null && !entityRemoves.isEmpty())
                || (heightMaps != null && !heightMaps.isEmpty())) {
            return false;
        }/*
        for (int i =  minSectionPosition; i <= maxSectionPosition; i++) {
            if (hasSection(i)) {
                return false;
            }
        }
        return true;*/
        //noinspection SimplifyStreamApiCallChains - this is faster than using #noneMatch
        return !IntStream.range(minSectionPosition, maxSectionPosition + 1).anyMatch(this::hasSection);
    }

    @Override
    public IChunkSet reset() {
        biomes = null;
        tiles = null;
        entities = null;
        entityRemoves = null;
        light = null;
        skyLight = null;
        heightMaps = null;
        super.reset();
        return null;
    }

    @Override
    public boolean hasBiomes(int layer) {
        layer -= minSectionPosition;
        if (layer < 0 || layer >= sections.length) {
            return false;
        }
        return biomes != null && biomes[layer] != null;
    }

    @Override
    public ThreadUnsafeIntBlocks createCopy() {
        DataArray[] blocksCopy = new DataArray[sectionCount];
        for (int i = 0; i < sectionCount; i++) {
            if (blocks[i] != null) {
                blocksCopy[i] = DataArray.createCopy(blocks[i]);
            }
        }
        BiomeType[][] biomesCopy;
        if (biomes == null) {
            biomesCopy = null;
        } else {
            biomesCopy = new BiomeType[sectionCount][];
            for (int i = 0; i < sectionCount; i++) {
                if (biomes[i] != null) {
                    biomesCopy[i] = new BiomeType[biomes[i].length];
                    System.arraycopy(biomes[i], 0, biomesCopy[i], 0, biomes[i].length);
                }
            }
        }
        char[][] lightCopy = createLightCopy(light, sectionCount);
        char[][] skyLightCopy = createLightCopy(skyLight, sectionCount);
        return new ThreadUnsafeIntBlocks(
                blocksCopy,
                minSectionPosition,
                maxSectionPosition,
                biomesCopy,
                sectionCount,
                lightCopy,
                skyLightCopy,
                tiles != null ? new BlockVector3ChunkMap<>(tiles) : null,
                entities != null ? new HashSet<>(entities) : null,
                entityRemoves != null ? new HashSet<>(entityRemoves) : null,
                heightMaps != null ? new EnumMap<>(heightMaps) : null,
                defaultOrdinal(),
                fastMode,
                bitMask
        );
    }

    static char[][] createLightCopy(char[][] lightArr, int sectionCount) {
        if (lightArr == null) {
            return null;
        } else {
            char[][] lightCopy = new char[sectionCount][];
            for (int i = 0; i < sectionCount; i++) {
                if (lightArr[i] != null) {
                    lightCopy[i] = new char[lightArr[i].length];
                    System.arraycopy(lightArr[i], 0, lightCopy[i], 0, lightArr[i].length);
                }
            }
            return lightCopy;
        }
    }

    @Override
    public DataArray load(final int layer) {
        updateSectionIndexRange(layer);
        return super.load(layer);
    }

    @Override
    protected char defaultOrdinal() {
        return BlockTypesCache.ReservedIDs.__RESERVED__;
    }

    // Checks and updates the various section arrays against the new layer index
    private void updateSectionIndexRange(int layer) {
        if (layer < minSectionPosition) {
            int diff = minSectionPosition - layer;
            sectionCount += diff;
            minSectionPosition = layer;
            resizeSectionsArrays(diff, false); // prepend new layer(s)
        } else if(layer > maxSectionPosition){
            int diff = layer - maxSectionPosition;
            sectionCount += diff;
            maxSectionPosition = layer;
            resizeSectionsArrays(diff, true); // append new layer(s)
        }
    }

    private void resizeSectionsArrays(int diff, boolean appendNew) {
        DataArray[] tmpBlocks = new DataArray[sectionCount];
        Section[] tmpSections = new Section[sectionCount];
        Object[] tmpSectionLocks = new Object[sectionCount];
        int destPos = appendNew ? 0 : diff;
        System.arraycopy(blocks, 0, tmpBlocks, destPos, blocks.length);
        System.arraycopy(sections, 0, tmpSections, destPos, sections.length);
        System.arraycopy(sectionLocks, 0, tmpSectionLocks, destPos, sections.length);
        int toFillFrom = appendNew ? sectionCount - diff : 0;
        int toFillTo = appendNew ? sectionCount : diff;
        for (int i = toFillFrom; i < toFillTo; i++) {
            tmpSections[i] = EMPTY;
            tmpSectionLocks[i] = new Object();
        }
        blocks = tmpBlocks;
        sections = tmpSections;
        sectionLocks = tmpSectionLocks;
        if (biomes != null) {
            BiomeType[][] tmpBiomes = new BiomeType[sectionCount][64];
            System.arraycopy(biomes, 0, tmpBiomes, destPos, biomes.length);
            biomes = tmpBiomes;
        }
        if (light != null) {
            char[][] tmplight = new char[sectionCount][];
            System.arraycopy(light, 0, tmplight, destPos, light.length);
            light = tmplight;
        }
        if (skyLight != null) {
            char[][] tmplight = new char[sectionCount][];
            System.arraycopy(skyLight, 0, tmplight, destPos, skyLight.length);
            skyLight = tmplight;
        }
    }

}