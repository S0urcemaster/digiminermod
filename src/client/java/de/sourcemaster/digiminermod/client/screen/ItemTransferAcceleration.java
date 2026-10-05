package de.sourcemaster.digiminermod.client.screen;

import de.sourcemaster.digiminermod.client.config.DigiMinerConfig;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import de.sourcemaster.digiminermod.InventoryNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.inventory.AbstractContainerMenu;

/** Tick-based repeat acceleration shared by all controller inventory screens. */
final class ItemTransferAcceleration {
	private static final int INITIAL_REPEAT_DELAY_TICKS = 6;
	private int heldTicks;

	int amount(boolean pressed, boolean wasPressed) {
		if (!pressed) {
			this.heldTicks = 0;
			return 0;
		}
		if (!wasPressed) {
			this.heldTicks = 0;
			return 1;
		}
		this.heldTicks++;
		if (this.heldTicks <= INITIAL_REPEAT_DELAY_TICKS) return 0;
		return 1 + this.heldTicks / DigiMinerConfig.get().itemTransferAccelerationTicks();
	}

	static boolean isFull(Slot slot) {
		ItemStack stack = slot.getItem();
		return !stack.isEmpty() && (stack.getCount() >= Math.min(stack.getMaxStackSize(), slot.getMaxStackSize(stack))
				|| !slot.mayPlace(stack));
	}

	static void transferOne(AbstractContainerMenu menu, int sourceSlot, int targetSlot) {
		ClientPlayNetworking.send(new InventoryNetworking.TransferOnePayload(
				menu.containerId, sourceSlot, targetSlot));
	}
}
