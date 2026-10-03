package de.sourcemaster.digiminermod.drone;

import de.sourcemaster.digiminermod.DigiMinerMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

public final class DroneCoreItem extends Item {
	public DroneCoreItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!(level instanceof ServerLevel serverLevel) || !(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
		DroneEntity drone = DigiMinerMod.findDrone(serverPlayer);
		if (drone == null) {
			drone = DigiMinerMod.DRONE.create(serverLevel, EntitySpawnReason.TRIGGERED);
			if (drone == null) return InteractionResult.FAIL;
			drone.setOwner(player);
			drone.setPos(player.getX() + 1.5, player.getY(), player.getZ() + 1.5);
			serverLevel.addFreshEntity(drone);
			serverPlayer.sendOverlayMessage(Component.literal("Drone deployed"));
		} else if (drone.level() == player.level()) {
			drone.discard();
		} else {
			serverPlayer.sendOverlayMessage(Component.literal("Drone is in another dimension"));
		}
		return InteractionResult.SUCCESS;
	}
}
