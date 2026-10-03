package de.sourcemaster.digiminermod.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

public final class RadialInventoryScreen extends Screen {
	private static final int MAIN_SLOT_COUNT = 27;
	private static final int MAIN_SLOT_OFFSET = 9;
	private static final int SLOT_SIZE = 20;
	private static final int ITEM_OFFSET = 2;
	private static final float STICK_DEADZONE = 0.50F;
	private static final double SECTOR_ANGLE = Math.PI * 2.0 / MAIN_SLOT_COUNT;
	private static final double ANGLE_HYSTERESIS = Math.toRadians(2.5);

	private int selectedSlot;
	private int activeController = -1;
	private boolean mappedGamepad;
	private String controllerName = "";
	private boolean previousBackButton;

	public RadialInventoryScreen() {
		super(Component.literal("Digi Miner Inventar"));
	}

	@Override
	public void tick() {
		super.tick();
		this.pollController();
	}

	private void pollController() {
		int controller = this.findController();
		if (controller < 0) {
			this.activeController = -1;
			this.controllerName = "";
			this.previousBackButton = false;
			return;
		}

		this.activeController = controller;
		this.controllerName = GLFW.glfwGetJoystickName(controller);
		if (this.controllerName == null) {
			this.controllerName = "Unbekannter Controller";
		}

		if (this.mappedGamepad) {
			this.pollMappedGamepad(controller);
		} else {
			this.pollRawJoystick(controller);
		}
	}

	private void pollMappedGamepad(int controller) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			GLFWGamepadState state = GLFWGamepadState.malloc(stack);
			if (!GLFW.glfwGetGamepadState(controller, state)) {
				return;
			}

			this.updateSelection(
					state.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_X),
					state.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_Y));

			boolean backPressed = state.buttons(GLFW.GLFW_GAMEPAD_BUTTON_B) == GLFW.GLFW_PRESS;
			this.handleBackButton(backPressed);
		}
	}

	private void pollRawJoystick(int controller) {
		FloatBuffer axes = GLFW.glfwGetJoystickAxes(controller);
		if (axes == null || axes.remaining() < 5) {
			return;
		}

		// Linux' xpad-Treiber meldet den rechten Stick des Xbox Elite 2 als Achsen 3 und 4.
		this.updateSelection(axes.get(3), axes.get(4));

		ByteBuffer buttons = GLFW.glfwGetJoystickButtons(controller);
		boolean backPressed = buttons != null
				&& buttons.remaining() > 1
				&& buttons.get(1) == GLFW.GLFW_PRESS;
		this.handleBackButton(backPressed);
	}

	private void updateSelection(float x, float y) {
		float magnitudeSquared = x * x + y * y;
		if (magnitudeSquared < STICK_DEADZONE * STICK_DEADZONE) {
			return;
		}

		double angle = normalizeAngle(Math.atan2(x, -y));
		double selectedCenter = this.selectedSlot * SECTOR_ANGLE;
		double distance = Math.abs(shortestAngle(angle - selectedCenter));

		if (distance > SECTOR_ANGLE / 2.0 + ANGLE_HYSTERESIS) {
			this.selectedSlot = Math.floorMod(
					(int) Math.round(angle / SECTOR_ANGLE), MAIN_SLOT_COUNT);
		}
	}

	private void handleBackButton(boolean backPressed) {
		if (backPressed && !this.previousBackButton) {
			this.onClose();
		}
		this.previousBackButton = backPressed;
	}

	private int findController() {
		if (this.activeController >= GLFW.GLFW_JOYSTICK_1
				&& this.activeController <= GLFW.GLFW_JOYSTICK_LAST
				&& GLFW.glfwJoystickPresent(this.activeController)) {
			this.mappedGamepad = GLFW.glfwJoystickIsGamepad(this.activeController);
			return this.activeController;
		}

		for (int joystick = GLFW.GLFW_JOYSTICK_1; joystick <= GLFW.GLFW_JOYSTICK_LAST; joystick++) {
			if (GLFW.glfwJoystickIsGamepad(joystick)) {
				this.mappedGamepad = true;
				return joystick;
			}
		}

		for (int joystick = GLFW.GLFW_JOYSTICK_1; joystick <= GLFW.GLFW_JOYSTICK_LAST; joystick++) {
			if (!GLFW.glfwJoystickPresent(joystick)) {
				continue;
			}

			FloatBuffer axes = GLFW.glfwGetJoystickAxes(joystick);
			String name = GLFW.glfwGetJoystickName(joystick);
			if (axes != null && axes.remaining() >= 5 && name != null
					&& (name.toLowerCase().contains("x-box")
					|| name.toLowerCase().contains("xbox")
					|| name.toLowerCase().contains("controller")
					|| name.toLowerCase().contains("gamepad"))) {
				this.mappedGamepad = false;
				return joystick;
			}
		}

		return -1;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		this.extractTransparentBackground(graphics);

		if (this.minecraft == null || this.minecraft.player == null) {
			return;
		}

		Inventory inventory = this.minecraft.player.getInventory();
		int centerX = this.width / 2;
		int centerY = Math.max(104, this.height / 2 - 10);
		int radius = Math.min(94, Math.max(78, Math.min(this.width / 2 - 24, (this.height - 76) / 2)));

		graphics.centeredText(this.font, this.title, centerX, 14, 0xFFFFFFFF);
		graphics.centeredText(
				this.font,
				this.activeController >= 0
						? this.controllerName + (this.mappedGamepad ? "" : " (Raw)")
						: "Controller nicht erkannt",
				centerX,
				27,
				this.activeController >= 0 ? 0xFFB8C4D6 : 0xFFFF8A80);

		for (int ringSlot = 0; ringSlot < MAIN_SLOT_COUNT; ringSlot++) {
			double angle = ringSlot * SECTOR_ANGLE;
			int left = (int) Math.round(centerX + Math.sin(angle) * radius) - SLOT_SIZE / 2;
			int top = (int) Math.round(centerY - Math.cos(angle) * radius) - SLOT_SIZE / 2;
			this.extractSlot(graphics, inventory.getItem(MAIN_SLOT_OFFSET + ringSlot), left, top,
					ringSlot == this.selectedSlot, ringSlot + 1);
		}

		ItemStack selectedStack = inventory.getItem(MAIN_SLOT_OFFSET + this.selectedSlot);
		this.extractSelectionDetails(graphics, selectedStack, centerX, centerY);
		this.extractHotbar(graphics, inventory);
	}

	private void extractSlot(
			GuiGraphicsExtractor graphics,
			ItemStack stack,
			int left,
			int top,
			boolean selected,
			int seed) {
		graphics.fill(left, top, left + SLOT_SIZE, top + SLOT_SIZE,
				selected ? 0xDD28313D : 0xB010141A);
		graphics.outline(left, top, SLOT_SIZE, SLOT_SIZE,
				selected ? 0xFFF4D35E : 0x906E747C);

		if (selected) {
			graphics.outline(left - 1, top - 1, SLOT_SIZE + 2, SLOT_SIZE + 2, 0xFFFFFFFF);
		}

		if (!stack.isEmpty()) {
			int itemX = left + ITEM_OFFSET;
			int itemY = top + ITEM_OFFSET;
			graphics.item(this.minecraft.player, stack, itemX, itemY, seed);
			graphics.itemDecorations(this.font, stack, itemX, itemY);
		}
	}

	private void extractSelectionDetails(
			GuiGraphicsExtractor graphics,
			ItemStack stack,
			int centerX,
			int centerY) {
		graphics.fill(centerX - 55, centerY - 25, centerX + 55, centerY + 26, 0xB010141A);
		graphics.outline(centerX - 55, centerY - 25, 110, 51, 0x806E747C);

		if (stack.isEmpty()) {
			graphics.centeredText(this.font, "Leerer Slot", centerX, centerY - 4, 0xFF9AA4B2);
			return;
		}

		graphics.item(this.minecraft.player, stack, centerX - 8, centerY - 20, 100);
		graphics.centeredText(this.font, stack.getHoverName(), centerX, centerY + 2, 0xFFFFFFFF);
		if (stack.getCount() > 1) {
			graphics.centeredText(this.font, "Anzahl: " + stack.getCount(), centerX, centerY + 13, 0xFFB8C4D6);
		}
	}

	private void extractHotbar(GuiGraphicsExtractor graphics, Inventory inventory) {
		int totalWidth = Inventory.getSelectionSize() * SLOT_SIZE;
		int left = (this.width - totalWidth) / 2;
		int top = this.height - SLOT_SIZE - 8;

		for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
			this.extractSlot(graphics, inventory.getItem(slot), left + slot * SLOT_SIZE, top,
					slot == inventory.getSelectedSlot(), 200 + slot);
		}
	}

	private static double normalizeAngle(double angle) {
		return angle < 0.0 ? angle + Math.PI * 2.0 : angle;
	}

	private static double shortestAngle(double angle) {
		return Math.atan2(Math.sin(angle), Math.cos(angle));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean isInGameUi() {
		return true;
	}
}
