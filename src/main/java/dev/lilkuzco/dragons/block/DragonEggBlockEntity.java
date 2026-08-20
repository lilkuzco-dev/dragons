package dev.lilkuzco.dragons.block;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The egg's clock and its imprint.
 *
 * <p>Two absolute game times and a UUID — no counters. {@code startedAt} and
 * {@code hatchAt} are stamped once when incubation begins, and every later question
 * ("how far along is it", "is it ready") is answered by comparing them to the current
 * game time. Nothing here accumulates, so nothing here can drift while the chunk is
 * unloaded; an egg buried in a chunk nobody visited for a week is exactly as ready as one
 * watched the whole time.
 *
 * <p>This block entity deliberately has no ticker. It is storage, not simulation — the
 * scheduled block tick in {@link DragonEggBlock} is what looks at the clock, and even that
 * is only an observer.
 */
public class DragonEggBlockEntity extends BlockEntity {
	private long startedAt;
	private long hatchAt;
	private @Nullable UUID hatcher;

	public DragonEggBlockEntity(BlockPos pos, BlockState state) {
		super(DragonsBlocks.DRAGON_EGG_BLOCK_ENTITY, pos, state);
	}

	public boolean isIncubating() {
		return this.hatchAt > 0L;
	}

	public long hatchAt() {
		return this.hatchAt;
	}

	public @Nullable UUID hatcher() {
		return this.hatcher;
	}

	public void setHatcher(UUID hatcher) {
		this.hatcher = hatcher;
		this.setChanged();
	}

	public void beginIncubation(long now, int span) {
		this.startedAt = now;
		this.hatchAt = now + span;
		this.setChanged();
	}

	/** Push the deadline out — used when the deadline arrives with the fire out. */
	public void postpone(long until) {
		this.hatchAt = until;
		this.setChanged();
	}

	/** Progress, quantised to the block's visible stages. Pure function of the clock. */
	public int stage(long now, int maxStage) {
		if (!this.isIncubating() || this.hatchAt <= this.startedAt) {
			return 0;
		}
		double progress = (double) (now - this.startedAt) / (double) (this.hatchAt - this.startedAt);
		return Mth.clamp((int) (progress * (maxStage + 1)), 0, maxStage);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putLong("started_at", this.startedAt);
		output.putLong("hatch_at", this.hatchAt);
		if (this.hatcher != null) {
			output.store("hatcher", UUIDUtil.CODEC, this.hatcher);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.startedAt = input.getLongOr("started_at", 0L);
		this.hatchAt = input.getLongOr("hatch_at", 0L);
		this.hatcher = input.read("hatcher", UUIDUtil.CODEC).orElse(null);
	}
}
