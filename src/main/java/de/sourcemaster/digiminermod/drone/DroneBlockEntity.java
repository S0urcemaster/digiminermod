package de.sourcemaster.digiminermod.drone;

import de.sourcemaster.digiminermod.DigiMinerMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.entity.player.Inventory;

public final class DroneBlockEntity extends BlockEntity implements ExtendedMenuProvider<BlockPos> {
	private UUID ownerId;
	private DroneMode mode = DroneMode.FOLLOW;
	private Direction facing = Direction.NORTH;
	private final SimpleContainer inventory = new SimpleContainer(27);
	private boolean menuOpen;

	public DroneBlockEntity(BlockPos pos, BlockState state) { super(DigiMinerMod.DRONE_BLOCK_ENTITY, pos, state); }

	public static void serverTick(net.minecraft.world.level.Level ignored, BlockPos pos, BlockState state, DroneBlockEntity drone) {
		if (!(drone.level instanceof ServerLevel level) || drone.menuOpen || drone.mode != DroneMode.FOLLOW || drone.ownerId == null
				|| level.getGameTime() % 4 != 0) return;
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(drone.ownerId);
		if (owner == null || owner.level() != level) return;
		int dx = owner.blockPosition().getX() - pos.getX();
		int dy = owner.blockPosition().getY() + 1 - pos.getY();
		int dz = owner.blockPosition().getZ() - pos.getZ();
		int ax = Math.abs(dx), ay = Math.abs(dy), az = Math.abs(dz);
		if (Math.max(ax, Math.max(ay, az)) <= 3) return;
		Direction direction;
		if (ay >= ax && ay >= az) direction = dy > 0 ? Direction.UP : Direction.DOWN;
		else if (ax >= az) direction = dx > 0 ? Direction.EAST : Direction.WEST;
		else direction = dz > 0 ? Direction.SOUTH : Direction.NORTH;
		BlockPos target = pos.relative(direction);
		if (!level.getBlockState(target).canBeReplaced()) return;
		drone.moveTo(level, target);
	}

	private void moveTo(ServerLevel level, BlockPos target) {
		UUID owner = this.ownerId;
		DroneMode oldMode = this.mode;
		List<ItemStack> stacks = this.copyInventory();
		level.setBlock(this.worldPosition, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
		level.setBlock(target, DigiMinerMod.DRONE_BLOCK.defaultBlockState(), 3);
		if (level.getBlockEntity(target) instanceof DroneBlockEntity moved) {
			moved.restore(owner, oldMode, stacks);
			moved.facing = this.facing;
			moved.setChanged();
			moved.rememberPosition();
		}
	}

	public void runStaticCommand(int command) {
		if (!(this.level instanceof ServerLevel serverLevel) || this.mode != DroneMode.STATIC) return;
		if (command == 8) { this.facing = this.facing.getCounterClockWise(); this.setChanged(); return; }
		if (command == 9) { this.facing = this.facing.getClockWise(); this.setChanged(); return; }
		Direction move = switch (command) {
			case 4 -> this.facing;
			case 5 -> this.facing.getOpposite();
			case 6 -> Direction.UP;
			case 7 -> Direction.DOWN;
			default -> null;
		};
		if (move == null) return;
		BlockPos target = this.worldPosition.relative(move);
		if (serverLevel.getBlockState(target).canBeReplaced()) this.moveTo(serverLevel, target);
	}

	public void restore(UUID owner, DroneMode mode, List<ItemStack> stacks) {
		this.ownerId = owner;
		this.mode = mode;
		for (int i = 0; i < Math.min(27, stacks.size()); i++) this.inventory.setItem(i, stacks.get(i).copy());
		this.setChanged();
		this.rememberPosition();
	}

	private void rememberPosition() {
		if (!(this.level instanceof ServerLevel serverLevel) || this.ownerId == null) return;
		ServerPlayer owner = serverLevel.getServer().getPlayerList().getPlayer(this.ownerId);
		if (owner != null) DigiMinerMod.rememberDroneLocation(owner, serverLevel, this.worldPosition);
	}

	public List<ItemStack> copyInventory() {
		List<ItemStack> result = new ArrayList<>(27);
		for (int i = 0; i < 27; i++) result.add(this.inventory.getItem(i).copy());
		return result;
	}

	public boolean isOwner(Player player) { return this.ownerId != null && this.ownerId.equals(player.getUUID()); }
	public UUID getOwnerId() { return this.ownerId; }
	public DroneMode getMode() { return this.mode; }
	public void setMode(DroneMode mode) { this.mode = mode; this.setChanged(); }
	public SimpleContainer getInventory() { return this.inventory; }
	public void setMenuOpen(boolean menuOpen) { this.menuOpen = menuOpen; }
	@Override public BlockPos getScreenOpeningData(net.minecraft.server.level.ServerPlayer player) { return this.worldPosition; }
	@Override public Component getDisplayName() { return Component.literal("Drone"); }
	@Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
		return new DroneMenu(id, inventory, this);
	}

	@Override protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (this.ownerId != null) output.store("Owner", UUIDUtil.CODEC, this.ownerId);
		output.putInt("Mode", this.mode.ordinal());
		output.putInt("Facing", this.facing.get2DDataValue());
		this.inventory.storeAsItemList(output.list("Inventory", ItemStack.CODEC));
	}

	@Override protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.ownerId = input.read("Owner", UUIDUtil.CODEC).orElse(null);
		this.mode = DroneMode.byId(input.getIntOr("Mode", 0));
		this.facing = Direction.from2DDataValue(input.getIntOr("Facing", Direction.NORTH.get2DDataValue()));
		this.inventory.fromItemList(input.listOrEmpty("Inventory", ItemStack.CODEC));
	}
}
