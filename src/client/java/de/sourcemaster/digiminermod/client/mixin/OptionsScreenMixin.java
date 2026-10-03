package de.sourcemaster.digiminermod.client.mixin;

import de.sourcemaster.digiminermod.client.screen.DigiMinerOptionsScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OptionsScreen.class)
public abstract class OptionsScreenMixin extends Screen {
	protected OptionsScreenMixin(Component title) {
		super(title);
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void digiminermod$addOptionsButton(CallbackInfo callbackInfo) {
		this.addRenderableWidget(Button.builder(Component.literal("Digi Miner Options..."),
				button -> this.minecraft.gui.setScreen(new DigiMinerOptionsScreen(this)))
				.bounds(6, this.height - 27, 150, 20)
				.build());
	}
}
