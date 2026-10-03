package de.sourcemaster.digiminermod.client.screen;

import de.sourcemaster.digiminermod.client.config.DigiMinerConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class DigiMinerOptionsScreen extends Screen {
	private final Screen parent;

	public DigiMinerOptionsScreen(Screen parent) {
		super(Component.literal("Digi Miner Options"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		this.addRenderableWidget(CycleButton.onOffBuilder(DigiMinerConfig.get().showIngameHints())
				.create(centerX - 100, this.height / 2 - 10, 200, 20,
						Component.literal("Show ingame hints"),
						(button, value) -> DigiMinerConfig.get().setShowIngameHints(value)));
		this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> this.onClose())
				.bounds(centerX - 100, this.height - 28, 200, 20)
				.build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		this.extractMenuBackground(graphics);
		graphics.centeredText(this.font, this.title, this.width / 2, 20, 0xFFFFFFFF);
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public void onClose() {
		if (this.minecraft != null) {
			this.minecraft.gui.setScreen(this.parent);
		}
	}
}
