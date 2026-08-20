package dev.lilkuzco.dragons.client;

import dev.lilkuzco.dragons.entity.DragonVariant;
import net.minecraft.client.renderer.entity.state.EnderDragonRenderState;

/**
 * Extends vanilla's Ender Dragon render state rather than replacing it.
 *
 * <p>{@code EnderDragonModel} is an {@code EntityModel<EnderDragonRenderState>} and reads
 * {@code flapTime}, {@code partialTicks} and the 64-tick {@code flightHistory} straight
 * off it. Subclassing is what lets this mod hand vanilla's model exactly the state it
 * expects while carrying the three extra facts a re-hued, saddleable, possibly-tiny
 * dragon needs.
 *
 * <p>(This is also why the renderer extends {@code EntityRenderer} and not
 * {@code LivingEntityRenderer}: that one requires a {@code LivingEntityRenderState}, and
 * a state cannot extend both.)
 */
public class DragonRenderState extends EnderDragonRenderState {
	public DragonVariant variant = DragonVariant.CRIMSON;
	public boolean saddled;
	/** 1 for an adult, an eighth for a hatchling. */
	public float ageScale = 1.0F;
}
