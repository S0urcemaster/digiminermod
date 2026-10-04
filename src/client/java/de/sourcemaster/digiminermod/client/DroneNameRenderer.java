package de.sourcemaster.digiminermod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import de.sourcemaster.digiminermod.drone.DroneBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

public final class DroneNameRenderer implements BlockEntityRenderer<DroneBlockEntity, DroneNameRenderer.State> {
	public DroneNameRenderer(BlockEntityRendererProvider.Context context) {}

	@Override public State createRenderState() { return new State(); }

	@Override
	public void extractRenderState(DroneBlockEntity drone, State state, float partialTick, Vec3 cameraPosition,
			ModelFeatureRenderer.CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(drone, state, partialTick, cameraPosition, breakProgress);
		state.name = drone.getDroneName().isEmpty() ? null : Component.literal(drone.getDroneName());
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraState) {
		if (state.name == null) return;
		collector.submitNameTag(poseStack, new Vec3(0.5, 1.35, 0.5), 0, state.name, true, state.lightCoords, cameraState);
	}

	public static final class State extends BlockEntityRenderState {
		private Component name;
	}
}
