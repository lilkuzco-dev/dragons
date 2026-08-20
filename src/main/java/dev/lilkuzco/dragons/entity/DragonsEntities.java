package dev.lilkuzco.dragons.entity;

import dev.lilkuzco.dragons.Dragons;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.phys.Vec3;

public final class DragonsEntities {
	/**
	 * One entity type; the seven colours are a synched variant, not seven types.
	 *
	 * <p>Sized for a creature that has to be able to <em>path</em>. The Ender Dragon's own
	 * 16x8 hitbox is not a candidate: {@code FlyNodeEvaluator} needs a corridor as wide as
	 * the mob, and nothing that wide fits between two trees, so a 16-wide dragon would
	 * fail every path and then drift into the scenery it was supposed to route around.
	 * The model is drawn at half the Ender Dragon's scale to match (see
	 * {@code DragonRenderer#RENDER_SCALE}); hatchlings are a quarter of that again via
	 * {@link DragonEntity#getAgeScale()}, which scales this box for them automatically.
	 *
	 * <p>The rider sits forward of centre, over the saddle painted on the body's front
	 * third rather than in the middle of the back.
	 */
	public static final EntityType<DragonEntity> DRAGON = register("dragon",
			EntityType.Builder.of(DragonEntity::new, MobCategory.CREATURE)
					.sized(2.0F, 2.0F)
					.eyeHeight(1.7F)
					.passengerAttachments(new Vec3(0.0, 1.85, -0.6))
					.clientTrackingRange(12));

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(
			String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Dragons.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(DRAGON, DragonEntity.createAttributes());
		// no SpawnPlacements registration and no biome spawn entry, on purpose: dragons
		// are hatched from a found egg, never spawned by the world
	}

	private DragonsEntities() {
	}
}
