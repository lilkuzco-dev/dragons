package dev.lilkuzco.dragons.entity.ai;

import dev.lilkuzco.dragons.entity.DragonEntity;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Fly somewhere, over whatever is in the way.
 *
 * <p>This is the goal that makes a dragon a neighbour rather than a wrecking ball. Two
 * things do the work, and neither of them is block-breaking:
 *
 * <ol>
 *   <li><b>Altitude.</b> The destination is chosen at least {@value #CLEARANCE_MIN} blocks
 *       above the world-surface heightmap at the destination column, so the default
 *       flight path is <em>over</em> the forest, the village and the ridge rather than
 *       through them. Vanilla's {@code WaterAvoidingRandomFlyingGoal} hugs the ground
 *       instead, which for something this size means constant contact with scenery.
 *   <li><b>Pathing.</b> The move is handed to the mob's {@code FlyingPathNavigation},
 *       whose {@code FlyNodeEvaluator} treats solid blocks as impassable — so where
 *       altitude is not enough (a cliff face, a tower) the A* result routes around the
 *       obstruction. If it cannot find a way, the goal simply does not start; a dragon
 *       that has nowhere to fly perches, which is a perfectly good outcome.
 * </ol>
 *
 * <p>Between flights the dragon rests. That is deliberate rather than incidental: a wild
 * dragon has to spend real time on the ground for "walk up to it with a chicken" to be
 * something a player can actually do.
 */
public class DragonFlightGoal extends Goal {
	private static final int RANGE_MIN = 12;
	private static final int RANGE_EXTRA = 24;
	private static final int CLEARANCE_MIN = 8;
	private static final int CLEARANCE_EXTRA = 12;
	private static final int ATTEMPTS = 8;

	private final DragonEntity dragon;
	private final double speedModifier;

	public DragonFlightGoal(DragonEntity dragon, double speedModifier) {
		this.dragon = dragon;
		this.speedModifier = speedModifier;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		if (this.dragon.isVehicle() || this.dragon.isOrderedToSit() || this.dragon.isResting()) {
			return false;
		}
		// a leashed dragon is a parked dragon: it stays where it was tied, the way a
		// happy ghast does, instead of hitting the end of the lead every few seconds
		if (this.dragon.isLeashed() || this.dragon.isPassenger()) {
			return false;
		}
		// somebody is standing there holding raw chicken — that is an invitation, and a
		// dragon that flies off mid-offer makes the 5% taming roll unreachable
		if (this.dragon.isBaited()) {
			return false;
		}
		// a hatchling's job is to stay with its player; the bond goal owns its movement
		if (this.dragon.isBonded()) {
			return false;
		}
		if (!this.dragon.getNavigation().isDone()) {
			return false;
		}
		Vec3 target = this.findFlightTarget();
		return target != null
				&& this.dragon.getNavigation().moveTo(target.x, target.y, target.z, this.speedModifier);
	}

	@Override
	public boolean canContinueToUse() {
		return !this.dragon.getNavigation().isDone()
				&& !this.dragon.isVehicle()
				&& !this.dragon.isLeashed()
				&& !this.dragon.isOrderedToSit();
	}

	@Override
	public void stop() {
		this.dragon.getNavigation().stop();
		this.dragon.restAfterFlight();
	}

	/**
	 * A destination in open air, well clear of the terrain under it.
	 *
	 * <p>{@code WORLD_SURFACE} rather than {@code MOTION_BLOCKING_NO_LEAVES}: canopy and
	 * rooftops both count as "the ground the dragon must clear". A dragon that has just
	 * been abandoned by its hatcher gets a longer leg, so leaving actually looks like
	 * leaving.
	 */
	private Vec3 findFlightTarget() {
		int reach = this.dragon.hasLeftForGood() ? RANGE_EXTRA * 2 : RANGE_EXTRA;
		for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
			float angle = this.dragon.getRandom().nextFloat() * (float) (Math.PI * 2);
			double distance = RANGE_MIN + this.dragon.getRandom().nextInt(reach);
			double x = this.dragon.getX() + Mth.cos(angle) * distance;
			double z = this.dragon.getZ() + Mth.sin(angle) * distance;

			BlockPos column = BlockPos.containing(x, this.dragon.getY(), z);
			int surface = this.dragon.level()
					.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, column).getY();
			int clearance = CLEARANCE_MIN + this.dragon.getRandom().nextInt(CLEARANCE_EXTRA);
			double y = Math.min(surface + clearance, this.dragon.level().getMaxY() - 4);
			// never aim below where it already is by much — descending into terrain is
			// the one way this goal could put a dragon inside a building
			y = Math.max(y, this.dragon.getY() - 4.0);

			BlockPos target = BlockPos.containing(x, y, z);
			if (this.dragon.level().isEmptyBlock(target)) {
				return new Vec3(x, y, z);
			}
		}
		return null;
	}
}
