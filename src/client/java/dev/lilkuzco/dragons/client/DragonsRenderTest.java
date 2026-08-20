package dev.lilkuzco.dragons.client;

import dev.lilkuzco.dragons.entity.DragonEntity;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

/**
 * The render battery.
 *
 * <p>Every screenshot here answers a question no server-side check can. This mod draws a
 * mob on a model it did not write, using textures it generated onto UV rectangles it
 * derived on paper, at a scale and a pose offset that were reasoned about rather than
 * measured — all of which pass every hash, every load check and every assertion while
 * being visibly wrong.
 *
 * <ul>
 *   <li><b>lineup</b> — seven hides facing the camera. Proves the recolour produced seven
 *       distinguishable dragons and not seven black ones (vanilla's dragon.png is
 *       greyscale, so a naive hue rotation would do exactly that).
 *   <li><b>grounded</b> — a dragon standing at floor level. Proves the render scale and
 *       the inherited pose offset put it ON the ground, not buried in it or hovering.
 *   <li><b>baby_beside_adult</b> — the quarter-scale hatchling next to a grown one.
 *   <li><b>saddle_bare / saddle_worn / saddle_above</b> — the same dragon with and without
 *       the saddle, from the side and from overhead. This pair is what catches a UV
 *       rectangle derived correctly on paper and applied to the wrong face, which is
 *       otherwise completely invisible.
 *   <li><b>eggs</b> — all seven eggs, intact and cracked, around a lit campfire.
 *   <li><b>hatched</b> — the real hatch path, driven by moving the deadline into the past
 *       rather than by calling anything the game would not call.
 *   <li><b>leashed</b> — a dragon parked on a lead.
 * </ul>
 *
 * <p>Elevated cameras are SPECTATOR, always: a creative player falls during the chunk
 * render wait and takes the shot from the floor.
 *
 * <p>Runs only under {@code ./gradlew runGametest} ({@code -Dfabric.client.gametest}).
 */
public class DragonsRenderTest implements FabricClientGameTest {
	private static final String[] VARIANTS =
			{"crimson", "emerald", "sapphire", "amethyst", "amber", "obsidian", "ivory"};
	/** Independent tamings to average. One draw cannot tell 5% from 100%. */
	private static final int TAMING_ROUNDS = 5;
	/** Give-up point per round. 0.95^400 is about one in a hundred million. */
	private static final int FEED_LIMIT = 400;
	/** Long enough to average out a tick of jitter, short enough to stay in loaded chunks. */
	private static final int FLIGHT_SAMPLE_TICKS = 40;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitTicks(80);
			TestServerContext server = world.getServer();
			server.runCommand("time set noon");
			server.runCommand("gamemode creative @p");
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule advance_weather false");
			stage(context, server);

			// ---- 1. the seven hides, facing the camera --------------------------------
			// yaw 180 turns them to face north, which is where the camera is; NoAI holds
			// them in place. Six blocks apart: the wings are four across at half scale.
			for (int i = 0; i < VARIANTS.length; i++) {
				server.runCommand("execute at @p run summon dragons:dragon ~" + (i * 6.0 - 18.0)
						+ " ~ ~20 {NoAI:1b,Rotation:[180f,0f],dragons_variant:\"" + VARIANTS[i] + "\"}");
			}
			context.waitTicks(60);
			context.takeScreenshot("dragon_lineup");
			server.runCommand("execute at @p run dragons census");

			// ---- 2. does the model sit on the floor? ----------------------------------
			// Summoned AT floor level with NoAI, so nothing moves it: the frame shows
			// purely where the renderer draws the model relative to the entity origin.
			clear(server);
			// dragons_rest parks the flight goal for 2000 ticks, so this one falls under
			// its own gravity and stays where it lands instead of leaving the frame —
			// which is the difference between "the model is drawn at floor level" and
			// "the model actually comes to rest on the floor"
			server.runCommand("execute at @p run summon dragons:dragon ~ ~4 ~9 "
					+ "{Rotation:[135f,0f],dragons_rest:2000,dragons_variant:\"emerald\"}");
			context.waitTicks(120);
			context.takeScreenshot("dragon_grounded");
			server.runCommand("execute at @p run dragons census");

			// ---- 3. hatchling scale ---------------------------------------------------
			clear(server);
			server.runCommand("execute at @p run summon dragons:dragon ~-3 ~ ~10 "
					+ "{NoAI:1b,Rotation:[180f,0f],dragons_variant:\"amber\"}");
			server.runCommand("execute at @p run summon dragons:dragon ~2 ~ ~7 "
					+ "{NoAI:1b,Rotation:[180f,0f],Age:-24000,dragons_variant:\"amber\"}");
			context.waitTicks(50);
			context.takeScreenshot("dragon_baby_beside_adult");
			server.runCommand("execute at @p run dragons census");

			// ---- 4. the saddle, A then B, from the same camera ------------------------
			// Two frames of ONE dragon rather than one frame of two: a difference between
			// consecutive shots of the same subject is unmissable, where two subjects side
			// by side invite the eye to explain the difference away.
			clear(server);
			String pose = "{NoAI:1b,Rotation:[150f,0f],dragons_variant:\"ivory\"";
			server.runCommand("execute at @p run summon dragons:dragon ~ ~ ~7 " + pose + "}");
			context.waitTicks(50);
			context.takeScreenshot("dragon_saddle_bare");
			// re-summoned rather than equipped in place: `/item replace ... saddle` goes
			// through canUseSlot, which this untamed dragon correctly refuses, and the
			// equipment tag is the same load path a saved dragon comes back through
			clear(server);
			server.runCommand("execute at @p run summon dragons:dragon ~ ~ ~7 " + pose
					+ ",equipment:{saddle:{id:\"dragons:dragon_saddle\",count:1}}}");
			context.waitTicks(50);
			context.takeScreenshot("dragon_saddle_worn");
			server.runCommand("execute at @p run dragons census");

			// overhead, where a saddle actually lives. SPECTATOR or the camera falls.
			server.runCommand("gamemode spectator @p");
			server.runCommand("execute at @p run tp @p ~ ~6 ~4 0 65");
			context.waitTicks(40);
			context.takeScreenshot("dragon_saddle_above");
			server.runCommand("gamemode creative @p");

			// ---- 5. the eggs ----------------------------------------------------------
			clear(server);
			stage(context, server);
			for (int i = 0; i < VARIANTS.length; i++) {
				String egg = "dragons:" + VARIANTS[i] + "_dragon_egg";
				// front row intact, back row raised a block so both rows read at once
				server.runCommand("execute at @p run setblock ~" + (i * 2 - 6) + " ~ ~7 "
						+ egg + "[incubation=0]");
				server.runCommand("execute at @p run setblock ~" + (i * 2 - 6) + " ~1 ~10 "
						+ "minecraft:stone");
				server.runCommand("execute at @p run setblock ~" + (i * 2 - 6) + " ~2 ~10 "
						+ egg + "[incubation=3]");
			}
			server.runCommand("execute at @p run setblock ~-9 ~ ~7 minecraft:campfire[lit=true]");
			server.runCommand("gamemode spectator @p");
			server.runCommand("execute at @p run tp @p ~ ~3 ~1 0 25");
			context.waitTicks(50);
			context.takeScreenshot("dragon_eggs");
			server.runCommand("execute at @p run dragons eggs");
			// close enough to read the fissures: intact left, late-incubation right
			server.runCommand("execute at @p run tp @p ~-4 ~-2 ~4 0 20");
			context.waitTicks(30);
			context.takeScreenshot("dragon_egg_stages");
			server.runCommand("gamemode creative @p");

			// ---- 6. an egg that really hatches ---------------------------------------
			// The clock is 10-25 minutes of world time, far too long to wait out, so the
			// DEADLINE is moved into the past and the block's own scheduled check is what
			// opens it. Nothing here calls anything the game would not call: /setblock has
			// to reach onPlace, onPlace has to schedule, the scheduled tick has to find a
			// lit campfire, and only then does a hatchling appear.
			clear(server);
			stage(context, server);
			server.runCommand("execute at @p run setblock ~ ~ ~6 minecraft:campfire[lit=true]");
			server.runCommand("execute at @p run setblock ~2 ~ ~6 dragons:sapphire_dragon_egg");
			context.waitTicks(20);
			server.runCommand("execute at @p run dragons eggs");
			// 1L is in the past (the world is already hundreds of ticks old) and non-zero,
			// which is what marks the egg as incubating at all
			server.runCommand("execute at @p run data merge block ~2 ~ ~6 {started_at:1L,hatch_at:1L}");
			context.waitTicks(160);
			context.takeScreenshot("dragon_hatched");
			server.runCommand("execute at @p run dragons census");
			server.runCommand("execute at @p run dragons eggs");

			// ---- 7. parked on a lead --------------------------------------------------
			// The census line is the evidence here as much as the frame: "leashed=true"
			// beside a dragon that has not wandered is what "parked like a ghast" means.
			clear(server);
			// The `leash` tag is an EntityReference, so it needs the knot's UUID rather
			// than a block position — hence pinning the knot's UUID at summon time and
			// naming the same one on the dragon.
			// LeashData.CODEC is xor(UUID-in-a-map, BlockPos-as-3-ints); the UUID form is
			// the one that needs no absolute coordinates, so the knot gets a pinned UUID
			// and the dragon names it.
			String knot = "[I;1,2,3,4]";
			server.runCommand("execute at @p run setblock ~-2 ~ ~7 minecraft:oak_fence");
			server.runCommand("execute at @p run summon minecraft:leash_knot ~-2 ~ ~7 "
					+ "{UUID:" + knot + "}");
			server.runCommand("execute at @p run summon dragons:dragon ~-2 ~1 ~10 "
					+ "{Rotation:[180f,0f],dragons_variant:\"crimson\",leash:{UUID:" + knot + "}}");
			context.waitTicks(150);
			context.takeScreenshot("dragon_leashed");
			server.runCommand("execute at @p run dragons census");

			// ---- 8. taming, for real, five times ------------------------------------
			// Everything above stages state with NBT. This plays the game: a landed
			// dragon, raw chicken and the use key, until it gives in.
			//
			// Five separate dragons rather than one, because ONE taming cannot tell 5%
			// from 100% — a single feed succeeding is a perfectly ordinary 1-in-20 event.
			// Five geometric draws at p=0.05 average about 20 feeds each; five draws at
			// p=1.0 average exactly 1. The logged mean is what distinguishes them, and it
			// is the only check on the rate that does not just restate the constant.
			int[] counts = new int[TAMING_ROUNDS];
			for (int round = 0; round < TAMING_ROUNDS; round++) {
				clear(server);
				stage(context, server);
				server.runCommand("execute at @p run summon dragons:dragon ~ ~ ~4 "
						+ "{Rotation:[0f,0f],dragons_rest:20000,dragons_variant:\"amethyst\"}");
				server.runCommand("give @p minecraft:chicken 64");
				context.waitTicks(50);

				int feeds = 0;
				while (!tamed(server) && feeds < FEED_LIMIT) {
					// re-aim every feed: the dragon is a live mob and drifts, and a
					// right-click that misses is a feed this loop would never make
					server.runCommand(
							"execute at @e[type=dragons:dragon,limit=1] run tp @p ~ ~ ~-3 0 0");
					context.getInput().pressKey(options -> options.keyUse);
					context.waitTicks(6);
					feeds++;
					if (round == 0 && feeds == 1) {
						// the wolf burst: smoke on a refusal, hearts on an acceptance
						context.takeScreenshot("dragon_taming_attempt");
					}
					if (feeds % 50 == 0) {
						server.runCommand("give @p minecraft:chicken 64");
					}
				}
				if (!tamed(server)) {
					throw new AssertionError("round " + round + ": " + FEED_LIMIT
							+ " raw chicken and still untamed. At 5% that is a 1-in-10^8 "
							+ "event, so the feed path is broken, not unlucky.");
				}
				counts[round] = feeds;
				server.runCommand("execute at @p run say taming round " + round + ": "
						+ feeds + " raw chicken");
				if (round == 0) {
					context.waitTicks(20);
					context.takeScreenshot("dragon_tamed");
					// and now that it IS tamed, canUseSlot lets the saddle on — the same
					// command that correctly bounces off an untamed dragon
					server.runCommand("execute as @e[type=dragons:dragon] run item replace "
							+ "entity @s saddle with dragons:dragon_saddle");
					context.waitTicks(30);
					context.takeScreenshot("dragon_tamed_saddled");
					server.runCommand("execute at @p run dragons census");
					flightTest(context, server);
				}
			}

			int total = 0;
			StringBuilder line = new StringBuilder();
			for (int round = 0; round < counts.length; round++) {
				total += counts[round];
				line.append(round == 0 ? "" : ", ").append(counts[round]);
			}
			double mean = (double) total / counts.length;
			server.runCommand("execute at @p run say TAMING RATE: counts [" + line + "]"
					+ " mean " + String.format("%.1f", mean) + " chicken per dragon"
					+ " (expected ~20 at 5%, exactly 1.0 if the roll were certain)");
			// Threshold picked for the FALSE-ALARM rate, not for tightness: five geometric
			// draws at p=0.05 sum to 12 or less about twice in ten thousand runs, so this
			// gate effectively never fires by chance — while a roll that always succeeds
			// gives exactly 1.0 and a coin flip gives 2.0. It is a "the roll is not really
			// rolling" alarm, not an estimator.
			if (mean < 2.5) {
				throw new AssertionError("mean of " + mean + " chicken over " + counts.length
						+ " tamings is far too low for a 5% roll (expected ~20) — the roll is "
						+ "not doing what it claims");
			}

			// ---- 9. the hatchling hour running out ----------------------------------
			clear(server);
			stage(context, server);
			bondExpiryTest(context, server);
		}
	}

	/**
	 * Mount the dragon we just tamed and fly it, then measure how fast it actually went.
	 *
	 * <p>Riding is the one headline feature no screenshot can check and no NBT can stage:
	 * it needs a real mount, a real key held down and a real controlling passenger, and
	 * every gate on the way (owner, saddled, grown) has to pass for any of it to happen.
	 *
	 * <p>The speed is measured rather than derived because deriving it went wrong twice.
	 * It falls out of {@code getRiddenInput}, {@code getInputVector}'s length cap and
	 * {@code travelFlying}'s drag together, and FLYING_SPEED enters that chain either once
	 * or twice depending on which side of the cap it lands — so the same attribute value
	 * can mean 9 blocks/s or 220. Reading the number off the attribute gave answers that
	 * were out by 3x in one direction and 25x in the other. This measures it.
	 */
	private static void flightTest(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("ride @p mount @e[type=dragons:dragon,limit=1]");
		context.waitTicks(20);
		if (!ridden(server)) {
			throw new AssertionError("mounting a tamed, saddled, owned dragon did not take — "
					+ "getControllingPassenger is refusing its own owner");
		}
		context.takeScreenshot("dragon_ridden");

		context.getInput().holdKey(options -> options.keyUp);
		context.waitTicks(30);                       // spend the acceleration curve first
		double[] from = dragonPos(server);
		context.waitTicks(FLIGHT_SAMPLE_TICKS);
		double[] to = dragonPos(server);
		context.getInput().releaseKey(options -> options.keyUp);

		double dx = to[0] - from[0];
		double dy = to[1] - from[1];
		double dz = to[2] - from[2];
		double blocksPerSecond = Math.sqrt(dx * dx + dy * dy + dz * dz)
				/ FLIGHT_SAMPLE_TICKS * 20.0;
		server.runCommand("execute at @p run say RIDDEN SPEED: "
				+ String.format("%.1f", blocksPerSecond) + " blocks/s over "
				+ FLIGHT_SAMPLE_TICKS + " ticks (want ~24, a shade over elytra glide)");
		context.takeScreenshot("dragon_ridden_flight");
		if (blocksPerSecond < 12.0) {
			throw new AssertionError("ridden dragon moved at " + blocksPerSecond
					+ " blocks/s, well under the ~24 this is tuned for — either the rider's "
					+ "input is not reaching travel() or FLYING_SPEED has drifted down");
		}
		if (blocksPerSecond > 80.0) {
			throw new AssertionError("ridden dragon moved at " + blocksPerSecond
					+ " blocks/s, which outruns chunk loading — FLYING_SPEED is being read "
					+ "as though getInputVector did not normalise the input");
		}
		server.runCommand("ride @p dismount");
		context.waitTicks(20);
	}

	/** Is a player actually in control of a dragon? */
	private static boolean ridden(TestServerContext server) {
		return server.computeOnServer(minecraftServer -> {
			for (ServerLevel level : minecraftServer.getAllLevels()) {
				for (var entity : level.getAllEntities()) {
					if (entity instanceof DragonEntity dragon
							&& dragon.getControllingPassenger() instanceof Player) {
						return true;
					}
				}
			}
			return false;
		});
	}

	private static double[] dragonPos(TestServerContext server) {
		return server.computeOnServer(minecraftServer -> {
			for (ServerLevel level : minecraftServer.getAllLevels()) {
				for (var entity : level.getAllEntities()) {
					if (entity instanceof DragonEntity dragon) {
						return new double[] {dragon.getX(), dragon.getY(), dragon.getZ()};
					}
				}
			}
			throw new AssertionError("no dragon left to measure");
		});
	}

	/**
	 * The hatchling hour, without waiting an hour.
	 *
	 * <p>The bond is stored as an absolute game time precisely so that it does not depend
	 * on anything having ticked, which means a deadline already in the past is a completely
	 * legitimate state to load — not a shortcut around the logic but the same arithmetic
	 * the real case does an hour later.
	 */
	private static void bondExpiryTest(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("execute at @p run summon dragons:dragon ~ ~ ~5 "
				+ "{Age:-24000,dragons_variant:\"emerald\",dragons_bonded_to:[I;1,2,3,4],"
				+ "dragons_bond_expires:1L}");
		context.waitTicks(40);
		boolean left = server.computeOnServer(minecraftServer -> {
			for (ServerLevel level : minecraftServer.getAllLevels()) {
				for (var entity : level.getAllEntities()) {
					if (entity instanceof DragonEntity dragon && dragon.hasLeftForGood()) {
						return true;
					}
				}
			}
			return false;
		});
		server.runCommand("execute at @p run dragons census");
		if (!left) {
			throw new AssertionError("a hatchling whose bond deadline is in the past never "
					+ "gave up on its player — the hour would never end");
		}
		context.takeScreenshot("dragon_bond_expired");
	}

	/** Is any dragon in the world tame? Asked of the server, not scraped from chat. */
	private static boolean tamed(TestServerContext server) {
		return server.computeOnServer(minecraftServer -> {
			for (ServerLevel level : minecraftServer.getAllLevels()) {
				for (var entity : level.getAllEntities()) {
					if (entity instanceof DragonEntity dragon && dragon.isTame()) {
						return true;
					}
				}
			}
			return false;
		});
	}

	/** A flat stone floor with the player standing on it, facing south and slightly down. */
	private static void stage(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("execute at @p run fill ~-24 ~-1 ~-4 ~24 ~-1 ~30 minecraft:stone");
		// 49x13x35 = 22,295 blocks. /fill silently refuses anything over the
		// max_block_modifications gamerule (32,768) and says so only in chat, so a taller
		// clearance here would leave the stage un-cleared with no error anywhere.
		server.runCommand("execute at @p run fill ~-24 ~ ~-4 ~24 ~12 ~30 minecraft:air");
		server.runCommand("execute at @p run tp @p ~ ~ ~ 0 8");
		context.waitTicks(40);
	}

	private static void clear(TestServerContext server) {
		server.runCommand("kill @e[type=dragons:dragon]");
	}
}
