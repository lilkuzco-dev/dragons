package dev.lilkuzco.dragons.entity.ai;

import dev.lilkuzco.dragons.entity.DragonEntity;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

/**
 * A hatchling keeps close to whoever set its egg down, for the hour it is willing to
 * give them.
 *
 * <p>Deliberately not {@code FollowOwnerGoal}: the dragon is not owned yet — that is the
 * whole point of the window — and {@code FollowOwnerGoal} also teleports, which would let
 * a hatchling appear beside a player who has walked off a cliff or ridden a boat across
 * an ocean. This one only ever walks or flies, so a player who leaves the hatchling
 * behind has genuinely left it behind.
 *
 * <p>The deadline itself lives on the entity as an absolute game time; see
 * {@link DragonEntity#isBonded()}.
 */
public class DragonBondGoal extends Goal {
	private static final float START_DISTANCE = 8.0F;
	private static final float STOP_DISTANCE = 3.0F;
	private static final int REPATH_INTERVAL = 10;

	private final DragonEntity dragon;
	private final double speedModifier;
	private Player target;
	private int repathCooldown;

	public DragonBondGoal(DragonEntity dragon, double speedModifier) {
		this.dragon = dragon;
		this.speedModifier = speedModifier;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (!this.dragon.isBonded() || this.dragon.isLeashed() || this.dragon.isOrderedToSit()) {
			return false;
		}
		Player player = this.dragon.getBondedPlayer();
		if (player == null || player.isSpectator()) {
			return false;
		}
		if (this.dragon.distanceToSqr(player) < START_DISTANCE * START_DISTANCE) {
			return false;
		}
		this.target = player;
		return true;
	}

	@Override
	public boolean canContinueToUse() {
		return this.target != null
				&& this.dragon.isBonded()
				&& !this.dragon.isLeashed()
				&& !this.dragon.isOrderedToSit()
				&& !this.dragon.getNavigation().isDone()
				&& this.dragon.distanceToSqr(this.target) > STOP_DISTANCE * STOP_DISTANCE;
	}

	@Override
	public void start() {
		this.repathCooldown = 0;
	}

	@Override
	public void stop() {
		this.target = null;
		this.dragon.getNavigation().stop();
	}

	@Override
	public void tick() {
		this.dragon.getLookControl().setLookAt(this.target, 10.0F, this.dragon.getMaxHeadXRot());
		if (--this.repathCooldown > 0) {
			return;
		}
		this.repathCooldown = REPATH_INTERVAL;
		// aim a little above the player's feet: the navigation is a flying one, and a
		// hatchling that paths to floor level lands on every step of the way
		this.dragon.getNavigation().moveTo(
				this.target.getX(), this.target.getY() + 1.0, this.target.getZ(), this.speedModifier);
	}
}
