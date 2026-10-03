package de.sourcemaster.digiminermod.client.mixin;

import de.sourcemaster.digiminermod.client.DigiMinerModClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Shadow @Final private Minecraft minecraft;

	@Inject(method = "extract", at = @At("HEAD"))
	private void digiminermod$updateController(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo callbackInfo) {
		DigiMinerModClient.updateControllerFrame(this.minecraft, deltaTracker);
	}
}
