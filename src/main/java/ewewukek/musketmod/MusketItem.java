package ewewukek.musketmod;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

public class MusketItem extends GunItem implements GeoItem {
    public final Multimap<Attribute, AttributeModifier> bayonetAttributeModifiers;
    private static final RawAnimation RELOAD_ANIMATION = RawAnimation.begin().thenPlay("reload");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public MusketItem(Properties properties, boolean withBayonet) {
        super(properties);
        if (withBayonet) {
            ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
            builder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(
                    BASE_ATTACK_DAMAGE_UUID, "Weapon modifier", Config.bayonetDamage - 1, AttributeModifier.Operation.ADDITION));
            builder.put(Attributes.ATTACK_SPEED, new AttributeModifier(
                    BASE_ATTACK_SPEED_UUID, "Weapon modifier", Config.bayonetSpeed - 4, AttributeModifier.Operation.ADDITION));
            bayonetAttributeModifiers = builder.build();
        } else {
            bayonetAttributeModifiers = null;
        }
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (hand == InteractionHand.OFF_HAND) {
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        }
        ItemStack stack = player.getItemInHand(hand);
        if (!canUse(player) || !canUseFrom(player, hand)) {
            return InteractionResultHolder.pass(stack);
        }

        if (isLoaded(stack)) {
            if (stack.getOrCreateTag().getBoolean("JustReloaded")) {
                stack.getOrCreateTag().remove("JustReloaded");
                return InteractionResultHolder.pass(stack);
            }

            if (!level.isClientSide) {
                Vec3 direction = Vec3.directionFromRotation(player.getXRot(), player.getYRot());
                fire(player, stack, direction, smokeOffsetFor(player, hand));
            }
            player.playSound(fireSound(stack), 3.5f, 1);

            setLoaded(stack, false);
            stack.hurtAndBreak(1, player, (ent) -> {
                ent.broadcastBreakEvent(hand);
            });

            player.releaseUsingItem();

            return InteractionResultHolder.consume(stack);

        } else {
            if (!checkAmmo(player, stack)) {
                return InteractionResultHolder.fail(stack);
            }

            player.startUsingItem(hand);
            if (level instanceof ServerLevel serverLevel) {
                MusketMod.LOGGER.info("Reloading musket");
                triggerAnim(player, GeoItem.getOrAssignId(stack, serverLevel), "controller", "reload");
            }

            return InteractionResultHolder.consume(stack);
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide) {
            if (entity instanceof Player player) {
                consumeAmmo(player, stack);
            }
            setLoaded(stack, true);
            stack.getOrCreateTag().putBoolean("JustReloaded", true);
            if (entity instanceof Player player) {
                player.getCooldowns().addCooldown(stack.getItem(), 10);
            }
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), Sounds.MUSKET_READY, entity.getSoundSource(), 0.8f, 1);
        }
        return stack;
    }

    @Override
    public SoundEvent fireSound(ItemStack stack) {
        return Sounds.MUSKET_FIRE;
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND && bayonetAttributeModifiers != null
                ? bayonetAttributeModifiers : super.getDefaultAttributeModifiers(slot);
    }

    @Override
    public void registerControllers(ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", state -> {
            return PlayState.STOP;
        }).triggerableAnim("reload", RELOAD_ANIMATION));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private MusketItemRender renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (this.renderer == null)
                    this.renderer = new MusketItemRender();

                return this.renderer;
            }
        });
    }
}
