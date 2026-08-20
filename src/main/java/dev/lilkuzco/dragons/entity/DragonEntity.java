package dev.lilkuzco.dragons.entity;

import dev.lilkuzco.dragons.DragonsItems;
import dev.lilkuzco.dragons.entity.ai.DragonBondGoal;
import dev.lilkuzco.dragons.entity.ai.DragonFlightGoal;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.boss.enderdragon.DragonFlightHistory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A dragon.
 *
 * <h2>Not the Ender Dragon</h2>
 * It wears the Ender Dragon's <em>model</em> and nothing else. The boss flies with
 * {@code noPhysics} set and calls {@code EnderDragon#checkWalls}, which deletes every
 * block its hitbox touches; that is why releasing one into the Overworld carves a trench
 * through whatever it crosses. This mob does neither. It is an ordinary
 * {@link TamableAnimal} that collides with the world like any other creature and steers
 * with a {@link FlyingPathNavigation}, whose {@code FlyNodeEvaluator} treats solid blocks
 * as impassable — so a village, a tree or a mountain in the way is something it paths
 * <em>around</em>. There is deliberately no block-breaking code anywhere in this class
 * and {@code noPhysics} is never set. {@link DragonFlightGoal} is the other half: cruising
 * altitude is chosen above the local terrain, so the routine case is flying over an
 * obstacle rather than negotiating one.
 *
 * <h2>Life cycle</h2>
 * Dragons do not spawn in the wild. One is hatched from a found egg beside a campfire
 * (see {@code DragonEggBlock}) and arrives as a baby at a quarter of adult size, bonded
 * to whoever set the egg down. It stays near that player for an hour of world time; raw
 * chicken offered during that window is a {@value #TAME_CHANCE} chance of taming, with
 * the wolf's own hearts-or-smoke feedback. If the hour runs out untamed it leaves and
 * grows up wild — still tameable, but now you have to find it and catch it on the ground.
 *
 * <h2>Riding and parking</h2>
 * Its owner rides a grown, tamed dragon once it wears a {@link DragonsItems#DRAGON_SADDLE},
 * which occupies the vanilla {@link EquipmentSlot#SADDLE} slot and so saves, syncs and
 * drops with no bespoke plumbing. A lead parks it: a leashed dragon will not start a
 * flight, so tying one to a fence leaves it where you tied it, the way a happy ghast
 * stays put.
 */
public class DragonEntity extends TamableAnimal {
	/** Per raw chicken. Deliberately low: a dragon is meant to cost a stack of chickens. */
	public static final float TAME_CHANCE = 0.05F;
	/** How long a freshly hatched dragon stays with the player who hatched it: one hour. */
	public static final long BOND_TICKS = 72000L;
	/** A hatchling is a quarter of a grown dragon, in hitbox and on screen alike. */
	public static final float BABY_SCALE = 0.25F;
	/** Ticks a dragon stays perched after a flight before it will take off again. */
	private static final int REST_MIN = 200;
	private static final int REST_EXTRA = 500;
	/** How far away a held raw chicken keeps a dragon on the ground. */
	public static final double BAITED_RANGE = 12.0;

	private static final EntityDataAccessor<String> DATA_VARIANT =
			SynchedEntityData.defineId(DragonEntity.class, EntityDataSerializers.STRING);

	/**
	 * Wing/neck/tail animation state, mirrored from the Ender Dragon.
	 *
	 * <p>{@code EnderDragonModel} poses the neck and tail purely from where the dragon
	 * <em>was</em> over the last 64 ticks, so the history has to be recorded on both
	 * sides — the client cannot ask the server for it per frame. Both fields are written
	 * in {@link #aiStep()}, which runs client-side too.
	 */
	public final DragonFlightHistory flightHistory = new DragonFlightHistory();
	public float flapTime;
	public float oFlapTime;

	private int restTicks;
	private @Nullable UUID bondedTo;
	/**
	 * Absolute game time the hatchling bond lapses — an epoch, not a countdown.
	 *
	 * <p>A ticking timer would only run while the chunk was loaded, so an hour "with the
	 * player" would silently stretch to however long the player happened to stand there.
	 * Storing the deadline means the answer is the same whether the dragon was ticked or
	 * not, which is the whole point of the house rule about unattended simulation.
	 */
	private long bondExpiresAt;
	private boolean leftForGood;

	public DragonEntity(EntityType<? extends DragonEntity> type, Level level) {
		super(type, level);
		// hoversInPlace = false: with no flight target the move control hands gravity
		// back, so a dragon that has finished a flight settles and perches. That is what
		// makes "walk up to it while it is landed" a state the player can actually find.
		this.moveControl = new FlyingMoveControl<>(this, 20, false);
		this.setPathfindingMalus(PathType.FIRE, -1.0F);
		this.setPathfindingMalus(PathType.FIRE_IN_NEIGHBOR, -1.0F);
		this.setPathfindingMalus(PathType.DAMAGING, -1.0F);
		this.setPathfindingMalus(PathType.LAVA, -1.0F);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Animal.createAnimalAttributes()
				.add(Attributes.MAX_HEALTH, 60.0)
				.add(Attributes.MOVEMENT_SPEED, 0.22)
				// Ridden top speed is not what reading this number suggests, and the
				// arithmetic is worth writing down because it bit twice.
				//
				// getRiddenInput returns a unit-ish direction scaled by 3.9*F, and
				// travelFlying then accelerates by (that, capped at length 1 by
				// getInputVector) * (5/3*F) per tick against 0.91 drag. So:
				//
				//   accel   = min(1, 3.9*F) * (5/3)*F        terminal = accel / 0.09
				//
				// F appears TWICE below the cap and once above it, which is why the two
				// regimes behave completely differently:
				//   F = 0.6  -> 3.9F = 2.34, capped: accel 1.0    -> 11 blocks per TICK
				//   F = 0.08 -> 3.9F = 0.31, uncapped: accel 0.042 -> 9.1 blocks/s
				// The first outran chunk loading; the second was measured at 9.1 b/s by
				// the render battery's flight test and felt like a barge.
				//
				// 0.13 measures ~24 blocks/s: a shade over elytra glide, comfortably under
				// anything that outruns the chunk loader. The battery measures it on every
				// run rather than trusting this comment.
				.add(Attributes.FLYING_SPEED, 0.13)
				.add(Attributes.ATTACK_DAMAGE, 8.0)
				.add(Attributes.FOLLOW_RANGE, 48.0)
				.add(Attributes.TEMPT_RANGE, 16.0);
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
		navigation.setCanOpenDoors(false);
		navigation.setCanFloat(true);
		// a dragon-sized detour around a castle is a long path; the default budget gives
		// up early and the mob then drifts into the wall it was supposed to route around
		navigation.setRequiredPathLength(96.0F);
		return navigation;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new SitWhenOrderedToGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
		// raw chicken is both the taming item and the lure that keeps one landed
		this.goalSelector.addGoal(3, new TemptGoal(this, 1.0, stack -> stack.is(Items.CHICKEN), false, 6.0));
		this.goalSelector.addGoal(4, new DragonBondGoal(this, 1.1));
		this.goalSelector.addGoal(5, new FollowOwnerGoal(this, 1.0, 10.0F, 4.0F));
		this.goalSelector.addGoal(6, new DragonFlightGoal(this, 1.0));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

		// no unprovoked hostility: a dragon is scenery until something hits it
		this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
		this.targetSelector.addGoal(3, new HurtByTargetGoal(this));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(DATA_VARIANT, DragonVariant.CRIMSON.id());
	}

	// ------------------------------------------------------------------ variant

	public DragonVariant getVariant() {
		return DragonVariant.byName(this.entityData.get(DATA_VARIANT));
	}

	public void setVariant(DragonVariant variant) {
		this.entityData.set(DATA_VARIANT, variant.id());
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
			EntitySpawnReason reason, @Nullable SpawnGroupData groupData) {
		// a hatching egg sets the colour itself, so only unspecified spawns roll for one
		if (reason != EntitySpawnReason.BREEDING) {
			this.setVariant(DragonVariant.random(level.getRandom()));
		}
		return super.finalizeSpawn(level, difficulty, reason, groupData);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putString("dragons_variant", this.getVariant().id());
		output.putInt("dragons_rest", this.restTicks);
		output.putLong("dragons_bond_expires", this.bondExpiresAt);
		output.putBoolean("dragons_left", this.leftForGood);
		if (this.bondedTo != null) {
			output.store("dragons_bonded_to", net.minecraft.core.UUIDUtil.CODEC, this.bondedTo);
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.setVariant(DragonVariant.byName(
				input.getStringOr("dragons_variant", DragonVariant.CRIMSON.id())));
		this.restTicks = input.getIntOr("dragons_rest", 0);
		this.bondExpiresAt = input.getLongOr("dragons_bond_expires", 0L);
		this.leftForGood = input.getBooleanOr("dragons_left", false);
		this.bondedTo = input.read("dragons_bonded_to", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
	}

	// ------------------------------------------------------------------ the hatchling bond

	/** Called by the egg the moment it opens. */
	public void bondTo(Player player) {
		this.bondedTo = player.getUUID();
		this.bondExpiresAt = this.level().getGameTime() + BOND_TICKS;
		this.leftForGood = false;
	}

	public boolean isBonded() {
		return !this.isTame() && !this.leftForGood
				&& this.bondedTo != null && this.level().getGameTime() < this.bondExpiresAt;
	}

	public @Nullable Player getBondedPlayer() {
		return this.bondedTo == null ? null : this.level().getPlayerByUUID(this.bondedTo);
	}

	/** Ticks left in the courting window, for the tooltip-ish command readout. */
	public long bondTicksLeft() {
		return this.isBonded() ? this.bondExpiresAt - this.level().getGameTime() : 0L;
	}

	public boolean hasLeftForGood() {
		return this.leftForGood;
	}

	/**
	 * The hour lapsed and nobody tamed it, so it goes and grows up wild.
	 *
	 * <p>Not a despawn: the dragon is still there, still tameable the hard way. It simply
	 * stops following, unfreezes its age and takes off with a long flight.
	 */
	private void leaveForGood() {
		this.leftForGood = true;
		this.bondedTo = null;
		this.bondExpiresAt = 0L;
		this.restTicks = 0;
		this.navigation.stop();
		if (this.level() instanceof ServerLevel level) {
			level.playSound(null, this, SoundEvents.ENDER_DRAGON_GROWL,
					net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.4F);
		}
	}

	// ------------------------------------------------------------------ flight

	/** True while airborne. Perched dragons are the ones a player can walk up to. */
	public boolean isFlying() {
		return !this.onGround();
	}

	public boolean isResting() {
		return this.restTicks > 0;
	}

	public void restFor(int ticks) {
		this.restTicks = ticks;
	}

	public void restAfterFlight() {
		this.restFor(REST_MIN + this.random.nextInt(REST_EXTRA));
	}

	/**
	 * A dragon will not take off while somebody nearby is offering raw chicken.
	 *
	 * <p>Without this the taming loop is a coin flip against the flight goal: you feed
	 * one chicken, the dragon leaves, and the 5% roll never gets a second attempt.
	 */
	public boolean isBaited() {
		if (this.isTame()) {
			return false;
		}
		Player player = this.level().getNearestPlayer(this, BAITED_RANGE);
		return player != null
				&& (player.getMainHandItem().is(Items.CHICKEN) || player.getOffhandItem().is(Items.CHICKEN));
	}

	@Override
	public void aiStep() {
		super.aiStep();

		// Recorded on BOTH sides: the model reads 64 ticks of history per frame, and the
		// client has no other source for it.
		this.oFlapTime = this.flapTime;
		Vec3 movement = this.getDeltaMovement();
		float flapSpeed = 0.2F / ((float) movement.horizontalDistance() * 10.0F + 1.0F);
		flapSpeed *= (float) Math.pow(2.0, movement.y);
		this.flapTime += this.isFlying() ? flapSpeed : flapSpeed * 0.35F;
		this.flightHistory.record(this.getY(), this.getYRot());
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.restTicks > 0) {
			this.restTicks--;
		}
		if (this.isBonded()) {
			// hold it at hatchling size for the whole courting window; it grows up only
			// once it is somebody's dragon, or once it has given up on becoming one
			if (this.isBaby()) {
				this.setAge(AgeableMob.BABY_START_AGE);
			}
		} else if (this.bondedTo != null && !this.isTame() && !this.leftForGood) {
			this.leaveForGood();
		}
	}

	@Override
	public void travel(Vec3 input) {
		if (this.getControllingPassenger() instanceof Player) {
			// under a rider the dragon is a flying vehicle: no gravity term at all, the
			// same shape happy ghasts use, so it holds altitude instead of sinking
			float speed = (float) this.getAttributeValue(Attributes.FLYING_SPEED) * 5.0F / 3.0F;
			this.travelFlying(input, speed, speed, speed);
		} else {
			// unridden it falls normally — which is how a dragon that has finished a
			// flight comes down and perches
			super.travel(input);
		}
	}

	@Override
	protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
		// a creature that lands from cruising altitude every few minutes cannot take
		// fall damage for doing so
	}

	@Override
	public boolean onClimbable() {
		return false;
	}

	@Override
	protected boolean omnidirectionalAirMover() {
		return true;
	}

	/**
	 * Cruising speed under its own power.
	 *
	 * <p>Vanilla hands every non-player mob a flat {@code 0.02} here regardless of its
	 * FLYING_SPEED attribute, which is tuned for a parrot and leaves something the size of
	 * a dragon drifting at about four blocks a second. Doubling it is still an unhurried
	 * cruise and stays well inside what the flying navigation can steer.
	 *
	 * <p>The ridden case is deliberately left to vanilla: {@link #travel} handles a rider
	 * itself and never reaches this method.
	 */
	@Override
	protected float getFlyingSpeed() {
		return this.getControllingPassenger() instanceof Player ? super.getFlyingSpeed() : 0.04F;
	}

	@Override
	public float getWalkTargetValue(BlockPos pos, LevelReader level) {
		return level.isEmptyBlock(pos) ? 10.0F : 0.0F;
	}

	// ------------------------------------------------------------------ size

	@Override
	public float getAgeScale() {
		// drives the hitbox, the rider attachment and (via the render state) the model
		return this.isBaby() ? BABY_SCALE : 1.0F;
	}

	@Override
	protected boolean canBeABaby() {
		return true;
	}

	@Override
	public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		// dragons come from eggs, not from each other
		return null;
	}

	@Override
	public boolean canFallInLove() {
		// raw chicken is the taming item; it must never double as a breeding trigger
		return false;
	}

	// ------------------------------------------------------------------ interaction

	@Override
	public boolean isFood(ItemStack stack) {
		return stack.is(Items.CHICKEN);
	}

	@Override
	public boolean canUseSlot(EquipmentSlot slot) {
		// only a grown, tamed dragon accepts a saddle, and the check lives here rather
		// than in mobInteract so the equippable component, dispensers and /item agree
		return slot == EquipmentSlot.SADDLE
				? this.isAlive() && this.isTame() && !this.isBaby()
				: super.canUseSlot(slot);
	}

	public boolean isSaddled() {
		return !this.getItemBySlot(EquipmentSlot.SADDLE).isEmpty();
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);

		if (!this.isTame()) {
			if (stack.is(Items.CHICKEN)) {
				if (this.isFlying()) {
					// airborne dragons are out of reach in every sense; pass rather than
					// silently eating the chicken
					return InteractionResult.PASS;
				}
				if (this.level().isClientSide()) {
					return InteractionResult.SUCCESS;
				}
				stack.consume(1, player);
				this.playEatingSound();
				this.tryToTame(player);
				return InteractionResult.SUCCESS_SERVER;
			}
			return super.mobInteract(player, hand);
		}

		// tamed: raw chicken heals
		if (stack.is(Items.CHICKEN) && this.getHealth() < this.getMaxHealth()) {
			this.feed(player, hand, stack, 4.0F, 8.0F);
			return InteractionResult.SUCCESS;
		}

		// tamed: the saddle equips through its own EQUIPPABLE component
		if (!stack.isEmpty()) {
			InteractionResult result = stack.interactLivingEntity(player, this, hand);
			if (result.consumesAction()) {
				return result;
			}
		}

		if (this.isOwnedBy(player)) {
			if (player.isSecondaryUseActive()) {
				this.setOrderedToSit(!this.isOrderedToSit());
				this.jumping = false;
				this.navigation.stop();
				this.setTarget(null);
				return InteractionResult.SUCCESS.withoutItem();
			}
			if (this.isSaddled()) {
				this.doPlayerRide(player);
				return InteractionResult.SUCCESS;
			}
		}

		return super.mobInteract(player, hand);
	}

	/**
	 * One {@value #TAME_CHANCE} roll, reported with the wolf's own taming particles.
	 *
	 * <p>Entity events 7 and 6 are {@code EntityEvent.TAMING_SUCCEEDED} and
	 * {@code TAMING_FAILED}; {@link TamableAnimal#handleEntityEvent} turns them into the
	 * heart and smoke bursts every player already associates with feeding a wolf bones.
	 */
	private void tryToTame(Player player) {
		if (this.random.nextFloat() < TAME_CHANCE) {
			this.tame(player);
			this.navigation.stop();
			this.setTarget(null);
			this.setOrderedToSit(true);
			this.bondedTo = null;
			this.bondExpiresAt = 0L;
			this.restAfterFlight();
			this.level().broadcastEntityEvent(this, (byte) 7);
		} else {
			// a fed dragon settles for a while, so the next chicken has somewhere to go
			this.restFor(Math.max(this.restTicks, REST_MIN));
			this.level().broadcastEntityEvent(this, (byte) 6);
		}
	}

	private void doPlayerRide(Player player) {
		if (!this.level().isClientSide()) {
			player.setYRot(this.getYRot());
			player.setXRot(this.getXRot());
			player.startRiding(this);
		}
	}

	// ------------------------------------------------------------------ riding

	@Override
	public @Nullable LivingEntity getControllingPassenger() {
		return this.isSaddled() && this.getFirstPassenger() instanceof Player player && this.isOwnedBy(player)
				? player
				: super.getControllingPassenger();
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return this.getPassengers().isEmpty() && !this.isBaby();
	}

	@Override
	public boolean isFlyingVehicle() {
		return !this.isBaby();
	}

	@Override
	protected Vec3 getRiddenInput(Player controller, Vec3 selfInput) {
		// happy-ghast controls: look where you want to go, jump to climb. Descending is
		// "point the nose down and hold forward", which is also how you land.
		float strafe = controller.xxa;
		float forward = 0.0F;
		float up = 0.0F;
		if (controller.zza != 0.0F) {
			float forwardLook = Mth.cos(controller.getXRot() * (float) (Math.PI / 180.0));
			float upLook = -Mth.sin(controller.getXRot() * (float) (Math.PI / 180.0));
			if (controller.zza < 0.0F) {
				forwardLook *= -0.5F;
				upLook *= -0.5F;
			}
			up = upLook;
			forward = forwardLook;
		}
		if (controller.isJumping()) {
			up += 0.5F;
		}
		return new Vec3(strafe, up, forward).scale(3.9F * this.getAttributeValue(Attributes.FLYING_SPEED));
	}

	@Override
	protected void tickRidden(Player controller, Vec3 riddenInput) {
		super.tickRidden(controller, riddenInput);
		this.setRot(controller.getYRot(), controller.getXRot() * 0.5F);
		this.yRotO = this.yBodyRot = this.yHeadRot = this.getYRot();
		if (this.isLocalInstanceAuthoritative()) {
			// a ridden dragon is under the rider's hand, not the flight goal's
			this.navigation.stop();
		}
	}

	@Override
	protected float getRiddenSpeed(Player controller) {
		return (float) this.getAttributeValue(Attributes.FLYING_SPEED);
	}

	@Override
	public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
		return new Vec3(this.getX(), this.getBoundingBox().maxY, this.getZ());
	}

	// ------------------------------------------------------------------ leashing ("parking")

	@Override
	public boolean canBeLeashed() {
		return true;
	}

	@Override
	public double leashSnapDistance() {
		// a flying mob on a short leash snaps it the first time it climbs; give it the
		// same slack a happy ghast gets
		return 16.0;
	}

	@Override
	public void onElasticLeashPull() {
		super.onElasticLeashPull();
		this.getMoveControl().setWait();
	}

	// ------------------------------------------------------------------ sound & polish

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return SoundEvents.ENDER_DRAGON_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENDER_DRAGON_HURT;
	}

	@Override
	protected @Nullable SoundEvent getDeathSound() {
		return SoundEvents.ENDER_DRAGON_DEATH;
	}

	@Override
	protected float getSoundVolume() {
		return this.isBaby() ? 0.35F : 0.7F;
	}

	@Override
	public float getVoicePitch() {
		return this.isBaby() ? 1.6F : 0.9F;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.ENDER_DRAGON_FLAP, 0.15F, this.isBaby() ? 1.8F : 1.4F);
	}

	@Override
	public int getMaxHeadYRot() {
		return 30;
	}
}
