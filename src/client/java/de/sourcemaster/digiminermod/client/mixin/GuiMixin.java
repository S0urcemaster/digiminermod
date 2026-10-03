package de.sourcemaster.digiminermod.client.mixin;

import de.sourcemaster.digiminermod.client.screen.RadialInventoryScreen;
import de.sourcemaster.digiminermod.client.screen.RadialCraftingScreen;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Gui.class)
public abstract class GuiMixin {
	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen digiminermod$replacePlayerInventory(Screen screen) {
		if (screen != null && screen.getClass() == InventoryScreen.class
				&& (Minecraft.getInstance().player == null || !Minecraft.getInstance().player.hasInfiniteMaterials())) {
			return new RadialInventoryScreen();
		}
		if (screen != null && screen.getClass() == CraftingScreen.class) {
			return new RadialCraftingScreen(((CraftingScreen) screen).getMenu());
		}

		return screen;
	}
}
