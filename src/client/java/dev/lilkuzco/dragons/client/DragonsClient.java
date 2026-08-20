package dev.lilkuzco.dragons.client;

import dev.lilkuzco.dragons.entity.DragonsEntities;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.entity.EntityRenderers;

public class DragonsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// No ModelLayerRegistry call: the dragon is drawn on vanilla's own
		// ModelLayers.ENDER_DRAGON layer, which is already registered and already baked.
		// Registering a duplicate would be a second copy of the same mesh.
		//
		// This registration is not optional decoration. EntityRenderDispatcher returns
		// null for a type with no renderer and the render thread dereferences it, so a
		// missing line here is a hard client crash the moment one is spawned — not a
		// missing texture.
		EntityRenderers.register(DragonsEntities.DRAGON, DragonRenderer::new);
	}
}
