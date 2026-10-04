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
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
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
	private final SimpleContainer equipmentInventory = new SimpleContainer(2) {
		@Override public void setChanged() {
			super.setChanged();
			DroneBlockEntity.this.updateLightState();
		}
	};
	private boolean menuOpen;
	private BlockPos previousPosition;
	private int activeProgram = -1;
	private int programRemaining;

	public DroneBlockEntity(BlockPos pos, BlockState state) {
		super(DigiMinerMod.DRONE_BLOCK_ENTITY, pos, state);
		this.equipmentInventory.setItem(0, new ItemStack(DigiMinerMod.DRONE_LIGHT));
		this.equipmentInventory.setItem(1, new ItemStack(DigiMinerMod.BASIC_PROGRAM_DRIVE));
	}

	public static void serverTick(net.minecraft.world.level.Level ignored, BlockPos pos, BlockState state, DroneBlockEntity drone) {
		if (!(drone.level instanceof ServerLevel level) || drone.ownerId == null || level.getGameTime() % 4 != 0) return;
		if (drone.activeProgram >= 0) {
			drone.runProgramStep(level);
			return;
		}
		if (drone.menuOpen || drone.mode != DroneMode.FOLLOW) return;
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(drone.ownerId);
		if (owner == null || owner.level() != level) return;
		BlockPos destination = owner.blockPosition().above();
		int tetherLength = chebyshevDistance(pos, destination);
		if (tetherLength <= 3) return;
		BlockPos next = drone.findNextPathStep(level, pos, destination, tetherLength);
		if (next != null) drone.moveTo(level, next);
	}

	public void startProgram(int program, int parameterOne, int parameterTwo) {
		if (this.mode != DroneMode.BUILD || program != 0
				|| !this.equipmentInventory.getItem(1).is(DigiMinerMod.BASIC_PROGRAM_DRIVE)) return;
		this.activeProgram = program;
		this.programRemaining = Math.max(0, Math.min(999, parameterOne));
		this.setChanged();
	}

	private void runProgramStep(ServerLevel level) {
		if (this.mode != DroneMode.BUILD || this.activeProgram != 0 || this.programRemaining <= 0) {
			this.activeProgram = -1;
			this.programRemaining = 0;
			this.setChanged();
			return;
		}
		ItemStack material = this.inventory.getItem(0);
		if (!(material.getItem() instanceof BlockItem blockItem)) {
			this.activeProgram = -1;
			this.setChanged();
			return;
		}
		BlockPos nextDronePos = this.worldPosition.relative(this.facing);
		BlockPos bridgePos = nextDronePos.below();
		if (!level.getBlockState(nextDronePos).canBeReplaced() || !level.getBlockState(bridgePos).canBeReplaced()) {
			this.activeProgram = -1;
			this.setChanged();
			return;
		}
		level.setBlock(bridgePos, blockItem.getBlock().defaultBlockState(), 3);
		material.shrink(1);
		this.inventory.setChanged();
		this.programRemaining--;
		if (this.programRemaining <= 0) this.activeProgram = -1;
		this.moveTo(level, nextDronePos);
	}

	private BlockPos findNextPathStep(ServerLevel level, BlockPos start, BlockPos destination, int tetherLength) {
		// Close to the player, direct movement dominates. A long tether lowers the heuristic
		// weight and raises the search budget, making sideways/vertical detours increasingly likely.
		double directionWeight = Math.max(0.65, 2.4 - Math.max(0, tetherLength - 3) * 0.12);
		int maximumSteps = Math.min(48, 10 + tetherLength * 2);
		int maximumVisited = Math.min(1200, 96 + tetherLength * 24);
		PriorityQueue<PathNode> open = new PriorityQueue<>(Comparator.comparingDouble(PathNode::score));
		Map<Long, Double> bestCosts = new HashMap<>();
		open.add(new PathNode(start, 0.0, directionWeight * distanceOutsideTether(start, destination), null));
		bestCosts.put(start.asLong(), 0.0);
		int visited = 0;
		while (!open.isEmpty() && visited++ < maximumVisited) {
			PathNode node = open.poll();
			if (distanceOutsideTether(node.pos(), destination) == 0 && node.firstStep() != null) return node.firstStep();
			if (node.cost() >= maximumSteps) continue;
			for (Direction direction : Direction.values()) {
				BlockPos next = node.pos().relative(direction);
				if (!level.hasChunkAt(next) || !level.getBlockState(next).canBeReplaced()) continue;
				double backtrackPenalty = next.equals(this.previousPosition) ? Math.max(0.5, 3.0 - tetherLength * 0.12) : 0.0;
				double cost = node.cost() + 1.0 + backtrackPenalty;
				if (cost >= bestCosts.getOrDefault(next.asLong(), Double.POSITIVE_INFINITY)) continue;
				bestCosts.put(next.asLong(), cost);
				BlockPos first = node.firstStep() == null ? next : node.firstStep();
				double score = cost + directionWeight * distanceOutsideTether(next, destination);
				open.add(new PathNode(next, cost, score, first));
			}
		}
		return null;
	}

	private static int chebyshevDistance(BlockPos first, BlockPos second) {
		return Math.max(Math.abs(first.getX() - second.getX()),
				Math.max(Math.abs(first.getY() - second.getY()), Math.abs(first.getZ() - second.getZ())));
	}

	private static int distanceOutsideTether(BlockPos pos, BlockPos destination) {
		return Math.max(0, Math.abs(pos.getX() - destination.getX()) - 3)
				+ Math.max(0, Math.abs(pos.getY() - destination.getY()) - 3)
				+ Math.max(0, Math.abs(pos.getZ() - destination.getZ()) - 3);
	}

	private void moveTo(ServerLevel level, BlockPos target) {
		UUID owner = this.ownerId;
		DroneMode oldMode = this.mode;
		List<ItemStack> stacks = this.copyInventory();
		ItemStack tool = this.equipmentInventory.getItem(0).copy();
		ItemStack programDrive = this.equipmentInventory.getItem(1).copy();
		int dx = target.getX() - this.worldPosition.getX(), dz = target.getZ() - this.worldPosition.getZ();
		if (dx > 0) this.facing = Direction.EAST;
		else if (dx < 0) this.facing = Direction.WEST;
		else if (dz > 0) this.facing = Direction.SOUTH;
		else if (dz < 0) this.facing = Direction.NORTH;
		level.setBlock(this.worldPosition, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
		BlockState movedState = DigiMinerMod.DRONE_BLOCK.defaultBlockState()
				.setValue(DroneBlock.FACING, this.facing)
				.setValue(DroneBlock.LIT, tool.is(DigiMinerMod.DRONE_LIGHT));
		level.setBlock(target, movedState, 3);
		if (level.getBlockEntity(target) instanceof DroneBlockEntity moved) {
			moved.restore(owner, oldMode, stacks, tool, programDrive);
			moved.facing = this.facing;
			moved.activeProgram = this.activeProgram;
			moved.programRemaining = this.programRemaining;
			moved.previousPosition = this.worldPosition;
			moved.setChanged();
			moved.rememberPosition();
		}
	}

	public void pushFromHit(Direction hitFace) {
		if (!(this.level instanceof ServerLevel serverLevel) || this.mode != DroneMode.STATIC) return;
		Direction movement = hitFace.getOpposite();
		if (movement.getAxis().isHorizontal()) this.facing = movement;
		BlockPos target = this.worldPosition.relative(movement);
		if (serverLevel.getBlockState(target).canBeReplaced()) this.moveTo(serverLevel, target);
	}

	public void restore(UUID owner, DroneMode mode, List<ItemStack> stacks) {
		this.restore(owner, mode, stacks, new ItemStack(DigiMinerMod.DRONE_LIGHT), new ItemStack(DigiMinerMod.BASIC_PROGRAM_DRIVE));
	}

	public void restore(UUID owner, DroneMode mode, List<ItemStack> stacks, ItemStack tool, ItemStack programDrive) {
		this.ownerId = owner;
		this.mode = mode;
		for (int i = 0; i < Math.min(27, stacks.size()); i++) this.inventory.setItem(i, stacks.get(i).copy());
		this.equipmentInventory.setItem(0, tool.copy());
		this.equipmentInventory.setItem(1, programDrive.copy());
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
	public SimpleContainer getEquipmentInventory() { return this.equipmentInventory; }
	private void updateLightState() {
		this.setChanged();
		if (this.level == null) return;
		BlockState state = this.getBlockState();
		if (!state.hasProperty(DroneBlock.LIT)) return;
		boolean lit = this.equipmentInventory.getItem(0).is(DigiMinerMod.DRONE_LIGHT);
		if (state.getValue(DroneBlock.LIT) != lit) this.level.setBlock(this.worldPosition, state.setValue(DroneBlock.LIT, lit), 3);
	}
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
		output.putInt("ActiveProgram", this.activeProgram);
		output.putInt("ProgramRemaining", this.programRemaining);
		this.inventory.storeAsItemList(output.list("Inventory", ItemStack.CODEC));
		if (!this.equipmentInventory.getItem(0).isEmpty()) output.store("Tool", ItemStack.CODEC, this.equipmentInventory.getItem(0));
		if (!this.equipmentInventory.getItem(1).isEmpty()) output.store("ProgramDrive", ItemStack.CODEC, this.equipmentInventory.getItem(1));
	}

	@Override protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.ownerId = input.read("Owner", UUIDUtil.CODEC).orElse(null);
		this.mode = DroneMode.byId(input.getIntOr("Mode", 0));
		this.facing = Direction.from2DDataValue(input.getIntOr("Facing", Direction.NORTH.get2DDataValue()));
		this.activeProgram = input.getIntOr("ActiveProgram", -1);
		this.programRemaining = input.getIntOr("ProgramRemaining", 0);
		this.inventory.fromItemList(input.listOrEmpty("Inventory", ItemStack.CODEC));
		this.equipmentInventory.setItem(0, input.read("Tool", ItemStack.CODEC).orElseGet(() -> new ItemStack(DigiMinerMod.DRONE_LIGHT)));
		this.equipmentInventory.setItem(1, input.read("ProgramDrive", ItemStack.CODEC)
				.orElseGet(() -> new ItemStack(DigiMinerMod.BASIC_PROGRAM_DRIVE)));
	}

	private record PathNode(BlockPos pos, double cost, double score, BlockPos firstStep) {}
}
