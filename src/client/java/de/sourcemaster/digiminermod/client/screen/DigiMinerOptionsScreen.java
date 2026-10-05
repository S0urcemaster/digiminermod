package de.sourcemaster.digiminermod.client.screen;

import de.sourcemaster.digiminermod.client.config.DigiMinerConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
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
		int left = centerX - 154;
		int right = centerX + 4;
		int top = Math.max(42, this.height / 2 - 48);
		this.addRenderableWidget(CycleButton.onOffBuilder(DigiMinerConfig.get().showIngameHints())
				.create(left, top, 150, 20,
						Component.literal("Show ingame hints"),
						(button, value) -> DigiMinerConfig.get().setShowIngameHints(value)));
		this.addRenderableWidget(CycleButton.onOffBuilder(DigiMinerConfig.get().invertLookY())
				.create(right, top, 150, 20,
						Component.literal("Invert look Y"),
						(button, value) -> DigiMinerConfig.get().setInvertLookY(value)));
		this.addRenderableWidget(new ConfigSlider(left, top + 24, 150, "Move deadzone", 0.0, 0.5,
				DigiMinerConfig.get().moveDeadzone(), DigiMinerConfig.get()::setMoveDeadzone, Unit.PERCENT));
		this.addRenderableWidget(new ConfigSlider(right, top + 24, 150, "Look deadzone", 0.0, 0.5,
				DigiMinerConfig.get().lookDeadzone(), DigiMinerConfig.get()::setLookDeadzone, Unit.PERCENT));
		this.addRenderableWidget(new ConfigSlider(left, top + 48, 150, "Horizontal speed", 45.0, 720.0,
				DigiMinerConfig.get().lookSpeedHorizontal(), DigiMinerConfig.get()::setLookSpeedHorizontal, Unit.SPEED));
		this.addRenderableWidget(new ConfigSlider(right, top + 48, 150, "Vertical speed", 45.0, 720.0,
				DigiMinerConfig.get().lookSpeedVertical(), DigiMinerConfig.get()::setLookSpeedVertical, Unit.SPEED));
		this.addRenderableWidget(new ConfigSlider(left, top + 72, 150, "Horizontal accel.", 90.0, 3600.0,
				DigiMinerConfig.get().lookAccelerationHorizontal(), DigiMinerConfig.get()::setLookAccelerationHorizontal, Unit.ACCELERATION));
		this.addRenderableWidget(new ConfigSlider(right, top + 72, 150, "Vertical accel.", 90.0, 3600.0,
				DigiMinerConfig.get().lookAccelerationVertical(), DigiMinerConfig.get()::setLookAccelerationVertical, Unit.ACCELERATION));
		this.addRenderableWidget(new ConfigSlider(centerX - 100, top + 96, 200, "Item transfer accel.", 1.0, 100.0,
				DigiMinerConfig.get().itemTransferAccelerationTicks(),
				DigiMinerConfig.get()::setItemTransferAccelerationTicks, Unit.TICKS));
		this.addRenderableWidget(Button.builder(Component.literal("Controller bindings..."),
				pressed -> this.minecraft.gui.setScreen(new ControllerBindingsScreen(this)))
				.bounds(centerX - 100, top + 120, 200, 20).build());
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

	@FunctionalInterface
	private interface ValueConsumer {
		void accept(double value);
	}

	private enum Unit { PERCENT, SPEED, ACCELERATION, TICKS }

	private static final class ConfigSlider extends AbstractSliderButton {
		private final String label;
		private final double minimum;
		private final double maximum;
		private final ValueConsumer consumer;
		private final Unit unit;

		private ConfigSlider(int x, int y, int width, String label, double minimum, double maximum,
				double current, ValueConsumer consumer, Unit unit) {
			super(x, y, width, 20, Component.empty(), (current - minimum) / (maximum - minimum));
			this.label = label;
			this.minimum = minimum;
			this.maximum = maximum;
			this.consumer = consumer;
			this.unit = unit;
			this.updateMessage();
		}

		private double actualValue() {
			return this.minimum + this.value * (this.maximum - this.minimum);
		}

		@Override
		protected void updateMessage() {
			double actual = this.actualValue();
			String formatted = switch (this.unit) {
				case PERCENT -> Math.round(actual * 100.0) + "%";
				case SPEED -> Math.round(actual) + "°/s";
				case ACCELERATION -> Math.round(actual) + "°/s²";
				case TICKS -> Math.round(actual) + " ticks";
			};
			this.setMessage(Component.literal(this.label + ": " + formatted));
		}

		@Override
		protected void applyValue() {
			this.consumer.accept(this.actualValue());
		}
	}
}
