package de.sourcemaster.digiminermod.drone;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

import java.util.UUID;

public final class DroneEntity extends PathfinderMob {
	private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(DroneEntity.class,
			EntityDataSerializers.INT);
	private UUID ownerId;
	private final SimpleContainer inventory = new SimpleContainer(27);
	private BlockPos movementTarget;

	public DroneEntity(EntityType<? extends DroneEntity> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
		this.setInvulnerable(true);
		this.setNoGravity(true);
		this.setCustomName(Component.literal("Drone"));
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 40.0)
				.add(Attributes.MOVEMENT_SPEED, 0.28)
				.add(Attributes.FOLLOW_RANGE, 32.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(MODE, DroneMode.FOLLOW.ordinal());
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		this.setDeltaMovement(0.0, 0.0, 0.0);
		if (this.getMode() != DroneMode.FOLLOW || this.ownerId == null) {
			this.getNavigation().stop();
			this.movementTarget = null;
			return;
		}
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(this.ownerId);
		if (owner == null || owner.level() != level) return;
		Direction behind = owner.getDirection().getOpposite();
		BlockPos destination = owner.blockPosition().relative(behind, 3).above();
		if (this.distanceToSqr(destination.getX() + 0.5, destination.getY() + 0.5, destination.getZ() + 0.5) > 576.0) {
			this.snapToCenter(destination);
			this.movementTarget = null;
			return;
		}
		this.moveOneAxisAtATime(destination);
	}

	private void moveOneAxisAtATime(BlockPos destination) {
		if (this.movementTarget == null || this.atCenter(this.movementTarget)) {
			BlockPos current = BlockPos.containing(this.getX(), this.getY(), this.getZ());
			if (current.equals(destination)) {
				this.snapToCenter(current);
				this.movementTarget = null;
				return;
			}
			int dx = destination.getX() - current.getX();
			int dy = destination.getY() - current.getY();
			int dz = destination.getZ() - current.getZ();
			BlockPos next = Math.abs(dx) >= Math.abs(dz) && dx != 0
					? current.offset(Integer.signum(dx), 0, 0)
					: dz != 0 ? current.offset(0, 0, Integer.signum(dz))
					: current.offset(0, Integer.signum(dy), 0);
			AABB targetBox = this.getBoundingBox().move(
					next.getX() + 0.5 - this.getX(), next.getY() + 0.5 - this.getY(), next.getZ() + 0.5 - this.getZ());
			if (!this.level().noCollision(this, targetBox)) return;
			this.movementTarget = next;
		}
		this.approachCenter(this.movementTarget, 0.18);
	}

	private boolean atCenter(BlockPos pos) {
		return Math.abs(this.getX() - pos.getX() - 0.5) < 0.02
				&& Math.abs(this.getY() - pos.getY() - 0.5) < 0.02
				&& Math.abs(this.getZ() - pos.getZ() - 0.5) < 0.02;
	}

	private void approachCenter(BlockPos pos, double speed) {
		double tx = pos.getX() + 0.5, ty = pos.getY() + 0.5, tz = pos.getZ() + 0.5;
		double x = this.getX(), y = this.getY(), z = this.getZ();
		if (Math.abs(tx - x) > 0.02) x += Math.copySign(Math.min(speed, Math.abs(tx - x)), tx - x);
		else if (Math.abs(tz - z) > 0.02) z += Math.copySign(Math.min(speed, Math.abs(tz - z)), tz - z);
		else if (Math.abs(ty - y) > 0.02) y += Math.copySign(Math.min(speed, Math.abs(ty - y)), ty - y);
		this.setPos(x, y, z);
	}

	private void snapToCenter(BlockPos pos) {
		this.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (!this.isOwner(player)) return InteractionResult.PASS;
		return InteractionResult.PASS;
	}

	public UUID getOwnerId() {
		return this.ownerId;
	}

	public void setOwner(Player player) {
		this.ownerId = player.getUUID();
	}

	public boolean isOwner(Player player) {
		return this.ownerId != null && this.ownerId.equals(player.getUUID());
	}

	public DroneMode getMode() {
		return DroneMode.byId(this.entityData.get(MODE));
	}

	public void setMode(DroneMode mode) {
		this.entityData.set(MODE, mode.ordinal());
		if (mode != DroneMode.FOLLOW) this.getNavigation().stop();
	}

	public SimpleContainer getDroneInventory() {
		return this.inventory;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		if (this.ownerId != null) output.store("Owner", UUIDUtil.CODEC, this.ownerId);
		output.putInt("Mode", this.getMode().ordinal());
		this.inventory.storeAsItemList(output.list("Inventory", net.minecraft.world.item.ItemStack.CODEC));
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.ownerId = input.read("Owner", UUIDUtil.CODEC).orElse(null);
		this.setMode(DroneMode.byId(input.getIntOr("Mode", 0)));
		this.inventory.fromItemList(input.listOrEmpty("Inventory", net.minecraft.world.item.ItemStack.CODEC));
	}
}
