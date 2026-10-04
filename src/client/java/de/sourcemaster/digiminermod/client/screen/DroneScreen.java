package de.sourcemaster.digiminermod.client.screen;

import de.sourcemaster.digiminermod.DigiMinerMod;
import de.sourcemaster.digiminermod.client.config.DigiMinerConfig;
import de.sourcemaster.digiminermod.client.input.ControllerAction;
import de.sourcemaster.digiminermod.client.input.ControllerSupport;
import de.sourcemaster.digiminermod.drone.DroneMenu;
import de.sourcemaster.digiminermod.drone.DroneNetworking;
import de.sourcemaster.digiminermod.drone.DroneBlockEntity;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.gui.screens.inventory.MenuAccess;

public final class DroneScreen extends Screen implements MenuAccess<DroneMenu> {
	private static final int PAGE_SIZE = 14, SLOT_SIZE = 20;
	private static final String[] MODES = {"Follow", "Static", "Build", "Excavate"};
	private static final String[] BUILD_PROGRAMS = {"Bridge", "Wall", "Floor"};
	private static final String[] EXCAVATE_PROGRAMS = {"3x3 Tunnel", "Shaft", "Room"};
	private static final String[][] BUILD_PARAMETERS = {{"Len", ""}, {"Len", "H"}, {"Len", "W"}};
	private static final String[][] EXCAVATE_PARAMETERS = {{"Len", ""}, {"Depth", ""}, {"Len", "W"}};
	private enum Focus { DRONE, INVENTORY, HOTBAR }
	private final DroneMenu menu;
	private Focus focus = Focus.DRONE;
	private int leftPage, mode, droneCursor, inventoryPage, inventoryCursor, hotbarCursor, hotbarPage, optionCursor;
	private boolean previousA, previousDpad, previousLB, previousRB, previousLT, previousRT;
	private EditBox parameterOne, parameterTwo, droneName;
	private final String[][][] parameterValues = {
			{{"10", ""}, {"10", "3"}, {"10", "3"}},
			{{"10", ""}, {"10", ""}, {"10", "3"}}
	};
	private int parameterMode = 2, parameterProgram;
	private int parameterCursor, parameterAdjustDirection, parameterAdjustTicks;
	private boolean parameterAdjustLocked;

	public DroneScreen(DroneMenu menu, Inventory inventory, Component title) { super(title); this.menu = menu; }
	@Override public DroneMenu getMenu() { return this.menu; }
	@Override protected void init() {
		if (this.minecraft != null && this.minecraft.player != null) this.hotbarCursor = this.minecraft.player.getInventory().getSelectedSlot();
		int spacing = Math.min(112, Math.max(82, this.width / 4));
		int left = this.width / 2 - spacing;
		this.parameterOne = this.addRenderableWidget(new EditBox(this.font, left - 30, 0, 34, 14, Component.literal("Parameter 1")));
		this.parameterTwo = this.addRenderableWidget(new EditBox(this.font, left + 25, 0, 34, 14, Component.literal("Parameter 2")));
		this.parameterOne.setMaxLength(3);
		this.parameterTwo.setMaxLength(3);
		this.parameterOne.setValue(this.parameterValues[0][0][0]);
		this.parameterTwo.setValue(this.parameterValues[0][0][1]);
		this.parameterOne.setHint(Component.literal("0-999"));
		this.parameterTwo.setHint(Component.literal("0-999"));
		this.droneName = this.addRenderableWidget(new EditBox(this.font, left - 55, Math.max(100, this.height / 2 - 10) - 4,
				110, 18, Component.literal("Drone name")));
		this.droneName.setMaxLength(16);
		if (this.minecraft != null && this.minecraft.level != null
				&& this.minecraft.level.getBlockEntity(this.menu.dronePos()) instanceof DroneBlockEntity drone) {
			this.droneName.setValue(drone.getDroneName());
		} else this.droneName.setValue("Digi");
		this.droneName.setResponder(name -> {
			String cleaned = name.replaceAll("[^A-Za-z0-9_]", "");
			if (!cleaned.equals(name)) { this.droneName.setValue(cleaned); return; }
			ClientPlayNetworking.send(new DroneNetworking.RenamePayload(this.menu.dronePos().asLong(), cleaned));
		});
	}

	@Override public void tick() {
		this.mode = this.menu.mode();
		boolean programPage = this.leftPage == 3 && (this.mode == 2 || this.mode == 3);
		boolean namePage = this.leftPage == 3 && this.mode == 0;
		boolean basicPrograms = this.hasCartridge(this.mode);
		if (programPage && (this.parameterMode != this.mode || this.parameterProgram != this.optionCursor)) {
			this.saveParameterValues();
			this.parameterMode = this.mode;
			this.parameterProgram = this.optionCursor;
			this.loadParameterValues();
		}
		if (this.parameterOne != null) {
			boolean fields = programPage && basicPrograms;
			String[][] definitions = this.parameterDefinitions();
			boolean second = fields && !definitions[this.optionCursor][1].isEmpty();
			int rowY = Math.max(100, this.height / 2 - 10) - 45 + this.optionCursor * 32 + 12;
			this.parameterOne.setY(rowY); this.parameterTwo.setY(rowY);
			this.parameterOne.setVisible(fields); this.parameterTwo.setVisible(second);
			if (fields && this.getFocused() != this.parameterOne && this.getFocused() != this.parameterTwo) this.focusParameterField();
			if (!fields && (this.getFocused() == this.parameterOne || this.getFocused() == this.parameterTwo)) this.setFocused(null);
		}
		if (this.droneName != null) {
			this.droneName.setVisible(namePage);
			if (namePage && this.getFocused() != this.droneName) this.setFocused(this.droneName);
			if (!namePage && this.getFocused() == this.droneName) this.setFocused(null);
		}
		ControllerSupport.Snapshot pad = ControllerSupport.poll();
		if (!pad.connected()) return;
		DigiMinerConfig config = DigiMinerConfig.get();
		this.updateLeft(pad.leftX(), pad.leftY());
		if (!this.updateParameterWithRightStick(pad.rightX(), pad.rightY())) this.updateRight(pad.rightX(), pad.rightY());
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
		else if (this.leftPage == 2) {
			if (Math.abs(y) < 0.25F) return;
			this.focus = Focus.DRONE;
			int count = MODES.length;
			this.optionCursor = Math.max(0, Math.min(count - 1, Math.round((y + 1) * 0.5F * (count - 1))));
		}
		else if ((this.mode == 2 || this.mode == 3) && this.hasCartridge(this.mode) && x * x + y * y >= 0.25F) {
			this.focus = Focus.DRONE;
			int next = axisSegment(y, 3);
			if (next != this.optionCursor) {
				this.saveParameterValues();
				this.optionCursor = next;
				this.parameterMode = this.mode;
				this.parameterProgram = next;
				this.loadParameterValues();
			}
			int fieldCount = this.parameterDefinitions()[this.optionCursor][1].isEmpty() ? 1 : 2;
			this.parameterCursor = axisSegment(x, fieldCount);
			this.focusParameterField();
		}
		else if (this.mode == 1 && Math.abs(y) >= 0.25F) {
			this.focus = Focus.DRONE;
			this.optionCursor = axisSegment(y, 3);
		}
	}

	private static int axisSegment(float value, int count) {
		if (count <= 1) return 0;
		float normalized = Math.max(0.0F, Math.min(0.999999F, (value + 1.0F) * 0.5F));
		return Math.min(count - 1, (int)(normalized * count));
	}

	private boolean updateParameterWithRightStick(float x, float y) {
		boolean available = this.leftPage == 3 && (this.mode == 2 || this.mode == 3) && this.hasCartridge(this.mode);
		if (!available) {
			this.parameterAdjustLocked = false;
			this.parameterAdjustDirection = 0;
			this.parameterAdjustTicks = 0;
			return false;
		}
		int direction = y <= -0.45F ? 1 : y >= 0.45F ? -1 : 0;
		if (direction == 0) {
			if (Math.abs(x) < 0.35F && Math.abs(y) < 0.35F) {
				this.parameterAdjustLocked = false;
				this.parameterAdjustDirection = 0;
				this.parameterAdjustTicks = 0;
			}
			return this.parameterAdjustLocked;
		}
		this.parameterAdjustLocked = true;
		this.focus = Focus.DRONE;
		if (direction != this.parameterAdjustDirection) {
			this.parameterAdjustDirection = direction;
			this.parameterAdjustTicks = 0;
			this.adjustParameter(direction);
			return true;
		}
		this.parameterAdjustTicks++;
		int interval = this.parameterAdjustTicks < 12 ? Integer.MAX_VALUE
				: this.parameterAdjustTicks < 32 ? 4 : this.parameterAdjustTicks < 60 ? 2 : 1;
		if (interval != Integer.MAX_VALUE && this.parameterAdjustTicks % interval == 0) this.adjustParameter(direction);
		return true;
	}

	private void adjustParameter(int direction) {
		EditBox field = this.parameterCursor == 1 ? this.parameterTwo : this.parameterOne;
		int next = Math.max(0, Math.min(999, this.parameterValue(field) + direction));
		field.setValue(Integer.toString(next));
		this.focusParameterField();
	}

	private void focusParameterField() {
		if (this.parameterOne == null || this.parameterTwo == null) return;
		EditBox field = this.parameterCursor == 1 && this.parameterTwo.isVisible() ? this.parameterTwo : this.parameterOne;
		this.parameterCursor = field == this.parameterTwo ? 1 : 0;
		this.setFocused(field);
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
		// A program page owns the A action even while Minecraft's EditBox or the
		// hotbar still has an internal focus. The fields only edit parameters.
		if (this.leftPage == 3 && (this.mode == 2 || this.mode == 3)) {
			if (this.hasCartridge(this.mode)) {
				this.saveParameterValues();
				ClientPlayNetworking.send(new DroneNetworking.ProgramPayload(this.menu.dronePos().asLong(), this.optionCursor,
						this.parameterValue(this.parameterOne), this.parameterValue(this.parameterTwo)));
			}
			return;
		}
		if (this.leftPage == 3) {
			if (this.mode != 1) return;
			int inventory = this.inventorySlot();
			int equipmentSlot = this.optionCursor == 1 ? DroneMenu.PROGRAM_DRIVE_SLOT
					: this.optionCursor == 2 ? DroneMenu.EXCAVATE_CARTRIDGE_SLOT : DroneMenu.SCANNER_CARTRIDGE_SLOT;
			boolean fromTool = this.focus == Focus.DRONE;
			this.moveOne(equipmentSlot, inventory, fromTool);
			return;
		}
		if (this.leftPage >= 2) return;
		int drone = this.droneSlot(), inventory = this.inventorySlot();
		boolean fromDrone = this.focus == Focus.DRONE;
		this.moveOne(drone, inventory, fromDrone);
	}

	private boolean hasCartridge(int selectedMode) {
		return selectedMode == 2
				? this.menu.getSlot(DroneMenu.PROGRAM_DRIVE_SLOT).getItem().is(DigiMinerMod.BASIC_BUILD_CARTRIDGE)
				: selectedMode == 3 && this.menu.getSlot(DroneMenu.EXCAVATE_CARTRIDGE_SLOT).getItem().is(DigiMinerMod.BASIC_EXCAVATE_CARTRIDGE);
	}

	private String[] programNames() { return this.mode == 3 ? EXCAVATE_PROGRAMS : BUILD_PROGRAMS; }
	private String[][] parameterDefinitions() { return this.mode == 3 ? EXCAVATE_PARAMETERS : BUILD_PARAMETERS; }
	private void saveParameterValues() {
		if (this.parameterOne == null || this.parameterMode < 2 || this.parameterMode > 3) return;
		this.parameterValues[this.parameterMode - 2][this.parameterProgram][0] = this.parameterOne.getValue();
		this.parameterValues[this.parameterMode - 2][this.parameterProgram][1] = this.parameterTwo.getValue();
	}
	private void loadParameterValues() {
		if (this.parameterOne == null || this.parameterMode < 2 || this.parameterMode > 3) return;
		this.parameterOne.setValue(this.parameterValues[this.parameterMode - 2][this.parameterProgram][0]);
		this.parameterTwo.setValue(this.parameterValues[this.parameterMode - 2][this.parameterProgram][1]);
	}

	private int parameterValue(EditBox field) {
		if (field == null) return 0;
		try { return Math.max(0, Math.min(999, Integer.parseInt(field.getValue()))); }
		catch (NumberFormatException ignored) { return 0; }
	}

	private void transferHotbar() {
		int other = this.inventorySlot();
		int hotbar = DroneMenu.HOTBAR_START + this.hotbarCursor;
		boolean fromOther = this.focus != Focus.HOTBAR;
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
	private int inventorySlot() { return DroneMenu.PLAYER_MAIN_START + this.inventoryPage * PAGE_SIZE + this.inventoryCursor; }

	@Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		this.extractTransparentBackground(graphics);
		if (this.minecraft == null || this.minecraft.player == null) return;
		int cy = Math.max(100, this.height / 2 - 10);
		int spacing = Math.min(112, Math.max(82, this.width / 4));
		int left = this.width / 2 - spacing, right = this.width / 2 + spacing;
		int radius = Math.min(68, Math.max(52, Math.min(spacing - 22, (this.height - 78) / 2)));
		if (this.leftPage < 2) this.ring(graphics, left, cy, true); else this.options(graphics, left, cy);
		this.ring(graphics, right, cy, false, radius);
		if (this.parameterOne != null && this.parameterOne.isVisible()) this.parameterOne.extractWidgetRenderState(graphics, mouseX, mouseY, partialTick);
		if (this.parameterTwo != null && this.parameterTwo.isVisible()) this.parameterTwo.extractWidgetRenderState(graphics, mouseX, mouseY, partialTick);
		if (this.droneName != null && this.droneName.isVisible()) this.droneName.extractWidgetRenderState(graphics, mouseX, mouseY, partialTick);
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
			int slot = drone ? page * 14 + i : DroneMenu.PLAYER_MAIN_START + page * 14 + i;
			this.slot(graphics, this.menu.getSlot(slot).getItem(), x, y, (drone ? this.droneCursor : this.inventoryCursor) == i,
					this.focus == (drone ? Focus.DRONE : Focus.INVENTORY));
		}
		ItemStack selected = this.menu.getSlot(drone ? this.droneSlot() : this.inventorySlot()).getItem();
		this.extractDetails(graphics, selected, cx, cy, drone ? (page + 1) + "/4" : (page + 1) + "/2",
				this.focus == (drone ? Focus.DRONE : Focus.INVENTORY));
	}

	private void options(GuiGraphicsExtractor graphics, int cx, int cy) {
		if (this.leftPage == 3) {
			if (this.mode == 1) {
				int top = cy - 40;
				ItemStack scanner = this.menu.getSlot(DroneMenu.SCANNER_CARTRIDGE_SLOT).getItem();
				ItemStack build = this.menu.getSlot(DroneMenu.PROGRAM_DRIVE_SLOT).getItem();
				ItemStack excavate = this.menu.getSlot(DroneMenu.EXCAVATE_CARTRIDGE_SLOT).getItem();
				this.slot(graphics, scanner, cx - 48, top, this.optionCursor == 0, this.focus == Focus.DRONE);
				graphics.text(this.font, "Scanner Cartridge", cx - 22, top + 6, 0xFFFFD95A, false);
				this.slot(graphics, build, cx - 48, top + 25, this.optionCursor == 1, this.focus == Focus.DRONE);
				graphics.text(this.font, "Build Cartridge", cx - 22, top + 31, 0xFF65CFFF, false);
				this.slot(graphics, excavate, cx - 48, top + 50, this.optionCursor == 2, this.focus == Focus.DRONE);
				graphics.text(this.font, "Excavate Cartridge", cx - 22, top + 56, 0xFFFF665E, false);
				graphics.centeredText(this.font, "4/4", cx, cy + 51, 0xFF9AA4B2);
			} else if (this.mode == 2 || this.mode == 3) {
				if (this.hasCartridge(this.mode)) {
					String[] programs = this.programNames();
					String[][] parameters = this.parameterDefinitions();
					int top = cy - 45;
					for (int i = 0; i < 3; i++) {
						int y = top + i * 32;
						boolean selected = i == this.optionCursor;
						graphics.fill(cx - 58, y, cx + 58, y + 29, selected ? 0xDD28313D : 0xB010141A);
						int cartridgeColor = this.mode == 2 ? 0xFF65CFFF : 0xFFFF665E;
						graphics.outline(cx - 58, y, 116, 29, selected && this.focus == Focus.DRONE ? 0xFFF4D35E : selected ? cartridgeColor : 0x706E747C);
						graphics.text(this.font, programs[i], cx - 53, y + 2, cartridgeColor, false);
						graphics.text(this.font, parameters[i][0], cx - 53, y + 17, 0xFFB8C4D6, false);
						if (!parameters[i][1].isEmpty()) graphics.text(this.font, parameters[i][1], cx + 4, y + 17, 0xFFB8C4D6, false);
						if (!selected) {
							graphics.text(this.font, this.parameterValues[this.mode - 2][i][0], cx - 24, y + 17, 0xFFFFFFFF, false);
							if (!parameters[i][1].isEmpty()) graphics.text(this.font, this.parameterValues[this.mode - 2][i][1], cx + 35, y + 17, 0xFFFFFFFF, false);
						}
					}
				} else graphics.centeredText(this.font, "No " + (this.mode == 2 ? "build" : "excavate") + " cartridge", cx, cy - 5, 0xFFB8C4D6);
				graphics.centeredText(this.font, "4/4", cx, cy + 51, 0xFF9AA4B2);
			} else {
				graphics.centeredText(this.font, "Name", cx, cy - 18, 0xFFB8C4D6);
				if (this.menu.getSlot(DroneMenu.SCANNER_CARTRIDGE_SLOT).getItem().is(DigiMinerMod.BASIC_SCANNER_CARTRIDGE)) {
					graphics.centeredText(this.font, "Coordinates I", cx, cy + 18, 0xFFFFD95A);
					graphics.centeredText(this.font, "Iron I", cx, cy + 28, 0xFFFFD95A);
				}
				graphics.centeredText(this.font, "4/4", cx, cy + 51, 0xFF9AA4B2);
			}
			return;
		}
		String[] labels = MODES;
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
		for (int i = 0; i < 9; i++) this.slot(graphics, this.menu.getSlot(DroneMenu.HOTBAR_START + i).getItem(), left + i * SLOT_SIZE, this.height - SLOT_SIZE - 7,
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
		String action = this.leftPage == 3 && (this.mode == 2 || this.mode == 3) ? "Start" : "Move";
		graphics.centeredText(this.font, "[RS] Inventory  [LS] Drone  [" + config.binding(ControllerAction.INVENTORY_HOTBAR_PREVIOUS).label()
				+ "/" + config.binding(ControllerAction.INVENTORY_HOTBAR_NEXT).label() + "] Hotbar  ["
				+ config.binding(ControllerAction.INVENTORY_PAGE_PREVIOUS).label() + "/"
				+ config.binding(ControllerAction.INVENTORY_PAGE_NEXT).label() + "] " + page,
				this.width / 2, top - 23, 0xFFDEE5EF);
		graphics.centeredText(this.font, "[" + config.binding(ControllerAction.INVENTORY_TRANSFER_HOTBAR).label()
				+ "] Inventory <-> Hotbar  [" + config.binding(ControllerAction.INVENTORY_TRANSFER_SECONDARY).label()
				+ "] " + action + "  [" + config.binding(ControllerAction.INVENTORY_CLOSE).label() + "] Close",
				this.width / 2, top - 12, 0xFFB8C4D6);
	}

	@Override public boolean isPauseScreen() { return false; }
	@Override public void onClose() { if (this.minecraft != null && this.minecraft.player != null) this.minecraft.player.closeContainer(); }
}
