package de.sourcemaster.digiminermod.drone;

import de.sourcemaster.digiminermod.DigiMinerMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

public final class DroneMenu extends AbstractContainerMenu {
	public static final int DRONE_SLOTS = 27;
	public static final int PLAYER_MAIN_START = 27;
	public static final int HOTBAR_START = 54;
	public static final int TOOL_SLOT = 63;
	public static final int PROGRAM_DRIVE_SLOT = 64;
	private final Container droneInventory;
	private final Container toolInventory;
	private final BlockPos dronePos;
	private final DroneBlockEntity droneBlockEntity;
	private final ContainerData data;

	public DroneMenu(int containerId, Inventory playerInventory, BlockPos pos) {
		this(containerId, playerInventory, new SimpleContainer(DRONE_SLOTS), new SimpleContainer(2), pos, null, new SimpleContainerData(1));
	}

	public DroneMenu(int containerId, Inventory playerInventory, DroneBlockEntity drone) {
		this(containerId, playerInventory, drone.getInventory(), drone.getEquipmentInventory(), drone.getBlockPos(), drone, new ContainerData() {
			@Override public int get(int index) { return drone.getMode().ordinal(); }
			@Override public void set(int index, int value) { drone.setMode(DroneMode.byId(value)); }
			@Override public int getCount() { return 1; }
		});
		drone.setMenuOpen(true);
	}

	private DroneMenu(int containerId, Inventory playerInventory, Container droneInventory, Container toolInventory, BlockPos pos,
			DroneBlockEntity droneBlockEntity, ContainerData data) {
		super(DigiMinerMod.DRONE_MENU, containerId);
		this.droneInventory = droneInventory;
		this.toolInventory = toolInventory;
		this.droneBlockEntity = droneBlockEntity;
		this.data = data;
		this.dronePos = pos;
		checkContainerSize(droneInventory, DRONE_SLOTS);
		for (int i = 0; i < DRONE_SLOTS; i++) this.addSlot(new Slot(droneInventory, i, 0, 0));
		for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
			this.addSlot(new Slot(playerInventory, column + row * 9 + 9, 0, 0));
		for (int i = 0; i < 9; i++) this.addSlot(new Slot(playerInventory, i, 0, 0));
		this.addSlot(new Slot(toolInventory, 0, 0, 0) {
			@Override public boolean mayPlace(ItemStack stack) {
				return stack.is(DigiMinerMod.DRONE_LIGHT) || stack.is(DigiMinerMod.DRONE_DRILL)
						|| stack.is(DigiMinerMod.DRONE_BUILDER);
			}
			@Override public int getMaxStackSize() { return 1; }
		});
		this.addSlot(new Slot(toolInventory, 1, 0, 0) {
			@Override public boolean mayPlace(ItemStack stack) { return stack.is(DigiMinerMod.BASIC_PROGRAM_DRIVE); }
			@Override public int getMaxStackSize() { return 1; }
		});
		this.addDataSlots(data);
	}

	public BlockPos dronePos() { return this.dronePos; }
	public int mode() { return this.data.get(0); }

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		ItemStack result = ItemStack.EMPTY;
		Slot slot = this.slots.get(index);
		if (!slot.hasItem()) return result;
		ItemStack stack = slot.getItem();
		result = stack.copy();
		if (index < DRONE_SLOTS || index == TOOL_SLOT || index == PROGRAM_DRIVE_SLOT) {
			if (!this.moveItemStackTo(stack, PLAYER_MAIN_START, TOOL_SLOT, true)) return ItemStack.EMPTY;
		} else {
			int equipmentSlot = this.slots.get(PROGRAM_DRIVE_SLOT).mayPlace(stack) ? PROGRAM_DRIVE_SLOT : TOOL_SLOT;
			boolean moved = this.slots.get(equipmentSlot).mayPlace(stack)
					&& this.moveItemStackTo(stack, equipmentSlot, equipmentSlot + 1, false);
			if (!moved && !this.moveItemStackTo(stack, 0, DRONE_SLOTS, false)) return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
		return result;
	}

	@Override
	public boolean stillValid(Player player) {
		return player.level().getBlockState(this.dronePos).is(DigiMinerMod.DRONE_BLOCK)
				&& this.dronePos.closerToCenterThan(player.position(), 8.0);
	}

	@Override public void removed(Player player) {
		super.removed(player);
		if (this.droneBlockEntity != null) this.droneBlockEntity.setMenuOpen(false);
	}
}
