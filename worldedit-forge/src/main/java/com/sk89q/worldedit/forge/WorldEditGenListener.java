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

import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;

import javax.annotation.Nullable;

// For now, this does nothing, but might be useful later for regen progress communication.
class WorldEditGenListener implements ChunkProgressListener {


    @Override
    public void updateSpawnPos(final ChunkPos p_9617_) {

    }

    @Override
    public void onStatusChange(final ChunkPos p_9618_, @org.jetbrains.annotations.Nullable final ChunkStatus p_9619_) {

    }

    @Override
    public void start() {

    }

    @Override
    public void stop() {
    }

}
