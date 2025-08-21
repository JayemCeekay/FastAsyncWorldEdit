package com.fastasyncworldedit.core.internal.simd;

import com.fastasyncworldedit.core.queue.IBlocks;
import com.fastasyncworldedit.core.queue.implementation.blocks.DataArray;
import com.sk89q.worldedit.world.block.BlockTypesCache;
import jdk.incubator.vector.ShortVector;
import jdk.incubator.vector.VectorSpecies;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class VectorFacade {
    private final IBlocks blocks;
    private int layer;
    private int index;
    private char[] data;

    VectorFacade(final IBlocks blocks) {
        this.blocks = blocks;
    }

    public ShortVector get(VectorSpecies<Short> species) {
        if (this.data == null) {
            load();
        }

        return ShortVector.fromCharArray(species, this.data, this.index);
    }

    public ShortVector getOrZero(VectorSpecies<Short> species) {
        if (this.data == null) {
            return ShortVector.zero(species);
        }
        return ShortVector.fromCharArray(species, this.data, this.index);
    }

    public void setOrIgnore(ShortVector vector) {
        if (this.data == null) {
            if (vector.eq((short) BlockTypesCache.ReservedIDs.__RESERVED__).allTrue()) {
                return;
            }
            load();
        }
        vector.intoCharArray(this.data, this.index);
    }

    private void load() {
        DataArray temp = this.blocks.load(this.layer);
        //load temp into a temp char array
        char[] tempData = new char[DataArray.CHUNK_SECTION_SIZE];
        for(int i = 0; i < DataArray.CHUNK_SECTION_SIZE; i++) {
            tempData[i] = (char) temp.getAt(i);
        }

        this.data = tempData;
    }

    public void setLayer(int layer) {
        this.layer = layer;
        this.data = null;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public void setData(DataArray data) {
        DataArray temp = this.blocks.load(this.layer);
        //load temp into a temp char array
        char[] tempData = new char[DataArray.CHUNK_SECTION_SIZE];
        for(int i = 0; i < DataArray.CHUNK_SECTION_SIZE; i++) {
            tempData[i] = (char) temp.getAt(i);
        }

        this.data = tempData;
    }

}
