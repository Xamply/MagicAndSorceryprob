package com.cesar.magicandsorcery.magic.catalyst;

import net.minecraft.world.item.ItemStack;

public interface ICatalyst {
    CastingMethod getCastingMethod(ItemStack stack);
}
