package de.sourcemaster.digiminermod.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.List;

public final class RadialInventoryScreen extends Screen {
	private static final int MAIN_SLOT_COUNT = 27;
	private static final int MAIN_SLOT_OFFSET = 9;
	private static final int PAGE_SIZE = 14;
	private static final int PAGE_COUNT = (MAIN_SLOT_COUNT + PAGE_SIZE - 1) / PAGE_SIZE;
	private static final int SLOT_SIZE = 20;
	private static final int ITEM_OFFSET = 2;
	private static final float STICK_DEADZONE = 0.50F;
	private static final double ANGLE_HYSTERESIS = Math.toRadians(2.5);
	private static final long MOVE_INITIAL_DELAY_NANOS = 350_000_000L;
	private static final long MOVE_REPEAT_NANOS = 90_000_000L;

	private int currentPage;
	private int selectedSlotOnPage;
	private int hotbarCursor;
	private int activeController = -1;
	private boolean mappedGamepad;
	private String controllerName = "";
	private boolean previousBackButton;
	private boolean previousLeftBumper;
	private boolean previousRightBumper;
	private boolean previousLeftTrigger;
	private boolean previousRightTrigger;
	private boolean previousMoveButton;
	private boolean moveFromRing;
	private long nextMoveRepeatAt;

	public RadialInventoryScreen() {
		super(Component.literal("Digi Miner Inventar"));
	}

	@Override
	protected void init() {
		super.init();
		if (this.minecraft != null && this.minecraft.player != null) {
			this.hotbarCursor = this.minecraft.player.getInventory().getSelectedSlot();
		}
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
			this.resetButtonStates();
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

			this.processInput(
					state.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_X),
					state.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_Y),
					state.buttons(GLFW.GLFW_GAMEPAD_BUTTON_A) == GLFW.GLFW_PRESS,
					state.buttons(GLFW.GLFW_GAMEPAD_BUTTON_B) == GLFW.GLFW_PRESS,
					state.buttons(GLFW.GLFW_GAMEPAD_BUTTON_LEFT_BUMPER) == GLFW.GLFW_PRESS,
					state.buttons(GLFW.GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER) == GLFW.GLFW_PRESS,
					state.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_TRIGGER) > 0.5F,
					state.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER) > 0.5F);
		}
	}

	private void pollRawJoystick(int controller) {
		FloatBuffer axes = GLFW.glfwGetJoystickAxes(controller);
		ByteBuffer buttons = GLFW.glfwGetJoystickButtons(controller);
		if (axes == null || axes.remaining() < 6 || buttons == null || buttons.remaining() < 6) {
			return;
		}

		// Linux xpad mapping for the Xbox Elite Series 2.
		this.processInput(
				axes.get(3), axes.get(4),
				buttons.get(0) == GLFW.GLFW_PRESS,
				buttons.get(1) == GLFW.GLFW_PRESS,
				buttons.get(4) == GLFW.GLFW_PRESS,
				buttons.get(5) == GLFW.GLFW_PRESS,
				axes.get(2) > 0.5F,
				axes.get(5) > 0.5F);
	}

	private void processInput(
			float stickX, float stickY,
			boolean movePressed, boolean backPressed,
			boolean leftBumper, boolean rightBumper,
			boolean leftTrigger, boolean rightTrigger) {
		this.updateRingSelection(stickX, stickY);

		if (leftBumper && !this.previousLeftBumper) {
			this.hotbarCursor = Math.floorMod(this.hotbarCursor - 1, Inventory.getSelectionSize());
		}
		if (rightBumper && !this.previousRightBumper) {
			this.hotbarCursor = (this.hotbarCursor + 1) % Inventory.getSelectionSize();
		}
		if (leftTrigger && !this.previousLeftTrigger) {
			this.changePage(-1);
		}
		if (rightTrigger && !this.previousRightTrigger) {
			this.changePage(1);
		}

		this.handleMoveButton(movePressed);
		if (backPressed && !this.previousBackButton) {
			this.onClose();
		}

		this.previousMoveButton = movePressed;
		this.previousBackButton = backPressed;
		this.previousLeftBumper = leftBumper;
		this.previousRightBumper = rightBumper;
		this.previousLeftTrigger = leftTrigger;
		this.previousRightTrigger = rightTrigger;
	}

	private void updateRingSelection(float x, float y) {
		if (x * x + y * y < STICK_DEADZONE * STICK_DEADZONE) {
			return;
		}

		int slotsOnPage = this.slotsOnCurrentPage();
		double sectorAngle = Math.PI * 2.0 / slotsOnPage;
		double angle = normalizeAngle(Math.atan2(x, -y));
		double selectedCenter = this.selectedSlotOnPage * sectorAngle;
		double distance = Math.abs(shortestAngle(angle - selectedCenter));
		if (distance > sectorAngle / 2.0 + ANGLE_HYSTERESIS) {
			this.selectedSlotOnPage = Math.floorMod(
					(int) Math.round(angle / sectorAngle), slotsOnPage);
		}
	}

	private void changePage(int direction) {
		this.currentPage = Math.floorMod(this.currentPage + direction, PAGE_COUNT);
		this.selectedSlotOnPage = Math.min(this.selectedSlotOnPage, this.slotsOnCurrentPage() - 1);
	}

	private void handleMoveButton(boolean pressed) {
		long now = System.nanoTime();
		if (pressed && !this.previousMoveButton) {
			this.moveFromRing = this.chooseMoveDirection();
			this.moveOneItem();
			this.nextMoveRepeatAt = now + MOVE_INITIAL_DELAY_NANOS;
		} else if (pressed && now >= this.nextMoveRepeatAt) {
			this.moveOneItem();
			this.nextMoveRepeatAt = now + MOVE_REPEAT_NANOS;
		}
	}

	private boolean chooseMoveDirection() {
		if (this.minecraft == null || this.minecraft.player == null) {
			return true;
		}

		Inventory inventory = this.minecraft.player.getInventory();
		ItemStack ringStack = inventory.getItem(this.selectedInventorySlot());
		ItemStack hotbarStack = inventory.getItem(this.hotbarCursor);
		return !ringStack.isEmpty() || hotbarStack.isEmpty();
	}

	private void moveOneItem() {
		if (this.minecraft == null || this.minecraft.player == null || this.minecraft.gameMode == null) {
			return;
		}

		Inventory inventory = this.minecraft.player.getInventory();
		int ringInventorySlot = this.selectedInventorySlot();
		ItemStack source = this.moveFromRing
				? inventory.getItem(ringInventorySlot)
				: inventory.getItem(this.hotbarCursor);
		ItemStack target = this.moveFromRing
				? inventory.getItem(this.hotbarCursor)
				: inventory.getItem(ringInventorySlot);
		if (source.isEmpty()
				|| (!target.isEmpty() && !ItemStack.isSameItemSameComponents(source, target))
				|| (!target.isEmpty() && target.getCount() >= target.getMaxStackSize())) {
			return;
		}

		int sourceMenuSlot = this.moveFromRing
				? ringInventorySlot
				: InventoryMenu.USE_ROW_SLOT_START + this.hotbarCursor;
		int targetMenuSlot = this.moveFromRing
				? InventoryMenu.USE_ROW_SLOT_START + this.hotbarCursor
				: ringInventorySlot;
		int containerId = this.minecraft.player.inventoryMenu.containerId;

		// Pick up the stack, place one unit with a right click, then return the remainder.
		this.minecraft.gameMode.handleContainerInput(
				containerId, sourceMenuSlot, 0, ContainerInput.PICKUP, this.minecraft.player);
		this.minecraft.gameMode.handleContainerInput(
				containerId, targetMenuSlot, 1, ContainerInput.PICKUP, this.minecraft.player);
		this.minecraft.gameMode.handleContainerInput(
				containerId, sourceMenuSlot, 0, ContainerInput.PICKUP, this.minecraft.player);
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
			String normalizedName = name == null ? "" : name.toLowerCase();
			if (axes != null && axes.remaining() >= 6
					&& (normalizedName.contains("x-box") || normalizedName.contains("xbox")
					|| normalizedName.contains("controller") || normalizedName.contains("gamepad"))) {
				this.mappedGamepad = false;
				return joystick;
			}
		}
		return -1;
	}

	private void resetButtonStates() {
		this.previousMoveButton = false;
		this.previousBackButton = false;
		this.previousLeftBumper = false;
		this.previousRightBumper = false;
		this.previousLeftTrigger = false;
		this.previousRightTrigger = false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		this.extractTransparentBackground(graphics);
		if (this.minecraft == null || this.minecraft.player == null) {
			return;
		}

		Inventory inventory = this.minecraft.player.getInventory();
		int centerX = this.width / 2;
		int centerY = Math.max(100, this.height / 2 - 10);
		int radius = Math.min(86, Math.max(68, Math.min(this.width / 2 - 24, (this.height - 76) / 2)));
		int slotsOnPage = this.slotsOnCurrentPage();
		double sectorAngle = Math.PI * 2.0 / slotsOnPage;

		graphics.centeredText(this.font, this.title, centerX, 10, 0xFFFFFFFF);
		graphics.centeredText(this.font,
				this.activeController >= 0
						? this.controllerName + (this.mappedGamepad ? "" : " (Raw)")
						: "Controller nicht erkannt",
				centerX, 22, this.activeController >= 0 ? 0xFFB8C4D6 : 0xFFFF8A80);

		for (int pageSlot = 0; pageSlot < slotsOnPage; pageSlot++) {
			double angle = pageSlot * sectorAngle;
			int left = (int) Math.round(centerX + Math.sin(angle) * radius) - SLOT_SIZE / 2;
			int top = (int) Math.round(centerY - Math.cos(angle) * radius) - SLOT_SIZE / 2;
			int inventorySlot = MAIN_SLOT_OFFSET + this.currentPage * PAGE_SIZE + pageSlot;
			this.extractSlot(graphics, inventory.getItem(inventorySlot), left, top,
					pageSlot == this.selectedSlotOnPage, 1 + inventorySlot);
		}

		this.extractSelectionDetails(graphics, inventory.getItem(this.selectedInventorySlot()), centerX, centerY);
		this.extractHotbar(graphics, inventory);
	}

	private void extractSlot(
			GuiGraphicsExtractor graphics, ItemStack stack,
			int left, int top, boolean selected, int seed) {
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
			GuiGraphicsExtractor graphics, ItemStack stack, int centerX, int centerY) {
		graphics.fill(centerX - 49, centerY - 32, centerX + 49, centerY + 33, 0xB010141A);
		graphics.outline(centerX - 49, centerY - 32, 98, 65, 0x806E747C);
		graphics.centeredText(this.font, "Seite " + (this.currentPage + 1) + "/" + PAGE_COUNT,
				centerX, centerY + 20, 0xFFF4D35E);

		if (stack.isEmpty()) {
			return;
		}

		graphics.item(this.minecraft.player, stack, centerX - 8, centerY - 29, 100);
		List<Component> tooltip = Screen.getTooltipFromItem(this.minecraft, stack);
		int lines = Math.min(3, tooltip.size());
		for (int line = 0; line < lines; line++) {
			graphics.centeredText(this.font, tooltip.get(line), centerX, centerY - 9 + line * 10, 0xFFFFFFFF);
		}
	}

	private void extractHotbar(GuiGraphicsExtractor graphics, Inventory inventory) {
		int totalWidth = Inventory.getSelectionSize() * SLOT_SIZE;
		int left = (this.width - totalWidth) / 2;
		int top = this.height - SLOT_SIZE - 8;
		for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
			this.extractSlot(graphics, inventory.getItem(slot), left + slot * SLOT_SIZE, top,
					slot == this.hotbarCursor, 200 + slot);
		}
	}

	private int slotsOnCurrentPage() {
		return Math.min(PAGE_SIZE, MAIN_SLOT_COUNT - this.currentPage * PAGE_SIZE);
	}

	private int selectedInventorySlot() {
		return MAIN_SLOT_OFFSET + this.currentPage * PAGE_SIZE + this.selectedSlotOnPage;
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
