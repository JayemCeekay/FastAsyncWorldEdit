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

package com.sk89q.worldedit.fabric;

import com.google.common.base.Suppliers;
import com.sk89q.jnbt.CompoundTag;
import com.sk89q.worldedit.fabric.fawe.FabricLazyCompoundTag;
import com.sk89q.worldedit.world.registry.BlockMaterial;
import com.sk89q.worldedit.world.registry.PassthroughBlockMaterial;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;

import javax.annotation.Nullable;

/**
 * Fabric block material that pulls as much info as possible from the Minecraft
 * Material, and passes the rest to another implementation, typically the
 * bundled block info.
 */
public class FabricBlockMaterial extends PassthroughBlockMaterial {
    private final Block block;
    private final BlockState blockState;
    private final int opacity;
    private final CompoundTag tile;

    public FabricBlockMaterial(Block block, BlockState blockState, @Nullable BlockMaterial secondary) {
        super(secondary);
        this.block = block;
        this.blockState = blockState;
        opacity = blockState.getLightBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        BlockEntity tileEntity = !(blockState instanceof EntityBlock) ? null : ((EntityBlock) blockState).newBlockEntity(
                BlockPos.ZERO,
                blockState
        );
        tile = tileEntity == null
                ? null
                : new FabricLazyCompoundTag(Suppliers.memoize(tileEntity::saveWithId));
    }

    @Override
    public boolean isAir() {
        return blockState.isAir() || super.isAir();
    }

    @Override
    public boolean isOpaque() {
        return blockState.canOcclude();
    }

    @Override
    public boolean isLiquid() {
        return blockState.liquid();
    }

    @Override
    public boolean isSolid() {
        return blockState.isSolid();
    }

    @Override
    public float getHardness() {
        return block.defaultDestroyTime();
    }

    @Override
    public float getResistance() {
        return block.getExplosionResistance();
    }

    @Override
    public float getSlipperiness() {
        return block.getFriction();
    }

    @Override
    public int getLightValue() {
        return blockState.getLightEmission();
    }

    @Override
    public int getLightOpacity() {
        return opacity;
    }

    @Override
    public boolean isFragileWhenPushed() {
        return blockState.getPistonPushReaction() == PushReaction.DESTROY;
    }

    @Override
    public boolean isUnpushable() {
        return blockState.getPistonPushReaction() == PushReaction.BLOCK;
    }

    @Override
    public boolean isTicksRandomly() {
        return block.isRandomlyTicking(blockState);
    }

    @Override
    public boolean isMovementBlocker() {
        return blockState.blocksMotion();
    }

    @Override
    public boolean isBurnable() {
        return blockState.ignitedByLava();
    }

    @Override
    public boolean isToolRequired() {
        return false;
    }

    @Override
    public boolean isReplacedDuringPlacement() {
        return blockState.canBeReplaced();
    }

    @Override
    public boolean isTranslucent() {
        return blockState.canOcclude();
    }


    @Override
    public boolean hasContainer() {
        return block instanceof EntityBlock;
    }

    @Override
    public boolean isTile() {
        return blockState instanceof EntityBlock;
    }

    @Override
    public CompoundTag getDefaultTile() {
        return tile;
    }

    @Override
    public int getMapColor() {
        // rgb field
        return block.defaultMapColor().col;
    }

}
