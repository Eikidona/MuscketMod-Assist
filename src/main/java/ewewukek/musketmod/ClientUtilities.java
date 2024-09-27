package ewewukek.musketmod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class ClientUtilities {
    public static void registerItemProperties() {
        ClampedItemPropertyFunction predicate = (stack, level, player, seed) -> {
            return GunItem.isLoaded(stack) ? 1 : 0;
        };
        ResourceLocation location = MusketMod.resource("loaded");
        ItemProperties.register(Items.MUSKET_WITH_BAYONET, location, predicate);
        ItemProperties.register(Items.BLUNDERBUSS, location, predicate);
        ItemProperties.register(Items.PISTOL, location, predicate);
    }
}
