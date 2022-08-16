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

package com.sk89q.worldedit.forge.internal;

import com.sk89q.worldedit.forge.ForgeAdapter;
import com.sk89q.worldedit.internal.block.BlockStateIdAccess;
import com.sk89q.worldedit.internal.wna.WorldNativeAccess;
import com.sk89q.worldedit.util.SideEffect;
import com.sk89q.worldedit.util.SideEffectSet;
import com.sk89q.worldedit.util.nbt.CompoundBinaryTag;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.Objects;

public class ForgeWorldNativeAccess implements WorldNativeAccess<ChunkAccess, BlockState, BlockPos> {

    private static final int UPDATE = 1, NOTIFY = 2;

    private final WeakReference<Level> world;
    private SideEffectSet sideEffectSet;

    public ForgeWorldNativeAccess(WeakReference<Level> world) {
        this.world = world;
    }

    private Level getWorld() {
        return Objects.requireNonNull(world.get(), "The reference to the world was lost");
    }

    @Override
    public void setCurrentSideEffectSet(SideEffectSet sideEffectSet) {
        this.sideEffectSet = sideEffectSet;
    }

    @Override
    public ChunkAccess getChunk(int x, int z) {
        return getWorld().getChunk(x, z);
    }

    @Override
    public BlockState toNative(com.sk89q.worldedit.world.block.BlockState state) {
        int stateId = BlockStateIdAccess.getBlockStateId(state);
        return BlockStateIdAccess.isValidInternalId(stateId)
                ? Block.stateById(stateId)
                : ForgeAdapter.adapt(state);
    }

    @Override
    public BlockState getBlockState(ChunkAccess chunk, BlockPos position) {
        return chunk.getBlockState(position);
    }

    @Nullable
    @Override
    public BlockState setBlockState(ChunkAccess chunk, BlockPos position, BlockState state) {
        return chunk.setBlockState(position, state, false);
    }

    @Override
    public BlockState getValidBlockForPosition(BlockState block, BlockPos position) {
        return Block.updateFromNeighbourShapes(block, getWorld(), position);
    }

    @Override
    public BlockPos getPosition(int x, int y, int z) {
        return new BlockPos(x, y, z);
    }

    @Override
    public void updateLightingForBlock(BlockPos position) {
        getWorld().getChunkSource().getLightEngine().checkBlock(position);
    }


    @Override
    public boolean updateTileEntity(BlockPos position, CompoundBinaryTag tag) {
        net.minecraft.nbt.CompoundTag nativeTag = NBTConverter.toNative(tag);
        return TileEntityUtils.setTileEntity(getWorld(), position, nativeTag);
    }

    @Override
    public void notifyBlockUpdate(ChunkAccess chunk, BlockPos position, BlockState oldState, BlockState newState) {
        getWorld().sendBlockUpdated(position, oldState, newState, UPDATE | NOTIFY);
    }

    @Override
    public boolean isChunkTicking(ChunkAccess chunk) {
        return chunk.getStatus().isOrAfter(ChunkStatus.FULL);
    }

    @Override
    public void markBlockChanged(ChunkAccess chunk, BlockPos position) {
        ((ChunkHolder) getWorld().getChunkSource().getLevel()).blockChanged(position);
    }

    @Override
    public void notifyNeighbors(BlockPos pos, BlockState oldState, BlockState newState) {
        Level world = getWorld();
        if (sideEffectSet.shouldApply(SideEffect.EVENTS)) {
            world.updateNeighborsAt(pos, oldState.getBlock());
        } else {
            // Manually update each side
            Block block = oldState.getBlock();
            world.neighborChanged(pos.west(), block, pos);
            world.neighborChanged(pos.east(), block, pos);
            world.neighborChanged(pos.below(), block, pos);
            world.neighborChanged(pos.above(), block, pos);
            world.neighborChanged(pos.north(), block, pos);
            world.neighborChanged(pos.south(), block, pos);
        }
        if (newState.hasAnalogOutputSignal()) {
            world.updateNeighbourForOutputSignal(pos, newState.getBlock());
        }
    }

    @Override
    public void updateNeighbors(BlockPos pos, BlockState oldState, BlockState newState, int RecursionLimit) {
        Level world = getWorld();
        oldState.updateIndirectNeighbourShapes(world, pos, NOTIFY);
        newState.updateNeighbourShapes(world, pos, NOTIFY);
        newState.updateIndirectNeighbourShapes(world, pos, NOTIFY);
    }

    @Override
    public void onBlockStateChange(BlockPos pos, BlockState oldState, BlockState newState) {
        getWorld().onBlockStateChange(pos, oldState, newState);
    }

    @Override
    public void flush() {

    }

}
