package de.sourcemaster.digiminermod.client.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class HudMixin {
	private static final int SLOT_SIZE = 20;
	private static final int ITEM_OFFSET = 2;
	private static final int SCREEN_MARGIN = 8;

	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "extractItemHotbar", at = @At("HEAD"), cancellable = true)
	private void digiminermod$extractVerticalHotbar(
			GuiGraphicsExtractor graphics,
			DeltaTracker deltaTracker,
			CallbackInfo callbackInfo) {
		if (this.minecraft.player == null) {
			callbackInfo.cancel();
			return;
		}

		Inventory inventory = this.minecraft.player.getInventory();
		int panelHeight = SLOT_SIZE * Inventory.getSelectionSize();
		int left = graphics.guiWidth() - SLOT_SIZE - SCREEN_MARGIN;
		int top = Math.max(SCREEN_MARGIN, (graphics.guiHeight() - panelHeight) / 2);

		for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
			int y = top + slot * SLOT_SIZE;
			boolean selected = slot == inventory.getSelectedSlot();

			// A quiet, translucent panel that keeps the world visible behind it.
			graphics.fill(left, y, left + SLOT_SIZE, y + SLOT_SIZE, 0xB0101114);
			graphics.outline(left, y, SLOT_SIZE, SLOT_SIZE,
					selected ? 0xFFF4D35E : 0x806E747C);

			if (selected) {
				graphics.outline(left - 1, y - 1, SLOT_SIZE + 2, SLOT_SIZE + 2, 0xFFFFFFFF);
			}

			ItemStack stack = inventory.getItem(slot);
			if (!stack.isEmpty()) {
				int itemX = left + ITEM_OFFSET;
				int itemY = y + ITEM_OFFSET;
				graphics.item(this.minecraft.player, stack, itemX, itemY, slot + 1);
				graphics.itemDecorations(this.minecraft.font, stack, itemX, itemY);
			}
		}

		callbackInfo.cancel();
	}
}
