package ewewukek.musketmod;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class MusketItemRender extends GeoItemRenderer<MusketItem> {
    public MusketItemRender() {
        super(new DefaultedItemGeoModel<>(new ResourceLocation(MusketMod.MODID, "jack_in_the_box")));
    }
}
