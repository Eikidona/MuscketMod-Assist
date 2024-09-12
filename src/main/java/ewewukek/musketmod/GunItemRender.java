package ewewukek.musketmod;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class GunItemRender extends GeoItemRenderer<GunItem> {
    public GunItemRender() {
        super(new DefaultedItemGeoModel<>(new ResourceLocation(MusketMod.MOD_ID, "steelgun")));
    }
}
