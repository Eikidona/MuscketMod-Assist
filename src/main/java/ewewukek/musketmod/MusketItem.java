package ewewukek.musketmod;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.apache.commons.lang3.tuple.Pair;
import software.bernie.example.registry.SoundRegistry;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.ClientUtils;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

public class MusketItem extends GunItem implements GeoItem {
    public final Multimap<Attribute, AttributeModifier> bayonetAttributeModifiers;
    private static final RawAnimation LOAD_ANIME = RawAnimation.begin().thenPlay("load");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public MusketItem(Properties properties, boolean withBayonet) {
        super(properties);
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
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
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int ticksLeft) {
        Pair<Integer, Integer> loadingDuration = getLoadingDuration(stack);
        int loadingStages = loadingDuration.getLeft();
        int ticksPerLoadingStage = loadingDuration.getRight();

        int usingTicks = getUseDuration(stack) - ticksLeft;
        int prevLoadingStage = getLoadingStage(stack);
        int loadingStage = prevLoadingStage + usingTicks / ticksPerLoadingStage;
        if (loadingStage < loadingStages && usingTicks == ticksPerLoadingStage / 2) {
            entity.playSound(Sounds.MUSKET_LOAD_0, 0.8f, 1);
        }
        if (usingTicks > 0 && usingTicks % ticksPerLoadingStage == 0) {
            if (loadingStage < loadingStages) {
                entity.playSound(Sounds.MUSKET_LOAD_1, 0.8f, 1);
            } else if (loadingStage == loadingStages) {
                entity.playSound(Sounds.MUSKET_LOAD_2, 0.8f, 1);
            }
        }

        if (level.isClientSide && entity instanceof Player) {
            setActiveStack(entity.getUsedItemHand(), stack);
            return;
        }

        if (loadingStage > loadingStages && !isLoaded(stack)) {
            // played on server
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), Sounds.MUSKET_READY, entity.getSoundSource(), 0.8f, 1);
            if (prevLoadingStage == 1 && entity instanceof Player player) {
                consumeAmmo(player, stack);
            }
            setLoaded(stack, true);
        }
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

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "musket_controller", 0, state -> PlayState.STOP)
                .triggerableAnim("load", LOAD_ANIME)
                // We've marked the "box_open" animation as being triggerable from the server
                .setSoundKeyframeHandler(state -> {
                    // Use helper method to avoid client-code in common class
                    Player player = ClientUtils.getClientPlayer();

//                    if (player != null)
//                        player.playSound(SoundRegistry.JACK_MUSIC.get(), 1, 1);
                }));
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
}
