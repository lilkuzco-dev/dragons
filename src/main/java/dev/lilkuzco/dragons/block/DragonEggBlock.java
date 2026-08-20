package dev.lilkuzco.dragons.block;

import com.mojang.serialization.MapCodec;
import dev.lilkuzco.dragons.entity.DragonEntity;
import dev.lilkuzco.dragons.entity.DragonVariant;
import dev.lilkuzco.dragons.entity.DragonsEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A dragon egg, incubating beside a campfire.
 *
 * <p>Modelled on vanilla's dried ghast — set it down, give it the one thing it needs, come
 * back later to a hatchling — with one deliberate difference in how the waiting is
 * counted.
 *
 * <h2>The clock is an epoch, not a countdown</h2>
 * The dried ghast advances one hydration step per scheduled block tick, and scheduled
 * ticks do not run in unloaded chunks. A ten-to-twenty-five minute incubation counted that
 * way would only advance while somebody stood next to it, so walking away would silently
 * stop the egg — no error, no log line, just an egg that never hatches. Instead the
 * hatching <em>deadline</em> is recorded once, as an absolute game time, in
 * {@link DragonEggBlockEntity}. Whether the chunk ticked for the whole wait, part of it or
 * none of it, the egg is ready at the same moment; the block tick is only there to notice.
 *
 * <h2>What the campfire is for</h2>
 * Incubation needs a lit campfire within {@value #CAMPFIRE_RADIUS} blocks, checked when
 * the egg is placed (to start the clock) and again when the deadline arrives (to open it).
 * Letting the fire go out in between costs nothing but does not finish the job — the egg
 * simply waits, re-checking, until there is a fire again.
 */
public class DragonEggBlock extends BaseEntityBlock {
	public static final MapCodec<DragonEggBlock> CODEC = simpleCodec(properties ->
			new DragonEggBlock(DragonVariant.CRIMSON, properties));

	/** Blocks of slack between egg and fire, measured as a sphere. */
	public static final int CAMPFIRE_RADIUS = 5;
	/** Shortest and longest incubation, in ticks: ten to twenty-five minutes. */
	public static final int HATCH_TICKS_MIN = 12000;
	public static final int HATCH_TICKS_MAX = 30000;
	/** How often the block looks at its own clock. */
	public static final int CHECK_INTERVAL = 100;
	/** Four visible stages, so the wait is legible from across the camp. */
	public static final int MAX_STAGE = 3;
	public static final IntegerProperty INCUBATION = IntegerProperty.create("incubation", 0, MAX_STAGE);

	private static final VoxelShape SHAPE = Block.column(12.0, 0.0, 16.0);

	private final DragonVariant variant;

	public DragonEggBlock(DragonVariant variant, BlockBehaviour.Properties properties) {
		super(properties);
		this.variant = variant;
		this.registerDefaultState(this.stateDefinition.any().setValue(INCUBATION, 0));
	}

	public DragonVariant variant() {
		return this.variant;
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(INCUBATION);
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new DragonEggBlockEntity(pos, state);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
			BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType type) {
		return false;
	}

	// ------------------------------------------------------------------ incubation

	/** Is there a lit campfire close enough to keep this egg warm? */
	public static boolean hasWarmth(Level level, BlockPos pos) {
		for (BlockPos candidate : BlockPos.betweenClosed(
				pos.offset(-CAMPFIRE_RADIUS, -CAMPFIRE_RADIUS, -CAMPFIRE_RADIUS),
				pos.offset(CAMPFIRE_RADIUS, CAMPFIRE_RADIUS, CAMPFIRE_RADIUS))) {
			if (!candidate.closerThan(pos, CAMPFIRE_RADIUS)) {
				continue;
			}
			BlockState state = level.getBlockState(candidate);
			if (state.is(BlockTags.CAMPFIRES)
					&& state.hasProperty(BlockStateProperties.LIT)
					&& state.getValue(BlockStateProperties.LIT)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Arm the clock for <em>any</em> way an egg can arrive.
	 *
	 * <p>{@code setPlacedBy} only fires when a player puts the block down by hand, so an
	 * egg placed by {@code /setblock}, by a structure, by a datapack or by another mod
	 * would never get its first scheduled tick and would sit beside a roaring campfire
	 * doing nothing forever — no error, no log line. This hook runs for all of them, and
	 * the tick it schedules is what actually starts the incubation.
	 */
	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		if (level instanceof ServerLevel serverLevel) {
			this.reschedule(serverLevel, pos);
		}
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state,
			@Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level instanceof ServerLevel serverLevel
				&& serverLevel.getBlockEntity(pos) instanceof DragonEggBlockEntity egg) {
			if (placer instanceof Player player) {
				egg.setHatcher(player.getUUID());
			}
			this.beginOrWait(serverLevel, pos, state, egg);
		}
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
			@Nullable Orientation orientation, boolean movedByPiston) {
		super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
		// a campfire lit next door should start the clock without waiting a full interval
		if (level instanceof ServerLevel serverLevel
				&& serverLevel.getBlockEntity(pos) instanceof DragonEggBlockEntity egg
				&& !egg.isIncubating()) {
			this.beginOrWait(serverLevel, pos, state, egg);
		}
	}

	private void beginOrWait(ServerLevel level, BlockPos pos, BlockState state, DragonEggBlockEntity egg) {
		if (!egg.isIncubating() && hasWarmth(level, pos)) {
			int span = HATCH_TICKS_MIN + level.getRandom().nextInt(HATCH_TICKS_MAX - HATCH_TICKS_MIN + 1);
			egg.beginIncubation(level.getGameTime(), span);
			level.playSound(null, pos, SoundEvents.DRIED_GHAST_PLACE_IN_WATER, SoundSource.BLOCKS, 0.8F, 0.6F);
		}
		this.reschedule(level, pos);
	}

	private void reschedule(ServerLevel level, BlockPos pos) {
		if (!level.getBlockTicks().hasScheduledTick(pos, this)) {
			level.scheduleTick(pos, this, CHECK_INTERVAL);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!(level.getBlockEntity(pos) instanceof DragonEggBlockEntity egg)) {
			return;
		}
		if (!egg.isIncubating()) {
			this.beginOrWait(level, pos, state, egg);
			return;
		}

		long now = level.getGameTime();
		if (now >= egg.hatchAt()) {
			// the deadline is met; the fire still has to be there to finish the job
			if (hasWarmth(level, pos)) {
				this.hatch(level, pos, egg);
				return;
			}
			egg.postpone(now + CHECK_INTERVAL);
		}

		int stage = egg.stage(now, MAX_STAGE);
		if (state.getValue(INCUBATION) != stage) {
			level.setBlock(pos, state.setValue(INCUBATION, stage), Block.UPDATE_ALL);
			level.playSound(null, pos, SoundEvents.DRIED_GHAST_TRANSITION, SoundSource.BLOCKS, 0.7F, 0.7F);
		}
		this.reschedule(level, pos);
	}

	/**
	 * The block also re-arms itself on random ticks.
	 *
	 * <p>Scheduled ticks survive a save, but a world that was rolled back, imported or
	 * edited might not carry one. A random tick costs nothing and means a stalled egg
	 * repairs itself instead of sitting there forever.
	 */
	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		this.reschedule(level, pos);
	}

	private void hatch(ServerLevel level, BlockPos pos, DragonEggBlockEntity egg) {
		level.removeBlock(pos, false);
		DragonEntity dragon = DragonsEntities.DRAGON.create(level, EntitySpawnReason.BREEDING);
		if (dragon == null) {
			return;
		}
		Vec3 at = Vec3.atBottomCenterOf(pos);
		dragon.setVariant(this.variant);
		dragon.setBaby(true);
		dragon.snapTo(at.x(), at.y(), at.z(), level.getRandom().nextFloat() * 360.0F, 0.0F);
		Player hatcher = egg.hatcher() == null ? null : level.getPlayerByUUID(egg.hatcher());
		if (hatcher == null) {
			// whoever set the egg down has logged out; the nearest player inherits it,
			// so a hatchling is never born with nobody to imprint on
			hatcher = level.getNearestPlayer(at.x(), at.y(), at.z(), 32.0, false);
		}
		if (hatcher != null) {
			dragon.bondTo(hatcher);
		}
		level.addFreshEntity(dragon);
		level.playSound(null, dragon, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.BLOCKS, 0.6F, 1.8F);
	}

	// ------------------------------------------------------------------ ambience

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		double x = pos.getX() + 0.5;
		double y = pos.getY() + 0.5;
		double z = pos.getZ() + 0.5;
		// the closer to hatching, the busier the egg looks
		int stage = state.getValue(INCUBATION);
		if (random.nextInt(Math.max(1, 8 - stage * 2)) == 0) {
			level.addParticle(ParticleTypes.PORTAL,
					x + (random.nextDouble() - 0.5) * 0.6, y + 0.2,
					z + (random.nextDouble() - 0.5) * 0.6, 0.0, 0.03, 0.0);
		}
		if (stage >= MAX_STAGE && random.nextInt(12) == 0) {
			level.addParticle(ParticleTypes.SMALL_FLAME, x, y + 0.4, z, 0.0, 0.01, 0.0);
		}
	}
}
