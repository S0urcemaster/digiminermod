package de.sourcemaster.digiminermod.client.screen;

import de.sourcemaster.digiminermod.client.config.DigiMinerConfig;
import de.sourcemaster.digiminermod.client.input.ControllerAction;
import de.sourcemaster.digiminermod.client.input.ControllerSupport;
import de.sourcemaster.digiminermod.drone.DroneMenu;
import de.sourcemaster.digiminermod.drone.DroneMode;
import de.sourcemaster.digiminermod.drone.DroneNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.gui.screens.inventory.MenuAccess;

public final class DroneScreen extends Screen implements MenuAccess<DroneMenu> {
	private static final int PAGE_SIZE = 14, SLOT_SIZE = 20;
	private static final String[] MODES = {"Follow", "Static", "Automatic", "Upgrade"};
	private static final String[] MOVES = {"Forward", "Backward", "Up", "Down", "Turn left", "Turn right"};
	private enum Focus { DRONE, INVENTORY, HOTBAR }
	private final DroneMenu menu;
	private Focus focus = Focus.DRONE;
	private int leftPage, mode, droneCursor, inventoryPage, inventoryCursor, hotbarCursor, hotbarPage, optionCursor;
	private boolean previousA, previousDpad, previousLB, previousRB, previousLT, previousRT;

	public DroneScreen(DroneMenu menu, Inventory inventory, Component title) { super(title); this.menu = menu; }
	@Override public DroneMenu getMenu() { return this.menu; }
	@Override protected void init() { if (this.minecraft != null && this.minecraft.player != null) this.hotbarCursor = this.minecraft.player.getInventory().getSelectedSlot(); }

	@Override public void tick() {
		this.mode = this.menu.mode();
		ControllerSupport.Snapshot pad = ControllerSupport.poll();
		if (!pad.connected()) return;
		DigiMinerConfig config = DigiMinerConfig.get();
		this.updateLeft(pad.leftX(), pad.leftY()); this.updateRight(pad.rightX(), pad.rightY());
		boolean lb = pad.pressed(config.binding(ControllerAction.INVENTORY_HOTBAR_PREVIOUS));
		boolean rb = pad.pressed(config.binding(ControllerAction.INVENTORY_HOTBAR_NEXT));
		if (lb && !this.previousLB) { this.hotbarCursor = Math.floorMod(this.hotbarCursor - 1, 9); this.focus = Focus.HOTBAR; }
		if (rb && !this.previousRB) { this.hotbarCursor = (this.hotbarCursor + 1) % 9; this.focus = Focus.HOTBAR; }
		boolean lt = pad.pressed(config.binding(ControllerAction.INVENTORY_PAGE_PREVIOUS));
		boolean rt = pad.pressed(config.binding(ControllerAction.INVENTORY_PAGE_NEXT));
		if (this.focus == Focus.DRONE && (lt && !this.previousLT || rt && !this.previousRT)) {
			this.leftPage = Math.floorMod(this.leftPage + (rt ? 1 : -1), 4);
			this.optionCursor = this.leftPage == 2 ? this.mode : 0;
		}
		if (this.focus == Focus.INVENTORY && (lt && !this.previousLT || rt && !this.previousRT)) this.inventoryPage = 1 - this.inventoryPage;
		if (this.focus == Focus.HOTBAR && lt && !this.previousLT) this.hotbarPage = 0;
		if (this.focus == Focus.HOTBAR && rt && !this.previousRT) this.hotbarPage = 1;
		boolean a = pad.pressed(config.binding(ControllerAction.INVENTORY_TRANSFER_SECONDARY));
		if (a && !this.previousA) this.activate(config);
		boolean dpad = pad.pressed(config.binding(ControllerAction.INVENTORY_TRANSFER_HOTBAR));
		if (dpad && !this.previousDpad && this.hotbarPage == 0) this.transferHotbar();
		this.previousA = a; this.previousDpad = dpad; this.previousLB = lb; this.previousRB = rb; this.previousLT = lt; this.previousRT = rt;
	}

	private void updateLeft(float x, float y) {
		if (this.leftPage < 2) {
			if (x * x + y * y < 0.25F) return;
			this.focus = Focus.DRONE;
			this.droneCursor = radialIndex(x, y, this.leftPage == 0 ? 14 : 13);
		}
		else {
			if (Math.abs(y) < 0.25F) return;
			this.focus = Focus.DRONE;
			int count = this.leftPage == 2 ? MODES.length : this.mode == DroneMode.STATIC.ordinal() ? MOVES.length : 1;
			this.optionCursor = Math.max(0, Math.min(count - 1, Math.round((y + 1) * 0.5F * (count - 1))));
		}
	}

	private void updateRight(float x, float y) {
		if (x * x + y * y < 0.25F) return;
		this.focus = Focus.INVENTORY; this.inventoryCursor = radialIndex(x, y, this.inventoryPage == 0 ? 14 : 13);
	}

	private static int radialIndex(float x, float y, int count) {
		double angle = Math.atan2(x, -y); if (angle < 0) angle += Math.PI * 2;
		return Math.floorMod((int)Math.round(angle / (Math.PI * 2 / count)), count);
	}

	private void activate(DigiMinerConfig config) {
		if (this.focus == Focus.HOTBAR && this.hotbarPage == 1) { config.setStartStopMining(!config.startStopMining()); return; }
		if (this.focus == Focus.DRONE && this.leftPage == 2) {
			this.mode = this.optionCursor; ClientPlayNetworking.send(new DroneNetworking.CommandPayload(this.menu.dronePos().asLong(), this.mode)); return;
		}
		if (this.focus == Focus.DRONE && this.leftPage == 3) {
			if (this.mode == DroneMode.STATIC.ordinal()) ClientPlayNetworking.send(new DroneNetworking.CommandPayload(this.menu.dronePos().asLong(), 4 + this.optionCursor));
			return;
		}
		if (this.focus == Focus.HOTBAR) return;
		int drone = this.droneSlot(), inventory = this.inventorySlot();
		boolean fromDrone = this.focus == Focus.DRONE ? !this.menu.getSlot(drone).getItem().isEmpty() : this.menu.getSlot(inventory).getItem().isEmpty();
		this.moveOne(drone, inventory, fromDrone);
	}

	private void transferHotbar() {
		int other = this.focus == Focus.DRONE && this.leftPage < 2 ? this.droneSlot() : this.inventorySlot();
		int hotbar = 54 + this.hotbarCursor;
		boolean fromOther = this.focus == Focus.HOTBAR ? this.menu.getSlot(hotbar).getItem().isEmpty() : !this.menu.getSlot(other).getItem().isEmpty();
		this.moveOne(other, hotbar, fromOther);
	}

	private void moveOne(int first, int second, boolean fromFirst) {
		if (this.minecraft == null || this.minecraft.player == null || this.minecraft.gameMode == null) return;
		int source = fromFirst ? first : second, target = fromFirst ? second : first;
		if (this.menu.getSlot(source).getItem().isEmpty()) return;
		this.minecraft.gameMode.handleContainerInput(this.menu.containerId, source, 0, ContainerInput.PICKUP, this.minecraft.player);
		this.minecraft.gameMode.handleContainerInput(this.menu.containerId, target, 1, ContainerInput.PICKUP, this.minecraft.player);
		this.minecraft.gameMode.handleContainerInput(this.menu.containerId, source, 0, ContainerInput.PICKUP, this.minecraft.player);
	}

	private int droneSlot() { return this.leftPage * PAGE_SIZE + this.droneCursor; }
	private int inventorySlot() { return 27 + this.inventoryPage * PAGE_SIZE + this.inventoryCursor; }

	@Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		this.extractTransparentBackground(graphics);
		if (this.minecraft == null || this.minecraft.player == null) return;
		int cy = Math.max(100, this.height / 2 - 10);
		int spacing = Math.min(112, Math.max(82, this.width / 4));
		int left = this.width / 2 - spacing, right = this.width / 2 + spacing;
		int radius = Math.min(68, Math.max(52, Math.min(spacing - 22, (this.height - 78) / 2)));
		if (this.leftPage < 2) this.ring(graphics, left, cy, true); else this.options(graphics, left, cy);
		this.ring(graphics, right, cy, false, radius);
		this.extractHints(graphics); this.hotbar(graphics);
	}

	private void ring(GuiGraphicsExtractor graphics, int cx, int cy, boolean drone) {
		int spacing = Math.min(112, Math.max(82, this.width / 4));
		int radius = Math.min(68, Math.max(52, Math.min(spacing - 22, (this.height - 78) / 2)));
		this.ring(graphics, cx, cy, drone, radius);
	}

	private void ring(GuiGraphicsExtractor graphics, int cx, int cy, boolean drone, int radius) {
		int page = drone ? this.leftPage : this.inventoryPage, count = page == 0 ? 14 : 13;
		for (int i = 0; i < count; i++) {
			double a = Math.PI * 2 * i / count - Math.PI / 2;
			int x = cx + (int)Math.round(Math.cos(a) * radius) - 10, y = cy + (int)Math.round(Math.sin(a) * radius) - 10;
			int slot = drone ? page * 14 + i : 27 + page * 14 + i;
			this.slot(graphics, this.menu.getSlot(slot).getItem(), x, y, (drone ? this.droneCursor : this.inventoryCursor) == i,
					this.focus == (drone ? Focus.DRONE : Focus.INVENTORY));
		}
		ItemStack selected = this.menu.getSlot(drone ? this.droneSlot() : this.inventorySlot()).getItem();
		this.extractDetails(graphics, selected, cx, cy, drone ? (page + 1) + "/4" : (page + 1) + "/2",
				this.focus == (drone ? Focus.DRONE : Focus.INVENTORY));
	}

	private void options(GuiGraphicsExtractor graphics, int cx, int cy) {
		String[] labels = this.leftPage == 2 ? MODES : this.mode == DroneMode.STATIC.ordinal() ? MOVES
				: new String[]{this.mode == 2 ? "No program installed" : "No upgrades installed"};
		int top = cy - labels.length * SLOT_SIZE / 2;
		for (int i = 0; i < labels.length; i++) {
			boolean selected = i == this.optionCursor, active = this.leftPage == 2 && i == this.mode;
			int x = cx - 42, y = top + i * SLOT_SIZE;
			this.slot(graphics, ItemStack.EMPTY, x, y, selected, this.focus == Focus.DRONE);
			if (active) graphics.fill(x + 6, y + 6, x + 14, y + 14, 0xFF65CFFF);
			graphics.text(this.font, labels[i], x + 25, y + 6, active ? 0xFF65CFFF : 0xFFFFFFFF, false);
		}
		graphics.centeredText(this.font, (this.leftPage + 1) + "/4", cx, cy + 51,
				this.focus == Focus.DRONE ? 0xFFF4D35E : 0xFF9AA4B2);
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
		for (int i = 0; i < 9; i++) this.slot(graphics, this.menu.getSlot(54 + i).getItem(), left + i * SLOT_SIZE, this.height - SLOT_SIZE - 7,
				i == this.hotbarCursor, this.focus == Focus.HOTBAR);
	}

	private void slot(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y, boolean selected, boolean focused) {
		graphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, selected ? 0xDD28313D : 0xB010141A);
		graphics.outline(x, y, SLOT_SIZE, SLOT_SIZE, selected && focused ? 0xFFF4D35E : selected ? 0xFF65CFFF : 0x906E747C);
		if (selected) graphics.outline(x - 1, y - 1, SLOT_SIZE + 2, SLOT_SIZE + 2,
				focused ? 0xFFFFE49A : 0xFFA8E8FF);
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
		int top = this.height - SLOT_SIZE - 7;
		DigiMinerConfig config = DigiMinerConfig.get();
		String page = this.focus == Focus.DRONE ? (this.leftPage + 1) + "/4"
				: this.focus == Focus.INVENTORY ? (this.inventoryPage + 1) + "/2" : (this.hotbarPage + 1) + "/2";
		graphics.centeredText(this.font, "[RS] Inventory  [LS] Drone  [" + config.binding(ControllerAction.INVENTORY_HOTBAR_PREVIOUS).label()
				+ "/" + config.binding(ControllerAction.INVENTORY_HOTBAR_NEXT).label() + "] Hotbar  ["
				+ config.binding(ControllerAction.INVENTORY_PAGE_PREVIOUS).label() + "/"
				+ config.binding(ControllerAction.INVENTORY_PAGE_NEXT).label() + "] " + page,
				this.width / 2, top - 23, 0xFFDEE5EF);
		graphics.centeredText(this.font, "[" + config.binding(ControllerAction.INVENTORY_TRANSFER_HOTBAR).label()
				+ "] Inventory <-> Hotbar  [" + config.binding(ControllerAction.INVENTORY_TRANSFER_SECONDARY).label()
				+ "] Move  [" + config.binding(ControllerAction.INVENTORY_CLOSE).label() + "] Close",
				this.width / 2, top - 12, 0xFFB8C4D6);
	}

	@Override public boolean isPauseScreen() { return false; }
	@Override public void onClose() { if (this.minecraft != null && this.minecraft.player != null) this.minecraft.player.closeContainer(); }
}
