package dev.lilkuzco.dragons.client;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

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
		}
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
