package de.sourcemaster.digiminermod.credit;

import de.sourcemaster.digiminermod.DigiMinerMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.UUID;

public final class CreditTerminalBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
	private static final int[] SLOTS = java.util.stream.IntStream.range(0, 27).toArray();
	private NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
	private UUID ownerId;

	public CreditTerminalBlockEntity(BlockPos pos, BlockState state) {
		super(DigiMinerMod.CREDIT_TERMINAL_BLOCK_ENTITY, pos, state);
	}

	public void setOwner(UUID ownerId) { this.ownerId = ownerId; this.setChanged(); }

	public static void serverTick(net.minecraft.world.level.Level ignored, BlockPos pos, BlockState state,
			CreditTerminalBlockEntity terminal) {
		if (!(terminal.level instanceof ServerLevel level) || terminal.ownerId == null || level.getGameTime() % 20 != 0) return;
		for (int slot = 0; slot < terminal.items.size(); slot++) {
			ItemStack stack = terminal.items.get(slot);
			int value = stack.is(Items.DIAMOND) ? 100 : stack.is(Items.GOLD_INGOT) ? 25 : 0;
			if (value == 0) continue;
			stack.shrink(1);
			terminal.setChanged();
			CreditAccount.add(level.getServer(), terminal.ownerId, value);
			return;
		}
	}

	@Override protected Component getDefaultName() { return Component.literal("Credit Terminal"); }
	@Override public int getContainerSize() { return 27; }
	@Override protected NonNullList<ItemStack> getItems() { return this.items; }
	@Override protected void setItems(NonNullList<ItemStack> items) { this.items = items; }
	@Override protected AbstractContainerMenu createMenu(int id, Inventory inventory) { return ChestMenu.threeRows(id, inventory, this); }
	@Override public boolean canPlaceItem(int slot, ItemStack stack) { return stack.is(Items.DIAMOND) || stack.is(Items.GOLD_INGOT); }
	@Override public int[] getSlotsForFace(Direction side) { return SLOTS; }
	@Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) { return this.canPlaceItem(slot, stack); }
	@Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return true; }

	@Override protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (this.ownerId != null) output.store("Owner", UUIDUtil.CODEC, this.ownerId);
	}
	@Override protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.ownerId = input.read("Owner", UUIDUtil.CODEC).orElse(null);
	}
}
