package ewewukek.musketmod;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class Items {
    public static final Item MUSKET = new MusketItem(new Item.Properties().stacksTo(1).durability(-1),false);
    public static final Item MUSKET_WITH_BAYONET = new MusketItem(new Item.Properties()
            .durability(Config.musketDurability), true);
    public static final Item BLUNDERBUSS = new BlunderbussItem(new Item.Properties()
            .durability(Config.blunderbussDurability));
    public static final Item PISTOL = new PistolItem(new Item.Properties()
            .durability(Config.pistolDurability));
    public static final Item CARTRIDGE = new CartridgeItem(new Item.Properties());

    public static void register(BiConsumer<String, Item> helper) {
        helper.accept("jack_in_the_box", MUSKET);
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