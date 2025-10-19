package com.sk89q.worldedit.fabric.fawe;

import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;

public class FabricMapChunkUtil extends MapChunkUtil<ClientboundLevelChunkWithLightPacket> {

    public FabricMapChunkUtil() throws NoSuchFieldException {
      /*
        fieldX = ClientboundLevelChunkWithLightPacket.class.getDeclaredField("x");
        fieldZ = ClientboundLevelChunkWithLightPacket.class.getDeclaredField("z");
        fieldHeightMap = ClientboundLevelChunkPacketData.class.getDeclaredField("heightmaps");
        fieldChunkData = ClientboundLevelChunkWithLightPacket.class.getDeclaredField("chunkData");
        fieldBlockEntities = ClientboundLevelChunkPacketData.class.getDeclaredField("blockEntitiesData");
        fieldX.setAccessible(true);
        fieldZ.setAccessible(true);
        fieldHeightMap.setAccessible(true);
        fieldChunkData.setAccessible(true);
        fieldBlockEntities.setAccessible(true);*/
    }

    @Override
    public ClientboundLevelChunkWithLightPacket createPacket() {

        throw new UnsupportedOperationException();
    }

}
