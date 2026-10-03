package de.sourcemaster.digiminermod.drone;

import com.mojang.serialization.MapCodec;
import de.sourcemaster.digiminermod.DigiMinerMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;

public final class DroneBlock extends BaseEntityBlock {
	public static final MapCodec<DroneBlock> CODEC = BlockBehaviour.simpleCodec(DroneBlock::new);
	private static final VoxelShape SHAPE = box(3, 3, 3, 13, 13, 13);

	public DroneBlock(Properties properties) { super(properties); }
	@Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
	@Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new DroneBlockEntity(pos, state); }
	@Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
	@Override protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
	@Override protected VoxelShape getCollisionShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		return this.openDrone(level, pos, player);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
			Player player, InteractionHand hand, BlockHitResult hit) {
		return this.openDrone(level, pos, player);
	}

	private InteractionResult openDrone(Level level, BlockPos pos, Player player) {
		if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
				&& level.getBlockEntity(pos) instanceof DroneBlockEntity drone && drone.isOwner(player)) {
			serverPlayer.openMenu(drone);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof DroneBlockEntity drone) {
			var owner = drone.getOwnerId();
			var stacks = drone.copyInventory();
			var mode = drone.getMode();
			serverLevel.getServer().execute(() -> DigiMinerMod.respawnDroneAtSpawn(serverLevel, owner, mode, stacks));
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, DigiMinerMod.DRONE_BLOCK_ENTITY, DroneBlockEntity::serverTick);
	}
}
