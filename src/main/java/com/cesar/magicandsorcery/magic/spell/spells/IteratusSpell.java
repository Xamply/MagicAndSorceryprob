package com.cesar.magicandsorcery.magic.spell.spells;

import com.cesar.magicandsorcery.MagicAndSorcery;
import com.cesar.magicandsorcery.magic.catalyst.CastingMethod;
import com.cesar.magicandsorcery.magic.spell.Spell;
import com.cesar.magicandsorcery.magic.spell.SpellSchool;
import com.cesar.magicandsorcery.magic.spell.SpellType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public class IteratusSpell extends Spell {
    public static final ResourceLocation ID = new ResourceLocation(MagicAndSorcery.MODID, "iteratus");

    public IteratusSpell() {
        super(ID, SpellSchool.ARCANE, SpellType.SINGLE_TARGET,
                10.0f, // 10 Mana cost per missile
                30,    // 1.5 seconds initial preparation (30 ticks)
                200,   // 10.0 seconds cooldown (200 ticks) applied when channeling ends
                10.0f  // 10 Base damage per missile (Wand: 16, Hand: 7.5, Ring: 11, Book: 13)
        );
    }

    @Override
    public double getRange() {
        return 32.0;
    }

    @Override
    public boolean execute(ServerPlayer player, Level level, CastingMethod method) {
        // Continuous missile barrage is streamed dynamically via PacketIteratusFire / PacketIteratusEnd
        return true;
    }
}
