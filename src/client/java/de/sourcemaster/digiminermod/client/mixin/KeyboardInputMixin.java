package de.sourcemaster.digiminermod.client.mixin;

import de.sourcemaster.digiminermod.client.config.DigiMinerConfig;
import de.sourcemaster.digiminermod.client.input.ControllerSupport;
import de.sourcemaster.digiminermod.client.input.ControllerAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends ClientInput {
	@Unique private boolean digiminermod$controllerSneaking;
	@Unique private boolean digiminermod$previousSneakButton;

	@Inject(method = "tick", at = @At("TAIL"))
	private void digiminermod$addControllerMovement(CallbackInfo callbackInfo) {
		ControllerSupport.Snapshot controller = ControllerSupport.poll();
		DigiMinerConfig config = DigiMinerConfig.get();
		boolean sneakButton = controller.connected()
				&& controller.pressed(config.binding(ControllerAction.WORLD_SNEAK));
		// A visible screen owns controller input. Keeping this generic makes the
		// lock apply to every current and future container screen automatically.
		if (Minecraft.getInstance().gui.screen() != null) {
			this.digiminermod$previousSneakButton = sneakButton;
			this.moveVector = Vec2.ZERO;
			this.keyPresses = new Input(false, false, false, false, false, false, false);
			return;
		}
		if (!controller.connected()) {
			this.digiminermod$controllerSneaking = false;
			this.digiminermod$previousSneakButton = false;
			return;
		}

		if (sneakButton && !this.digiminermod$previousSneakButton) {
			this.digiminermod$controllerSneaking = !this.digiminermod$controllerSneaking;
		}
		this.digiminermod$previousSneakButton = sneakButton;
		boolean jump = this.keyPresses.jump() || controller.pressed(config.binding(ControllerAction.WORLD_JUMP));
		boolean sneak = this.keyPresses.shift() || this.digiminermod$controllerSneaking;
		boolean sprint = this.keyPresses.sprint() || controller.pressed(config.binding(ControllerAction.WORLD_SPRINT));
		float deadzone = (float) config.moveDeadzone();
		float sideways = -ControllerSupport.applyDeadzone(controller.leftX(), deadzone);
		float forward = -ControllerSupport.applyDeadzone(controller.leftY(), deadzone);
		if (sideways == 0.0F && forward == 0.0F) {
			this.keyPresses = new Input(this.keyPresses.forward(), this.keyPresses.backward(),
					this.keyPresses.left(), this.keyPresses.right(), jump, sneak, sprint);
			return;
		}

		Vec2 movement = new Vec2(sideways, forward);
		if (movement.lengthSquared() > 1.0F) {
			movement = movement.normalized();
		}
		this.moveVector = movement;
		this.keyPresses = new Input(
				forward > 0.0F, forward < 0.0F, sideways > 0.0F, sideways < 0.0F,
				jump, sneak, sprint);
	}
}
