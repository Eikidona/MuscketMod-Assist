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

import javax.annotation.Nullable;

public class GunItem extends Item {
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
        return creative || true; // 允许在生存模式下使用
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
            if (stack.getOrCreateTag().getBoolean("JustReloaded")) {
                // 防止装填后立即开火
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

            return InteractionResultHolder.consume(stack);
        }
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int ticksLeft) {
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide) {
            if (entity instanceof Player player) {
                consumeAmmo(player, stack);
            }
            setLoaded(stack, true);
            stack.getOrCreateTag().putBoolean("JustReloaded", true);
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), Sounds.MUSKET_READY, entity.getSoundSource(), 0.8f, 1);
        }
        return stack;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return getReloadDuration();
    }

    public int getReloadDuration() {
        return 18;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    public static boolean isLoaded(ItemStack stack) {
        return stack.getOrCreateTag().getByte("loaded") != 0;
    }

    public static void setLoaded(ItemStack stack, boolean loaded) {
        if (loaded) {
            stack.getOrCreateTag().putByte("loaded", (byte) 1);
        } else {
            stack.getOrCreateTag().remove("loaded");
        }
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

    public static boolean isAmmo(ItemStack stack) {
        return stack.getItem() == Items.CARTRIDGE;
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

    public static Vec3 addSpread(Vec3 direction, RandomSource random, float spreadStdDev) {
        float gaussian = (float) random.nextGaussian();
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
    public int getEnchantmentValue() {
        return 14;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity entity) {
        stack.hurtAndBreak(hitDurabilityDamage(), entity, (ent) -> {
            ent.broadcastBreakEvent(EquipmentSlot.MAINHAND);
        });
        return false;
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
}
