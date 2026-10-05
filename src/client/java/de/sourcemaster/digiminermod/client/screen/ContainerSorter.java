package de.sourcemaster.digiminermod.client.screen;

import de.sourcemaster.digiminermod.InventoryNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.inventory.AbstractContainerMenu;

final class ContainerSorter {
	private ContainerSorter() {}

	static void sort(AbstractContainerMenu menu, int firstSlot, int slotCount, boolean descending) {
		ClientPlayNetworking.send(new InventoryNetworking.SortPayload(
				menu.containerId, firstSlot, slotCount, descending));
	}
}
