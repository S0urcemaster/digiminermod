package de.sourcemaster.digiminermod.credit;

import com.mojang.serialization.MapCodec;
import de.sourcemaster.digiminermod.DigiMinerMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.Containers;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

public final class CreditTerminalBlock extends BaseEntityBlock {
	public static final MapCodec<CreditTerminalBlock> CODEC = BlockBehaviour.simpleCodec(CreditTerminalBlock::new);
	public static final EnumProperty<net.minecraft.core.Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

	public CreditTerminalBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
	}
	@Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
	@Override protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) { builder.add(FACING); }
	@Override public BlockState getStateForPlacement(BlockPlaceContext context) { return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()); }
	@Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CreditTerminalBlockEntity(pos, state); }
	@Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

	@Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		if (placer != null && level.getBlockEntity(pos) instanceof CreditTerminalBlockEntity terminal) terminal.setOwner(placer.getUUID());
	}

	@Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
				&& level.getBlockEntity(pos) instanceof CreditTerminalBlockEntity terminal) {
			// Development/offline player UUIDs may change between launches. Opening
			// the terminal also establishes who receives subsequent hopper sales.
			terminal.setOwner(serverPlayer.getUUID());
			CreditAccount.sync(serverPlayer);
			serverPlayer.openMenu(terminal);
		}
		return InteractionResult.SUCCESS;
	}

	@Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, DigiMinerMod.CREDIT_TERMINAL_BLOCK_ENTITY,
				CreditTerminalBlockEntity::serverTick);
	}

	@Override protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		if (level.getBlockEntity(pos) instanceof CreditTerminalBlockEntity terminal) Containers.dropContents(level, pos, terminal);
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
	}
}
