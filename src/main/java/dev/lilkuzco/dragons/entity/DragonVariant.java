package dev.lilkuzco.dragons.entity;

import dev.lilkuzco.dragons.Dragons;
import com.mojang.serialization.Codec;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

/**
 * The seven hides.
 *
 * <p>This list is the single source of truth for the roster: {@code tools/gen-textures.py}
 * paints a bare hide, a saddled hide and an eye sheet per name, {@code tools/asset-audit.py} holds the two
 * lists against each other, and the renderer resolves a texture by asking a variant for
 * its own path. Adding an eighth colour therefore means adding it here and in the
 * generator — and the build fails until both are done, rather than shipping a
 * checkerboard dragon.
 */
public enum DragonVariant {
	CRIMSON("crimson"),
	EMERALD("emerald"),
	SAPPHIRE("sapphire"),
	AMETHYST("amethyst"),
	AMBER("amber"),
	OBSIDIAN("obsidian"),
	IVORY("ivory");

	public static final List<DragonVariant> ALL = List.of(values());
	/** For the egg block's own codec, so all seven blocks are not one colour on paper. */
	public static final Codec<DragonVariant> CODEC =
			Codec.STRING.xmap(DragonVariant::byName, DragonVariant::id);
	private static final Map<String, DragonVariant> BY_NAME =
			ALL.stream().collect(Collectors.toUnmodifiableMap(DragonVariant::id, Function.identity()));

	private final String id;
	private final Identifier texture;
	private final Identifier saddledTexture;
	private final Identifier eyes;

	DragonVariant(String id) {
		this.id = id;
		this.texture = Dragons.id("textures/entity/dragon/" + id + ".png");
		this.saddledTexture = Dragons.id("textures/entity/dragon/" + id + "_saddled.png");
		this.eyes = Dragons.id("textures/entity/dragon/" + id + "_eyes.png");
	}

	public String id() {
		return this.id;
	}

	public Identifier texture() {
		return this.texture;
	}

	/**
	 * The same hide with the saddle already painted on.
	 *
	 * <p>A separate sheet rather than a second render pass: an overlay would submit
	 * vanilla's model twice at identical geometry, so both passes would write the same
	 * depth values and z-fight. One texture swap cannot flicker.
	 */
	public Identifier saddledTexture() {
		return this.saddledTexture;
	}

	public Identifier eyes() {
		return this.eyes;
	}

	/** Unknown names fall back to the first variant rather than crashing a world load. */
	public static DragonVariant byName(String name) {
		return BY_NAME.getOrDefault(name, CRIMSON);
	}

	public static DragonVariant random(RandomSource random) {
		return ALL.get(random.nextInt(ALL.size()));
	}
}
