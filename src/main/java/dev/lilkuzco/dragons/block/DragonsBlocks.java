package dev.lilkuzco.dragons.block;

import dev.lilkuzco.dragons.Dragons;
import dev.lilkuzco.dragons.entity.DragonVariant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/** One egg block per colour, plus the block entity all seven share. */
public final class DragonsBlocks {
	public static final Map<DragonVariant, DragonEggBlock> EGGS = new LinkedHashMap<>();

	static {
		for (DragonVariant variant : DragonVariant.ALL) {
			EGGS.put(variant, register(variant));
		}
	}

	public static final net.minecraft.world.level.block.entity.BlockEntityType<DragonEggBlockEntity>
			DRAGON_EGG_BLOCK_ENTITY = Registry.register(
					BuiltInRegistries.BLOCK_ENTITY_TYPE, Dragons.id("dragon_egg"),
					new net.minecraft.world.level.block.entity.BlockEntityType<>(
							DragonEggBlockEntity::new, Set.copyOf(EGGS.values())));

	public static String eggName(DragonVariant variant) {
		return variant.id() + "_dragon_egg";
	}

	private static DragonEggBlock register(DragonVariant variant) {
		Identifier id = Dragons.id(eggName(variant));
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);
		DragonEggBlock block = Registry.register(BuiltInRegistries.BLOCK, blockKey,
				new DragonEggBlock(variant, BlockBehaviour.Properties.of()
						.mapColor(MapColor.COLOR_BLACK)
						.strength(3.0F, 9.0F)
						.sound(SoundType.METAL)
						.lightLevel(state -> 3 + state.getValue(DragonEggBlock.INCUBATION) * 2)
						.randomTicks()
						.noOcclusion()
						// an egg mid-incubation must not be shoved out from under its
						// campfire by a piston, which would strand its clock
						.pushReaction(PushReaction.BLOCK)
						.setId(blockKey)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
		Registry.register(BuiltInRegistries.ITEM, itemKey,
				new BlockItem(block, new Item.Properties()
						.useBlockDescriptionPrefix()
						.rarity(net.minecraft.world.item.Rarity.EPIC)
						.stacksTo(1)
						.setId(itemKey)));
		return block;
	}

	public static void init() {
	}

	private DragonsBlocks() {
	}
}
