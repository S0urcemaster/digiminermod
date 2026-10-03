package de.sourcemaster.digiminermod.client;

import de.sourcemaster.digiminermod.client.config.DigiMinerConfig;
import de.sourcemaster.digiminermod.client.input.ControllerAction;
import de.sourcemaster.digiminermod.client.input.ControllerSupport;
import de.sourcemaster.digiminermod.client.screen.RadialInventoryScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class DigiMinerModClient implements ClientModInitializer {
	private boolean previousMenuButton;
	private double lookVelocityHorizontal;
	private double lookVelocityVertical;
	private boolean controllerAttack;
	private boolean controllerUse;

	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			ControllerSupport.Snapshot controller = ControllerSupport.poll();
			if (controller.connected() && client.player != null && client.gui.screen() == null) {
				DigiMinerConfig config = DigiMinerConfig.get();
				boolean attack = controller.pressed(config.binding(ControllerAction.WORLD_ATTACK));
				boolean use = controller.pressed(config.binding(ControllerAction.WORLD_USE));
				if (attack != this.controllerAttack) client.options.keyAttack.setDown(attack);
				if (use != this.controllerUse) client.options.keyUse.setDown(use);
				this.controllerAttack = attack;
				this.controllerUse = use;
				float lookX = ControllerSupport.applyDeadzone(controller.rightX(), (float) config.lookDeadzone());
				float lookY = ControllerSupport.applyDeadzone(controller.rightY(), (float) config.lookDeadzone());
				double targetHorizontal = lookX * config.lookSpeedHorizontal();
				double targetVertical = lookY * config.lookSpeedVertical();
				this.lookVelocityHorizontal = approach(this.lookVelocityHorizontal, targetHorizontal,
						config.lookAccelerationHorizontal() / 20.0);
				this.lookVelocityVertical = approach(this.lookVelocityVertical, targetVertical,
						config.lookAccelerationVertical() / 20.0);
				// Entity.turn applies another factor of 0.15; at 20 ticks/s this yields degrees/s.
				client.player.turn(this.lookVelocityHorizontal / 3.0,
						this.lookVelocityVertical / 3.0 * (config.invertLookY() ? -1.0 : 1.0));
			} else {
				if (this.controllerAttack) client.options.keyAttack.setDown(false);
				if (this.controllerUse) client.options.keyUse.setDown(false);
				this.controllerAttack = false;
				this.controllerUse = false;
				this.lookVelocityHorizontal = 0.0;
				this.lookVelocityVertical = 0.0;
			}
			ControllerAction menuAction = client.gui.screen() instanceof RadialInventoryScreen
					? ControllerAction.INVENTORY_CLOSE : ControllerAction.OPEN_INVENTORY;
			boolean menuPressed = controller.connected()
					&& controller.pressed(DigiMinerConfig.get().binding(menuAction));
			if (menuPressed && !this.previousMenuButton && client.player != null) {
				if (client.gui.screen() instanceof RadialInventoryScreen) {
					client.gui.setScreen(null);
				} else if (client.gui.screen() == null) {
					client.gui.setScreen(new RadialInventoryScreen());
				}
			}
			this.previousMenuButton = menuPressed;
		});
	}

	private static double approach(double current, double target, double maximumChange) {
		if (current < target) {
			return Math.min(current + maximumChange, target);
		}
		return Math.max(current - maximumChange, target);
	}
}
