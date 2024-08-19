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

import com.fastasyncworldedit.core.util.TaskManager;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.platform.AbstractNonPlayerActor;
import com.sk89q.worldedit.extension.platform.Locatable;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.session.SessionKey;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.util.auth.AuthorizationException;
import com.sk89q.worldedit.util.formatting.WorldEditText;
import com.sk89q.worldedit.util.formatting.text.Component;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.util.formatting.text.format.TextColor;
import com.sk89q.worldedit.util.formatting.text.serializer.gson.GsonComponentSerializer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

import static com.google.common.base.Preconditions.checkNotNull;

public class FabricBlockCommandSender extends AbstractNonPlayerActor implements Locatable {

    private static final String UUID_PREFIX = "CMD";

    private final CommandSourceStack sender;
    private final WorldEdit plugin;
    private final Location location;
    private final UUID uuid;

    public FabricBlockCommandSender(WorldEdit plugin, CommandSourceStack sender) {
        checkNotNull(plugin);
        checkNotNull(sender);

        this.plugin = plugin;
        this.sender = sender;
        this.location = new Location(FabricAdapter.adapt(sender.getLevel()), FabricAdapter.adapt(sender.getPosition()));
        this.uuid = UUID.nameUUIDFromBytes((UUID_PREFIX + sender.getTextName()).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String getName() {
        return sender.getTextName();
    }

    @Override
    @Deprecated
    public void printRaw(String msg) {
        //FAWE start - ensure executed on main thread
        TaskManager.taskManager().sync(() -> {
            for (String part : msg.split("\n")) {
                sender.sendSystemMessage(net.minecraft.network.chat.Component.literal(part));
            }
            return null;
        });
        //FAWE end
    }

    @Override
    @Deprecated
    public void print(String msg) {
        //FAWE start - ensure executed on main thread
        TaskManager.taskManager().sync(() -> {
            for (String part : msg.split("\n")) {
                print(TextComponent.of(part, TextColor.LIGHT_PURPLE));
            }
            return null;
        });
        //FAWE end
    }

    @Override
    @Deprecated
    public void printDebug(String msg) {
        //FAWE start - ensure executed on main thread
        TaskManager.taskManager().sync(() -> {
            for (String part : msg.split("\n")) {
                print(TextComponent.of(part, TextColor.GRAY));
            }
            return null;
        });
        //FAWE end
    }

    @Override
    @Deprecated
    public void printError(String msg) {
        //FAWE start - ensure executed on main thread
        TaskManager.taskManager().sync(() -> {
            for (String part : msg.split("\n")) {
                print(TextComponent.of(part, TextColor.RED));
            }
            return null;
        });
        //FAWE end
    }

    @Override
    public void print(Component component) {
        //FAWE start - ensure executed on main thread
        TaskManager.taskManager().sync(() -> {
            sender.sendSystemMessage(net.minecraft.network.chat.Component.Serializer.fromJson(GsonComponentSerializer.INSTANCE.serialize(WorldEditText.format(component, getLocale()))));
            return null;
        });
        //FAWE end
    }

    @Override
    public Locale getLocale() {
        return WorldEdit.getInstance().getConfiguration().defaultLocale;
    }

    @Override
    public Location getLocation() {
        return this.location;
    }

    @Override
    public boolean setLocation(Location location) {
        return false;
    }

    @Override
    public Extent getExtent() {
        return this.location.getExtent();
    }

    @Override
    public UUID getUniqueId() {
        return uuid;
    }

    @Override
    public String[] getGroups() {
        return new String[0];
    }

    @Override
    public void checkPermission(String permission) throws AuthorizationException {
        if (!hasPermission(permission)) {
            throw new AuthorizationException();
        }
    }

    @Override
    public boolean hasPermission(String permission) {
        return true;
    }

    //FAWE start
    @Override
    public boolean togglePermission(String permission) {
        return true;
    }
    //FAWE end

    @Override
    public void setPermission(String permission, boolean value) {
    }

    public CommandSourceStack getSender() {
        return this.sender;
    }

    @Override
    public SessionKey getSessionKey() {
        return new SessionKey() {

            private volatile boolean active = true;

            private void updateActive() {
                BlockPos block = sender.getEntity().blockPosition();
                if (!sender.getLevel().isLoaded(block)) {
                    active = false;
                    return;
                }
                Block type = sender.getLevel().getBlockState(block).getBlock();
                active = type == Blocks.COMMAND_BLOCK
                        || type == Blocks.CHAIN_COMMAND_BLOCK
                        || type == Blocks.REPEATING_COMMAND_BLOCK;
            }

            @Override
            public String getName() {
                return sender.getTextName();
            }

            @Override
            public boolean isActive() {
                if (sender.getServer().isSameThread()) {
                    // we can update eagerly
                    updateActive();
                } else {
                    // we should update it eventually
                    FabricWorldEdit.inst.getTaskManager().sync(() -> {
                        updateActive();
                        return null;
                    });
                }
                return active;
            }

            @Override
            public boolean isPersistent() {
                return true;
            }

            @Override
            public UUID getUniqueId() {
                return uuid;
            }
        };
    }

}
