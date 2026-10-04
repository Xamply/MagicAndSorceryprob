package com.cesar.magicandsorcery.magic.capability;

import com.cesar.magicandsorcery.magic.catalyst.CastingMethod;
import com.cesar.magicandsorcery.magic.spell.ModSpells;
import com.cesar.magicandsorcery.magic.spell.Spell;
import com.cesar.magicandsorcery.magic.spell.spells.BlizzardSpell;
import com.cesar.magicandsorcery.magic.spell.spells.BoltSpell;
import com.cesar.magicandsorcery.magic.spell.spells.FlashSpell;
import com.cesar.magicandsorcery.magic.spell.spells.ThundajaSpell;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayerMagicData {
    public static final float DEFAULT_MAX_MANA = 100.0f;
    public static final float DEFAULT_MANA_REGEN = 2.0f; // per second (2.0 MP/s)
    public static final int DEFAULT_SPELL_CAPACITY = 4;  // Initial capacity to test all 4 spells
    public static final int MAX_SPELL_CAPACITY = 9;

    private float mana;
    private float maxMana;
    private float manaRegen; // mana per second
    private int spellCapacity;
    private final List<ResourceLocation> learnedSpells = new ArrayList<>();
    private final List<ResourceLocation> preparedSpells = new ArrayList<>();
    private int selectedSpellIndex;
    private final Map<ResourceLocation, Integer> cooldowns = new HashMap<>();

    public PlayerMagicData() {
        this.maxMana = DEFAULT_MAX_MANA;
        this.mana = DEFAULT_MAX_MANA;
        this.manaRegen = DEFAULT_MANA_REGEN;
        this.spellCapacity = DEFAULT_SPELL_CAPACITY;
        this.selectedSpellIndex = 0;

        // Initialize all registered spells into learned library by default
        for (Spell s : ModSpells.getAllSpells()) {
            if (!learnedSpells.contains(s.getId())) {
                learnedSpells.add(s.getId());
            }
        }

        // Initialize 9 permanent slots
        for (int i = 0; i < MAX_SPELL_CAPACITY; i++) {
            preparedSpells.add(null);
        }
        preparedSpells.set(0, BoltSpell.ID);
        preparedSpells.set(1, BlizzardSpell.ID);
        preparedSpells.set(2, FlashSpell.ID);
        preparedSpells.set(3, ThundajaSpell.ID);
        preparedSpells.set(4, com.cesar.magicandsorcery.magic.spell.spells.DivineSwordSpell.ID);
        preparedSpells.set(5, com.cesar.magicandsorcery.magic.spell.spells.LaPollaCayendoSpell.ID);
    }

    private void ensureCapacity() {
        while (preparedSpells.size() < MAX_SPELL_CAPACITY) {
            preparedSpells.add(null);
        }
    }

    public float getMana() {
        return mana;
    }

    public void setMana(float mana) {
        this.mana = Math.max(0.0f, Math.min(mana, maxMana));
    }

    public float getMaxMana() {
        return maxMana;
    }

    public void setMaxMana(float maxMana) {
        this.maxMana = Math.max(10.0f, maxMana);
        if (this.mana > this.maxMana) {
            this.mana = this.maxMana;
        }
    }

    public float getManaRegen() {
        return manaRegen;
    }

    public void setManaRegen(float manaRegen) {
        this.manaRegen = Math.max(0.0f, manaRegen);
    }

    public int getSpellCapacity() {
        return spellCapacity;
    }

    public void setSpellCapacity(int capacity) {
        this.spellCapacity = Math.max(1, Math.min(capacity, MAX_SPELL_CAPACITY));
    }

    public List<ResourceLocation> getPreparedSpells() {
        ensureCapacity();
        return preparedSpells;
    }

    public int getSelectedSpellIndex() {
        return selectedSpellIndex;
    }

    public void setSelectedSpellIndex(int index) {
        ensureCapacity();
        if (index >= 0 && index < MAX_SPELL_CAPACITY && preparedSpells.get(index) != null) {
            this.selectedSpellIndex = index;
        }
    }

    public void cycleSpell(boolean next) {
        ensureCapacity();
        int step = next ? 1 : -1;
        for (int i = 1; i <= MAX_SPELL_CAPACITY; i++) {
            int candidate = Math.floorMod(selectedSpellIndex + i * step, MAX_SPELL_CAPACITY);
            if (candidate < preparedSpells.size() && preparedSpells.get(candidate) != null) {
                this.selectedSpellIndex = candidate;
                return;
            }
        }
    }

    public Spell getSelectedSpell() {
        ensureCapacity();
        if (selectedSpellIndex < 0 || selectedSpellIndex >= preparedSpells.size()) {
            return null;
        }
        ResourceLocation id = preparedSpells.get(selectedSpellIndex);
        return id != null ? ModSpells.getSpell(id) : null;
    }

    public boolean moveOrSwapSpell(int fromSlot, int toSlot) {
        if (fromSlot < 0 || fromSlot >= MAX_SPELL_CAPACITY || toSlot < 0 || toSlot >= MAX_SPELL_CAPACITY) {
            return false;
        }
        if (fromSlot == toSlot) {
            return false;
        }
        ensureCapacity();

        ResourceLocation spellFrom = preparedSpells.get(fromSlot);
        if (spellFrom == null) {
            return false;
        }

        ResourceLocation spellTo = preparedSpells.get(toSlot);

        // Swap / move
        preparedSpells.set(toSlot, spellFrom);
        preparedSpells.set(fromSlot, spellTo);

        // Update selectedSpellIndex if the moved spell was currently selected
        if (selectedSpellIndex == fromSlot) {
            selectedSpellIndex = toSlot;
        } else if (selectedSpellIndex == toSlot && spellTo != null) {
            selectedSpellIndex = fromSlot;
        }

        return true;
    }

    public List<ResourceLocation> getLearnedSpells() {
        return Collections.unmodifiableList(learnedSpells);
    }

    public boolean isSpellLearned(ResourceLocation spellId) {
        return spellId != null && learnedSpells.contains(spellId);
    }

    public void learnSpell(ResourceLocation spellId) {
        if (spellId != null && !learnedSpells.contains(spellId)) {
            learnedSpells.add(spellId);
        }
    }

    public void unlearnSpell(ResourceLocation spellId) {
        if (spellId != null) {
            learnedSpells.remove(spellId);
            for (int i = 0; i < preparedSpells.size(); i++) {
                if (spellId.equals(preparedSpells.get(i))) {
                    preparedSpells.set(i, null);
                }
            }
            fixSelectedSpellIndex();
        }
    }

    public boolean assignSpell(int targetSlot, ResourceLocation spellId) {
        if (targetSlot < 0 || targetSlot >= MAX_SPELL_CAPACITY) {
            return false;
        }
        ensureCapacity();

        if (spellId == null) {
            // Unequip slot
            preparedSpells.set(targetSlot, null);
            fixSelectedSpellIndex();
            return true;
        }

        // Must be learned or registered spell
        if (!isSpellLearned(spellId) && ModSpells.getSpell(spellId) == null) {
            return false;
        }

        // Remove from existing slot to prevent duplicates on the wheel
        for (int i = 0; i < MAX_SPELL_CAPACITY; i++) {
            if (i != targetSlot && spellId.equals(preparedSpells.get(i))) {
                preparedSpells.set(i, null);
            }
        }

        preparedSpells.set(targetSlot, spellId);
        if (preparedSpells.get(selectedSpellIndex) == null) {
            selectedSpellIndex = targetSlot;
        }
        return true;
    }

    public void fixSelectedSpellIndex() {
        ensureCapacity();
        if (selectedSpellIndex >= 0 && selectedSpellIndex < MAX_SPELL_CAPACITY && preparedSpells.get(selectedSpellIndex) != null) {
            return;
        }
        for (int i = 0; i < MAX_SPELL_CAPACITY; i++) {
            if (preparedSpells.get(i) != null) {
                selectedSpellIndex = i;
                return;
            }
        }
        selectedSpellIndex = 0;
    }

    public boolean canConsumeMana(float amount) {
        return this.mana >= amount;
    }

    public boolean consumeMana(float amount) {
        if (canConsumeMana(amount)) {
            setMana(this.mana - amount);
            return true;
        }
        return false;
    }

    public void addMana(float amount) {
        setMana(this.mana + amount);
    }

    public boolean isSpellOnCooldown(ResourceLocation spellId) {
        return cooldowns.getOrDefault(spellId, 0) > 0;
    }

    public int getSpellCooldown(ResourceLocation spellId) {
        return cooldowns.getOrDefault(spellId, 0);
    }

    public void setSpellCooldown(ResourceLocation spellId, int ticks) {
        if (ticks > 0) {
            cooldowns.put(spellId, ticks);
        } else {
            cooldowns.remove(spellId);
        }
    }

    public Map<ResourceLocation, Integer> getCooldowns() {
        return cooldowns;
    }

    public void tickServer() {
        // Regenerate mana (manaRegen is per second, divided by 20 ticks)
        if (mana < maxMana) {
            addMana(manaRegen / 20.0f);
        }

        // Tick cooldowns
        if (!cooldowns.isEmpty()) {
            List<ResourceLocation> expired = new ArrayList<>();
            for (Map.Entry<ResourceLocation, Integer> entry : cooldowns.entrySet()) {
                int remaining = entry.getValue() - 1;
                if (remaining <= 0) {
                    expired.add(entry.getKey());
                } else {
                    entry.setValue(remaining);
                }
            }
            for (ResourceLocation id : expired) {
                cooldowns.remove(id);
            }
        }
    }

    public void copyFrom(PlayerMagicData source) {
        this.mana = source.mana;
        this.maxMana = source.maxMana;
        this.manaRegen = source.manaRegen;
        this.spellCapacity = source.spellCapacity;
        this.selectedSpellIndex = source.selectedSpellIndex;
        this.learnedSpells.clear();
        this.learnedSpells.addAll(source.learnedSpells);
        this.preparedSpells.clear();
        this.preparedSpells.addAll(source.preparedSpells);
        ensureCapacity();
        this.cooldowns.clear();
        this.cooldowns.putAll(source.cooldowns);
    }

    public void saveNBTData(CompoundTag nbt) {
        nbt.putFloat("Mana", mana);
        nbt.putFloat("MaxMana", maxMana);
        nbt.putFloat("ManaRegen", manaRegen);
        nbt.putInt("SpellCapacity", spellCapacity);
        nbt.putInt("SelectedSpellIndex", selectedSpellIndex);

        ListTag learnedTag = new ListTag();
        for (ResourceLocation id : learnedSpells) {
            if (id != null) {
                learnedTag.add(StringTag.valueOf(id.toString()));
            }
        }
        nbt.put("LearnedSpells", learnedTag);

        ensureCapacity();
        ListTag spellsTag = new ListTag();
        for (int i = 0; i < MAX_SPELL_CAPACITY; i++) {
            ResourceLocation id = preparedSpells.get(i);
            spellsTag.add(StringTag.valueOf(id != null ? id.toString() : ""));
        }
        nbt.put("PreparedSpells", spellsTag);

        CompoundTag cdTag = new CompoundTag();
        for (Map.Entry<ResourceLocation, Integer> entry : cooldowns.entrySet()) {
            cdTag.putInt(entry.getKey().toString(), entry.getValue());
        }
        nbt.put("Cooldowns", cdTag);
    }

    public void loadNBTData(CompoundTag nbt) {
        if (nbt.contains("Mana")) mana = nbt.getFloat("Mana");
        if (nbt.contains("MaxMana")) maxMana = nbt.getFloat("MaxMana");
        if (nbt.contains("ManaRegen")) manaRegen = nbt.getFloat("ManaRegen");
        if (nbt.contains("SpellCapacity")) spellCapacity = nbt.getInt("SpellCapacity");
        if (nbt.contains("SelectedSpellIndex")) selectedSpellIndex = nbt.getInt("SelectedSpellIndex");

        if (nbt.contains("LearnedSpells", Tag.TAG_LIST)) {
            learnedSpells.clear();
            ListTag list = nbt.getList("LearnedSpells", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                ResourceLocation id = ResourceLocation.tryParse(list.getString(i));
                if (id != null && !learnedSpells.contains(id)) {
                    learnedSpells.add(id);
                }
            }
        }
        // Ensure all registered spells are known
        for (Spell s : ModSpells.getAllSpells()) {
            if (!learnedSpells.contains(s.getId())) {
                learnedSpells.add(s.getId());
            }
        }

        if (nbt.contains("PreparedSpells", Tag.TAG_LIST)) {
            preparedSpells.clear();
            ListTag list = nbt.getList("PreparedSpells", Tag.TAG_STRING);
            for (int i = 0; i < list.size() && i < MAX_SPELL_CAPACITY; i++) {
                String str = list.getString(i);
                if (str.isEmpty()) {
                    preparedSpells.add(null);
                } else {
                    preparedSpells.add(ResourceLocation.tryParse(str));
                }
            }
            ensureCapacity();
        }

        fixSelectedSpellIndex();

        if (nbt.contains("Cooldowns", Tag.TAG_COMPOUND)) {
            cooldowns.clear();
            CompoundTag cdTag = nbt.getCompound("Cooldowns");
            for (String key : cdTag.getAllKeys()) {
                ResourceLocation id = ResourceLocation.tryParse(key);
                if (id != null) {
                    cooldowns.put(id, cdTag.getInt(key));
                }
            }
        }
    }
}
