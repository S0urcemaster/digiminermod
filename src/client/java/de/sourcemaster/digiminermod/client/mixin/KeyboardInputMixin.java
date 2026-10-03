package de.sourcemaster.digiminermod.client.mixin;

import de.sourcemaster.digiminermod.client.config.DigiMinerConfig;
import de.sourcemaster.digiminermod.client.input.ControllerSupport;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends ClientInput {

	@Inject(method = "tick", at = @At("TAIL"))
	private void digiminermod$addControllerMovement(CallbackInfo callbackInfo) {
		ControllerSupport.Snapshot controller = ControllerSupport.poll();
		if (!controller.connected()) {
			return;
		}

		float deadzone = (float) DigiMinerConfig.get().moveDeadzone();
		float sideways = -ControllerSupport.applyDeadzone(controller.leftX(), deadzone);
		float forward = -ControllerSupport.applyDeadzone(controller.leftY(), deadzone);
		if (sideways == 0.0F && forward == 0.0F) {
			return;
		}

		Vec2 movement = new Vec2(sideways, forward);
		if (movement.lengthSquared() > 1.0F) {
			movement = movement.normalized();
		}
		this.moveVector = movement;
		this.keyPresses = new Input(
				forward > 0.0F, forward < 0.0F, sideways > 0.0F, sideways < 0.0F,
				this.keyPresses.jump(), this.keyPresses.shift(), this.keyPresses.sprint());
	}
}
