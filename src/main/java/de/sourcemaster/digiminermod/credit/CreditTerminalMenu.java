package de.sourcemaster.digiminermod.credit;

import de.sourcemaster.digiminermod.DigiMinerMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class CreditTerminalMenu extends AbstractContainerMenu {
	public static final int TERMINAL_SLOTS = 14;
	public static final int PLAYER_MAIN_START = 14;
	public static final int HOTBAR_START = 41;
	private final BlockPos terminalPos;

	public CreditTerminalMenu(int id, Inventory playerInventory, BlockPos pos) {
		this(id, playerInventory, new SimpleContainer(TERMINAL_SLOTS), pos);
	}

	public CreditTerminalMenu(int id, Inventory playerInventory, Container terminal, BlockPos pos) {
		super(DigiMinerMod.CREDIT_TERMINAL_MENU, id);
		this.terminalPos = pos;
		checkContainerSize(terminal, TERMINAL_SLOTS);
		for (int i = 0; i < TERMINAL_SLOTS; i++) this.addSlot(new Slot(terminal, i, 0, 0));
		for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
			this.addSlot(new Slot(playerInventory, column + row * 9 + 9, 0, 0));
		for (int i = 0; i < 9; i++) this.addSlot(new Slot(playerInventory, i, 0, 0));
	}

	@Override public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = this.slots.get(index);
		if (!slot.hasItem()) return ItemStack.EMPTY;
		ItemStack stack = slot.getItem(), result = stack.copy();
		if (index < TERMINAL_SLOTS) {
			if (!this.moveItemStackTo(stack, PLAYER_MAIN_START, this.slots.size(), true)) return ItemStack.EMPTY;
		} else if (!this.moveItemStackTo(stack, 0, TERMINAL_SLOTS, false)) return ItemStack.EMPTY;
		if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
		return result;
	}

	@Override public boolean stillValid(Player player) {
		return player.level().getBlockState(this.terminalPos).is(DigiMinerMod.CREDIT_TERMINAL_BLOCK)
				&& this.terminalPos.closerToCenterThan(player.position(), 8.0);
	}
}
