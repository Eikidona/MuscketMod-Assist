package ewewukek.musketmod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class BulletEntity extends AbstractHurtingProjectile {
    // workaround for ClientboundAddEntityPacket.LIMIT
    public static final EntityDataAccessor<Float> INITIAL_SPEED = SynchedEntityData.defineId(BulletEntity.class, EntityDataSerializers.FLOAT);
    public static final EntityDataAccessor<Float> DROP_REDUCTION = SynchedEntityData.defineId(BulletEntity.class, EntityDataSerializers.FLOAT);
    public static final EntityDataAccessor<Byte> PELLET_COUNT = SynchedEntityData.defineId(BulletEntity.class, EntityDataSerializers.BYTE);

    public static final TagKey<Block> DESTROYED_BY_BULLETS = TagKey.create(Registries.BLOCK, MusketMod.resource("destroyed_by_bullets"));
    public static final TagKey<Block> DROPPED_BY_BULLETS = TagKey.create(Registries.BLOCK, MusketMod.resource("dropped_by_bullets"));
    public static final TagKey<EntityType<?>> HEADSHOTABLE = TagKey.create(Registries.ENTITY_TYPE, MusketMod.resource("headshotable"));
    public static final ResourceKey<DamageType> BULLET_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, MusketMod.resource("bullet"));
    public static EntityType<BulletEntity> ENTITY_TYPE;
    public static final short LIFETIME = 100;
    public static final int HIT_PARTICLE_COUNT = 5;
    public static final float IGNITE_SECONDS = 5.0f;

    public float damage;
    public boolean touchedWater;
    public boolean headshot;
    public float distanceTravelled;
    public short tickCounter;

    private boolean packetSpeedReceived;

    public BulletEntity(EntityType<BulletEntity>entityType, Level level) {
        super(entityType, level);
    }

    public BulletEntity(Level level) {
        this(ENTITY_TYPE, level);
    }

    public boolean isFirstTick() {
        return tickCounter == 0;
    }

    public void discardOnNextTick() {
        tickCounter = LIFETIME;
    }

    public float calculateEnergyFraction() {
        double maxEnergy = Math.pow(entityData.get(INITIAL_SPEED), 2);
        double energy = getDeltaMovement().lengthSqr();
        if (maxEnergy < energy) maxEnergy = energy; // empty entityData
        return (float)(energy / maxEnergy);
    }

    public int pelletCount() {
        int count = entityData.get(PELLET_COUNT);
        return count > 1 ? count : 1;
    }

    public boolean soundEffectRoll() {
        int count = pelletCount();
        return count == 1 ? true : random.nextFloat() < 1.5f / count;
    }

    public DamageSource getDamageSource() {
        Entity attacker = getOwner() != null ? getOwner() : this;
        return level().damageSources().source(BULLET_DAMAGE, this, attacker);
    }

    public void setVelocity(float bulletSpeed, Vec3 direction) {
        float tickSpeed = bulletSpeed / 20; // to blocks per tick
        setInitialSpeed(tickSpeed);
        setDeltaMovement(direction.scale(tickSpeed));
    }

    @Override
    public boolean displayFireAnimation() {
        return false;
    }

    @Override
    public void tick() {
        if (++tickCounter > LIFETIME || distanceTravelled > Config.bulletMaxDistance) {
            discard();
            return;
        }

        Level level = level();
        Vec3 currentPos = position();
        Vec3 velocity = getDeltaMovement();
        Vec3 nextPos = currentPos.add(velocity);

        // 水体检测
        wasTouchingWater = updateFluidHeightAndDoFluidPushing(FluidTags.WATER, 0);
        if (wasTouchingWater && !touchedWater) {
            handleWaterEntry(currentPos);
        }

        if (wasTouchingWater) {
            touchedWater = true;
        }

        List<EntityHitResult> entityHits = getEntityHitResults(currentPos, nextPos);
        for (EntityHitResult entityHit : entityHits) {
            handleEntityHit(entityHit);
        }

        BlockHitResult blockHit = level.clip(new ClipContext(currentPos, nextPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (blockHit.getType() != HitResult.Type.MISS) {
            BlockPos hitPos = blockHit.getBlockPos();
            BlockState hitState = level().getBlockState(hitPos);
            if (!hitState.isAir()) {
                handleBlockHit(blockHit);
                nextPos = blockHit.getLocation();
                // 移除这里的 discard() 调用
                return;
            }
        }

        distanceTravelled += currentPos.distanceTo(nextPos);
        setPos(nextPos);
        setDeltaMovement(velocity);

        if (level.isClientSide) {
            createFlightEffects(currentPos, nextPos, velocity);
        }

        checkInsideBlocks();
    }

    private List<EntityHitResult> getEntityHitResults(Vec3 start, Vec3 end) {
        List<EntityHitResult> results = new ArrayList<>();
        AABB aabb = new AABB(start, end).inflate(0.5);
        List<Entity> entities = level().getEntities(this, aabb, this::canHitEntity);

        for (Entity entity : entities) {
            AABB entityAabb = entity.getBoundingBox();
            Optional<Vec3> hit = entityAabb.clip(start, end);
            if (hit.isPresent()) {
                results.add(new EntityHitResult(entity, hit.get()));
            }
        }

        return results;
    }

    private void handleEntityHit(EntityHitResult entityHit) {
        if (!level().isClientSide) {
            Entity target = entityHit.getEntity();
            if (touchedWater) {
                damage *= calculateEnergyFraction();
            }
            onHitEntity(entityHit);
            // 注意：我们不再在这里调用 discard()，允许子弹继续飞行
        } else {
            createHitEffects(entityHit);
        }
    }

    private void handleBlockDestruction(BlockHitResult blockHit) {
        float destroyProbability = calculateEnergyFraction();
        if (pelletCount() > 1) destroyProbability = 1.5f * destroyProbability / pelletCount();

        if (random.nextFloat() < destroyProbability) {
            BlockPos blockPos = blockHit.getBlockPos();
            BlockState blockState = level().getBlockState(blockPos);

            if (blockState.is(Blocks.TNT)) {
                TntBlock.explode(level(), blockPos);
                level().removeBlock(blockPos, false);
            } else if (blockState.is(DESTROYED_BY_BULLETS)) {
                if (level().removeBlock(blockPos, false)) {
                    blockState.getBlock().destroy(level(), blockPos, blockState);
                }
            } else if (blockState.is(DROPPED_BY_BULLETS)) {
                if (level().removeBlock(blockPos, false)) {
                    Block.dropResources(blockState, level(), blockPos);
                }
            }
        }
    }

    private void handleWaterEntry(Vec3 pos) {
        if (level().isClientSide) {
            createHitParticles(ParticleTypes.SPLASH, pos, new Vec3(0.0, 0.02, 0.0));
            playHitSound(Sounds.BULLET_WATER_HIT, pos);
        }
    }

    private void createFlightEffects(Vec3 from, Vec3 to, Vec3 velocity) {
        if (!wasTouchingWater && soundEffectRoll()) {
            createFlyBySound(from, to, velocity);
        }
        if (wasTouchingWater) {
            createWaterBubbles(to, velocity);
        }
    }

    private void handleBlockHit(BlockHitResult blockHit) {
        if (!level().isClientSide) {
            if (touchedWater) {
                damage *= calculateEnergyFraction();
            }
            onHit(blockHit);
            handleBlockDestruction(blockHit);
            // 在这里添加 discard() 调用
            discard();
        } else {
            createHitEffects(blockHit);
        }
    }

    private void createFlyBySound(Vec3 from, Vec3 to, Vec3 velocity) {
        double length = velocity.length();
        Vec3 dir = velocity.normalize();
        float volume = calculateEnergyFraction();

        AABB aabbSelection = new AABB(from, to).inflate(8.0);
        Predicate<Entity> predicate = entity -> (entity instanceof Player) && !entity.equals(getOwner());
        for (Entity entity : level().getEntities(this, aabbSelection, predicate)) {
            Vec3 entityPos = new Vec3(entity.getX(), entity.getEyeY(), entity.getZ());
            Vec3 bulletToEntity = entityPos.subtract(from);
            double proj = bulletToEntity.dot(dir);

            if (proj > 0 && proj < length) {
                Vec3 closestPoint = from.add(dir.scale(proj));
                double distanceToPath = entityPos.subtract(closestPoint).length();

                if (distanceToPath < 2.0) {  // 只在子弹路径附近播放音效
                    level().playLocalSound(
                            closestPoint.x, closestPoint.y, closestPoint.z,
                            Sounds.BULLET_FLY_BY, getSoundSource(),
                            volume, 0.92f + 0.16f * random.nextFloat(), false
                    );
                }
            }
        }
    }

    private void createWaterBubbles(Vec3 waterPos, Vec3 velocity) {
        double length = velocity.length();
        Vec3 step = velocity.scale(1 / length);
        Vec3 pos = waterPos.add(step.scale(0.5));
        float prob = 1.5f * calculateEnergyFraction() / pelletCount();
        while (length > 0.5) {
            pos = pos.add(step);
            length -= 1;
            if (random.nextFloat() < prob) {
                level().addParticle(ParticleTypes.BUBBLE, pos.x, pos.y, pos.z, 0, 0, 0);
            }
        }
    }

    private void createHitEffects(HitResult hitResult) {
        if (hitResult.getType() == HitResult.Type.BLOCK) {
            Vec3 pos = hitResult.getLocation();
            BlockState blockState = level().getBlockState(((BlockHitResult)hitResult).getBlockPos());
            BlockParticleOption particle = new BlockParticleOption(ParticleTypes.BLOCK, blockState);
            createHitParticles(particle, pos, Vec3.ZERO);
            if (isOnFire()) {
                level().addParticle(ParticleTypes.DRIPPING_LAVA, pos.x, pos.y, pos.z, 0, 0.01, 0);
            }
            playHitSound(blockState.getSoundType().getBreakSound(), pos);
        }
    }

    @Override
    public void onHitEntity(EntityHitResult hitResult) {
        if (calculateEnergyFraction() < 0.05) return;

        float damageMult = 1.0f;
        Entity target = hitResult.getEntity();
        Entity shooter = getOwner();
        if (shooter instanceof Player playerShooter) {
            if (target instanceof Player playerTarget) {
                damageMult = Config.pvpDamageMultiplier;
                if (!playerShooter.canHarmPlayer(playerTarget)) {
                    return;
                }
            }
        } else {
            damageMult = Config.mobDamageMultiplier;
        }
        DamageSource source = getDamageSource();

        if (pelletCount() == 1) {
            if (headshot) {
                damageMult *= Config.headshotDamageMultiplier;
            }
            target.invulnerableTime = 0;
            target.hurt(source, damage * damageMult);

        } else {
            damageMult /= pelletCount();
            DeferredDamage.hurt(target, source, damage * damageMult, 0.0f);
        }
    }

    public boolean checkHeadshot(Entity entity, AABB aabb, Vec3 start, Vec3 end) {
        if (pelletCount() > 1 || !entity.getType().is(HEADSHOTABLE)) {
            return false;
        }
        double width = (aabb.maxX - aabb.minX + aabb.maxZ - aabb.minZ) / 2;
        aabb = aabb.setMinY(aabb.maxY - width);
        return aabb.clip(start, end).isPresent();
    }

    public EntityHitResult findHitEntity(Vec3 start, Vec3 end) {
        Entity resultEntity = null;
        Vec3 resultPos = null;
        double resultDist = 0;

        AABB aabbSelection = getBoundingBox().expandTowards(getDeltaMovement()).inflate(0.5);
        for (Entity entity : level().getEntities(this, aabbSelection, this::canHitEntity)) {
            AABB aabb = entity.getBoundingBox();
            Optional<Vec3> clipResult = aabb.clip(start, end);

            if (!clipResult.isPresent()) {
                aabb = aabb.move( // previous tick position
                        entity.xOld - entity.getX(),
                        entity.yOld - entity.getY(),
                        entity.zOld - entity.getZ()
                );
                clipResult = aabb.clip(start, end);
            }
            if (clipResult.isPresent()) {
                double dist = start.distanceToSqr(clipResult.get());
                if (dist < resultDist || resultEntity == null) {
                    resultEntity = entity;
                    resultPos = clipResult.get();
                    resultDist = dist;
                    headshot = checkHeadshot(entity, aabb, start, end);
                }
            }
        }

        return resultEntity != null ? new EntityHitResult(resultEntity, resultPos) : null;
    }

    @Override
    public boolean canHitEntity(Entity entity) {
        if (entity instanceof BulletEntity || entity == getOwner()) return false;
        if (super.canHitEntity(entity)) return true;
        // entity may become dead on client side before
        // this check occurs due to packet order
        Level level = level();
        return level.isClientSide && entity instanceof LivingEntity;
    }

    public void createHitParticles(ParticleOptions particle, Vec3 position, Vec3 velocity) {
        float amount = HIT_PARTICLE_COUNT * calculateEnergyFraction() / pelletCount();
        int count = (int)amount + (random.nextFloat() < amount % 1 ? 1 : 0);

        for (int i = 0; i < count; i++) {
            level().addParticle(
                    particle,
                    position.x, position.y, position.z,
                    velocity.x + 0.01 * random.nextGaussian(),
                    velocity.y + 0.01 * random.nextGaussian(),
                    velocity.z + 0.01 * random.nextGaussian()
            );
        }
    }

    public void playHitSound(SoundEvent sound, Vec3 position) {
        if (soundEffectRoll()) {
            level().playLocalSound(
                    position.x, position.y, position.z,
                    sound, getSoundSource(),
                    calculateEnergyFraction(),
                    0.95f + 0.1f * random.nextFloat(), false
            );
        }
    }

    public void setInitialSpeed(float speed) {
        entityData.set(INITIAL_SPEED, speed);
    }

    public void setDropReduction(float reduction) {
        entityData.set(DROP_REDUCTION, reduction);
    }

    public void setPelletCount(int count) {
        entityData.set(PELLET_COUNT, (byte)count);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(INITIAL_SPEED, 0.0f);
        entityData.define(DROP_REDUCTION, 0.0f);
        entityData.define(PELLET_COUNT, (byte)1);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        damage = compound.getFloat("damage");
        distanceTravelled = compound.getFloat("distanceTravelled");
        entityData.set(DROP_REDUCTION, compound.getFloat("dropReduction"));
        entityData.set(PELLET_COUNT, compound.getByte("pelletCount"));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putFloat("damage", damage);
        compound.putFloat("distanceTravelled", distanceTravelled);
        compound.putFloat("dropReduction", entityData.get(DROP_REDUCTION));
        compound.putByte("pelletCount", entityData.get(PELLET_COUNT));
    }

    // workaround for ClientboundAddEntityPacket.LIMIT
    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        Entity owner = getOwner();
        return new ClientboundAddEntityPacket(
                getId(), getUUID(),
                getX(), getY(), getZ(),
                getXRot(), getYRot(),
                getType(), owner != null ? owner.getId() : 0,
                getDeltaMovement().scale(3.9 / entityData.get(INITIAL_SPEED)),
                getYHeadRot()
        );
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        Vec3 packetVelocity = new Vec3(packet.getXa(), packet.getYa(), packet.getZa());
        setDeltaMovement(packetVelocity.scale(1.0 / 3.9));
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (INITIAL_SPEED.equals(accessor) && level().isClientSide && !packetSpeedReceived) {
            setDeltaMovement(getDeltaMovement().scale(entityData.get(INITIAL_SPEED)));
            packetSpeedReceived = true;
        }
    }
}