package com.chocodanette.legacycrafting;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModAttributes
{
    public static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(Registries.ATTRIBUTE, LegacyCrafting.MOD_ID);

    /**
     * Multiplies the time of every in-world craft of the player. 1 is the normal time; a trait such as "lazy" adds a
     * modifier (for example +0.5 with add_multiplied_base for 1.5 times longer).
     */
    public static final DeferredHolder<Attribute, Attribute> CRAFTING_TIME = ATTRIBUTES.register("crafting_time",
      () -> new RangedAttribute("attribute.name.legacycrafting.crafting_time", 1.0, 0.1, 16.0).setSyncable(true));

    private ModAttributes()
    {
    }
}
