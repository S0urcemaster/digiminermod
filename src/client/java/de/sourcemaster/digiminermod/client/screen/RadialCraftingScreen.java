package de.sourcemaster.digiminermod.client.screen;

import de.sourcemaster.digiminermod.client.config.DigiMinerConfig;
import de.sourcemaster.digiminermod.client.input.ControllerAction;
import de.sourcemaster.digiminermod.client.input.ControllerSupport;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RadialCraftingScreen extends Screen {
	private static final int SLOT_SIZE = 20;
	private static final int PAGE_SIZE = 14;
	private static final long INITIAL_REPEAT_DELAY = 350_000_000L;
	private static final long REPEAT_INTERVAL = 90_000_000L;
	private final CraftingMenu menu;
	private int craftCursor = 4;
	private int inventoryCursor;
	private int hotbarCursor;
	private int hotbarPage;
	private int page;
	private enum Focus { CRAFTING, INVENTORY, HOTBAR }
	private enum LeftPage { CRAFTING, RECIPES }
	private Focus focus = Focus.CRAFTING;
	private LeftPage leftPage = LeftPage.CRAFTING;
	private int recipeCursor;
	private List<RecipeSuggestion> recipeSuggestions = List.of();
	private Item lastRecipeFilter = Items.AIR;
	private boolean previousA;
	private boolean previousY;
	private boolean previousDpad;
	private boolean previousLB;
	private boolean previousRB;
	private boolean previousLT;
	private boolean previousRT;
	private long nextYRepeat;
	private ControllerSupport.Snapshot controller = ControllerSupport.Snapshot.NONE;

	public RadialCraftingScreen(CraftingMenu menu) {
		super(Component.literal("Crafting"));
		this.menu = menu;
	}

	@Override
	protected void init() {
		this.controller = ControllerSupport.poll();
		if (this.minecraft != null && this.minecraft.player != null) {
			this.hotbarCursor = this.minecraft.player.getInventory().getSelectedSlot();
		}
	}

	@Override
	public void tick() {
		this.controller = ControllerSupport.poll();
		if (!this.controller.connected()) return;
		DigiMinerConfig config = DigiMinerConfig.get();
		this.updateCraftCursor(this.controller.leftX(), this.controller.leftY());
		this.updateInventoryCursor(this.controller.rightX(), this.controller.rightY());

		boolean lb = this.controller.pressed(config.binding(ControllerAction.INVENTORY_HOTBAR_PREVIOUS));
		boolean rb = this.controller.pressed(config.binding(ControllerAction.INVENTORY_HOTBAR_NEXT));
		if (lb && !this.previousLB) {
			this.hotbarCursor = Math.floorMod(this.hotbarCursor - 1, Inventory.getSelectionSize());
			this.focus = Focus.HOTBAR;
		}
		if (rb && !this.previousRB) {
			this.hotbarCursor = (this.hotbarCursor + 1) % Inventory.getSelectionSize();
			this.focus = Focus.HOTBAR;
		}
		boolean lt = this.controller.pressed(config.binding(ControllerAction.INVENTORY_PAGE_PREVIOUS));
		boolean rt = this.controller.pressed(config.binding(ControllerAction.INVENTORY_PAGE_NEXT));
		if (this.focus == Focus.HOTBAR && lt && !this.previousLT) this.hotbarPage = 0;
		if (this.focus == Focus.HOTBAR && rt && !this.previousRT) this.hotbarPage = 1;
		if (this.focus == Focus.CRAFTING && lt && !this.previousLT) this.leftPage = LeftPage.CRAFTING;
		if (this.focus == Focus.CRAFTING && rt && !this.previousRT) this.leftPage = LeftPage.RECIPES;
		if (this.focus == Focus.INVENTORY && lt && !this.previousLT) this.page = Math.floorMod(this.page - 1, 2);
		if (this.focus == Focus.INVENTORY && rt && !this.previousRT) this.page = Math.floorMod(this.page + 1, 2);

		boolean a = this.controller.pressed(config.binding(ControllerAction.INVENTORY_TRANSFER_SECONDARY));
		boolean y = this.controller.pressed(config.binding(ControllerAction.INVENTORY_TAKE_RESULT));
		boolean dpad = this.controller.pressed(config.binding(ControllerAction.INVENTORY_TRANSFER_HOTBAR));
		if (a && !this.previousA && this.focus == Focus.HOTBAR && this.hotbarPage == 1) {
			config.setStartStopMining(!config.startStopMining());
		} else if (a && !this.previousA && this.focus == Focus.CRAFTING && this.leftPage == LeftPage.RECIPES) {
			this.placeSelectedRecipe();
		} else if (a && !this.previousA && this.leftPage != LeftPage.RECIPES) {
			int crafting = 1 + this.craftCursor;
			int inventory = this.inventorySlot();
			boolean fromCrafting = this.focus == Focus.CRAFTING
					? !this.menu.getSlot(crafting).getItem().isEmpty()
					: this.focus == Focus.INVENTORY && this.menu.getSlot(inventory).getItem().isEmpty();
			this.moveOne(crafting, inventory, fromCrafting);
		}
		this.handleCraftResult(y);
		if (dpad && !this.previousDpad && (this.focus != Focus.HOTBAR || this.hotbarPage == 0)) {
			int inventory = this.inventorySlot();
			int hotbar = 37 + this.hotbarCursor;
			boolean fromInventory = this.focus == Focus.HOTBAR
					? this.menu.getSlot(hotbar).getItem().isEmpty()
					: this.focus == Focus.INVENTORY
							? !this.menu.getSlot(inventory).getItem().isEmpty()
							: true;
			this.moveOne(inventory, hotbar, fromInventory);
		}
		this.previousA = a;
		this.previousY = y;
		this.previousDpad = dpad;
		this.previousLB = lb;
		this.previousRB = rb;
		this.previousLT = lt;
		this.previousRT = rt;
		this.refreshRecipeSuggestions();
	}

	private void updateCraftCursor(float x, float y) {
		if (x * x + y * y < 0.18F * 0.18F) return;
		this.focus = Focus.CRAFTING;
		if (this.leftPage == LeftPage.RECIPES) {
			int count = this.recipeSuggestions.size();
			if (count > 0) {
				double angle = Math.atan2(x, -y);
				if (angle < 0) angle += Math.PI * 2.0;
				this.recipeCursor = Math.floorMod((int) Math.round(angle / (Math.PI * 2.0 / count)), count);
			}
			return;
		}
		int column = x < -0.42F ? 0 : x > 0.42F ? 2 : 1;
		int row = y < -0.42F ? 0 : y > 0.42F ? 2 : 1;
		this.craftCursor = row * 3 + column;
	}

	private void updateInventoryCursor(float x, float y) {
		if (x * x + y * y < 0.25F) return;
		this.focus = Focus.INVENTORY;
		int count = this.page == 0 ? 14 : 13;
		double angle = Math.atan2(x, -y);
		if (angle < 0) angle += Math.PI * 2.0;
		this.inventoryCursor = Math.floorMod((int) Math.round(angle / (Math.PI * 2.0 / count)), count);
	}

	private void moveOne(int first, int second, boolean fromFirst) {
		if (this.minecraft == null || this.minecraft.player == null || this.minecraft.gameMode == null) return;
		int source = fromFirst ? first : second;
		int target = fromFirst ? second : first;
		if (this.menu.getSlot(source).getItem().isEmpty()) return;
		this.minecraft.gameMode.handleContainerInput(this.menu.containerId, source, 0, ContainerInput.PICKUP, this.minecraft.player);
		this.minecraft.gameMode.handleContainerInput(this.menu.containerId, target, 1, ContainerInput.PICKUP, this.minecraft.player);
		this.minecraft.gameMode.handleContainerInput(this.menu.containerId, source, 0, ContainerInput.PICKUP, this.minecraft.player);
	}

	private void handleCraftResult(boolean pressed) {
		long now = System.nanoTime();
		if (pressed && !this.previousY) {
			this.takeCraftResult();
			this.nextYRepeat = now + INITIAL_REPEAT_DELAY;
		} else if (pressed && now >= this.nextYRepeat) {
			this.takeCraftResult();
			this.nextYRepeat = now + REPEAT_INTERVAL;
		}
	}

	private void takeCraftResult() {
		if (this.minecraft == null || this.minecraft.player == null || this.minecraft.gameMode == null) return;
		ItemStack result = this.menu.getSlot(0).getItem();
		var target = this.menu.getSlot(this.inventorySlot());
		ItemStack existing = target.getItem();
		if (result.isEmpty() || !target.mayPlace(result)
				|| (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(result, existing))
				|| existing.getCount() + result.getCount() > target.getMaxStackSize(result)) return;
		this.minecraft.gameMode.handleContainerInput(this.menu.containerId, 0, 0, ContainerInput.PICKUP, this.minecraft.player);
		this.minecraft.gameMode.handleContainerInput(this.menu.containerId, this.inventorySlot(), 0,
				ContainerInput.PICKUP, this.minecraft.player);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		this.extractTransparentBackground(graphics);
		if (this.minecraft == null || this.minecraft.player == null) return;
		int cy = this.height / 2 - 8;
		int leftCenter = this.width / 2 - 105;
		int rightCenter = this.width / 2 + 105;
		if (this.leftPage == LeftPage.CRAFTING) {
			for (int i = 0; i < 9; i++) {
				this.slot(graphics, this.menu.getSlot(1 + i).getItem(), leftCenter - 30 + i % 3 * SLOT_SIZE,
						cy - 30 + i / 3 * SLOT_SIZE, i == this.craftCursor, this.focus == Focus.CRAFTING);
			}
			this.slot(graphics, this.menu.getSlot(0).getItem(), leftCenter - 10, cy + 40, false, false);
		} else {
			this.extractRecipeBrowser(graphics, leftCenter, cy);
		}
		graphics.centeredText(this.font, this.leftPage == LeftPage.CRAFTING ? "1/2" : "2/2",
				leftCenter, cy + 54, this.focus == Focus.CRAFTING ? 0xFFF4D35E : 0xFF9AA4B2);
		int count = this.page == 0 ? 14 : 13;
		for (int i = 0; i < count; i++) {
			double angle = i * Math.PI * 2.0 / count;
			this.slot(graphics, this.menu.getSlot(10 + this.page * PAGE_SIZE + i).getItem(),
					(int) Math.round(rightCenter + Math.sin(angle) * 65) - 10,
					(int) Math.round(cy - Math.cos(angle) * 65) - 10,
					i == this.inventoryCursor, this.focus == Focus.INVENTORY);
		}
		graphics.centeredText(this.font, (this.page + 1) + "/2", rightCenter, cy + 20, 0xFFFFFFFF);
		if (this.focus == Focus.HOTBAR && this.hotbarPage == 1) {
			String mode = DigiMinerConfig.get().startStopMining() ? "Start/Stop mining" : "Hold to mine";
			graphics.fill(this.width / 2 - 90, this.height - 27, this.width / 2 + 90, this.height - 7, 0xDD28313D);
			graphics.outline(this.width / 2 - 90, this.height - 27, 180, 20, 0xFFF4D35E);
			graphics.centeredText(this.font, "[A] " + mode, this.width / 2, this.height - 21, 0xFFFFFFFF);
		} else {
			int hotbarLeft = (this.width - 9 * SLOT_SIZE) / 2;
			for (int i = 0; i < 9; i++) this.slot(graphics, this.menu.getSlot(37 + i).getItem(),
					hotbarLeft + i * SLOT_SIZE, this.height - 27,
					i == this.hotbarCursor, this.focus == Focus.HOTBAR);
		}
		if (DigiMinerConfig.get().showIngameHints()) {
			graphics.centeredText(this.font, "[LS] Crafting  [RS] Inventory  [A] Move  [Y] Craft",
					this.width / 2, this.height - 39, 0xFFDEE5EF);
		}
	}

	private void extractRecipeBrowser(GuiGraphicsExtractor graphics, int centerX, int centerY) {
		int count = this.recipeSuggestions.size();
		for (int i = 0; i < count; i++) {
			double angle = i * Math.PI * 2.0 / count;
			this.slot(graphics, this.recipeSuggestions.get(i).output(),
					(int) Math.round(centerX + Math.sin(angle) * 68) - SLOT_SIZE / 2,
					(int) Math.round(centerY - Math.cos(angle) * 68) - SLOT_SIZE / 2,
					i == this.recipeCursor, this.focus == Focus.CRAFTING);
		}
		if (count == 0) {
			graphics.centeredText(this.font, "No recipes", centerX, centerY - 4, 0xFF9AA4B2);
			return;
		}
		List<ItemStack> grid = this.recipeSuggestions.get(this.recipeCursor).grid();
		for (int i = 0; i < 9; i++) {
			this.slot(graphics, grid.get(i), centerX - 30 + i % 3 * SLOT_SIZE,
					centerY - 30 + i / 3 * SLOT_SIZE, false, false);
		}
	}

	private void slot(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y,
			boolean selected, boolean focused) {
		boolean active = selected && focused;
		graphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, selected ? 0xDD28313D : 0xB010141A);
		graphics.outline(x, y, SLOT_SIZE, SLOT_SIZE,
				active ? 0xFFF4D35E : selected ? 0xFF65CFFF : 0x906E747C);
		if (selected) {
			graphics.outline(x - 1, y - 1, SLOT_SIZE + 2, SLOT_SIZE + 2,
					active ? 0xFFFFE49A : 0xFFA8E8FF);
		}
		if (!stack.isEmpty()) {
			graphics.item(this.minecraft.player, stack, x + 2, y + 2, x + y);
			graphics.itemDecorations(this.font, stack, x + 2, y + 2);
		}
	}

	private void refreshRecipeSuggestions() {
		if (this.minecraft == null || this.minecraft.player == null || this.minecraft.level == null) return;
		ItemStack selected = this.menu.getSlot(this.inventorySlot()).getItem();
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
			if (fitsInTwoByTwo(entry)) continue;
			if (entry.craftingRequirements().isEmpty()
					|| entry.craftingRequirements().get().stream().noneMatch(ingredient -> ingredient.test(selected))) continue;
			ItemStack output = entry.display().result().resolveForFirstStack(context);
			if (output.isEmpty()) continue;
			int dependencies = 0;
			for (RecipeDisplayEntry other : all) {
				if (other.craftingRequirements().isPresent()
						&& other.craftingRequirements().get().stream().anyMatch(ingredient -> ingredient.test(output))) dependencies++;
			}
			int score = dependencies * 100 + (output.isDamageableItem() ? 40 : 0)
					+ (output.getItem() instanceof BlockItem ? 10 : 0);
			matches.add(new RecipeSuggestion(entry, output, recipeGrid(entry, context), score));
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
		this.menu.fillCraftSlotsStackedContents(contents);
		if (!suggestion.entry().canCraft(contents)) return;
		this.minecraft.gameMode.handlePlaceRecipe(this.menu.containerId, suggestion.entry().id(), false);
		this.leftPage = LeftPage.CRAFTING;
		this.focus = Focus.CRAFTING;
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
				grid.set((i / shaped.width() + offsetY) * 3 + i % shaped.width() + offsetX,
						shaped.ingredients().get(i).resolveForFirstStack(context));
			}
		} else if (entry.display() instanceof ShapelessCraftingRecipeDisplay shapeless) {
			for (int i = 0; i < Math.min(9, shapeless.ingredients().size()); i++) {
				grid.set(i, shapeless.ingredients().get(i).resolveForFirstStack(context));
			}
		}
		return List.copyOf(grid);
	}

	private int inventorySlot() { return 10 + this.page * PAGE_SIZE + this.inventoryCursor; }
	private record RecipeSuggestion(RecipeDisplayEntry entry, ItemStack output, List<ItemStack> grid, int score) {}

	@Override public void onClose() { if (this.minecraft != null && this.minecraft.player != null) this.minecraft.player.closeContainer(); }
	@Override public boolean isPauseScreen() { return false; }
	@Override public boolean isInGameUi() { return true; }
}
