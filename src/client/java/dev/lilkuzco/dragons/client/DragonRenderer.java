package dev.lilkuzco.dragons.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.lilkuzco.dragons.entity.DragonEntity;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.dragon.EnderDragonModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * Draws a dragon on vanilla's Ender Dragon model.
 *
 * <p>The model is used as-is — no copy, no fork. It poses itself entirely from
 * {@link DragonRenderState}'s inherited fields, which the entity fills each tick, so the
 * neck, tail and wings animate exactly as the boss's do. What this renderer adds is three
 * things vanilla's has no notion of: a per-variant hide, a saddle drawn over it, and a
 * scale.
 *
 * <h2>The transform</h2>
 * The pose block below is vanilla's, in vanilla's order, with one insertion. Those
 * numbers are not arbitrary — {@code EnderDragonModel} parks its root at
 * {@code y = (bounce - 2) * 16} and {@code z = -48} and expects the renderer to undo it,
 * so changing the sequence puts the dragon somewhere other than where its hitbox is. The
 * scale goes in <em>first</em>, before the rotations, so it multiplies the whole
 * arrangement about the entity's own origin rather than shifting the model off its feet.
 */
public class DragonRenderer extends EntityRenderer<DragonEntity, DragonRenderState> {
	/**
	 * Half the Ender Dragon. Together with the 2x2 hitbox in {@code DragonsEntities} this
	 * gives a creature about four blocks across the wings and eight nose to tail: big
	 * enough to be a dragon, small enough that a flying path can be found for it.
	 */
	public static final float RENDER_SCALE = 0.5F;

	private final EnderDragonModel model;

	public DragonRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 1.2F;
		this.model = new EnderDragonModel(context.bakeLayer(ModelLayers.ENDER_DRAGON));
	}

	@Override
	public DragonRenderState createRenderState() {
		return new DragonRenderState();
	}

	@Override
	public void extractRenderState(DragonEntity entity, DragonRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.flapTime = Mth.lerp(partialTicks, entity.oFlapTime, entity.flapTime);
		state.deathTime = entity.deathTime > 0 ? entity.deathTime + partialTicks : 0.0F;
		state.hasRedOverlay = entity.hurtTime > 0;
		state.beamOffset = null;                 // no end crystals out here
		state.isLandingOrTakingOff = false;
		// a perched dragon rears its neck the way the boss does on the podium; airborne
		// it lets the flight history drive the neck instead
		state.isSitting = entity.onGround();
		state.distanceToEgg = 0.0;
		state.partialTicks = entity.isDeadOrDying() ? 0.0F : partialTicks;
		state.flightHistory.copyFrom(entity.flightHistory);

		state.variant = entity.getVariant();
		state.saddled = entity.isSaddled();
		state.ageScale = entity.getAgeScale();
	}

	@Override
	public void submit(DragonRenderState state, PoseStack poseStack,
			SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		float scale = RENDER_SCALE * state.ageScale;
		poseStack.scale(scale, scale, scale);

		float yr = state.getHistoricalPos(7).yRot();
		float pitch = (float) (state.getHistoricalPos(5).y() - state.getHistoricalPos(10).y());
		poseStack.mulPose(Axis.YP.rotationDegrees(-yr));
		poseStack.mulPose(Axis.XP.rotationDegrees(pitch * 10.0F));
		poseStack.translate(0.0F, 0.0F, 1.0F);
		poseStack.scale(-1.0F, -1.0F, 1.0F);
		poseStack.translate(0.0F, EnderDragonModel.MODEL_Y_OFFSET, 0.0F);

		int overlay = OverlayTexture.pack(0.0F, state.hasRedOverlay);
		// The saddle is a different SHEET, not a second pass. Drawing it as an overlay
		// would submit this model twice with identical geometry, so both submissions
		// would write the same depth and z-fight; a texture swap cannot.
		Identifier hide = state.saddled ? state.variant.saddledTexture() : state.variant.texture();
		collector.submitModel(this.model, state, poseStack, hide,
				state.lightCoords, overlay, state.outlineColor, null);
		// eyes are their own emissive pass, as they are on the boss
		collector.submitModel(this.model, state, poseStack, eyeLayer(state.variant.eyes()),
				state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor, null);

		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}

	private static RenderType eyeLayer(Identifier texture) {
		return RenderTypes.eyes(texture);
	}

	/** A hatchling's shadow must shrink with it, or a speck sits in a dinner plate. */
	@Override
	protected float getShadowRadius(DragonRenderState state) {
		return this.shadowRadius * state.ageScale;
	}

	/**
	 * The hitbox is 2x2 but the wings and tail reach well past it, so culling by the
	 * hitbox alone makes a dragon vanish at the edge of the screen while most of it is
	 * still on it.
	 */
	@Override
	protected AABB getBoundingBoxForCulling(DragonEntity entity) {
		return super.getBoundingBoxForCulling(entity).inflate(8.0 * entity.getAgeScale());
	}
}
