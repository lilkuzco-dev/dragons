package dev.lilkuzco.dragons;

import dev.lilkuzco.dragons.block.DragonsBlocks;
import dev.lilkuzco.dragons.entity.DragonVariant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

/**
 * Where eggs come from: castles, and nowhere else.
 *
 * <p>Warfront's four faction castles are the only source. That is a deliberate coupling —
 * the most valuable thing this mod adds is gated behind the hardest structure in the pack,
 * so getting a dragon means taking a castle rather than getting lucky in a desert pyramid.
 *
 * <h2>Why the chance looks so small</h2>
 * Each castle is one structure holding <b>16 to 24 chests</b>, and every one of them rolls
 * the same table. A "30% chance" per chest would therefore be six eggs per castle, not
 * one. {@value #CASTLE_CHANCE} per chest works out to roughly one egg per castle taken —
 * about a 60-70% chance of at least one, occasionally two, sometimes none. The number to
 * reason about is always <em>chests times chance</em>, never the chance on its own.
 *
 * <h2>This mod is unobtainable without Warfront</h2>
 * There is no fallback source, which means dragons cannot be obtained in survival at all
 * if Warfront is missing — and that would present as "I have looted everything and never
 * found an egg", with nothing in any log to explain it. So both halves are checked out
 * loud: whether the mod is present at all, and whether each table this class names
 * actually turned up during the loot reload. A renamed table on Warfront's side is the
 * same silent failure as an absent mod, and gets the same warning.
 */
public final class DragonsLoot {
	private static final String WARFRONT = "warfront";

	/**
	 * Per chest. See the class note: multiply by 16-24 to get the per-castle rate.
	 */
	private static final float CASTLE_CHANCE = 0.05F;
	/**
	 * The vault is a single chest rather than a room full of them, so it carries a real
	 * chance on its own. (Warfront 0.4.7 ships this table but no structure references it
	 * yet; an entry here simply never rolls until one does.)
	 */
	private static final float VAULT_CHANCE = 0.5F;

	private static final Map<ResourceKey<LootTable>, Float> CASTLES = new LinkedHashMap<>();

	static {
		castle("castle/aegis", CASTLE_CHANCE);
		castle("castle/dracula", CASTLE_CHANCE);
		castle("castle/sarab", CASTLE_CHANCE);
		castle("castle/vostok", CASTLE_CHANCE);
		castle("castle/hidden_vault", VAULT_CHANCE);
	}

	/** Tables seen during the current loot reload, for the absence check below. */
	private static final Set<ResourceKey<LootTable>> SEEN = ConcurrentHashMap.newKeySet();
	/** Snapshot of the last completed reload, for {@code /dragons loot}. */
	private static volatile Set<ResourceKey<LootTable>> lastSeen = Set.of();

	private static void castle(String path, float chance) {
		CASTLES.put(ResourceKey.create(Registries.LOOT_TABLE,
				Identifier.fromNamespaceAndPath(WARFRONT, path)), chance);
	}

	public static Map<ResourceKey<LootTable>, Float> targets() {
		return Map.copyOf(CASTLES);
	}

	public static boolean wasSeen(ResourceKey<LootTable> table) {
		return lastSeen.contains(table);
	}

	public static boolean warfrontPresent() {
		return FabricLoader.getInstance().isModLoaded(WARFRONT);
	}

	public static void init() {
		if (!warfrontPresent()) {
			Dragons.LOGGER.warn("Warfront is not installed. Dragon eggs are castle loot and have "
					+ "no other source, so dragons cannot be obtained in survival — only via "
					+ "the creative spawn egg or /summon.");
		}

		LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
			Float chance = CASTLES.get(key);
			if (chance == null) {
				return;
			}
			SEEN.add(key);
			// A datapack that REPLACES a castle table outright has made a deliberate
			// choice about what is in that chest; do not bolt our pool back on.
			if (!source.isBuiltin()) {
				return;
			}
			// One extra pool of one roll, gated on its own chance, so it adds to whatever
			// the castle already held instead of competing for a slot in it. When the pool
			// does roll it picks one of the seven colours uniformly.
			LootPool.Builder pool = LootPool.lootPool()
					.setRolls(ConstantValue.exactly(1.0F))
					.when(LootItemRandomChanceCondition.randomChance(chance));
			for (DragonVariant variant : DragonVariant.ALL) {
				pool.add(LootItem.lootTableItem(DragonsBlocks.EGGS.get(variant)));
			}
			builder.pool(pool.build());
		});

		// MODIFY has finished for every table by the time this fires, so anything still
		// missing from SEEN is a table we are keyed to that does not exist.
		LootTableEvents.ALL_LOADED.register((resourceManager, registry) -> {
			Set<ResourceKey<LootTable>> seen = Set.copyOf(SEEN);
			SEEN.clear();
			lastSeen = seen;
			if (!warfrontPresent()) {
				return;
			}
			for (ResourceKey<LootTable> table : CASTLES.keySet()) {
				if (!seen.contains(table)) {
					Dragons.LOGGER.warn("Warfront is installed but its loot table {} never "
							+ "loaded, so no dragon eggs will appear in it. The table has "
							+ "probably been renamed on Warfront's side.",
							table.identifier());
				}
			}
			Dragons.LOGGER.info("Dragon eggs wired into {}/{} castle loot tables",
					seen.size(), CASTLES.size());
		});
	}

	private DragonsLoot() {
	}
}
