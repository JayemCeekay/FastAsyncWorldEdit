package com.sk89q.worldedit.fabric.fawe;

import com.sk89q.jnbt.LazyCompoundTag;
import com.sk89q.jnbt.ListTag;
import com.sk89q.jnbt.StringTag;
import com.sk89q.jnbt.Tag;
import com.sk89q.worldedit.fabric.FabricWorldEdit;
import com.sk89q.worldedit.util.nbt.CompoundBinaryTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class FabricLazyCompoundTag extends LazyCompoundTag {

    private final Supplier<CompoundTag> compoundTagSupplier;
    private com.sk89q.jnbt.CompoundTag compoundTag;

    public FabricLazyCompoundTag(Supplier<net.minecraft.nbt.CompoundTag> compoundTagSupplier) {
        super(new HashMap<>());
        this.compoundTagSupplier = compoundTagSupplier;
    }

    public FabricLazyCompoundTag(net.minecraft.nbt.CompoundTag compoundTag) {
        this(() -> compoundTag);
    }

    public net.minecraft.nbt.CompoundTag get() {
        return compoundTagSupplier.get();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Tag> getValue() {
        if (compoundTag == null) {
            compoundTag = (com.sk89q.jnbt.CompoundTag) FabricWorldEdit.inst.getFaweAdapter().toNative(compoundTagSupplier.get());
        }
        return compoundTag.getValue();
    }

    @Override
    public CompoundBinaryTag asBinaryTag() {
        getValue();
        return compoundTag.asBinaryTag();
    }

    public boolean containsKey(String key) {
        return compoundTagSupplier.get().contains(key);
    }

    public byte[] getByteArray(String key) {
        return compoundTagSupplier.get().getByteArray(key);
    }

    public byte getByte(String key) {
        return compoundTagSupplier.get().getByte(key);
    }

    public double getDouble(String key) {
        return compoundTagSupplier.get().getDouble(key);
    }

    public double asDouble(String key) {
        net.minecraft.nbt.Tag tag = compoundTagSupplier.get().get(key);
        if (tag instanceof NumericTag numTag) {
            return numTag.getAsDouble();
        }
        return 0;
    }

    public float getFloat(String key) {
        return compoundTagSupplier.get().getFloat(key);
    }

    public int[] getIntArray(String key) {
        return compoundTagSupplier.get().getIntArray(key);
    }

    public int getInt(String key) {
        return compoundTagSupplier.get().getInt(key);
    }

    public int asInt(String key) {
        net.minecraft.nbt.Tag tag = compoundTagSupplier.get().get(key);
        if (tag instanceof NumericTag numTag) {
            return numTag.getAsInt();
        }
        return 0;
    }

    @SuppressWarnings("unchecked")
    public List<Tag> getList(String key) {
        net.minecraft.nbt.Tag tag = compoundTagSupplier.get().get(key);
        if (tag instanceof net.minecraft.nbt.ListTag nbtList) {
            ArrayList<Tag> list = new ArrayList<>();
            for (net.minecraft.nbt.Tag elem : nbtList) {
                if (elem instanceof net.minecraft.nbt.CompoundTag compoundTag) {
                    list.add(new FabricLazyCompoundTag(compoundTag));
                } else {
                    list.add(FabricWorldEdit.inst.getFaweAdapter().toNative(elem));
                }
            }
            return list;
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    public ListTag getListTag(String key) {
        net.minecraft.nbt.Tag tag = compoundTagSupplier.get().get(key);
        if (tag instanceof net.minecraft.nbt.ListTag) {
            return (ListTag) FabricWorldEdit.inst.getFaweAdapter().toNative(tag);
        }
        return new ListTag(StringTag.class, Collections.emptyList());
    }

    @SuppressWarnings("unchecked")
    public <T extends Tag> List<T> getList(String key, Class<T> listType) {
        ListTag listTag = getListTag(key);
        if (listTag.getType().equals(listType)) {
            return (List<T>) listTag.getValue();
        } else {
            return Collections.emptyList();
        }
    }

    public long[] getLongArray(String key) {
        return compoundTagSupplier.get().getLongArray(key);
    }

    public long getLong(String key) {
        return compoundTagSupplier.get().getLong(key);
    }

    public long asLong(String key) {
        net.minecraft.nbt.Tag tag = compoundTagSupplier.get().get(key);
        if (tag instanceof NumericTag numTag) {
            return numTag.getAsLong();
        }
        return 0;
    }

    public short getShort(String key) {
        return compoundTagSupplier.get().getShort(key);
    }

    public String getString(String key) {
        return compoundTagSupplier.get().getString(key);
    }

    @Override
    public String toString() {
        return compoundTagSupplier.get().toString();
    }

}