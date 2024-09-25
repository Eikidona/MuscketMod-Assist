package ewewukek.musketmod;

import java.util.function.Consumer;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;

public class VanillaHelper {
    public static void modifyLootTableItems(ResourceLocation location, LootContext context, Consumer<ItemStack> adder) {
        if (location.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE)) {
            LootTable modTable = context.getLevel().getServer().getLootData()
                .getLootTable(MusketMod.resource(location.getPath()));
            if (modTable != null) {
                modTable.getRandomItemsRaw(context,
                    LootTable.createStackSplitter(context.getLevel(), adder));
            }
        }
    }
}
