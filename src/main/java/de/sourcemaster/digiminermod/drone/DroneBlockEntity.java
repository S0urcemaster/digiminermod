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
	private final SimpleContainer equipmentInventory = new SimpleContainer(3) {
		@Override public void setChanged() {
			super.setChanged();
			DroneBlockEntity.this.updateLightState();
		}
	};
	private boolean menuOpen;
	private BlockPos previousPosition;
	private int activeProgram = -1;
	private int programRemaining;
	private int excavationFuelUnits;
	private int shaftClearIndex;
	private boolean shaftMoveDownPending;
	private BlockPos programBreakPos;
	private int programBreakTicks;
	private int programBreakTicksRequired;
	private int roomWidth;
	private int roomHeight;
	private int roomBlockIndex;
	private BlockPos roomOrigin;
	private Direction roomFacing = Direction.NORTH;
	private String droneName = "Digi";

	public DroneBlockEntity(BlockPos pos, BlockState state) {
		super(DigiMinerMod.DRONE_BLOCK_ENTITY, pos, state);
		this.equipmentInventory.setItem(0, new ItemStack(DigiMinerMod.BASIC_SCANNER_CARTRIDGE));
		this.equipmentInventory.setItem(1, new ItemStack(DigiMinerMod.BASIC_BUILD_CARTRIDGE));
		this.equipmentInventory.setItem(2, new ItemStack(DigiMinerMod.BASIC_EXCAVATE_CARTRIDGE));
	}

	public static void serverTick(net.minecraft.world.level.Level ignored, BlockPos pos, BlockState state, DroneBlockEntity drone) {
		if (!(drone.level instanceof ServerLevel level) || drone.ownerId == null) return;
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(drone.ownerId);
		if (owner != null && owner.level() == level && level.getGameTime() % 20 == 0) {
			boolean scannerActive = drone.equipmentInventory.getItem(0).is(DigiMinerMod.BASIC_SCANNER_CARTRIDGE);
			if (scannerActive) {
				if (!drone.damageCartridge(0, 20)) return;
				boolean ironNearby = false;
				boolean diamondNearby = false;
				// The basic cartridge carries Iron I and Diamond I. Both level-I
				// scanners have radius 3; Diamond II-IV will use 4, 5 and 6.
				for (BlockPos scan : BlockPos.betweenClosed(pos.offset(-3, -3, -3), pos.offset(3, 3, 3))) {
					if (scan.distManhattan(pos) > 3) continue;
					BlockState scanned = level.getBlockState(scan);
					if (scanned.is(net.minecraft.world.level.block.Blocks.IRON_ORE)
							|| scanned.is(net.minecraft.world.level.block.Blocks.DEEPSLATE_IRON_ORE)) ironNearby = true;
					if (scanned.is(net.minecraft.world.level.block.Blocks.DIAMOND_ORE)
							|| scanned.is(net.minecraft.world.level.block.Blocks.DEEPSLATE_DIAMOND_ORE)) diamondNearby = true;
					if (ironNearby && diamondNearby) break;
				}
				net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(owner,
						new DroneNetworking.ScannerPayload(pos.asLong(), ironNearby, diamondNearby));
			}
		}
		if (!state.getValue(DroneBlock.LIT)) level.setBlock(pos, state.setValue(DroneBlock.LIT, true), 3);
		if (drone.activeProgram >= 0) {
			if (drone.mode == DroneMode.EXCAVATE || level.getGameTime() % 4 == 0) drone.runProgramStep(level);
			return;
		}
		if (level.getGameTime() % 4 != 0) return;
		if (drone.menuOpen || drone.mode != DroneMode.FOLLOW) return;
		if (owner == null || owner.level() != level) return;
		BlockPos destination = owner.blockPosition().above();
		int tetherLength = chebyshevDistance(pos, destination);
		if (horizontalDistance(pos, destination) <= 3 && pos.getY() == destination.getY()) return;
		BlockPos next = drone.findNextPathStep(level, pos, destination, tetherLength);
		if (next != null) drone.moveTo(level, next);
	}

	public boolean startProgram(int program, int parameterOne, int parameterTwo, int parameterThree) {
		if (parameterOne <= 0) return false;
		if (this.mode == DroneMode.BUILD) {
			if (program != 0 || !this.equipmentInventory.getItem(1).is(DigiMinerMod.BASIC_BUILD_CARTRIDGE)
					|| !(this.inventory.getItem(0).getItem() instanceof BlockItem)) return false;
		} else if (this.mode == DroneMode.EXCAVATE) {
			if ((program != 1 && program != 2) || !this.equipmentInventory.getItem(2).is(DigiMinerMod.BASIC_EXCAVATE_CARTRIDGE)
					|| !this.hasIronFuel()) return false;
			if (program == 2 && (parameterTwo <= 0 || parameterThree <= 0)) return false;
			this.shaftClearIndex = 0;
			this.shaftMoveDownPending = false;
			this.roomWidth = Math.max(1, Math.min(99, parameterTwo));
			this.roomHeight = Math.max(1, Math.min(99, parameterThree));
			this.roomBlockIndex = 0;
			if (program == 2) {
				this.roomFacing = this.facing;
				this.roomOrigin = this.worldPosition.relative(this.roomFacing);
			}
			this.clearBreakProgress();
		} else return false;
		this.activeProgram = program;
		this.programRemaining = Math.max(0, Math.min(99, parameterOne));
		this.setChanged();
		return true;
	}

	private void runProgramStep(ServerLevel level) {
		if (this.programRemaining <= 0) {
			this.stopProgram(level);
			return;
		}
		if (this.mode == DroneMode.EXCAVATE && this.activeProgram == 1) {
			this.runShaftStep(level);
			return;
		}
		if (this.mode == DroneMode.EXCAVATE && this.activeProgram == 2) {
			this.runRoomStep(level);
			return;
		}
		if (this.mode != DroneMode.BUILD || this.activeProgram != 0) {
			this.stopProgram(level);
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
		if (!this.damageCartridge(1, 1)) this.activeProgram = -1;
		if (this.programRemaining <= 0) this.activeProgram = -1;
		this.moveTo(level, nextDronePos);
	}

	private void runShaftStep(ServerLevel level) {
		if (this.shaftMoveDownPending) {
			BlockPos down = this.worldPosition.below();
			if (!level.getBlockState(down).canBeReplaced()) { this.stopProgram(level); return; }
			this.shaftMoveDownPending = false;
			this.shaftClearIndex = 0;
			this.programRemaining--;
			if (this.programRemaining <= 0) this.activeProgram = -1;
			this.moveTo(level, down);
			return;
		}

		BlockPos base = this.worldPosition.relative(this.facing).below();
		while (this.shaftClearIndex < 5) {
			BlockPos target = base.above(this.shaftClearIndex);
			BlockState targetState = level.getBlockState(target);
			if (targetState.isAir() || targetState.canBeReplaced()) {
				this.shaftClearIndex++;
				continue;
			}
			if (!this.mineBlock(level, target, targetState)) this.stopProgram(level);
			return;
		}

		this.clearBreakProgress();
		this.shaftMoveDownPending = true;
		this.moveTo(level, this.worldPosition.relative(this.facing));
	}

	private void runRoomStep(ServerLevel level) {
		if (this.roomOrigin == null) { this.stopProgram(level); return; }
		int total = this.programRemaining * this.roomWidth * this.roomHeight;
		if (this.roomBlockIndex >= total) { this.stopProgram(level); return; }

		int plane = this.roomWidth * this.roomHeight;
		int forward = this.roomBlockIndex / plane;
		int withinPlane = this.roomBlockIndex % plane;
		int verticalStep = withinPlane / this.roomWidth;
		int horizontalStep = withinPlane % this.roomWidth;
		int up = forward % 2 == 0 ? verticalStep : this.roomHeight - 1 - verticalStep;
		int row = forward * this.roomHeight + verticalStep;
		int right = row % 2 == 0 ? horizontalStep : this.roomWidth - 1 - horizontalStep;
		BlockPos target = this.roomOrigin.relative(this.roomFacing, forward)
				.relative(this.roomFacing.getClockWise(), right).above(up);
		if (target.distManhattan(this.worldPosition) != 1) { this.stopProgram(level); return; }

		BlockState targetState = level.getBlockState(target);
		if (!targetState.isAir() && !targetState.canBeReplaced()) {
			if (!this.mineBlock(level, target, targetState)) { this.stopProgram(level); return; }
			if (!level.getBlockState(target).canBeReplaced()) return;
		}
		this.roomBlockIndex++;
		this.moveTo(level, target);
	}

	/** One ingot supplies 250 scaled units; a normal mined block costs 3. */
	private boolean mineBlock(ServerLevel level, BlockPos target, BlockState state) {
		ItemStack ironPickaxe = new ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE);
		float hardness = state.getDestroySpeed(level, target);
		if (hardness < 0.0F || state.requiresCorrectToolForDrops() && !ironPickaxe.isCorrectToolForDrops(state)) return false;
		if (hardness > 0.0F && !this.ensureExcavationFuel(3)) return false;

		if (!target.equals(this.programBreakPos)) {
			this.clearBreakProgress();
			this.programBreakPos = target.immutable();
			float speed = Math.max(0.0001F, ironPickaxe.getDestroySpeed(state));
			this.programBreakTicksRequired = Math.max(1, (int)Math.ceil(hardness * 30.0F / speed));
		}
		this.programBreakTicks++;
		level.destroyBlockProgress(this.worldPosition.hashCode(), target,
				Math.min(9, this.programBreakTicks * 10 / this.programBreakTicksRequired));
		if (this.programBreakTicks < this.programBreakTicksRequired) return true;

		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(this.ownerId);
		level.destroyBlockProgress(this.worldPosition.hashCode(), target, -1);
		if (!level.destroyBlock(target, true, owner, 512)) return false;
		if (hardness > 0.0F) this.excavationFuelUnits -= 3;
		this.clearBreakProgress();
		this.setChanged();
		if (!this.damageCartridge(2, 1)) return false;
		return true;
	}

	private boolean damageCartridge(int slot, int amount) {
		ItemStack cartridge = this.equipmentInventory.getItem(slot);
		if (cartridge.isEmpty() || !cartridge.isDamageableItem()) return false;
		int damage = cartridge.getDamageValue() + amount;
		if (damage >= cartridge.getMaxDamage()) {
			this.equipmentInventory.setItem(slot, ItemStack.EMPTY);
			return false;
		}
		cartridge.setDamageValue(damage);
		this.equipmentInventory.setChanged();
		return true;
	}

	private boolean hasIronFuel() {
		if (this.excavationFuelUnits >= 3) return true;
		for (int i = 0; i < this.inventory.getContainerSize(); i++) {
			if (this.inventory.getItem(i).is(net.minecraft.world.item.Items.IRON_INGOT)) return true;
		}
		return false;
	}

	private boolean ensureExcavationFuel(int required) {
		while (this.excavationFuelUnits < required) {
			int fuelSlot = -1;
			for (int i = 0; i < this.inventory.getContainerSize(); i++) {
				if (this.inventory.getItem(i).is(net.minecraft.world.item.Items.IRON_INGOT)) { fuelSlot = i; break; }
			}
			if (fuelSlot < 0) return false;
			this.inventory.getItem(fuelSlot).shrink(1);
			this.excavationFuelUnits += 250;
			this.inventory.setChanged();
		}
		return true;
	}

	private void stopProgram(ServerLevel level) {
		this.clearBreakProgress();
		this.activeProgram = -1;
		this.programRemaining = 0;
		this.shaftMoveDownPending = false;
		this.shaftClearIndex = 0;
		this.roomOrigin = null;
		this.setChanged();
	}

	private void clearBreakProgress() {
		if (this.level instanceof ServerLevel serverLevel && this.programBreakPos != null) {
			serverLevel.destroyBlockProgress(this.worldPosition.hashCode(), this.programBreakPos, -1);
		}
		this.programBreakPos = null;
		this.programBreakTicks = 0;
		this.programBreakTicksRequired = 0;
	}

	private BlockPos findNextPathStep(ServerLevel level, BlockPos start, BlockPos destination, int tetherLength) {
		// Close to the player, direct movement dominates. A long tether lowers the heuristic
		// weight and raises the search budget, making sideways/vertical detours increasingly likely.
		double directionWeight = Math.max(0.65, 2.4 - Math.max(0, tetherLength - 3) * 0.12);
		int maximumSteps = Math.min(48, 10 + tetherLength * 2);
		int maximumVisited = Math.min(1200, 96 + tetherLength * 24);
		PriorityQueue<PathNode> open = new PriorityQueue<>(Comparator.comparingDouble(PathNode::score));
		Map<Long, Double> bestCosts = new HashMap<>();
		open.add(new PathNode(start, 0.0, directionWeight * followDistance(start, destination), null));
		bestCosts.put(start.asLong(), 0.0);
		int visited = 0;
		while (!open.isEmpty() && visited++ < maximumVisited) {
			PathNode node = open.poll();
			if (followDistance(node.pos(), destination) == 0 && node.firstStep() != null) return node.firstStep();
			if (node.cost() >= maximumSteps) continue;
			for (Direction direction : Direction.values()) {
				BlockPos next = node.pos().relative(direction);
				if (!level.hasChunkAt(next) || !level.getBlockState(next).canBeReplaced()) continue;
				double backtrackPenalty = next.equals(this.previousPosition) ? Math.max(0.5, 3.0 - tetherLength * 0.12) : 0.0;
				double cost = node.cost() + 1.0 + backtrackPenalty;
				if (cost >= bestCosts.getOrDefault(next.asLong(), Double.POSITIVE_INFINITY)) continue;
				bestCosts.put(next.asLong(), cost);
				BlockPos first = node.firstStep() == null ? next : node.firstStep();
				double score = cost + directionWeight * followDistance(next, destination);
				open.add(new PathNode(next, cost, score, first));
			}
		}
		return null;
	}

	private static int chebyshevDistance(BlockPos first, BlockPos second) {
		return Math.max(Math.abs(first.getX() - second.getX()),
				Math.max(Math.abs(first.getY() - second.getY()), Math.abs(first.getZ() - second.getZ())));
	}

	private static int horizontalDistance(BlockPos first, BlockPos second) {
		return Math.max(Math.abs(first.getX() - second.getX()), Math.abs(first.getZ() - second.getZ()));
	}

	private static int followDistance(BlockPos pos, BlockPos destination) {
		return Math.max(0, Math.abs(pos.getX() - destination.getX()) - 3)
				+ Math.abs(pos.getY() - destination.getY()) * 2
				+ Math.max(0, Math.abs(pos.getZ() - destination.getZ()) - 3);
	}

	private void moveTo(ServerLevel level, BlockPos target) {
		UUID owner = this.ownerId;
		DroneMode oldMode = this.mode;
		List<ItemStack> stacks = this.copyInventory();
		ItemStack scannerCartridge = this.equipmentInventory.getItem(0).copy();
		ItemStack programDrive = this.equipmentInventory.getItem(1).copy();
		ItemStack excavateCartridge = this.equipmentInventory.getItem(2).copy();
		int dx = target.getX() - this.worldPosition.getX(), dz = target.getZ() - this.worldPosition.getZ();
		if (dx > 0) this.facing = Direction.EAST;
		else if (dx < 0) this.facing = Direction.WEST;
		else if (dz > 0) this.facing = Direction.SOUTH;
		else if (dz < 0) this.facing = Direction.NORTH;
		level.setBlock(this.worldPosition, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
		BlockState movedState = DigiMinerMod.DRONE_BLOCK.defaultBlockState()
				.setValue(DroneBlock.FACING, this.facing)
				.setValue(DroneBlock.LIT, true);
		level.setBlock(target, movedState, 3);
		if (level.getBlockEntity(target) instanceof DroneBlockEntity moved) {
			moved.restore(owner, oldMode, stacks, scannerCartridge, programDrive, excavateCartridge);
			moved.facing = this.facing;
			moved.activeProgram = this.activeProgram;
			moved.programRemaining = this.programRemaining;
			moved.excavationFuelUnits = this.excavationFuelUnits;
			moved.shaftClearIndex = this.shaftClearIndex;
			moved.shaftMoveDownPending = this.shaftMoveDownPending;
			moved.programBreakPos = this.programBreakPos;
			moved.programBreakTicks = this.programBreakTicks;
			moved.programBreakTicksRequired = this.programBreakTicksRequired;
			moved.roomWidth = this.roomWidth;
			moved.roomHeight = this.roomHeight;
			moved.roomBlockIndex = this.roomBlockIndex;
			moved.roomOrigin = this.roomOrigin;
			moved.roomFacing = this.roomFacing;
			moved.setDroneName(this.droneName);
			moved.previousPosition = this.worldPosition;
			moved.setChanged();
			moved.rememberPosition();
		}
	}

	public void pushFromHit(Direction hitFace) {
		if (!(this.level instanceof ServerLevel serverLevel)
				|| this.mode != DroneMode.STATIC && this.mode != DroneMode.FOLLOW) return;
		Direction movement = hitFace.getOpposite();
		if (movement.getAxis().isHorizontal()) this.facing = movement;
		BlockPos target = this.worldPosition.relative(movement);
		if (serverLevel.getBlockState(target).canBeReplaced()) this.moveTo(serverLevel, target);
	}

	public void restore(UUID owner, DroneMode mode, List<ItemStack> stacks) {
		this.restore(owner, mode, stacks, new ItemStack(DigiMinerMod.BASIC_SCANNER_CARTRIDGE),
				new ItemStack(DigiMinerMod.BASIC_BUILD_CARTRIDGE), new ItemStack(DigiMinerMod.BASIC_EXCAVATE_CARTRIDGE));
	}

	public void restore(UUID owner, DroneMode mode, List<ItemStack> stacks, ItemStack scannerCartridge, ItemStack programDrive,
			ItemStack excavateCartridge) {
		this.ownerId = owner;
		this.mode = mode;
		for (int i = 0; i < Math.min(27, stacks.size()); i++) this.inventory.setItem(i, stacks.get(i).copy());
		this.equipmentInventory.setItem(0, scannerCartridge.copy());
		this.equipmentInventory.setItem(1, programDrive.copy());
		this.equipmentInventory.setItem(2, excavateCartridge.copy());
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
	public String getDroneName() { return this.droneName; }
	public void setDroneName(String name) {
		String cleaned = name == null ? "" : name.replaceAll("[^A-Za-z0-9_]", "");
		this.droneName = cleaned.substring(0, Math.min(16, cleaned.length()));
		this.setChanged();
		if (this.level != null) this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
	}
	private void updateLightState() {
		this.setChanged();
		if (this.level == null) return;
		BlockState state = this.getBlockState();
		if (!state.hasProperty(DroneBlock.LIT)) return;
		boolean lit = true;
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
		output.putInt("ExcavationFuelUnits", this.excavationFuelUnits);
		output.putInt("ShaftClearIndex", this.shaftClearIndex);
		output.putBoolean("ShaftMoveDownPending", this.shaftMoveDownPending);
		if (this.programBreakPos != null) output.putLong("ProgramBreakPos", this.programBreakPos.asLong());
		output.putInt("ProgramBreakTicks", this.programBreakTicks);
		output.putInt("ProgramBreakTicksRequired", this.programBreakTicksRequired);
		output.putInt("RoomWidth", this.roomWidth);
		output.putInt("RoomHeight", this.roomHeight);
		output.putInt("RoomBlockIndex", this.roomBlockIndex);
		if (this.roomOrigin != null) output.putLong("RoomOrigin", this.roomOrigin.asLong());
		output.putInt("RoomFacing", this.roomFacing.get2DDataValue());
		output.putString("DroneName", this.droneName);
		this.inventory.storeAsItemList(output.list("Inventory", ItemStack.CODEC));
		output.putBoolean("CartridgeSlotsStored", true);
		if (!this.equipmentInventory.getItem(0).isEmpty()) output.store("ScannerCartridge", ItemStack.CODEC, this.equipmentInventory.getItem(0));
		if (!this.equipmentInventory.getItem(1).isEmpty()) output.store("ProgramDrive", ItemStack.CODEC, this.equipmentInventory.getItem(1));
		if (!this.equipmentInventory.getItem(2).isEmpty()) output.store("ExcavateCartridge", ItemStack.CODEC, this.equipmentInventory.getItem(2));
	}

	@Override protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.ownerId = input.read("Owner", UUIDUtil.CODEC).orElse(null);
		this.mode = DroneMode.byId(input.getIntOr("Mode", 0));
		this.facing = Direction.from2DDataValue(input.getIntOr("Facing", Direction.NORTH.get2DDataValue()));
		this.activeProgram = input.getIntOr("ActiveProgram", -1);
		this.programRemaining = input.getIntOr("ProgramRemaining", 0);
		this.excavationFuelUnits = input.getIntOr("ExcavationFuelUnits", 0);
		this.shaftClearIndex = input.getIntOr("ShaftClearIndex", 0);
		this.shaftMoveDownPending = input.getBooleanOr("ShaftMoveDownPending", false);
		this.programBreakPos = input.getLong("ProgramBreakPos").map(BlockPos::of).orElse(null);
		this.programBreakTicks = input.getIntOr("ProgramBreakTicks", 0);
		this.programBreakTicksRequired = input.getIntOr("ProgramBreakTicksRequired", 0);
		this.roomWidth = input.getIntOr("RoomWidth", 1);
		this.roomHeight = input.getIntOr("RoomHeight", 1);
		this.roomBlockIndex = input.getIntOr("RoomBlockIndex", 0);
		this.roomOrigin = input.getLong("RoomOrigin").map(BlockPos::of).orElse(null);
		this.roomFacing = Direction.from2DDataValue(input.getIntOr("RoomFacing", Direction.NORTH.get2DDataValue()));
		this.droneName = input.getStringOr("DroneName", "Digi");
		this.inventory.fromItemList(input.listOrEmpty("Inventory", ItemStack.CODEC));
		boolean cartridgeSlotsStored = input.getBooleanOr("CartridgeSlotsStored", false);
		this.equipmentInventory.setItem(0, input.read("ScannerCartridge", ItemStack.CODEC)
				.orElseGet(() -> cartridgeSlotsStored ? ItemStack.EMPTY : new ItemStack(DigiMinerMod.BASIC_SCANNER_CARTRIDGE)));
		ItemStack oldDrive = input.read("ProgramDrive", ItemStack.CODEC).orElse(ItemStack.EMPTY);
		this.equipmentInventory.setItem(1, oldDrive.is(DigiMinerMod.BASIC_PROGRAM_DRIVE)
				? new ItemStack(DigiMinerMod.BASIC_BUILD_CARTRIDGE)
				: oldDrive.isEmpty() && !cartridgeSlotsStored ? new ItemStack(DigiMinerMod.BASIC_BUILD_CARTRIDGE) : oldDrive);
		this.equipmentInventory.setItem(2, input.read("ExcavateCartridge", ItemStack.CODEC)
				.orElseGet(() -> cartridgeSlotsStored ? ItemStack.EMPTY : new ItemStack(DigiMinerMod.BASIC_EXCAVATE_CARTRIDGE)));
	}

	@Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
		return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
	}
	@Override public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider provider) {
		return this.saveCustomOnly(provider);
	}

	private record PathNode(BlockPos pos, double cost, double score, BlockPos firstStep) {}
}
