package de.sourcemaster.digiminermod.client;

import de.sourcemaster.digiminermod.client.input.ControllerSupport;
import de.sourcemaster.digiminermod.client.screen.RadialInventoryScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class DigiMinerModClient implements ClientModInitializer {
	private boolean previousMenuButton;

	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			ControllerSupport.Snapshot controller = ControllerSupport.poll();
			boolean menuPressed = controller.connected() && controller.menuLeft();
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
}
