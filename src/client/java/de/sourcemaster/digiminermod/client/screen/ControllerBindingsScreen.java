package de.sourcemaster.digiminermod.client.screen;

import de.sourcemaster.digiminermod.client.config.DigiMinerConfig;
import de.sourcemaster.digiminermod.client.input.ControllerAction;
import de.sourcemaster.digiminermod.client.input.ControllerButton;
import de.sourcemaster.digiminermod.client.input.ControllerSupport;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.EnumMap;

public final class ControllerBindingsScreen extends Screen {
	private final Screen parent;
	private final EnumMap<ControllerAction, Button> buttons = new EnumMap<>(ControllerAction.class);
	private ControllerAction listening;
	private boolean captureArmed;

	public ControllerBindingsScreen(Screen parent) {
		super(Component.literal("Controller Bindings"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		this.buttons.clear();
		int width = 240;
		int gap = 8;
		int left = this.width / 2 - width - gap / 2;
		int right = this.width / 2 + gap / 2;
		int top = 40;
		ControllerAction[] actions = ControllerAction.values();
		int rows = (actions.length + 1) / 2;
		for (int i = 0; i < actions.length; i++) {
			ControllerAction action = actions[i];
			int x = i < rows ? left : right;
			int row = i < rows ? i : i - rows;
			Button button = Button.builder(this.label(action), pressed -> this.beginCapture(action))
					.bounds(x, top + row * 22, width, 20).build();
			this.buttons.put(action, this.addRenderableWidget(button));
		}
		this.addRenderableWidget(Button.builder(Component.literal("Reset All"), pressed -> {
			DigiMinerConfig.get().resetBindings();
			this.refreshLabels();
		}).bounds(this.width / 2 - 204, this.height - 28, 200, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Done"), pressed -> this.onClose())
				.bounds(this.width / 2 + 4, this.height - 28, 200, 20).build());
	}

	private void beginCapture(ControllerAction action) {
		this.listening = action;
		this.captureArmed = false;
		this.refreshLabels();
	}

	@Override
	public void tick() {
		super.tick();
		if (this.listening == null) return;
		ControllerSupport.Snapshot snapshot = ControllerSupport.poll();
		ControllerButton pressed = firstPressed(snapshot);
		if (!this.captureArmed) {
			this.captureArmed = pressed == null;
			return;
		}
		if (pressed != null) {
			DigiMinerConfig.get().setBinding(this.listening, pressed);
			this.listening = null;
			this.captureArmed = false;
			this.refreshLabels();
		}
	}

	private static ControllerButton firstPressed(ControllerSupport.Snapshot snapshot) {
		for (ControllerButton button : ControllerButton.values()) {
			if (snapshot.pressed(button)) return button;
		}
		return null;
	}

	private Component label(ControllerAction action) {
		String binding = this.listening == action ? "[...]" : "[" + DigiMinerConfig.get().binding(action).label() + "]";
		return Component.literal(action.label() + "  " + binding);
	}

	private void refreshLabels() {
		this.buttons.forEach((action, button) -> button.setMessage(this.label(action)));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		this.extractMenuBackground(graphics);
		graphics.centeredText(this.font, this.title, this.width / 2, 20, 0xFFFFFFFF);
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public void onClose() {
		if (this.minecraft != null) this.minecraft.gui.setScreen(this.parent);
	}
}
