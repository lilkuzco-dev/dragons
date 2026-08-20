package dev.lilkuzco.dragons;

import dev.lilkuzco.dragons.block.DragonsBlocks;
import dev.lilkuzco.dragons.entity.DragonsEntities;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Dragons implements ModInitializer {
	public static final String MOD_ID = "dragons";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		// Order matters and is not cosmetic: the egg blocks name the entity type when
		// they hatch, and the saddle's EQUIPPABLE component pins itself to the dragon
		// type at construction, so the entity has to be registered first.
		DragonsEntities.init();
		DragonsBlocks.init();
		DragonsItems.init();
		DragonsLoot.init();
		DragonsCommands.init();
		LOGGER.info("Dragons initialized");
	}
}
