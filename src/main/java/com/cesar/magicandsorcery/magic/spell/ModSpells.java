package com.cesar.magicandsorcery.magic.spell;

import com.cesar.magicandsorcery.magic.spell.spells.BlizzardSpell;
import com.cesar.magicandsorcery.magic.spell.spells.BoltSpell;
import com.cesar.magicandsorcery.magic.spell.spells.FlashSpell;
import com.cesar.magicandsorcery.magic.spell.spells.ThundajaSpell;
import com.cesar.magicandsorcery.magic.spell.spells.LaPollaCayendoSpell;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class ModSpells {
    private static final Map<ResourceLocation, Spell> SPELLS = new LinkedHashMap<>();

    public static final BoltSpell BOLT = register(new BoltSpell());
    public static final BlizzardSpell BLIZZARD = register(new BlizzardSpell());
    public static final FlashSpell FLASH = register(new FlashSpell());
    public static final ThundajaSpell THUNDAJA = register(new ThundajaSpell());
    public static final com.cesar.magicandsorcery.magic.spell.spells.DivineSwordSpell DIVINE_SWORD = register(new com.cesar.magicandsorcery.magic.spell.spells.DivineSwordSpell());
    public static final LaPollaCayendoSpell LA_POLLA_CAYENDO = register(new LaPollaCayendoSpell());
    public static final com.cesar.magicandsorcery.magic.spell.spells.RedshaSpell REDSHA = register(new com.cesar.magicandsorcery.magic.spell.spells.RedshaSpell());
    public static final com.cesar.magicandsorcery.magic.spell.spells.DenySpell DENY = register(new com.cesar.magicandsorcery.magic.spell.spells.DenySpell());

    public static <T extends Spell> T register(T spell) {
        SPELLS.put(spell.getId(), spell);
        return spell;
    }

    public static Spell getSpell(ResourceLocation id) {
        return SPELLS.get(id);
    }

    public static Collection<Spell> getAllSpells() {
        return Collections.unmodifiableCollection(SPELLS.values());
    }

    public static void init() {
        // Classloading triggers registration
    }
}
