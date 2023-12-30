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

package com.sk89q.worldedit.fabric.mixin;

import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.fabric.FabricWorldEdit;
import com.sk89q.worldedit.fabric.internal.ExtendedPlayerEntity;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.util.Location;
import net.minecraft.network.protocol.game.ServerboundClientInformationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayer implements ExtendedPlayerEntity {

    @Shadow
    public ServerGamePacketListenerImpl connection;
    private String language = "en_us";

    @Inject(method = "updateOptions", at = @At(value = "HEAD"))
    public void updateOptions(
            ServerboundClientInformationPacket clientSettingsC2SPacket,
            CallbackInfo callbackInfo
    ) {
        this.language = clientSettingsC2SPacket.language();
    }

    @Override
    public String getLanguage() {
        return language;
    }

/*
    @Inject(method = "changeDimension", at = @At(value = "HEAD"))
    public void changeDimension(ServerLevel serverLevel, CallbackInfoReturnable<Entity> cir) {
        FabricWorldEdit.inst.wrapPlayer(this.connection.getPlayer()).setLocation(new Location(FabricAdapter.adapt(serverLevel)));
    }
*/
}
