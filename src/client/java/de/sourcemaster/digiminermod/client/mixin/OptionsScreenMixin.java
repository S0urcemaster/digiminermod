package de.sourcemaster.digiminermod.client.mixin;

import de.sourcemaster.digiminermod.client.screen.DigiMinerOptionsScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(OptionsScreen.class)
public abstract class OptionsScreenMixin extends Screen {
	protected OptionsScreenMixin(Component title) {
		super(title);
	}

	@Redirect(
			method = "init",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/gui/layouts/HeaderAndFooterLayout;addToContents(Lnet/minecraft/client/gui/layouts/LayoutElement;)Lnet/minecraft/client/gui/layouts/LayoutElement;"))
	private LayoutElement digiminermod$addOptionsToLayout(
			HeaderAndFooterLayout layout, LayoutElement contents) {
		if (contents instanceof GridLayout grid) {
			Button button = Button.builder(Component.literal("Digi Miner Options..."),
					pressed -> this.minecraft.gui.setScreen(new DigiMinerOptionsScreen(this)))
					.width(200)
					.build();
			grid.addChild(button, 5, 0, 1, 2);
		}
		return layout.addToContents(contents);
	}
}
