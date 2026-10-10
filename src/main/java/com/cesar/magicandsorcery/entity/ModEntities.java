package com.cesar.magicandsorcery.entity;

import com.cesar.magicandsorcery.MagicAndSorcery;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MagicAndSorcery.MODID);

    public static final RegistryObject<EntityType<IteratusMissileEntity>> ITERATUS_MISSILE =
            ENTITIES.register("iteratus_missile", () ->
                    EntityType.Builder.<IteratusMissileEntity>of(IteratusMissileEntity::new, MobCategory.MISC)
                            .sized(0.35f, 0.35f)
                            .clientTrackingRange(64)
                            .updateInterval(1)
                            .build(new ResourceLocation(MagicAndSorcery.MODID, "iteratus_missile").toString())
            );

    public static final RegistryObject<EntityType<BobEntity>> BOB =
            ENTITIES.register("bob", () ->
                    EntityType.Builder.<BobEntity>of(BobEntity::new, MobCategory.MISC)
                            .sized(0.6f, 1.8f)
                            .clientTrackingRange(64)
                            .updateInterval(1)
                            .build(new ResourceLocation(MagicAndSorcery.MODID, "bob").toString())
            );

    public static void register(IEventBus eventBus) {
        ENTITIES.register(eventBus);
    }
}
