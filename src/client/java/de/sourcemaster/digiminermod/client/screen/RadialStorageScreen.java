package de.sourcemaster.digiminermod.client.screen;

import de.sourcemaster.digiminermod.client.config.DigiMinerConfig;
import de.sourcemaster.digiminermod.client.input.ControllerAction;
import de.sourcemaster.digiminermod.client.input.ControllerSupport;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

/** Controller-first screen shared by all vanilla storage-only containers. */
public class RadialStorageScreen<T extends AbstractContainerMenu> extends Screen implements MenuAccess<T> {
	private static final int PAGE_SIZE = 14;
	private static final int SLOT_SIZE = 20;
	private static final long INITIAL_REPEAT_DELAY = 350_000_000L, REPEAT_INTERVAL = 90_000_000L;
	private enum Focus { STORAGE, INVENTORY, HOTBAR }

	private final T menu;
	private final int storageSlots;
	private Focus focus = Focus.STORAGE;
	private int storagePage, storageCursor, inventoryPage, inventoryCursor, hotbarCursor, hotbarPage;
	private boolean previousA, previousDpad, previousLB, previousRB, previousLT, previousRT;
	private boolean aFromStorage, dpadFromInventory;
	private long nextARepeat, nextDpadRepeat;
	private final ItemTransferAcceleration aTransferAcceleration = new ItemTransferAcceleration();
	private final ItemTransferAcceleration dpadTransferAcceleration = new ItemTransferAcceleration();

	public RadialStorageScreen(T menu, Inventory inventory, Component title, int storageSlots) {
		super(title);
		this.menu = menu;
		this.storageSlots = storageSlots;
	}

	@Override public T getMenu() { return this.menu; }

	@Override protected void init() {
		if (this.minecraft != null && this.minecraft.player != null) {
			this.hotbarCursor = this.minecraft.player.getInventory().getSelectedSlot();
		}
	}

	@Override public void tick() {
		ControllerSupport.Snapshot pad = ControllerSupport.poll();
		if (!pad.connected()) { this.resetButtons(); return; }
		this.updateStick(pad.leftX(), pad.leftY(), Focus.STORAGE);
		this.updateStick(pad.rightX(), pad.rightY(), Focus.INVENTORY);
		DigiMinerConfig config = DigiMinerConfig.get();
		boolean lb = pad.pressed(config.binding(ControllerAction.INVENTORY_HOTBAR_PREVIOUS));
		boolean rb = pad.pressed(config.binding(ControllerAction.INVENTORY_HOTBAR_NEXT));
		if (lb && !this.previousLB) { this.hotbarCursor = Math.floorMod(this.hotbarCursor - 1, 9); this.focus = Focus.HOTBAR; }
		if (rb && !this.previousRB) { this.hotbarCursor = (this.hotbarCursor + 1) % 9; this.focus = Focus.HOTBAR; }
		boolean lt = pad.pressed(config.binding(ControllerAction.INVENTORY_PAGE_PREVIOUS));
		boolean rt = pad.pressed(config.binding(ControllerAction.INVENTORY_PAGE_NEXT));
		if (this.focus == Focus.STORAGE && (lt && !this.previousLT || rt && !this.previousRT)) {
			this.storagePage = Math.floorMod(this.storagePage + (rt ? 1 : -1), this.storagePageCount());
			this.storageCursor = Math.min(this.storageCursor, this.storagePageSize() - 1);
		}
		if (this.focus == Focus.INVENTORY && (lt && !this.previousLT || rt && !this.previousRT)) {
			this.inventoryPage = 1 - this.inventoryPage;
			this.inventoryCursor = Math.min(this.inventoryCursor, this.inventoryPageSize() - 1);
		}
		if (this.focus == Focus.HOTBAR && lt && !this.previousLT) this.hotbarPage = 0;
		if (this.focus == Focus.HOTBAR && rt && !this.previousRT) this.hotbarPage = 1;
		boolean a = pad.pressed(config.binding(ControllerAction.INVENTORY_TRANSFER_SECONDARY));
		if (this.focus == Focus.HOTBAR && this.hotbarPage == 1) {
			if (a && !this.previousA) config.setStartStopMining(!config.startStopMining());
		} else this.handleATransfer(a);
		boolean dpad = pad.pressed(config.binding(ControllerAction.INVENTORY_TRANSFER_HOTBAR));
		if (this.hotbarPage == 0) this.handleDpadTransfer(dpad);
		this.previousA = a; this.previousDpad = dpad; this.previousLB = lb; this.previousRB = rb;
		this.previousLT = lt; this.previousRT = rt;
	}

	private void updateStick(float x, float y, Focus target) {
		if (x * x + y * y < 0.25F) return;
		this.focus = target;
		int count = target == Focus.STORAGE ? this.storagePageSize() : this.inventoryPageSize();
		int selected = radialIndex(x, y, count);
		if (target == Focus.STORAGE) this.storageCursor = selected; else this.inventoryCursor = selected;
	}

	private static int radialIndex(float x, float y, int count) {
		double angle = Math.atan2(x, -y); if (angle < 0) angle += Math.PI * 2;
		return Math.floorMod((int)Math.round(angle / (Math.PI * 2 / count)), count);
	}

	private void handleATransfer(boolean pressed) {
		int amount = this.aTransferAcceleration.amount(pressed, this.previousA);
		int storage = this.storageSlot(), inventory = this.inventorySlot();
		if (pressed && !this.previousA) {
			this.aFromStorage = switch (this.focus) {
				case STORAGE -> !this.menu.getSlot(storage).getItem().isEmpty();
				case INVENTORY -> this.menu.getSlot(inventory).getItem().isEmpty();
				case HOTBAR -> false;
			};
		}
		for (int i = 0; i < amount; i++) this.moveOne(storage, inventory, this.aFromStorage);
	}

	private void handleDpadTransfer(boolean pressed) {
		int amount = this.dpadTransferAcceleration.amount(pressed, this.previousDpad);
		int inventory = this.inventorySlot(), hotbar = this.hotbarSlot();
		if (pressed && !this.previousDpad) {
			this.dpadFromInventory = switch (this.focus) {
				case HOTBAR -> this.menu.getSlot(hotbar).getItem().isEmpty();
				case INVENTORY -> !this.menu.getSlot(inventory).getItem().isEmpty();
				case STORAGE -> true;
			};
		}
		for (int i = 0; i < amount; i++) this.moveOne(inventory, hotbar, this.dpadFromInventory);
	}

	private void moveOne(int first, int second, boolean fromFirst) {
		if (this.minecraft == null || this.minecraft.player == null || this.minecraft.gameMode == null) return;
		int source = fromFirst ? first : second, target = fromFirst ? second : first;
		if (this.menu.getSlot(source).getItem().isEmpty()) return;
		this.minecraft.gameMode.handleContainerInput(this.menu.containerId, source, 0, ContainerInput.PICKUP, this.minecraft.player);
		this.minecraft.gameMode.handleContainerInput(this.menu.containerId, target, 1, ContainerInput.PICKUP, this.minecraft.player);
		this.minecraft.gameMode.handleContainerInput(this.menu.containerId, source, 0, ContainerInput.PICKUP, this.minecraft.player);
	}

	private int storagePageCount() { return (this.storageSlots + PAGE_SIZE - 1) / PAGE_SIZE; }
	private int storagePageSize() { return Math.min(PAGE_SIZE, this.storageSlots - this.storagePage * PAGE_SIZE); }
	private int inventoryPageSize() { return this.inventoryPage == 0 ? 14 : 13; }
	private int storageSlot() { return this.storagePage * PAGE_SIZE + this.storageCursor; }
	private int inventorySlot() { return this.storageSlots + this.inventoryPage * PAGE_SIZE + this.inventoryCursor; }
	private int hotbarSlot() { return this.storageSlots + 27 + this.hotbarCursor; }

	@Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		this.extractTransparentBackground(graphics);
		if (this.minecraft == null || this.minecraft.player == null) return;
		int cy = Math.max(100, this.height / 2 - 10), spacing = Math.min(112, Math.max(82, this.width / 4));
		int radius = Math.min(68, Math.max(52, Math.min(spacing - 22, (this.height - 78) / 2)));
		this.ring(graphics, this.width / 2 - spacing, cy, true, radius);
		this.ring(graphics, this.width / 2 + spacing, cy, false, radius);
		this.extractHints(graphics); this.hotbar(graphics);
	}

	private void ring(GuiGraphicsExtractor graphics, int cx, int cy, boolean storage, int radius) {
		int count = storage ? this.storagePageSize() : this.inventoryPageSize();
		for (int i = 0; i < count; i++) {
			double a = Math.PI * 2 * i / count - Math.PI / 2;
			int x = cx + (int)Math.round(Math.cos(a) * radius) - 10;
			int y = cy + (int)Math.round(Math.sin(a) * radius) - 10;
			int slot = storage ? this.storagePage * PAGE_SIZE + i : this.storageSlots + this.inventoryPage * PAGE_SIZE + i;
			this.slot(graphics, this.menu.getSlot(slot).getItem(), x, y,
					(storage ? this.storageCursor : this.inventoryCursor) == i,
					this.focus == (storage ? Focus.STORAGE : Focus.INVENTORY));
		}
		ItemStack selected = this.menu.getSlot(storage ? this.storageSlot() : this.inventorySlot()).getItem();
		this.extractDetails(graphics, selected, cx, cy,
				storage ? (this.storagePage + 1) + "/" + this.storagePageCount() : (this.inventoryPage + 1) + "/2",
				this.focus == (storage ? Focus.STORAGE : Focus.INVENTORY));
	}

	private void hotbar(GuiGraphicsExtractor graphics) {
		if (this.focus == Focus.HOTBAR && this.hotbarPage == 1) {
			int top = this.height - SLOT_SIZE - 7;
			String mode = DigiMinerConfig.get().startStopMining() ? "Start/Stop mining" : "Hold to mine";
			graphics.fill(this.width / 2 - 90, top, this.width / 2 + 90, top + SLOT_SIZE, 0xDD28313D);
			graphics.outline(this.width / 2 - 90, top, 180, SLOT_SIZE, 0xFFF4D35E);
			graphics.centeredText(this.font, "[A] " + mode, this.width / 2, top + 6, 0xFFFFFFFF); return;
		}
		int left = (this.width - 9 * SLOT_SIZE) / 2;
		for (int i = 0; i < 9; i++) this.slot(graphics, this.menu.getSlot(this.storageSlots + 27 + i).getItem(),
				left + i * SLOT_SIZE, this.height - SLOT_SIZE - 7, i == this.hotbarCursor, this.focus == Focus.HOTBAR);
	}

	private void slot(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y, boolean selected, boolean focused) {
		graphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, selected ? 0xDD28313D : 0xB010141A);
		graphics.outline(x, y, SLOT_SIZE, SLOT_SIZE, selected && focused ? 0xFFF4D35E : selected ? 0xFF65CFFF : 0x906E747C);
		if (selected) graphics.outline(x - 1, y - 1, SLOT_SIZE + 2, SLOT_SIZE + 2, focused ? 0xFFFFE49A : 0xFFA8E8FF);
		if (!stack.isEmpty()) { graphics.item(this.minecraft.player, stack, x + 2, y + 2, x + y); graphics.itemDecorations(this.font, stack, x + 2, y + 2); }
	}

	private void extractDetails(GuiGraphicsExtractor graphics, ItemStack stack, int cx, int cy, String page, boolean active) {
		graphics.fill(cx - 43, cy - 30, cx + 43, cy + 32, 0xB010141A);
		graphics.outline(cx - 43, cy - 30, 86, 62, active ? 0x80F4D35E : 0x406E747C);
		graphics.centeredText(this.font, page, cx, cy + 20, 0xFF9AA4B2);
		if (stack.isEmpty()) return;
		graphics.item(this.minecraft.player, stack, cx - 8, cy - 27, 500);
		var tooltip = Screen.getTooltipFromItem(this.minecraft, stack);
		for (int i = 0; i < Math.min(2, tooltip.size()); i++) graphics.centeredText(this.font, tooltip.get(i), cx, cy - 7 + i * 10, 0xFFFFFFFF);
	}

	private void extractHints(GuiGraphicsExtractor graphics) {
		if (!DigiMinerConfig.get().showIngameHints()) return;
		int top = this.height - SLOT_SIZE - 7; DigiMinerConfig config = DigiMinerConfig.get();
		String page = this.focus == Focus.STORAGE ? (this.storagePage + 1) + "/" + this.storagePageCount()
				: this.focus == Focus.INVENTORY ? (this.inventoryPage + 1) + "/2" : (this.hotbarPage + 1) + "/2";
		graphics.centeredText(this.font, "[RS] Inventory  [LS] Container  [" + config.binding(ControllerAction.INVENTORY_HOTBAR_PREVIOUS).label()
				+ "/" + config.binding(ControllerAction.INVENTORY_HOTBAR_NEXT).label() + "] Hotbar  ["
				+ config.binding(ControllerAction.INVENTORY_PAGE_PREVIOUS).label() + "/"
				+ config.binding(ControllerAction.INVENTORY_PAGE_NEXT).label() + "] " + page,
				this.width / 2, top - 23, 0xFFDEE5EF);
		graphics.centeredText(this.font, "[" + config.binding(ControllerAction.INVENTORY_TRANSFER_HOTBAR).label()
				+ "] Inventory <-> Hotbar  [" + config.binding(ControllerAction.INVENTORY_TRANSFER_SECONDARY).label()
				+ "] Move  [" + config.binding(ControllerAction.INVENTORY_CLOSE).label() + "] Close",
				this.width / 2, top - 12, 0xFFB8C4D6);
	}

	private void resetButtons() { this.previousA = this.previousDpad = this.previousLB = this.previousRB = this.previousLT = this.previousRT = false; }
	@Override public boolean isPauseScreen() { return false; }
	@Override public void onClose() { if (this.minecraft != null && this.minecraft.player != null) this.minecraft.player.closeContainer(); }
}
