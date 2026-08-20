package dev.lilkuzco.dragons;

import dev.lilkuzco.dragons.block.DragonsBlocks;
import dev.lilkuzco.dragons.entity.DragonVariant;
import dev.lilkuzco.dragons.entity.DragonsEntities;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.equipment.Equippable;

public final class DragonsItems {
	/**
	 * The dragon saddle: a happy-ghast harness scaled up for something with claws.
	 *
	 * <p>It rides in the vanilla {@link EquipmentSlot#SADDLE} slot, so saving, syncing and
	 * dropping on death are all vanilla's problem rather than ours, and shears take it
	 * back off. {@code allowedEntities} pins it to the dragon — this is why
	 * {@link DragonsEntities} has to be initialised before this class, and why the
	 * reference below is a real one rather than a lazily-resolved id.
	 *
	 * <p>Whether a <em>particular</em> dragon will accept it is
	 * {@code DragonEntity#canUseSlot}: tamed, grown, alive.
	 */
	public static final Item DRAGON_SADDLE = register("dragon_saddle", properties -> new Item(properties
			.stacksTo(1)
			.rarity(Rarity.RARE)
			.component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.SADDLE)
					.setEquipSound(SoundEvents.HORSE_SADDLE)
					.setAllowedEntities(DragonsEntities.DRAGON)
					.setEquipOnInteract(true)
					.setCanBeSheared(true)
					.setShearingSound(SoundEvents.SADDLE_UNEQUIP)
					.build())));

	/**
	 * Creative/testing only. Dragons are not obtainable this way in survival — the egg is
	 * the survival path — but the render battery and any future debugging need a way to
	 * put one in front of the camera without waiting out an incubation.
	 */
	public static final Item DRAGON_SPAWN_EGG = register("dragon_spawn_egg",
			properties -> new SpawnEggItem(properties.spawnEgg(DragonsEntities.DRAGON)));

	public static final ResourceKey<CreativeModeTab> TAB_KEY =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, Dragons.id("dragons"));

	private static Item register(String name, Function<Item.Properties, Item> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Dragons.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key,
				factory.apply(new Item.Properties().setId(key)));
	}

	/** Everything the tab shows, in display order — also what the tests assert against. */
	public static List<Item> tabContents() {
		List<Item> out = new ArrayList<>();
		out.add(DRAGON_SADDLE);
		for (DragonVariant variant : DragonVariant.ALL) {
			out.add(DragonsBlocks.EGGS.get(variant).asItem());
		}
		out.add(DRAGON_SPAWN_EGG);
		return out;
	}

	public static void init() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB_KEY,
				FabricCreativeModeTab.builder()
						.title(Component.translatable("itemGroup.dragons.dragons"))
						.icon(() -> new ItemStack(DragonsBlocks.EGGS.get(DragonVariant.AMETHYST)))
						.displayItems((params, output) -> tabContents().forEach(output::accept))
						.build());
	}

	private DragonsItems() {
	}
}
