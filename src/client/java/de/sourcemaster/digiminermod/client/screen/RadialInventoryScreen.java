package de.sourcemaster.digiminermod.client.screen;

import de.sourcemaster.digiminermod.client.config.DigiMinerConfig;
import de.sourcemaster.digiminermod.client.input.ControllerSupport;
import de.sourcemaster.digiminermod.client.input.ControllerAction;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RadialInventoryScreen extends Screen {
	private static final int MAIN_SLOT_COUNT = 27;
	private static final int PAGE_SIZE = 14;
	private static final int PAGE_COUNT = 2;
	private static final int[] EQUIPMENT_MENU_SLOTS = {5, 6, 7, 8, InventoryMenu.SHIELD_SLOT};
	private static final int[] CRAFTING_MENU_SLOTS = {1, 2, 3, 4};
	private static final int SLOT_SIZE = 20;
	private static final float STICK_DEADZONE = 0.50F;
	private static final double ANGLE_HYSTERESIS = Math.toRadians(2.5);
	private static final long INITIAL_REPEAT_DELAY = 350_000_000L;
	private static final long REPEAT_INTERVAL = 90_000_000L;

	private enum ActiveRing { LEFT, INVENTORY, HOTBAR }
	private enum LeftPage { CRAFTING, EQUIPMENT, RECIPES }

	private ActiveRing activeRing = ActiveRing.INVENTORY;
	private LeftPage leftPage = LeftPage.CRAFTING;
	private int currentPage;
	private int inventoryCursor;
	private int equipmentCursor;
	private int recipeCursor;
	private List<RecipeSuggestion> recipeSuggestions = List.of();
	private Item lastRecipeFilter = Items.AIR;
	private int hotbarCursor;
	private int hotbarPage;
	private boolean previousLeftBumper;
	private boolean previousRightBumper;
	private boolean previousLeftTrigger;
	private boolean previousRightTrigger;
	private boolean previousDpadDown;
	private boolean previousA;
	private boolean previousY;
	private boolean dpadFromInventory;
	private boolean aFromEquipment;
	private long nextDpadRepeat;
	private long nextARepeat;
	private long nextYRepeat;
	private ControllerSupport.Snapshot controller = ControllerSupport.Snapshot.NONE;

	public RadialInventoryScreen() {
		super(Component.literal("Digi Miner Inventar"));
	}

	@Override
	protected void init() {
		super.init();
		this.controller = ControllerSupport.poll();
		if (this.minecraft != null && this.minecraft.player != null) {
			this.hotbarCursor = this.minecraft.player.getInventory().getSelectedSlot();
		}
		this.refreshRecipeSuggestions();
	}

	@Override
	public void tick() {
		super.tick();
		this.controller = ControllerSupport.poll();
		if (!this.controller.connected()) {
			this.resetButtons();
			return;
		}

		this.updateStick(this.controller.leftX(), this.controller.leftY(), ActiveRing.LEFT);
		this.updateStick(this.controller.rightX(), this.controller.rightY(), ActiveRing.INVENTORY);

		DigiMinerConfig config = DigiMinerConfig.get();
		boolean leftBumper = this.controller.pressed(config.binding(ControllerAction.INVENTORY_HOTBAR_PREVIOUS));
		boolean rightBumper = this.controller.pressed(config.binding(ControllerAction.INVENTORY_HOTBAR_NEXT));
		if (leftBumper && !this.previousLeftBumper) {
			this.hotbarCursor = Math.floorMod(this.hotbarCursor - 1, Inventory.getSelectionSize());
			this.activeRing = ActiveRing.HOTBAR;
		}
		if (rightBumper && !this.previousRightBumper) {
			this.hotbarCursor = (this.hotbarCursor + 1) % Inventory.getSelectionSize();
			this.activeRing = ActiveRing.HOTBAR;
		}

		boolean leftTrigger = this.controller.pressed(config.binding(ControllerAction.INVENTORY_PAGE_PREVIOUS));
		boolean rightTrigger = this.controller.pressed(config.binding(ControllerAction.INVENTORY_PAGE_NEXT));
		if (leftTrigger && !this.previousLeftTrigger) {
			if (this.activeRing == ActiveRing.HOTBAR) this.hotbarPage = 0;
			else this.changeActivePage(-1);
		}
		if (rightTrigger && !this.previousRightTrigger) {
			if (this.activeRing == ActiveRing.HOTBAR) this.hotbarPage = 1;
			else this.changeActivePage(1);
		}

		boolean primaryTransfer = this.controller.pressed(config.binding(ControllerAction.INVENTORY_TRANSFER_HOTBAR));
		boolean secondaryTransfer = this.controller.pressed(config.binding(ControllerAction.INVENTORY_TRANSFER_SECONDARY));
		boolean takeResult = this.controller.pressed(config.binding(ControllerAction.INVENTORY_TAKE_RESULT));
		if (this.activeRing != ActiveRing.HOTBAR || this.hotbarPage == 0) this.handleDpadTransfer(primaryTransfer);
		if (secondaryTransfer && !this.previousA && this.activeRing == ActiveRing.HOTBAR && this.hotbarPage == 1) {
			config.setStartStopMining(!config.startStopMining());
		} else if (secondaryTransfer && !this.previousA && this.activeRing == ActiveRing.LEFT
				&& this.leftPage == LeftPage.RECIPES) {
			this.placeSelectedRecipe();
		} else {
			this.handleATransfer(secondaryTransfer);
		}
		this.handleCraftResult(takeResult);

		this.previousLeftBumper = leftBumper;
		this.previousRightBumper = rightBumper;
		this.previousLeftTrigger = leftTrigger;
		this.previousRightTrigger = rightTrigger;
		this.previousDpadDown = primaryTransfer;
		this.previousA = secondaryTransfer;
		this.previousY = takeResult;
		this.refreshRecipeSuggestions();
	}

	private void updateStick(float x, float y, ActiveRing ring) {
		float deadzone = ring == ActiveRing.LEFT && this.leftPage == LeftPage.CRAFTING ? 0.18F : STICK_DEADZONE;
		if (x * x + y * y < deadzone * deadzone) {
			return;
		}

		this.activeRing = ring;
		if (ring == ActiveRing.LEFT && this.leftPage == LeftPage.CRAFTING) {
			this.equipmentCursor = (y >= 0.0F ? 2 : 0) + (x >= 0.0F ? 1 : 0);
			return;
		}
		if (ring == ActiveRing.LEFT && this.leftPage == LeftPage.RECIPES) {
			int count = this.recipeSuggestions.size();
			if (count > 0) {
				double angle = normalizeAngle(Math.atan2(x, -y));
				this.recipeCursor = Math.floorMod((int) Math.round(angle / (Math.PI * 2.0 / count)), count);
			}
			return;
		}
		if (ring == ActiveRing.LEFT) {
			this.equipmentCursor = nearestEquipmentSlot(x, y);
			return;
		}
		int count = ring == ActiveRing.LEFT ? this.leftSlotCount() : this.slotsOnCurrentPage();
		int current = ring == ActiveRing.LEFT ? this.equipmentCursor : this.inventoryCursor;
		double sector = Math.PI * 2.0 / count;
		double angle = normalizeAngle(Math.atan2(x, -y));
		if (Math.abs(shortestAngle(angle - current * sector)) > sector / 2.0 + ANGLE_HYSTERESIS) {
			int next = Math.floorMod((int) Math.round(angle / sector), count);
			if (ring == ActiveRing.LEFT) {
				this.equipmentCursor = next;
			} else {
				this.inventoryCursor = next;
			}
		}
	}

	private static int nearestEquipmentSlot(float x, float y) {
		float[][] positions = {
				{-0.65F, -1.0F}, {-0.65F, -0.33F}, {-0.65F, 0.33F}, {-0.65F, 1.0F}, {0.65F, 0.0F}
		};
		int nearest = 0;
		float nearestDistance = Float.MAX_VALUE;
		for (int i = 0; i < positions.length; i++) {
			float dx = x - positions[i][0];
			float dy = y - positions[i][1];
			float distance = dx * dx + dy * dy;
			if (distance < nearestDistance) {
				nearestDistance = distance;
				nearest = i;
			}
		}
		return nearest;
	}

	private void changeActivePage(int direction) {
		if (this.activeRing == ActiveRing.LEFT) {
			LeftPage[] pages = LeftPage.values();
			this.leftPage = pages[Math.floorMod(this.leftPage.ordinal() + direction, pages.length)];
			this.equipmentCursor = Math.min(this.equipmentCursor, this.leftSlotCount() - 1);
			return;
		}
		if (this.activeRing == ActiveRing.HOTBAR) return;
		this.currentPage = Math.floorMod(this.currentPage + direction, PAGE_COUNT);
		this.inventoryCursor = Math.min(this.inventoryCursor, this.slotsOnCurrentPage() - 1);
	}

	private void handleDpadTransfer(boolean pressed) {
		long now = System.nanoTime();
		if (pressed && !this.previousDpadDown) {
			this.dpadFromInventory = switch (this.activeRing) {
				case HOTBAR -> this.menuStack(this.hotbarMenuSlot()).isEmpty();
				case INVENTORY -> !this.menuStack(this.inventoryMenuSlot()).isEmpty();
				case LEFT -> true;
			};
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
			if (this.leftPage == LeftPage.RECIPES) return;
			this.aFromEquipment = switch (this.activeRing) {
				case LEFT -> !this.menuStack(this.leftMenuSlot()).isEmpty();
				case INVENTORY -> this.menuStack(this.inventoryMenuSlot()).isEmpty();
				case HOTBAR -> false;
			};
			this.moveOne(this.leftMenuSlot(), this.inventoryMenuSlot(), this.aFromEquipment);
			this.nextARepeat = now + INITIAL_REPEAT_DELAY;
		} else if (pressed && now >= this.nextARepeat) {
			if (this.leftPage != LeftPage.RECIPES) {
				this.moveOne(this.leftMenuSlot(), this.inventoryMenuSlot(), this.aFromEquipment);
			}
			this.nextARepeat = now + REPEAT_INTERVAL;
		}
	}

	private void handleCraftResult(boolean pressed) {
		if (this.leftPage != LeftPage.CRAFTING) return;
		long now = System.nanoTime();
		if (pressed && !this.previousY) {
			this.takeCraftResult(InventoryMenu.RESULT_SLOT, this.inventoryMenuSlot());
			this.nextYRepeat = now + INITIAL_REPEAT_DELAY;
		} else if (pressed && now >= this.nextYRepeat) {
			this.takeCraftResult(InventoryMenu.RESULT_SLOT, this.inventoryMenuSlot());
			this.nextYRepeat = now + REPEAT_INTERVAL;
		}
	}

	private void takeCraftResult(int resultSlot, int targetSlot) {
		if (this.minecraft == null || this.minecraft.player == null || this.minecraft.gameMode == null) return;
		ItemStack result = this.minecraft.player.inventoryMenu.getSlot(resultSlot).getItem();
		Slot target = this.minecraft.player.inventoryMenu.getSlot(targetSlot);
		ItemStack existing = target.getItem();
		if (result.isEmpty() || !target.mayPlace(result)
				|| (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(result, existing))
				|| existing.getCount() + result.getCount() > target.getMaxStackSize(result)) return;
		int containerId = this.minecraft.player.inventoryMenu.containerId;
		this.minecraft.gameMode.handleContainerInput(containerId, resultSlot, 0, ContainerInput.PICKUP, this.minecraft.player);
		this.minecraft.gameMode.handleContainerInput(containerId, targetSlot, 0, ContainerInput.PICKUP, this.minecraft.player);
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

		this.extractLeftPanel(graphics, leftCenterX, centerY);
		this.extractInventoryRing(graphics, rightCenterX, centerY, radius);
		this.extractHints(graphics);
		this.extractHotbar(graphics);
	}

	private void extractLeftPanel(GuiGraphicsExtractor graphics, int centerX, int centerY) {
		int count = this.leftSlotCount();
		if (this.leftPage == LeftPage.CRAFTING) {
			for (int i = 0; i < count; i++) {
				int left = centerX - SLOT_SIZE + (i % 2) * SLOT_SIZE;
				int top = centerY - SLOT_SIZE + (i / 2) * SLOT_SIZE;
				this.extractSlot(graphics, this.menuStack(CRAFTING_MENU_SLOTS[i]), left, top,
						i == this.equipmentCursor, this.activeRing == ActiveRing.LEFT, 300 + i);
			}
			this.extractSlot(graphics, this.menuStack(InventoryMenu.RESULT_SLOT), centerX - SLOT_SIZE / 2,
					centerY + 27, false, false, 310);
		} else if (this.leftPage == LeftPage.EQUIPMENT) {
			// Armor follows the familiar head-to-feet layout; offhand sits next to the body.
			for (int i = 0; i < 4; i++) {
				this.extractSlot(graphics, this.menuStack(EQUIPMENT_MENU_SLOTS[i]), centerX - 22,
						centerY - 40 + i * SLOT_SIZE, i == this.equipmentCursor,
						this.activeRing == ActiveRing.LEFT, 320 + i);
			}
			this.extractSlot(graphics, this.menuStack(EQUIPMENT_MENU_SLOTS[4]), centerX + 8, centerY - 10,
					this.equipmentCursor == 4, this.activeRing == ActiveRing.LEFT, 324);
		} else {
			this.extractRecipeBrowser(graphics, centerX, centerY);
		}
		graphics.centeredText(this.font, (this.leftPage.ordinal() + 1) + "/3",
				centerX, centerY + 51, this.activeRing == ActiveRing.LEFT ? 0xFFF4D35E : 0xFF9AA4B2);
	}

	private void extractRecipeBrowser(GuiGraphicsExtractor graphics, int centerX, int centerY) {
		int count = this.recipeSuggestions.size();
		for (int i = 0; i < count; i++) {
			double angle = i * Math.PI * 2.0 / count;
			this.extractSlot(graphics, this.recipeSuggestions.get(i).output(),
					(int) Math.round(centerX + Math.sin(angle) * 68) - SLOT_SIZE / 2,
					(int) Math.round(centerY - Math.cos(angle) * 68) - SLOT_SIZE / 2,
					i == this.recipeCursor, this.activeRing == ActiveRing.LEFT, 600 + i);
		}
		if (count == 0) {
			graphics.centeredText(this.font, "No recipes", centerX, centerY - 4, 0xFF9AA4B2);
			return;
		}
		List<ItemStack> grid = this.recipeSuggestions.get(this.recipeCursor).grid();
		for (int i = 0; i < 9; i++) {
			this.extractSlot(graphics, grid.get(i), centerX - 30 + (i % 3) * SLOT_SIZE,
					centerY - 30 + (i / 3) * SLOT_SIZE, false, false, 650 + i);
		}
	}

	private void extractInventoryRing(GuiGraphicsExtractor graphics, int centerX, int centerY, int radius) {
		int count = this.slotsOnCurrentPage();
		for (int i = 0; i < count; i++) {
			int menuSlot = InventoryMenu.INV_SLOT_START + this.currentPage * PAGE_SIZE + i;
			this.extractRingSlot(graphics, this.menuStack(menuSlot), centerX, centerY, radius,
					i, count, i == this.inventoryCursor, this.activeRing == ActiveRing.INVENTORY, menuSlot);
		}
		this.extractDetails(graphics, this.menuStack(this.inventoryMenuSlot()), centerX, centerY,
				(this.currentPage + 1) + "/" + PAGE_COUNT,
				this.activeRing == ActiveRing.INVENTORY);
	}

	private void extractRingSlot(
			GuiGraphicsExtractor graphics, ItemStack stack, int centerX, int centerY, int radius,
			int position, int count, boolean selected, boolean focused, int seed) {
		double angle = position * Math.PI * 2.0 / count;
		int left = (int) Math.round(centerX + Math.sin(angle) * radius) - SLOT_SIZE / 2;
		int top = (int) Math.round(centerY - Math.cos(angle) * radius) - SLOT_SIZE / 2;
		this.extractSlot(graphics, stack, left, top, selected, focused, seed);
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
		String activePage = switch (this.activeRing) {
			case INVENTORY -> (this.currentPage + 1) + "/" + PAGE_COUNT;
			case LEFT -> (this.leftPage.ordinal() + 1) + "/3";
			case HOTBAR -> (this.hotbarPage + 1) + "/2";
		};
		DigiMinerConfig config = DigiMinerConfig.get();
		graphics.centeredText(this.font,
				"[RS] Inventory  [LS] Inventory  [" + config.binding(ControllerAction.INVENTORY_HOTBAR_PREVIOUS).label()
						+ "/" + config.binding(ControllerAction.INVENTORY_HOTBAR_NEXT).label() + "] Hotbar  ["
						+ config.binding(ControllerAction.INVENTORY_PAGE_PREVIOUS).label() + "/"
						+ config.binding(ControllerAction.INVENTORY_PAGE_NEXT).label() + "] " + activePage,
				this.width / 2, hotbarTop - 23, 0xFFDEE5EF);
		graphics.centeredText(this.font,
				"[" + config.binding(ControllerAction.INVENTORY_TRANSFER_HOTBAR).label()
						+ "] Inventory <-> Hotbar  [" + config.binding(ControllerAction.INVENTORY_TRANSFER_SECONDARY).label()
						+ "] Move  [" + config.binding(ControllerAction.INVENTORY_TAKE_RESULT).label() + "] Craft  ["
						+ config.binding(ControllerAction.INVENTORY_CLOSE).label() + "] Close",
				this.width / 2, hotbarTop - 12, 0xFFB8C4D6);
	}

	private void extractHotbar(GuiGraphicsExtractor graphics) {
		if (this.activeRing == ActiveRing.HOTBAR && this.hotbarPage == 1) {
			int centerX = this.width / 2;
			int top = this.height - SLOT_SIZE - 7;
			String mode = DigiMinerConfig.get().startStopMining() ? "Start/Stop mining" : "Hold to mine";
			graphics.fill(centerX - 90, top, centerX + 90, top + SLOT_SIZE, 0xDD28313D);
			graphics.outline(centerX - 90, top, 180, SLOT_SIZE, 0xFFF4D35E);
			graphics.centeredText(this.font, "[A] " + mode, centerX, top + 6, 0xFFFFFFFF);
			return;
		}
		int totalWidth = Inventory.getSelectionSize() * SLOT_SIZE;
		int left = (this.width - totalWidth) / 2;
		int top = this.height - SLOT_SIZE - 7;
		for (int i = 0; i < Inventory.getSelectionSize(); i++) {
			this.extractSlot(graphics, this.menuStack(InventoryMenu.USE_ROW_SLOT_START + i),
					left + i * SLOT_SIZE, top, i == this.hotbarCursor,
					this.activeRing == ActiveRing.HOTBAR, 400 + i);
		}
	}

	private void extractSlot(
			GuiGraphicsExtractor graphics, ItemStack stack, int left, int top,
			boolean selected, boolean focused, int seed) {
		boolean active = selected && focused;
		graphics.fill(left, top, left + SLOT_SIZE, top + SLOT_SIZE,
				selected ? 0xDD28313D : 0xB010141A);
		graphics.outline(left, top, SLOT_SIZE, SLOT_SIZE,
				active ? 0xFFF4D35E : selected ? 0xFF65CFFF : 0x906E747C);
		if (selected) {
			graphics.outline(left - 1, top - 1, SLOT_SIZE + 2, SLOT_SIZE + 2,
					active ? 0xFFFFE49A : 0xFFA8E8FF);
		}
		if (!stack.isEmpty()) {
			graphics.item(this.minecraft.player, stack, left + 2, top + 2, seed);
			graphics.itemDecorations(this.font, stack, left + 2, top + 2);
		}
	}

	private ItemStack menuStack(int menuSlot) {
		return this.minecraft.player.inventoryMenu.getSlot(menuSlot).getItem();
	}

	private void refreshRecipeSuggestions() {
		if (this.minecraft == null || this.minecraft.player == null || this.minecraft.level == null) return;
		ItemStack selected = this.menuStack(this.inventoryMenuSlot());
		if (selected.getItem() == this.lastRecipeFilter) return;
		this.lastRecipeFilter = selected.getItem();
		if (selected.isEmpty()) {
			this.recipeSuggestions = List.of();
			this.recipeCursor = 0;
			return;
		}

		var context = SlotDisplayContext.fromLevel(this.minecraft.level);
		List<RecipeDisplayEntry> all = new ArrayList<>();
		Set<Object> seen = new HashSet<>();
		for (var collection : this.minecraft.player.getRecipeBook().getCollections()) {
			for (RecipeDisplayEntry entry : collection.getRecipes()) {
				if (seen.add(entry.id()) && (entry.display() instanceof ShapedCraftingRecipeDisplay
						|| entry.display() instanceof ShapelessCraftingRecipeDisplay)) all.add(entry);
			}
		}

		List<RecipeSuggestion> matches = new ArrayList<>();
		for (RecipeDisplayEntry entry : all) {
			if (!fitsInTwoByTwo(entry)) continue;
			if (entry.craftingRequirements().isEmpty()
					|| entry.craftingRequirements().get().stream().noneMatch(ingredient -> ingredient.test(selected))) continue;
			ItemStack output = entry.display().result().resolveForFirstStack(context);
			if (output.isEmpty()) continue;
			int dependencyScore = 0;
			for (RecipeDisplayEntry other : all) {
				if (other.craftingRequirements().isPresent()
						&& other.craftingRequirements().get().stream().anyMatch(ingredient -> ingredient.test(output))) {
					dependencyScore++;
				}
			}
			int utilityScore = dependencyScore * 100
					+ (output.isDamageableItem() ? 40 : 0)
					+ (output.getItem() instanceof BlockItem ? 10 : 0);
			matches.add(new RecipeSuggestion(entry, output, recipeGrid(entry, context), utilityScore));
		}
		matches.sort(Comparator.comparingInt(RecipeSuggestion::score).reversed()
				.thenComparing(suggestion -> suggestion.output().getHoverName().getString()));
		this.recipeSuggestions = List.copyOf(matches.subList(0, Math.min(PAGE_SIZE, matches.size())));
		this.recipeCursor = Math.min(this.recipeCursor, Math.max(0, this.recipeSuggestions.size() - 1));
	}

	private void placeSelectedRecipe() {
		if (this.minecraft == null || this.minecraft.player == null || this.minecraft.gameMode == null
				|| this.recipeSuggestions.isEmpty()) return;
		RecipeSuggestion suggestion = this.recipeSuggestions.get(this.recipeCursor);
		StackedItemContents contents = new StackedItemContents();
		this.minecraft.player.getInventory().fillStackedContents(contents);
		this.minecraft.player.inventoryMenu.fillCraftSlotsStackedContents(contents);
		if (!suggestion.entry().canCraft(contents)) return;
		this.minecraft.gameMode.handlePlaceRecipe(this.minecraft.player.inventoryMenu.containerId,
				suggestion.entry().id(), false);
		this.leftPage = LeftPage.CRAFTING;
		this.activeRing = ActiveRing.LEFT;
	}

	private static boolean fitsInTwoByTwo(RecipeDisplayEntry entry) {
		if (entry.display() instanceof ShapedCraftingRecipeDisplay shaped) {
			return shaped.width() <= 2 && shaped.height() <= 2;
		}
		return entry.display() instanceof ShapelessCraftingRecipeDisplay shapeless
				&& shapeless.ingredients().size() <= 4;
	}

	private static List<ItemStack> recipeGrid(RecipeDisplayEntry entry, net.minecraft.util.context.ContextMap context) {
		List<ItemStack> grid = new ArrayList<>();
		for (int i = 0; i < 9; i++) grid.add(ItemStack.EMPTY);
		if (entry.display() instanceof ShapedCraftingRecipeDisplay shaped) {
			int offsetX = (3 - shaped.width()) / 2;
			int offsetY = (3 - shaped.height()) / 2;
			for (int i = 0; i < shaped.ingredients().size(); i++) {
				int x = i % shaped.width() + offsetX;
				int y = i / shaped.width() + offsetY;
				grid.set(y * 3 + x, shaped.ingredients().get(i).resolveForFirstStack(context));
			}
		} else if (entry.display() instanceof ShapelessCraftingRecipeDisplay shapeless) {
			for (int i = 0; i < Math.min(9, shapeless.ingredients().size()); i++) {
				grid.set(i, shapeless.ingredients().get(i).resolveForFirstStack(context));
			}
		}
		return List.copyOf(grid);
	}

	private int slotsOnCurrentPage() {
		return Math.min(PAGE_SIZE, MAIN_SLOT_COUNT - this.currentPage * PAGE_SIZE);
	}

	private int inventoryMenuSlot() {
		return InventoryMenu.INV_SLOT_START + this.currentPage * PAGE_SIZE + this.inventoryCursor;
	}

	private int leftSlotCount() {
		return switch (this.leftPage) {
			case CRAFTING -> CRAFTING_MENU_SLOTS.length;
			case EQUIPMENT -> EQUIPMENT_MENU_SLOTS.length;
			case RECIPES -> Math.max(1, this.recipeSuggestions.size());
		};
	}

	private int leftMenuSlot() {
		return switch (this.leftPage) {
			case CRAFTING -> CRAFTING_MENU_SLOTS[this.equipmentCursor];
			case EQUIPMENT -> EQUIPMENT_MENU_SLOTS[this.equipmentCursor];
			case RECIPES -> throw new IllegalStateException("Recipe suggestions are not container slots");
		};
	}

	private record RecipeSuggestion(RecipeDisplayEntry entry, ItemStack output, List<ItemStack> grid, int score) {}

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
		this.previousY = false;
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
