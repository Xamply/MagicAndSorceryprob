package com.cesar.magicandsorcery.item;

import com.cesar.magicandsorcery.MagicAndSorcery;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MagicAndSorcery.MODID);

    public static final RegistryObject<Item> MAGIC_CRYSTAL = ITEMS.register("magic_crystal",
            () -> new Item(new Item.Properties().rarity(Rarity.RARE)));

    public static final RegistryObject<MagicWandItem> MAGIC_WAND = ITEMS.register("magic_wand",
            MagicWandItem::new);

    public static final RegistryObject<SpellBookItem> SPELL_TOME = ITEMS.register("spell_tome",
            SpellBookItem::new);

    public static final RegistryObject<MagicRingItem> MAGIC_RING = ITEMS.register("magic_ring",
            MagicRingItem::new);

    public static final RegistryObject<ManaPotionItem> MANA_POTION = ITEMS.register("mana_potion",
            ManaPotionItem::new);

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
