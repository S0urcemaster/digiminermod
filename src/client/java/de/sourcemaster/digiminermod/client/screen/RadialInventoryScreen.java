package de.sourcemaster.digiminermod.client.screen;

import de.sourcemaster.digiminermod.client.config.DigiMinerConfig;
import de.sourcemaster.digiminermod.client.input.ControllerSupport;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class RadialInventoryScreen extends Screen {
	private static final int MAIN_SLOT_COUNT = 27;
	private static final int PAGE_SIZE = 14;
	private static final int PAGE_COUNT = 2;
	private static final int[] EQUIPMENT_MENU_SLOTS = {5, 6, 7, 8, InventoryMenu.SHIELD_SLOT};
	private static final int SLOT_SIZE = 20;
	private static final float STICK_DEADZONE = 0.50F;
	private static final double ANGLE_HYSTERESIS = Math.toRadians(2.5);
	private static final long INITIAL_REPEAT_DELAY = 350_000_000L;
	private static final long REPEAT_INTERVAL = 90_000_000L;

	private enum ActiveRing { EQUIPMENT, INVENTORY }

	private ActiveRing activeRing = ActiveRing.INVENTORY;
	private int currentPage;
	private int inventoryCursor;
	private int equipmentCursor;
	private int hotbarCursor;
	private boolean previousLeftBumper;
	private boolean previousRightBumper;
	private boolean previousLeftTrigger;
	private boolean previousRightTrigger;
	private boolean previousDpadDown;
	private boolean previousA;
	private boolean previousB;
	private boolean dpadFromInventory;
	private boolean aFromEquipment;
	private long nextDpadRepeat;
	private long nextARepeat;
	private ControllerSupport.Snapshot controller = ControllerSupport.Snapshot.NONE;

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
		this.controller = ControllerSupport.poll();
		if (!this.controller.connected()) {
			this.resetButtons();
			return;
		}

		this.updateStick(this.controller.leftX(), this.controller.leftY(), ActiveRing.EQUIPMENT);
		this.updateStick(this.controller.rightX(), this.controller.rightY(), ActiveRing.INVENTORY);

		if (this.controller.leftBumper() && !this.previousLeftBumper) {
			this.hotbarCursor = Math.floorMod(this.hotbarCursor - 1, Inventory.getSelectionSize());
		}
		if (this.controller.rightBumper() && !this.previousRightBumper) {
			this.hotbarCursor = (this.hotbarCursor + 1) % Inventory.getSelectionSize();
		}

		boolean leftTrigger = this.controller.leftTrigger() > 0.5F;
		boolean rightTrigger = this.controller.rightTrigger() > 0.5F;
		if (leftTrigger && !this.previousLeftTrigger) {
			this.changeActivePage(-1);
		}
		if (rightTrigger && !this.previousRightTrigger) {
			this.changeActivePage(1);
		}

		this.handleDpadTransfer(this.controller.dpadDown());
		this.handleATransfer(this.controller.a());
		if (this.controller.b() && !this.previousB) {
			this.onClose();
		}

		this.previousLeftBumper = this.controller.leftBumper();
		this.previousRightBumper = this.controller.rightBumper();
		this.previousLeftTrigger = leftTrigger;
		this.previousRightTrigger = rightTrigger;
		this.previousDpadDown = this.controller.dpadDown();
		this.previousA = this.controller.a();
		this.previousB = this.controller.b();
	}

	private void updateStick(float x, float y, ActiveRing ring) {
		if (x * x + y * y < STICK_DEADZONE * STICK_DEADZONE) {
			return;
		}

		this.activeRing = ring;
		int count = ring == ActiveRing.EQUIPMENT ? EQUIPMENT_MENU_SLOTS.length : this.slotsOnCurrentPage();
		int current = ring == ActiveRing.EQUIPMENT ? this.equipmentCursor : this.inventoryCursor;
		double sector = Math.PI * 2.0 / count;
		double angle = normalizeAngle(Math.atan2(x, -y));
		if (Math.abs(shortestAngle(angle - current * sector)) > sector / 2.0 + ANGLE_HYSTERESIS) {
			int next = Math.floorMod((int) Math.round(angle / sector), count);
			if (ring == ActiveRing.EQUIPMENT) {
				this.equipmentCursor = next;
			} else {
				this.inventoryCursor = next;
			}
		}
	}

	private void changeActivePage(int direction) {
		if (this.activeRing != ActiveRing.INVENTORY) {
			return;
		}
		this.currentPage = Math.floorMod(this.currentPage + direction, PAGE_COUNT);
		this.inventoryCursor = Math.min(this.inventoryCursor, this.slotsOnCurrentPage() - 1);
	}

	private void handleDpadTransfer(boolean pressed) {
		long now = System.nanoTime();
		if (pressed && !this.previousDpadDown) {
			this.dpadFromInventory = this.chooseFirstSource(this.inventoryMenuSlot(), this.hotbarMenuSlot());
			this.moveOne(this.inventoryMenuSlot(), this.hotbarMenuSlot(), this.dpadFromInventory);
			this.nextDpadRepeat = now + INITIAL_REPEAT_DELAY;
		} else if (pressed && now >= this.nextDpadRepeat) {
			this.moveOne(this.inventoryMenuSlot(), this.hotbarMenuSlot(), this.dpadFromInventory);
			this.nextDpadRepeat = now + REPEAT_INTERVAL;
		}
	}

	private void handleATransfer(boolean pressed) {
		long now = System.nanoTime();
		if (pressed && !this.previousA) {
			this.aFromEquipment = this.chooseFirstSource(this.equipmentMenuSlot(), this.inventoryMenuSlot());
			this.moveOne(this.equipmentMenuSlot(), this.inventoryMenuSlot(), this.aFromEquipment);
			this.nextARepeat = now + INITIAL_REPEAT_DELAY;
		} else if (pressed && now >= this.nextARepeat) {
			this.moveOne(this.equipmentMenuSlot(), this.inventoryMenuSlot(), this.aFromEquipment);
			this.nextARepeat = now + REPEAT_INTERVAL;
		}
	}

	private boolean chooseFirstSource(int firstSlot, int secondSlot) {
		if (this.minecraft == null || this.minecraft.player == null) {
			return true;
		}
		ItemStack first = this.minecraft.player.inventoryMenu.getSlot(firstSlot).getItem();
		ItemStack second = this.minecraft.player.inventoryMenu.getSlot(secondSlot).getItem();
		return !first.isEmpty() || second.isEmpty();
	}

	private void moveOne(int firstSlot, int secondSlot, boolean fromFirst) {
		if (this.minecraft == null || this.minecraft.player == null || this.minecraft.gameMode == null) {
			return;
		}

		int sourceIndex = fromFirst ? firstSlot : secondSlot;
		int targetIndex = fromFirst ? secondSlot : firstSlot;
		Slot sourceSlot = this.minecraft.player.inventoryMenu.getSlot(sourceIndex);
		Slot targetSlot = this.minecraft.player.inventoryMenu.getSlot(targetIndex);
		ItemStack source = sourceSlot.getItem();
		ItemStack target = targetSlot.getItem();
		if (source.isEmpty() || !sourceSlot.mayPickup(this.minecraft.player) || !targetSlot.mayPlace(source)
				|| (!target.isEmpty() && !ItemStack.isSameItemSameComponents(source, target))
				|| (!target.isEmpty() && target.getCount() >= targetSlot.getMaxStackSize(source))) {
			return;
		}

		int containerId = this.minecraft.player.inventoryMenu.containerId;
		this.minecraft.gameMode.handleContainerInput(
				containerId, sourceIndex, 0, ContainerInput.PICKUP, this.minecraft.player);
		this.minecraft.gameMode.handleContainerInput(
				containerId, targetIndex, 1, ContainerInput.PICKUP, this.minecraft.player);
		this.minecraft.gameMode.handleContainerInput(
				containerId, sourceIndex, 0, ContainerInput.PICKUP, this.minecraft.player);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		this.extractTransparentBackground(graphics);
		if (this.minecraft == null || this.minecraft.player == null) {
			return;
		}

		int centerY = Math.max(100, this.height / 2 - 10);
		int spacing = Math.min(112, Math.max(82, this.width / 4));
		int leftCenterX = this.width / 2 - spacing;
		int rightCenterX = this.width / 2 + spacing;
		int radius = Math.min(68, Math.max(52, Math.min(spacing - 22, (this.height - 78) / 2)));

		if (!this.controller.connected()) {
			graphics.centeredText(this.font, "No controller detected", this.width / 2, 10, 0xFFFF8A80);
		}

		this.extractEquipmentRing(graphics, leftCenterX, centerY, radius);
		this.extractInventoryRing(graphics, rightCenterX, centerY, radius);
		this.extractHints(graphics);
		this.extractHotbar(graphics);
	}

	private void extractEquipmentRing(GuiGraphicsExtractor graphics, int centerX, int centerY, int radius) {
		for (int i = 0; i < EQUIPMENT_MENU_SLOTS.length; i++) {
			this.extractRingSlot(graphics, this.menuStack(EQUIPMENT_MENU_SLOTS[i]), centerX, centerY, radius,
					i, EQUIPMENT_MENU_SLOTS.length, i == this.equipmentCursor, 300 + i);
		}
		this.extractDetails(graphics, this.menuStack(this.equipmentMenuSlot()), centerX, centerY,
				"1/1", this.activeRing == ActiveRing.EQUIPMENT);
	}

	private void extractInventoryRing(GuiGraphicsExtractor graphics, int centerX, int centerY, int radius) {
		int count = this.slotsOnCurrentPage();
		for (int i = 0; i < count; i++) {
			int menuSlot = InventoryMenu.INV_SLOT_START + this.currentPage * PAGE_SIZE + i;
			this.extractRingSlot(graphics, this.menuStack(menuSlot), centerX, centerY, radius,
					i, count, i == this.inventoryCursor, menuSlot);
		}
		this.extractDetails(graphics, this.menuStack(this.inventoryMenuSlot()), centerX, centerY,
				(this.currentPage + 1) + "/" + PAGE_COUNT,
				this.activeRing == ActiveRing.INVENTORY);
	}

	private void extractRingSlot(
			GuiGraphicsExtractor graphics, ItemStack stack, int centerX, int centerY, int radius,
			int position, int count, boolean selected, int seed) {
		double angle = position * Math.PI * 2.0 / count;
		int left = (int) Math.round(centerX + Math.sin(angle) * radius) - SLOT_SIZE / 2;
		int top = (int) Math.round(centerY - Math.cos(angle) * radius) - SLOT_SIZE / 2;
		this.extractSlot(graphics, stack, left, top, selected, seed);
	}

	private void extractDetails(
			GuiGraphicsExtractor graphics, ItemStack stack, int centerX, int centerY,
			String pageText, boolean active) {
		graphics.fill(centerX - 43, centerY - 30, centerX + 43, centerY + 32, 0xB010141A);
		graphics.outline(centerX - 43, centerY - 30, 86, 62, active ? 0x80F4D35E : 0x406E747C);
		graphics.centeredText(this.font, pageText, centerX, centerY + 20, 0xFF9AA4B2);
		if (stack.isEmpty()) {
			return;
		}

		graphics.item(this.minecraft.player, stack, centerX - 8, centerY - 27, 500);
		List<Component> tooltip = Screen.getTooltipFromItem(this.minecraft, stack);
		int lines = Math.min(2, tooltip.size());
		for (int line = 0; line < lines; line++) {
			graphics.centeredText(this.font, tooltip.get(line), centerX,
					centerY - 7 + line * 10, 0xFFFFFFFF);
		}
	}

	private void extractHints(GuiGraphicsExtractor graphics) {
		if (!DigiMinerConfig.get().showIngameHints()) {
			return;
		}

		int hotbarTop = this.height - SLOT_SIZE - 7;
		String activePage = this.activeRing == ActiveRing.INVENTORY
				? (this.currentPage + 1) + "/" + PAGE_COUNT
				: "1/1";
		graphics.centeredText(this.font,
				"[RS] Inventory  |  [LS] Inventory  |  [LB/RB] Hotbar  |  [LT/RT] " + activePage,
				this.width / 2, hotbarTop - 23, 0xFFDEE5EF);
		graphics.centeredText(this.font,
				"[D-pad Down] Inventory <-> Hotbar  |  [A] Inventory <-> Inventory  |  [View] Close",
				this.width / 2, hotbarTop - 12, 0xFFB8C4D6);
	}

	private void extractHotbar(GuiGraphicsExtractor graphics) {
		int totalWidth = Inventory.getSelectionSize() * SLOT_SIZE;
		int left = (this.width - totalWidth) / 2;
		int top = this.height - SLOT_SIZE - 7;
		for (int i = 0; i < Inventory.getSelectionSize(); i++) {
			this.extractSlot(graphics, this.menuStack(InventoryMenu.USE_ROW_SLOT_START + i),
					left + i * SLOT_SIZE, top, i == this.hotbarCursor, 400 + i);
		}
	}

	private void extractSlot(
			GuiGraphicsExtractor graphics, ItemStack stack, int left, int top, boolean selected, int seed) {
		graphics.fill(left, top, left + SLOT_SIZE, top + SLOT_SIZE,
				selected ? 0xDD28313D : 0xB010141A);
		graphics.outline(left, top, SLOT_SIZE, SLOT_SIZE,
				selected ? 0xFFF4D35E : 0x906E747C);
		if (selected) {
			graphics.outline(left - 1, top - 1, SLOT_SIZE + 2, SLOT_SIZE + 2, 0xFFFFFFFF);
		}
		if (!stack.isEmpty()) {
			graphics.item(this.minecraft.player, stack, left + 2, top + 2, seed);
			graphics.itemDecorations(this.font, stack, left + 2, top + 2);
		}
	}

	private ItemStack menuStack(int menuSlot) {
		return this.minecraft.player.inventoryMenu.getSlot(menuSlot).getItem();
	}

	private int slotsOnCurrentPage() {
		return Math.min(PAGE_SIZE, MAIN_SLOT_COUNT - this.currentPage * PAGE_SIZE);
	}

	private int inventoryMenuSlot() {
		return InventoryMenu.INV_SLOT_START + this.currentPage * PAGE_SIZE + this.inventoryCursor;
	}

	private int equipmentMenuSlot() {
		return EQUIPMENT_MENU_SLOTS[this.equipmentCursor];
	}

	private int hotbarMenuSlot() {
		return InventoryMenu.USE_ROW_SLOT_START + this.hotbarCursor;
	}

	private void resetButtons() {
		this.previousLeftBumper = false;
		this.previousRightBumper = false;
		this.previousLeftTrigger = false;
		this.previousRightTrigger = false;
		this.previousDpadDown = false;
		this.previousA = false;
		this.previousB = false;
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
