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

import com.google.common.collect.ImmutableList;
import com.mojang.math.Vector3d;
import com.sk89q.jnbt.CompoundTag;
import com.sk89q.jnbt.Tag;
import com.sk89q.worldedit.blocks.BaseItemStack;
import com.sk89q.worldedit.forge.internal.NBTConverter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.registry.state.DirectionalProperty;
import com.sk89q.worldedit.registry.state.Property;
import com.sk89q.worldedit.util.Direction;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.biome.BiomeType;
import com.sk89q.worldedit.world.biome.BiomeTypes;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;
import com.sk89q.worldedit.world.block.BlockTypesCache;
import com.sk89q.worldedit.world.item.ItemType;
import com.sk89q.worldedit.world.item.ItemTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

public final class ForgeAdapter {

    private ForgeAdapter() {
    }

    public static World adapt(net.minecraft.world.level.Level world) {
        return new ForgeWorld(world);
    }

    public static Biome adapt(BiomeType biomeType) {
        return ForgeRegistries.BIOMES.getValue(new ResourceLocation(biomeType.getId()));
    }

    public static BiomeType adapt(Biome biome) {
        return BiomeTypes.get(biome.getRegistryName().toString());
    }

    public static Vector3 adapt(Vector3d vector) {
        return Vector3.at(vector.x, vector.y, vector.z);
    }

    public static BlockVector3 adapt(BlockPos pos) {
        return BlockVector3.at(pos.getX(), pos.getY(), pos.getZ());
    }

    public static Vec3 toVec3(BlockVector3 vector) {
        return new Vec3(vector.getBlockX(), vector.getBlockY(), vector.getBlockZ());
    }

    public static net.minecraft.core.Direction adapt(Direction face) {
        switch (face) {
            case NORTH:
                return net.minecraft.core.Direction.NORTH;
            case SOUTH:
                return net.minecraft.core.Direction.SOUTH;
            case WEST:
                return net.minecraft.core.Direction.WEST;
            case EAST:
                return net.minecraft.core.Direction.EAST;
            case DOWN:
                return net.minecraft.core.Direction.DOWN;
            case UP:
            default:
                return net.minecraft.core.Direction.UP;
        }
    }

    public static Direction adaptEnumFacing(net.minecraft.core.Direction face) {
        switch (face) {
            case NORTH:
                return Direction.NORTH;
            case SOUTH:
                return Direction.SOUTH;
            case WEST:
                return Direction.WEST;
            case EAST:
                return Direction.EAST;
            case DOWN:
                return Direction.DOWN;
            case UP:
            default:
                return Direction.UP;
        }
    }

    public static BlockPos toBlockPos(BlockVector3 vector) {
        return new BlockPos(vector.getBlockX(), vector.getBlockY(), vector.getBlockZ());
    }

    public static Property<?> adaptProperty(net.minecraft.world.level.block.state.properties.Property<?> property) {
        if (property instanceof BooleanProperty) {
            return new com.sk89q.worldedit.registry.state.BooleanProperty(
                    property.getName(),
                    ImmutableList.copyOf(((BooleanProperty) property).getPossibleValues())
            );
        }
        if (property instanceof IntegerProperty) {
            return new com.sk89q.worldedit.registry.state.IntegerProperty(
                    property.getName(),
                    ImmutableList.copyOf(((IntegerProperty) property).getPossibleValues())
            );
        }
        if (property instanceof DirectionProperty) {
            return new DirectionalProperty(property.getName(), ((DirectionProperty) property).getPossibleValues().stream()
                    .map(ForgeAdapter::adaptEnumFacing)
                    .collect(Collectors.toList()));
        }
        if (property instanceof EnumProperty) {
            // Note: do not make x.getName a method reference.
            // It will cause runtime bootstrap exceptions.
            return new com.sk89q.worldedit.registry.state.EnumProperty(
                    property.toString(),
                    ((EnumProperty<?>) property).getPossibleValues().stream()
                            .map(x -> x.name())
                            .collect(Collectors.toList())
            ).withOffset(0);
        }
        return new IPropertyAdapter<>(property);
    }

    public static Map<Property<?>, Object> adaptProperties(
            BlockType block,
            Map<net.minecraft.world.level.block.state.properties.Property<?>, Comparable<?>> mcProps
    ) {
        Map<Property<?>, Object> props = new TreeMap<>(Comparator.comparing(Property::getName));
        for (Map.Entry<net.minecraft.world.level.block.state.properties.Property<?>, Comparable<?>> prop : mcProps.entrySet()) {
            Object value = prop.getValue();
            if (prop.getKey() instanceof DirectionProperty) {
                value = adaptEnumFacing((net.minecraft.core.Direction) value);
            } else if (prop.getKey() instanceof net.minecraft.world.level.block.state.properties.EnumProperty) {
                value = ((StringRepresentable) value).getSerializedName();
            }
            props.put(block.getProperty(prop.getKey().getName()), value);
        }
        return props;
    }

    private static net.minecraft.world.level.block.state.BlockState applyProperties(
            StateDefinition<Block, net.minecraft.world.level.block.state.BlockState> stateContainer,
            net.minecraft.world.level.block.state.BlockState newState, Map<Property<?>, Object> states
    ) {
        for (Map.Entry<Property<?>, Object> state : states.entrySet()) {


            net.minecraft.world.level.block.state.properties.Property property =
                    stateContainer.getProperty(state.getKey().getName());

            //stateContainer.getProperties().stream().filter(property1 -> property1.getName().equalsIgnoreCase(state
            // .getKey().getName())).findAny().get();
            Object value = state.getValue();
            // we may need to adapt this value, depending on the source prop
            if (property instanceof DirectionProperty) {
                Direction dir = (Direction) value;
                value = adapt(dir);
            } else if (property instanceof net.minecraft.world.level.block.state.properties.EnumProperty) {
                String enumName = (String) value;
                value =
                        ((net.minecraft.world.level.block.state.properties.EnumProperty<?>) property)
                                .getValue((String) value)
                                .orElseGet(() -> {
                                    throw new IllegalStateException("Enum property " + property.getName() + " does not contain " + enumName);
                                });
            }
            newState = newState.setValue(property, (Comparable) value);
        }
        return newState;
    }

    public static net.minecraft.world.level.block.state.BlockState adapt(BlockState blockState) {
        Block mcBlock = adapt(blockState.getBlockType());
        net.minecraft.world.level.block.state.BlockState newState = mcBlock.defaultBlockState();
        Map<Property<?>, Object> states = blockState.getStates();
        return applyProperties(mcBlock.getStateDefinition(), newState, states);
    }

    public static BlockState adapt(net.minecraft.world.level.block.state.BlockState blockState) {
        BlockType blockType = adapt(blockState.getBlock());
        return blockType.getState(adaptProperties(blockType, blockState.getValues()));
    }

    public static Block adapt(BlockType blockType) {
        return ForgeRegistries.BLOCKS.getValue(new ResourceLocation(blockType.getId()));
    }

    public static BlockType adapt(Block block) {
        System.out.println("adapting " + block.getRegistryName().getPath());
        return BlockTypes.parse(block.getRegistryName().getPath());
    }

    public static Item adapt(ItemType itemType) {
        return ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemType.getId()));
    }

    public static ItemType adapt(Item item) {
        return ItemTypes.get(ForgeRegistries.ITEMS.getKey(item).toString());
    }

    public static ItemStack adapt(BaseItemStack baseItemStack) {
        net.minecraft.nbt.CompoundTag forgeCompound = null;
        if (baseItemStack.getNbtData() != null) {
            forgeCompound = (net.minecraft.nbt.CompoundTag) NBTConverter.toNative(baseItemStack.getNbtData());
        }
        final ItemStack itemStack = new ItemStack(adapt(baseItemStack.getType()), baseItemStack.getAmount());
        itemStack.setTag(forgeCompound);
        return itemStack;
    }

    public static BaseItemStack adapt(ItemStack itemStack) {
        CompoundTag tag = NBTConverter.fromNative(itemStack.serializeNBT());
        if (tag.getValue().isEmpty()) {
            tag = null;
        } else {
            final Tag tagTag = tag.getValue().get("tag");
            if (tagTag instanceof CompoundTag) {
                tag = ((CompoundTag) tagTag);
            } else {
                tag = null;
            }
        }
        return new BaseItemStack(adapt(itemStack.getItem()), tag, itemStack.getCount());
    }

    /**
     * Get the WorldEdit proxy for the given player.
     *
     * @param player the player
     * @return the WorldEdit player
     */
    public static ForgePlayer adaptPlayer(ServerPlayer player) {
        checkNotNull(player);
        return new ForgePlayer(player);
    }

}
