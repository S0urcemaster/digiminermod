package de.sourcemaster.digiminermod.client;

import de.sourcemaster.digiminermod.drone.DroneEntity;
import net.minecraft.client.model.animal.golem.CopperGolemModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.CopperGolemRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.animal.golem.CopperGolemState;
import net.minecraft.world.level.block.WeatheringCopper;

public final class DroneRenderer extends MobRenderer<DroneEntity, CopperGolemRenderState, CopperGolemModel> {
	private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/entity/copper_golem/copper_golem.png");

	public DroneRenderer(EntityRendererProvider.Context context) {
		super(context, new CopperGolemModel(context.bakeLayer(ModelLayers.COPPER_GOLEM)), 0.5F);
	}

	@Override public CopperGolemRenderState createRenderState() { return new CopperGolemRenderState(); }
	@Override
	public void extractRenderState(DroneEntity drone, CopperGolemRenderState state, float partialTick) {
		super.extractRenderState(drone, state, partialTick);
		state.weathering = WeatheringCopper.WeatherState.UNAFFECTED;
		state.copperGolemState = CopperGolemState.IDLE;
	}
	@Override public Identifier getTextureLocation(CopperGolemRenderState state) { return TEXTURE; }
}
