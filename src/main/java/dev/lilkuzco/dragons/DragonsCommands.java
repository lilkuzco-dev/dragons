package dev.lilkuzco.dragons;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.lilkuzco.dragons.block.DragonEggBlock;
import dev.lilkuzco.dragons.block.DragonEggBlockEntity;
import dev.lilkuzco.dragons.entity.DragonEntity;
import java.util.List;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

/**
 * Read-only status commands.
 *
 * <p>These exist because the interesting state here is invisible: an egg's clock is a
 * game-time deadline and a hatchling's bond is another one, so a screenshot of either is
 * a picture of a block or a mob and proves nothing. {@code /dragons census} prints what
 * the picture cannot, which is what makes the render battery's frames checkable.
 */
public final class DragonsCommands {
	private static final int CENSUS_RADIUS = 48;

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) ->
				dispatcher.register(literal()));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> literal() {
		return Commands.literal("dragons")
				.then(Commands.literal("census").executes(context -> census(context.getSource())))
				.then(Commands.literal("eggs").executes(context -> eggs(context.getSource())))
				.then(Commands.literal("loot").executes(context -> loot(context.getSource())));
	}

	private static int census(CommandSourceStack source) {
		ServerLevel level = source.getLevel();
		BlockPos origin = BlockPos.containing(source.getPosition());
		List<DragonEntity> dragons = level.getEntitiesOfClass(DragonEntity.class,
				new AABB(origin).inflate(CENSUS_RADIUS));
		if (dragons.isEmpty()) {
			source.sendSuccess(() -> Component.literal("no dragons within " + CENSUS_RADIUS + " blocks"), false);
			return 0;
		}
		for (DragonEntity dragon : dragons) {
			String line = String.format(
					"%s %s  tame=%s saddled=%s flying=%s resting=%s leashed=%s bond=%s at %d %d %d",
					dragon.isBaby() ? "baby" : "adult",
					dragon.getVariant().id(),
					dragon.isTame(), dragon.isSaddled(), dragon.isFlying(),
					dragon.isResting(), dragon.isLeashed(),
					dragon.isBonded() ? (dragon.bondTicksLeft() / 20) + "s left"
							: (dragon.hasLeftForGood() ? "left" : "none"),
					dragon.getBlockX(), dragon.getBlockY(), dragon.getBlockZ());
			source.sendSuccess(() -> Component.literal(line), false);
		}
		return dragons.size();
	}

	/**
	 * Did the egg actually get into the castle loot tables?
	 *
	 * <p>Loot injection is the one part of this mod that cannot be verified in the dev
	 * environment, because it keys off tables another mod owns and that mod is not on the
	 * dev classpath. This prints the answer from the live registry instead, so the check
	 * takes one command on the server rather than a looting expedition.
	 */
	private static int loot(CommandSourceStack source) {
		if (!DragonsLoot.warfrontPresent()) {
			source.sendSuccess(() -> Component.literal(
					"warfront NOT installed — dragon eggs have no loot source at all; "
							+ "dragons are creative-only in this pack"), false);
			return 0;
		}
		int wired = 0;
		for (var entry : DragonsLoot.targets().entrySet()) {
			boolean seen = DragonsLoot.wasSeen(entry.getKey());
			if (seen) {
				wired++;
			}
			String line = String.format("%-28s %s  (%.0f%% per chest)",
					entry.getKey().identifier().toString(),
					seen ? "wired" : "MISSING — table never loaded",
					entry.getValue() * 100.0F);
			source.sendSuccess(() -> Component.literal(line), false);
		}
		int total = DragonsLoot.targets().size();
		int finalWired = wired;
		source.sendSuccess(() -> Component.literal(
				finalWired + "/" + total + " castle tables carry dragon eggs"), false);
		return wired;
	}

	private static int eggs(CommandSourceStack source) {
		ServerLevel level = source.getLevel();
		BlockPos origin = BlockPos.containing(source.getPosition());
		int found = 0;
		for (BlockPos pos : BlockPos.betweenClosed(
				origin.offset(-16, -8, -16), origin.offset(16, 8, 16))) {
			if (!(level.getBlockState(pos).getBlock() instanceof DragonEggBlock egg)) {
				continue;
			}
			if (!(level.getBlockEntity(pos) instanceof DragonEggBlockEntity data)) {
				continue;
			}
			found++;
			long now = level.getGameTime();
			String line = String.format("%s egg at %d %d %d  warm=%s incubating=%s%s",
					egg.variant().id(), pos.getX(), pos.getY(), pos.getZ(),
					DragonEggBlock.hasWarmth(level, pos), data.isIncubating(),
					data.isIncubating()
							? "  stage=" + data.stage(now, DragonEggBlock.MAX_STAGE)
									+ "  hatches in " + Math.max(0, (data.hatchAt() - now) / 20) + "s"
							: "");
			source.sendSuccess(() -> Component.literal(line), false);
		}
		if (found == 0) {
			source.sendSuccess(() -> Component.literal("no dragon eggs nearby"), false);
		}
		return found;
	}

	private DragonsCommands() {
	}
}
