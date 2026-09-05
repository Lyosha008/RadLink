package org.Lyosha008.radlink;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PlayerCustomSlots implements ICapabilityProvider, INBTSerializable<CompoundTag> {
    public static final Capability<ItemStackHandler> CAPABILITY = CapabilityManager.get(new CapabilityToken<>(){});

    private final ItemStackHandler inventory = new ItemStackHandler(10);
    private final LazyOptional<ItemStackHandler> optional = LazyOptional.of(() -> inventory);

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return cap == CAPABILITY ? optional.cast() : LazyOptional.empty();
    }

    @Override public CompoundTag serializeNBT() {return inventory.serializeNBT();}
    @Override public void deserializeNBT(CompoundTag nbt) {inventory.deserializeNBT(nbt);}
}