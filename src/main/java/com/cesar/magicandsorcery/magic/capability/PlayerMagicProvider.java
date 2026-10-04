package com.cesar.magicandsorcery.magic.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PlayerMagicProvider implements ICapabilitySerializable<CompoundTag> {
    public static final Capability<PlayerMagicData> PLAYER_MAGIC = CapabilityManager.get(new CapabilityToken<>() {});

    private PlayerMagicData magicData = null;
    private final LazyOptional<PlayerMagicData> optional = LazyOptional.of(this::getOrCreateMagicData);

    private PlayerMagicData getOrCreateMagicData() {
        if (this.magicData == null) {
            this.magicData = new PlayerMagicData();
        }
        return this.magicData;
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == PLAYER_MAGIC) {
            return optional.cast();
        }
        return LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        getOrCreateMagicData().saveNBTData(nbt);
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        getOrCreateMagicData().loadNBTData(nbt);
    }
}
