package com.hellking;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

public class ModItems {
    public static final Item HELL_SCYTHE = Registry.register(Registries.ITEM,
            Identifier.of(HellKingMod.MOD_ID, "hell_scythe"),
            new SwordItem(ToolMaterials.NETHERITE,
                    new Item.Settings()
                            .attributeModifiers(SwordItem.createAttributeModifiers(ToolMaterials.NETHERITE, 8, -2.6f))
                            .fireproof()
                            .rarity(Rarity.EPIC)
                            .maxCount(1)));

    public static void init() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> entries.add(HELL_SCYTHE));
    }
}
