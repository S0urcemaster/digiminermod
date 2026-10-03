package de.sourcemaster.digiminermod.client;

import de.sourcemaster.digiminermod.DigiMinerMod;
import de.sourcemaster.digiminermod.client.config.DigiMinerConfig;
import de.sourcemaster.digiminermod.client.input.ControllerAction;
import de.sourcemaster.digiminermod.client.input.ControllerSupport;
import de.sourcemaster.digiminermod.client.screen.RadialInventoryScreen;
import de.sourcemaster.digiminermod.client.screen.RadialCraftingScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.player.Inventory;

public final class DigiMinerModClient implements ClientModInitializer {
	private static DigiMinerModClient instance;
	private boolean previousMenuButton;
	private double lookVelocityHorizontal;
	private double lookVelocityVertical;
	private boolean controllerAttack;
	private boolean previousAttackButton;
	private boolean miningLatched;
	private boolean previousHotbarPrevious;
	private boolean previousHotbarNext;
	private boolean controllerUse;
	private boolean previousPerspectiveButton;

	@Override
	public void onInitializeClient() {
		instance = this;
		ScannerHud scannerHud = new ScannerHud();
		HudElementRegistry.addLast(DigiMinerMod.id("spawner_scanner_hud"),
				(graphics, deltaTracker) -> scannerHud.extract(graphics));
	}

	public static void updateControllerFrame(Minecraft client, DeltaTracker deltaTracker) {
		if (instance != null) instance.updateFrame(client, deltaTracker);
	}

	private void updateFrame(Minecraft client, DeltaTracker deltaTracker) {
			ControllerSupport.Snapshot controller = ControllerSupport.pollFrame();
			double deltaSeconds = Math.min(0.1, deltaTracker.getRealtimeDeltaTicks() / 20.0);
			if (controller.connected() && client.player != null && client.gui.screen() == null) {
				DigiMinerConfig config = DigiMinerConfig.get();
				boolean attackButton = controller.pressed(config.binding(ControllerAction.WORLD_ATTACK));
				if (config.startStopMining()) {
					if (attackButton && !this.previousAttackButton) this.miningLatched = !this.miningLatched;
				} else {
					this.miningLatched = false;
				}
				boolean attack = config.startStopMining() ? this.miningLatched : attackButton;
				boolean use = controller.pressed(config.binding(ControllerAction.WORLD_USE));
				boolean hotbarPrevious = controller.pressed(config.binding(ControllerAction.INVENTORY_HOTBAR_PREVIOUS));
				boolean hotbarNext = controller.pressed(config.binding(ControllerAction.INVENTORY_HOTBAR_NEXT));
				int selectedSlot = client.player.getInventory().getSelectedSlot();
				if (hotbarPrevious && !this.previousHotbarPrevious) {
					client.player.getInventory().setSelectedSlot(Math.floorMod(selectedSlot - 1, Inventory.getSelectionSize()));
				}
				if (hotbarNext && !this.previousHotbarNext) {
					client.player.getInventory().setSelectedSlot((selectedSlot + 1) % Inventory.getSelectionSize());
				}
				this.previousHotbarPrevious = hotbarPrevious;
				this.previousHotbarNext = hotbarNext;
				boolean perspective = controller.pressed(config.binding(ControllerAction.WORLD_CHANGE_PERSPECTIVE));
				if (attack != this.controllerAttack) client.options.keyAttack.setDown(attack);
				if (use != this.controllerUse) client.options.keyUse.setDown(use);
				this.controllerAttack = attack;
				this.previousAttackButton = attackButton;
				this.controllerUse = use;
				if (perspective && !this.previousPerspectiveButton) {
					client.options.setCameraType(client.options.getCameraType().cycle());
				}
				this.previousPerspectiveButton = perspective;
				float lookX = ControllerSupport.applyDeadzone(controller.rightX(), (float) config.lookDeadzone());
				float lookY = ControllerSupport.applyDeadzone(controller.rightY(), (float) config.lookDeadzone());
				double targetHorizontal = lookX * config.lookSpeedHorizontal();
				double targetVertical = lookY * config.lookSpeedVertical();
				this.lookVelocityHorizontal = approach(this.lookVelocityHorizontal, targetHorizontal,
						config.lookAccelerationHorizontal() * deltaSeconds);
				this.lookVelocityVertical = approach(this.lookVelocityVertical, targetVertical,
						config.lookAccelerationVertical() * deltaSeconds);
				// Entity.turn applies a factor of 0.15, so convert degrees/s to this frame's input.
				client.player.turn(this.lookVelocityHorizontal * deltaSeconds / 0.15,
						this.lookVelocityVertical * deltaSeconds / 0.15 * (config.invertLookY() ? -1.0 : 1.0));
			} else {
				if (this.controllerAttack) client.options.keyAttack.setDown(false);
				if (this.controllerUse) client.options.keyUse.setDown(false);
				this.controllerAttack = false;
				this.previousAttackButton = controller.connected()
						&& controller.pressed(DigiMinerConfig.get().binding(ControllerAction.WORLD_ATTACK));
				this.miningLatched = false;
				this.previousHotbarPrevious = controller.connected()
						&& controller.pressed(DigiMinerConfig.get().binding(ControllerAction.INVENTORY_HOTBAR_PREVIOUS));
				this.previousHotbarNext = controller.connected()
						&& controller.pressed(DigiMinerConfig.get().binding(ControllerAction.INVENTORY_HOTBAR_NEXT));
				this.controllerUse = false;
				this.previousPerspectiveButton = controller.connected()
						&& controller.pressed(DigiMinerConfig.get().binding(ControllerAction.WORLD_CHANGE_PERSPECTIVE));
				this.lookVelocityHorizontal = 0.0;
				this.lookVelocityVertical = 0.0;
			}
			boolean inventoryOpen = client.gui.screen() instanceof RadialInventoryScreen
					|| client.gui.screen() instanceof RadialCraftingScreen
					|| client.gui.screen() instanceof CreativeModeInventoryScreen
					|| client.gui.screen() instanceof InventoryScreen;
			ControllerAction menuAction = inventoryOpen
					? ControllerAction.INVENTORY_CLOSE : ControllerAction.OPEN_INVENTORY;
			boolean menuPressed = controller.connected()
					&& controller.pressed(DigiMinerConfig.get().binding(menuAction));
			if (menuPressed && !this.previousMenuButton && client.player != null) {
				if (client.gui.screen() instanceof RadialCraftingScreen) {
					client.player.closeContainer();
				} else if (inventoryOpen) {
					client.gui.setScreen(null);
				} else if (client.gui.screen() == null) {
					client.gui.setScreen(client.player.hasInfiniteMaterials()
							? new InventoryScreen(client.player) : new RadialInventoryScreen());
				}
			}
			this.previousMenuButton = menuPressed;
	}

	private static double approach(double current, double target, double maximumChange) {
		if (current < target) {
			return Math.min(current + maximumChange, target);
		}
		return Math.max(current - maximumChange, target);
	}
}
