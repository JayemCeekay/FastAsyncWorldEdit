package com.sk89q.worldedit.forge.internal;

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

import com.sk89q.worldedit.forge.ForgeAdapter;
import com.sk89q.worldedit.registry.state.Property;
import com.sk89q.worldedit.util.Direction;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockType;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

/**
 * Raw, un-cached transformations.
 */
public class ForgeTransmogrifier {



    public static Map<Property<?>, Object> transmogToWorldEditProperties(BlockType block, Map<net.minecraft.world.level.block.state.properties.Property<?>, Comparable<?>> mcProps) {
        Map<Property<?>, Object> props = new TreeMap<>(Comparator.comparing(Property::getName));
        for (Map.Entry<net.minecraft.world.level.block.state.properties.Property<?>, Comparable<?>> prop : mcProps.entrySet()) {
            Object value = prop.getValue();
            if (prop.getKey() instanceof DirectionProperty) {
                value = ForgeAdapter.adaptEnumFacing((net.minecraft.core.Direction) value);
            } else if (prop.getKey() instanceof net.minecraft.world.level.block.state.properties.EnumProperty) {
                value = ((StringRepresentable) value).getSerializedName();
            }
            props.put(block.getProperty(prop.getKey().getName()), value);
        }
        return props;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static net.minecraft.world.level.block.state.BlockState transmogToMinecraftProperties(StateDefinition<Block, net.minecraft.world.level.block.state.BlockState> stateContainer, net.minecraft.world.level.block.state.BlockState newState, Map<Property<?>, Object> states) {
        for (Map.Entry<Property<?>, Object> state : states.entrySet()) {
            net.minecraft.world.level.block.state.properties.Property property = stateContainer.getProperty(state.getKey().getName());
            Comparable value = (Comparable) state.getValue();
            // we may need to adapt this value, depending on the source prop
            if (property instanceof DirectionProperty) {
                Direction dir = (Direction) value;
                value = ForgeAdapter.adapt(dir);
            } else if (property instanceof net.minecraft.world.level.block.state.properties.EnumProperty) {
                String enumName = (String) value;
                value = ((net.minecraft.world.level.block.state.properties.EnumProperty<?>) property).getValue((String) value)
                        .orElseThrow(() -> new IllegalStateException("Enum property " + property.getName() + " does not contain " + enumName));
            }

            newState = newState.setValue(property, value);
        }
        return newState;
    }

    public static net.minecraft.world.level.block.state.BlockState transmogToMinecraft(BlockState blockState) {
        Block mcBlock = ForgeAdapter.adapt(blockState.getBlockType());
        net.minecraft.world.level.block.state.BlockState newState = mcBlock.defaultBlockState();
        Map<Property<?>, Object> states = blockState.getStates();
        return transmogToMinecraftProperties(mcBlock.getStateDefinition(), newState, states);
    }

    public static BlockState transmogToWorldEdit(net.minecraft.world.level.block.state.BlockState blockState) {
        BlockType blockType = ForgeAdapter.adapt(blockState.getBlock());
        return blockType.getState(transmogToWorldEditProperties(blockType, blockState.getValues()));
    }

    private ForgeTransmogrifier() {
    }
}
