package ewewukek.musketmod;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;

import javax.annotation.Nullable;

public class GunItem extends Item implements GeoItem {
    public static ItemStack activeMainHandStack;
    public static ItemStack activeOffhandStack;
    public static final ResourceKey<DamageType> BULLET_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, MusketMod.resource("bullet"));

    public GunItem(Properties pProperties) {
        super(pProperties);
    }

    public float bulletStdDev() {
        return 0;
    }

    public float bulletSpeed() {
        return 400;
    }

    public float damage() {
        return 96;
    }

    public SoundEvent fireSound(ItemStack stack) {
        return null;
    }

    public int pelletCount() {
        return 1;
    }

    public boolean twoHanded() {
        return true;
    }

    public float bulletDropReduction() {
        return 0.0f;
    }

    public int hitDurabilityDamage() {
        return 1;
    }

    public static boolean canUse(LivingEntity entity) {
        boolean creative = entity instanceof Player player && player.getAbilities().instabuild;
        return creative || true; // Allow use in survival mode
    }

    public boolean canUseFrom(LivingEntity entity, InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND) {
            return true;
        }
        if (twoHanded()) {
            return false;
        }
        ItemStack stack = entity.getMainHandItem();
        if (!stack.isEmpty() && stack.getItem() instanceof GunItem gun) {
            return !gun.twoHanded();
        }
        return true;
    }

    public static boolean isInHand(LivingEntity entity, InteractionHand hand) {
        ItemStack stack = entity.getItemInHand(hand);
        if (stack.isEmpty()) return false;
        if (stack.getItem() instanceof GunItem gun) {
            return gun.canUseFrom(entity, hand);
        }
        return false;
    }

    public static boolean isHoldingGun(LivingEntity entity) {
        return getHoldingHand(entity) != null;
    }

    @Nullable
    public static InteractionHand getHoldingHand(LivingEntity entity) {
        if (isInHand(entity, InteractionHand.MAIN_HAND)) return InteractionHand.MAIN_HAND;
        if (isInHand(entity, InteractionHand.OFF_HAND)) return InteractionHand.OFF_HAND;
        return null;
    }

    public Vec3 smokeOffsetFor(LivingEntity entity, InteractionHand hand) {
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND
                ? entity.getMainArm() : entity.getMainArm().getOpposite();
        return smokeOffsetFor(entity, arm);
    }

    public Vec3 smokeOffsetFor(LivingEntity entity, HumanoidArm arm) {
        boolean isRightHand = arm == HumanoidArm.RIGHT;
        Vec3 side = Vec3.directionFromRotation(0, entity.getYRot() + (isRightHand ? 90 : -90));
        Vec3 down = Vec3.directionFromRotation(entity.getXRot() + 90, entity.getYRot());
        return side.add(down).scale(0.15);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!canUse(player) || !canUseFrom(player, hand)) {
            return InteractionResultHolder.pass(stack);
        }

        if (isLoaded(stack)) {
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
            if (level.isClientSide) setActiveStack(hand, stack);

            return InteractionResultHolder.consume(stack);

        } else {
            if (!checkAmmo(player, stack)) {
                return InteractionResultHolder.fail(stack);
            }

            player.startUsingItem(hand);
            if (level instanceof ServerLevel serverLevel) {
                triggerReloadAnimation(player, stack, serverLevel);
            }

            return InteractionResultHolder.consume(stack);
        }
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.CROSSBOW;
    }

    public int getReloadDuration() {
        return 40; // Adjust reload time as needed
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        if (isLoaded(stack)) {
            return 0;
        } else {
            return getReloadDuration();
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide) {
            if (entity instanceof Player player) {
                consumeAmmo(player, stack);
            }
            setLoaded(stack, true);
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), Sounds.MUSKET_READY, entity.getSoundSource(), 0.8f, 1);
        }
        return stack;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int ticksLeft) {
        if (level.isClientSide && entity instanceof Player) {
            setActiveStack(entity.getUsedItemHand(), stack);
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        // Handle reloading interruption if needed
        if (!level.isClientSide && !isLoaded(stack)) {
            // Optionally reset any reload progress or handle partial reload logic
        }
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity entity) {
        stack.hurtAndBreak(hitDurabilityDamage(), entity, (ent) -> {
            ent.broadcastBreakEvent(EquipmentSlot.MAINHAND);
        });
        return false;
    }

    @Override
    public int getEnchantmentValue() {
        return 0;
    }

    public void fire(LivingEntity entity, ItemStack stack, Vec3 direction, Vec3 smokeOffset) {
        Level level = entity.level();
        Vec3 origin = new Vec3(entity.getX(), entity.getEyeY(), entity.getZ());
        float damage = damage();

        for (int i = 0; i < pelletCount(); i++) {
            BulletEntity bullet = new BulletEntity(level);
            bullet.setOwner(entity);
            bullet.setPos(origin);
            bullet.setVelocity(bulletSpeed(), addSpread(direction, entity.getRandom(), bulletStdDev()));
            bullet.damage = damage;
            bullet.setDropReduction(bulletDropReduction());
            bullet.setPelletCount(pelletCount());

            level.addFreshEntity(bullet);
        }

        if (level instanceof ServerLevel serverLevel) {
            MusketMod.sendSmokeEffect(serverLevel, origin.add(smokeOffset), direction);
        }
    }

    public static void fireParticles(ClientLevel level, Vec3 origin, Vec3 direction) {
        RandomSource random = RandomSource.create();

        for (int i = 0; i < 5; i++) {
            double t = Math.pow(random.nextFloat(), 2);
            Vec3 p = origin.add(direction.scale(0.75 + t));
            p = p.add(new Vec3(random.nextFloat() - 0.5, random.nextFloat() - 0.5, random.nextFloat() - 0.5).scale(0.05));
            Vec3 v = direction.scale(0.05);
            level.addParticle(ParticleTypes.POOF, p.x, p.y, p.z, v.x, v.y, v.z);
        }
    }

    public static ItemStack getActiveStack(InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND) {
            return activeMainHandStack;
        } else {
            return activeOffhandStack;
        }
    }

    public static void setActiveStack(InteractionHand hand, ItemStack stack) {
        if (hand == InteractionHand.MAIN_HAND) {
            activeMainHandStack = stack;
        } else {
            activeOffhandStack = stack;
        }
    }

    public static boolean isAmmo(ItemStack stack) {
        return stack.getItem() == Items.CARTRIDGE;
    }

    public static ItemStack findAmmo(Player player) {
        ItemStack stack = player.getItemBySlot(EquipmentSlot.OFFHAND);
        if (isAmmo(stack)) return stack;

        stack = player.getItemBySlot(EquipmentSlot.MAINHAND);
        if (isAmmo(stack)) return stack;

        int size = player.getInventory().getContainerSize();
        for (int i = 0; i < size; i++) {
            stack = player.getInventory().getItem(i);
            if (isAmmo(stack)) return stack;
        }

        return ItemStack.EMPTY;
    }

    public static boolean checkAmmo(Player player, ItemStack stack) {
        if (player.getAbilities().instabuild) return true;
        ItemStack ammoStack = findAmmo(player);
        return !ammoStack.isEmpty();
    }

    public static void consumeAmmo(Player player, ItemStack stack) {
        if (player.getAbilities().instabuild) return;

        ItemStack ammoStack = findAmmo(player);
        ammoStack.shrink(1);
        if (ammoStack.isEmpty()) {
            player.getInventory().removeItem(ammoStack);
        }
    }

    public static boolean isLoaded(ItemStack stack) {
        return stack.getOrCreateTag().getByte("loaded") != 0;
    }

    public static void setLoaded(ItemStack stack, boolean loaded) {
        if (loaded) {
            stack.getOrCreateTag().putByte("loaded", (byte)1);
        } else {
            stack.getOrCreateTag().remove("loaded");
        }
    }

    // Restored the aimAt method
    public Vec3 aimAt(LivingEntity shooter, LivingEntity target) {
        double dx = target.getX() - shooter.getX();
        double dy = target.getEyeY() - shooter.getEyeY();
        double dz = target.getZ() - shooter.getZ();
        Vec3 dir = new Vec3(dx, dy, dz).normalize();
        return dir;
    }

    // Restored the addUniformSpread method
    public static Vec3 addUniformSpread(Vec3 direction, RandomSource random, float spread) {
        float error = (float) Math.toRadians(spread) * random.nextFloat();
        return applyError(direction, random, error);
    }

    // Restored the mobUse method
    public <T extends Monster> void mobUse(T mob, InteractionHand hand, Vec3 direction) {
        ItemStack stack = mob.getItemInHand(hand);
        if (!isLoaded(stack)) {
            // Mob needs to reload
            mob.startUsingItem(hand);
        } else {
            // Mob fires the gun
            fire(mob, stack, direction, smokeOffsetFor(mob, hand));
            mob.playSound(fireSound(stack), 3.5f, 1);
            setLoaded(stack, false);
        }
    }

    // Method to trigger reload animation
    private void triggerReloadAnimation(Player player, ItemStack stack, ServerLevel level) {
        // Implement the method to trigger reload animation
        // For example:
        // triggerAnim(player, GeoItem.getOrAssignId(stack, level), "musket_controller", "reload");
    }

    // Additional methods for bullet spread, error application, etc.
    public static Vec3 addSpread(Vec3 direction, RandomSource random, float spreadStdDev) {
        float gaussian = Math.abs((float) random.nextGaussian());
        if (gaussian > 4) gaussian = 4;
        float error = (float) Math.toRadians(spreadStdDev) * gaussian;
        return applyError(direction, random, error);
    }

    public static Vec3 applyError(Vec3 direction, RandomSource random, float coneAngle) {
        Vec3 n1;
        Vec3 n2;
        if (Math.abs(direction.x) < 1e-5 && Math.abs(direction.z) < 1e-5) {
            n1 = new Vec3(1, 0, 0);
            n2 = new Vec3(0, 0, 1);
        } else {
            n1 = new Vec3(-direction.z, 0, direction.x).normalize();
            n2 = direction.cross(n1);
        }

        float angle = Mth.TWO_PI * random.nextFloat();
        return direction.scale(Mth.cos(coneAngle))
                .add(n1.scale(Mth.sin(coneAngle) * Mth.sin(angle)))
                .add(n2.scale(Mth.sin(coneAngle) * Mth.cos(angle)));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {

    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return null;
    }
}
