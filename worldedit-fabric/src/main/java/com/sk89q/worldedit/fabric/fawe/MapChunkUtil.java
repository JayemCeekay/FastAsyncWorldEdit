package com.sk89q.worldedit.fabric.fawe;

import com.fastasyncworldedit.core.queue.implementation.packet.ChunkPacket;
import com.sk89q.jnbt.CompoundTag;
import com.sk89q.worldedit.fabric.internal.NBTConverter;
import com.sk89q.worldedit.math.BlockVector3;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Map;

public abstract class MapChunkUtil<T> {

    protected Field fieldX;
    protected Field fieldZ;
    protected Field fieldHeightMap;
    // protected Field fieldBitMask;
    protected Field fieldChunkData;
    protected Field fieldBlockEntities;
    //  protected Field fieldFull;

    protected abstract T createPacket();

    public T create(ChunkPacket packet) {
        try {
            T nmsPacket;
            nmsPacket = createPacket();
            fieldX.setInt(nmsPacket, packet.getChunkX());
            fieldZ.setInt(nmsPacket, packet.getChunkZ());

            if (fieldHeightMap != null) {
                Object heightMap = NBTConverter.toNative(packet.getHeightMap());
                fieldHeightMap.set(nmsPacket, heightMap);
            }

            fieldChunkData.set(nmsPacket, packet.getSectionBytes());

            Map<BlockVector3, CompoundTag> tiles = packet.getChunk().getTiles();
            ArrayList<Object> nmsTiles = new ArrayList<>(tiles.size());
            for (Map.Entry<BlockVector3, CompoundTag> entry : tiles.entrySet()) {
                Object nmsTag = NBTConverter.toNative(entry.getValue());
                nmsTiles.add(nmsTag);
            }
            fieldBlockEntities.set(nmsPacket, nmsTiles);
            return nmsPacket;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
