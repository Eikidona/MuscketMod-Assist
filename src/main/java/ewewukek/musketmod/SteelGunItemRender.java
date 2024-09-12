package ewewukek.musketmod;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class SteelGunItemRender extends GeoItemRenderer<SteelGunItem> {
    public SteelGunItemRender() {
        super(new DefaultedItemGeoModel<>(new ResourceLocation(MusketMod.MOD_ID, "steelgun")));
    }
}
