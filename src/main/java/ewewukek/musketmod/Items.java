package ewewukek.musketmod;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SmithingTemplateItem;

public class Items {
    public static final Item MUSKET = new MusketItem(new Item.Properties(),false);
    public static final Item MUSKET_WITH_BAYONET = new MusketItem(new Item.Properties()
            .durability(Config.musketDurability), true);
    public static final Item BLUNDERBUSS = new BlunderbussItem(new Item.Properties()
            .durability(Config.blunderbussDurability));
    public static final Item PISTOL = new PistolItem(new Item.Properties()
            .durability(Config.pistolDurability));
    public static final Item CARTRIDGE = new CartridgeItem(new Item.Properties());

    public static void register(BiConsumer<String, Item> helper) {
        helper.accept("musket", MUSKET);
        helper.accept("musket_with_bayonet", MUSKET_WITH_BAYONET);
        helper.accept("blunderbuss", BLUNDERBUSS);
        helper.accept("pistol", PISTOL);
        helper.accept("cartridge", CARTRIDGE);
    }

    public static void addToCreativeTab(ResourceKey<CreativeModeTab> tab, Consumer<Item> helper) {
        if (tab == CreativeModeTabs.COMBAT) {
            helper.accept(MUSKET);
            helper.accept(MUSKET_WITH_BAYONET);
            helper.accept(BLUNDERBUSS);
            helper.accept(PISTOL);
            helper.accept(CARTRIDGE);
        }
    }
}