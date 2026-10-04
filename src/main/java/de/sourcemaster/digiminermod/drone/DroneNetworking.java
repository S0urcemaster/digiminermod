package de.sourcemaster.digiminermod.drone;

import de.sourcemaster.digiminermod.DigiMinerMod;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public final class DroneNetworking {
	private DroneNetworking() {}

	public record OpenPayload(long blockPos, int mode) implements CustomPacketPayload {
		public static final Type<OpenPayload> TYPE = new Type<>(DigiMinerMod.id("open_drone"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenPayload> CODEC = StreamCodec.composite(
				ByteBufCodecs.LONG, OpenPayload::blockPos,
				ByteBufCodecs.VAR_INT, OpenPayload::mode, OpenPayload::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public record CommandPayload(long blockPos, int command) implements CustomPacketPayload {
		public static final Type<CommandPayload> TYPE = new Type<>(DigiMinerMod.id("drone_command"));
		public static final StreamCodec<RegistryFriendlyByteBuf, CommandPayload> CODEC = StreamCodec.composite(
				ByteBufCodecs.LONG, CommandPayload::blockPos,
				ByteBufCodecs.VAR_INT, CommandPayload::command, CommandPayload::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public record ProgramPayload(long blockPos, int program, int parameterOne, int parameterTwo) implements CustomPacketPayload {
		public static final Type<ProgramPayload> TYPE = new Type<>(DigiMinerMod.id("drone_program"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ProgramPayload> CODEC = StreamCodec.composite(
				ByteBufCodecs.LONG, ProgramPayload::blockPos,
				ByteBufCodecs.VAR_INT, ProgramPayload::program,
				ByteBufCodecs.VAR_INT, ProgramPayload::parameterOne,
				ByteBufCodecs.VAR_INT, ProgramPayload::parameterTwo,
				ProgramPayload::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public static void registerServer() {
		PayloadTypeRegistry.clientboundPlay().register(OpenPayload.TYPE, OpenPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(CommandPayload.TYPE, CommandPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ProgramPayload.TYPE, ProgramPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(CommandPayload.TYPE, (payload, context) -> {
			var pos = net.minecraft.core.BlockPos.of(payload.blockPos());
			if (!(context.player().level().getBlockEntity(pos) instanceof DroneBlockEntity drone)
					|| !drone.isOwner(context.player()) || !pos.closerToCenterThan(context.player().position(), 16.0)) return;
			if (payload.command() >= 0 && payload.command() < DroneMode.values().length) {
				drone.setMode(DroneMode.byId(payload.command()));
				return;
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(ProgramPayload.TYPE, (payload, context) -> {
			var pos = net.minecraft.core.BlockPos.of(payload.blockPos());
			if (!(context.player().level().getBlockEntity(pos) instanceof DroneBlockEntity drone)
					|| !drone.isOwner(context.player()) || !pos.closerToCenterThan(context.player().position(), 16.0)) return;
			drone.startProgram(payload.program(), payload.parameterOne(), payload.parameterTwo());
		});
	}

	public static void open(ServerPlayer player, DroneBlockEntity drone) {
		ServerPlayNetworking.send(player, new OpenPayload(drone.getBlockPos().asLong(), drone.getMode().ordinal()));
	}
}
