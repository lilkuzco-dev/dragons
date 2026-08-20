package dev.lilkuzco.dragons;

import dev.lilkuzco.dragons.block.DragonsBlocks;
import dev.lilkuzco.dragons.entity.DragonVariant;
import java.util.Map;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

/**
 * Where eggs come from.
 *
 * <p>A dragon is the most valuable thing this mod adds and there is exactly one way to
 * get one, so the egg is placed where an expedition already goes rather than anywhere a
 * player might stumble. Every entry is a single extra pool of one roll, gated on its own
 * chance, so it adds to whatever the chest already contained instead of competing with
 * it — and a chest that rolls the pool picks one of the seven colours uniformly.
 *
 * <p>Chances are per chest, and every table here is a treasure chest that a structure has
 * only a handful of.
 */
public final class DragonsLoot {
	private static final Map<net.minecraft.resources.ResourceKey<
			net.minecraft.world.level.storage.loot.LootTable>, Float> SOURCES = Map.of(
			BuiltInLootTables.END_CITY_TREASURE, 0.30F,   // the dragon's own dimension
			BuiltInLootTables.BASTION_TREASURE, 0.15F,
			BuiltInLootTables.NETHER_BRIDGE, 0.08F,
			BuiltInLootTables.STRONGHOLD_LIBRARY, 0.08F,
			BuiltInLootTables.WOODLAND_MANSION, 0.10F,
			BuiltInLootTables.DESERT_PYRAMID, 0.06F);

	public static void init() {
		LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
			Float chance = SOURCES.get(key);
			// only touch the vanilla table itself; a datapack that replaces one has made
			// a deliberate choice and should not get our pool bolted back on
			if (chance == null || !source.isBuiltin()) {
				return;
			}
			LootPool.Builder pool = LootPool.lootPool()
					.setRolls(ConstantValue.exactly(1.0F))
					.when(LootItemRandomChanceCondition.randomChance(chance));
			for (DragonVariant variant : DragonVariant.ALL) {
				pool.add(LootItem.lootTableItem(DragonsBlocks.EGGS.get(variant)));
			}
			builder.pool(pool.build());
		});
	}

	private DragonsLoot() {
	}
}
